import { Injectable, signal, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subject, map, throwError, of, firstValueFrom } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { FileAnalysis } from '../../shared/models/file-analysis.model';
import { ConfigService } from './config.service';
import { LoggerService } from './logger.service';
import { TableClassificationResponse, StoredAnalysis } from './fta-classifier.service';
import { TableClassifierService } from './table-classifier.service';
import { generateId } from '../../shared/utils/uuid.util';

@Injectable({
  providedIn: 'root',
})
export class AnalysisService {
  private configService = inject(ConfigService);
  private logger = inject(LoggerService);
  private tableClassifierService = inject(TableClassifierService);

  private analysesSubject = new BehaviorSubject<FileAnalysis[]>([]);

  analyses = signal<FileAnalysis[]>([]);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string>('');

  private newAnalysisSubject = new Subject<string>();
  newAnalysis$ = this.newAnalysisSubject.asObservable();

  constructor() {
    this.loadAnalysesFromBackend();
  }

  loadAnalysesFromBackend(): void {
    this.isLoading.set(true);
    this.errorMessage.set('');

    this.tableClassifierService.getAllAnalyses().subscribe({
      next: (storedAnalyses: StoredAnalysis[]) => {
        const fileAnalyses: FileAnalysis[] = storedAnalyses.map(stored => this.convertStoredAnalysisToFileAnalysis(stored));
        // Sort analyses by most recent first
        const sortedAnalyses = fileAnalyses.sort(
          (a, b) => new Date(b.lastAnalyzedAt).getTime() - new Date(a.lastAnalyzedAt).getTime()
        );
        this.analyses.set(sortedAnalyses);
        this.analysesSubject.next(sortedAnalyses);
        this.isLoading.set(false);
      },
      error: (error) => {
        this.logger.error('Failed to load analyses', error, 'AnalysisService');
        this.errorMessage.set('Failed to load analyses');
        this.isLoading.set(false);
      }
    });
  }

  addAnalysis(analysis: FileAnalysis): void {
    // The analysis is already stored in backend by FileUploadController
    // Just update the frontend state
    const currentAnalyses = this.analyses();
    const updatedAnalyses = [analysis, ...currentAnalyses];
    this.analyses.set(updatedAnalyses);
    this.analysesSubject.next(updatedAnalyses);

    this.newAnalysisSubject.next(analysis.id);
  }

  removeAnalysis(analysisId: string): void {
    this.tableClassifierService.deleteAnalysis(analysisId).subscribe({
      next: () => {
        const currentAnalyses = this.analyses();
        const updatedAnalyses = currentAnalyses.filter(analysis => analysis.id !== analysisId);
        this.analyses.set(updatedAnalyses);
        this.analysesSubject.next(updatedAnalyses);
      },
      error: (error) => {
        this.logger.error('Failed to delete analysis', error, 'AnalysisService');
      }
    });
  }

  clearAllAnalyses(): void {
    this.tableClassifierService.deleteAllAnalyses().subscribe({
      next: () => {
        this.analyses.set([]);
        this.analysesSubject.next([]);
      },
      error: (error) => {
        this.logger.error('Failed to clear analyses', error, 'AnalysisService');
      }
    });
  }

  getAnalyses(): Observable<FileAnalysis[]> {
    return this.analysesSubject.asObservable();
  }

  getAnalysisById(id: string): FileAnalysis | undefined {
    return this.analyses().find(analysis => analysis.id === id);
  }

  processAnalysisResult(fileName: string, response: TableClassificationResponse): FileAnalysis {
    const now = new Date();
    return {
      id: response.analysisId || generateId(), // Use backend's analysisId if available
      fileName: fileName,
      uploadTime: now,
      lastAnalyzedAt: now,
      classificationResults: response,
      tableData: response.data || [],
      dynamicColumns: Object.entries(response.columnClassifications || {}).map(
        ([fieldName, classification]) => ({
          field: fieldName,
          header: fieldName,
          baseType: classification.baseType || (classification as any)['base_type'] || 'unknown',
          semanticType: classification.semanticType || (classification as any)['semantic_type'] || 'none',
          confidence: classification.confidence || 0,
          pattern: classification.pattern,
          isBuiltIn: classification.isBuiltIn ?? (classification as any)['is_built_in'],
        })
      ),
      originalData: response.data || [],
      isExpanded: false,
    };
  }

  exportAnalysisResults(analysisId: string, format: 'csv' | 'json'): Observable<Blob> {
    const analysis = this.getAnalysisById(analysisId);
    if (!analysis) {
      return throwError(() => new Error('Analysis not found'));
    }

    let data: string;
    let mimeType: string;

    if (format === 'csv') {
      // Convert to CSV
      const headers = analysis.dynamicColumns.map(col => col.field).join(',');
      const rows = analysis.tableData.map(row => 
        analysis.dynamicColumns.map(col => {
          const value = row[col.field];
          // Escape values containing commas or quotes
          if (typeof value === 'string' && (value.includes(',') || value.includes('"'))) {
            return `"${value.replace(/"/g, '""')}"`;
          }
          return value ?? '';
        }).join(',')
      ).join('\n');
      data = `${headers}\n${rows}`;
      mimeType = 'text/csv';
    } else {
      // Export as JSON
      try {
        data = JSON.stringify({
          analysisId: analysis.id,
          fileName: analysis.fileName,
          analyzedAt: analysis.lastAnalyzedAt,
          columns: analysis.dynamicColumns,
          data: analysis.tableData
        }, null, 2);
      } catch (error: unknown) {
        // Handle circular references or other JSON stringify errors
        const errorMessage = error instanceof Error ? error.message : 'Failed to export as JSON';
        return throwError(() => new Error(errorMessage));
      }
      mimeType = 'application/json';
    }

    const blob = new Blob([data], { type: mimeType });
    return of(blob);
  }

  async reanalyzeAllAnalyses(): Promise<void> {
    let currentAnalyses = [...this.analyses()]; // Create a mutable copy

    for (const analysis of currentAnalyses) {
      try {
        // Use the actual reanalyze endpoint
        const response = await firstValueFrom(
          this.tableClassifierService.reanalyzeWithUpdatedTypes(analysis.id)
        );

        // Convert response to FileAnalysis
        const updatedAnalysis = this.processAnalysisResult(analysis.fileName, response);
        updatedAnalysis.id = analysis.id; // Keep the same ID

        const index = currentAnalyses.findIndex(a => a.id === analysis.id);
        if (index !== -1) {
          currentAnalyses[index] = updatedAnalysis; // Update the mutable array directly
          this.analyses.set([...currentAnalyses]); // Set a new array reference
          this.analysesSubject.next([...currentAnalyses]); // Notify subscribers with a new array
        }
      } catch (error: unknown) {
        this.logger.error(`Failed to reanalyze ${analysis.id}`, error, 'AnalysisService');
        throw error;
      }
    }
  }

  regenerateSemanticTypes(analysisId: string, _columnKeys: string[]): Observable<FileAnalysis> {
    const analysis = this.getAnalysisById(analysisId);

    if (!analysis) {
      return throwError(() => new Error('Analysis not found'));
    }

    // For now, reanalyze the entire analysis
    // In the future, backend could support column-specific regeneration
    // _columnKeys parameter is preserved for future column-specific regeneration
    return this.tableClassifierService.reanalyzeWithUpdatedTypes(analysisId).pipe(
      map((response: TableClassificationResponse) => {
        const updatedAnalysis = this.processAnalysisResult(analysis.fileName, response);
        updatedAnalysis.id = analysisId; // Keep the same ID

        const currentAnalyses = this.analyses();
        const index = currentAnalyses.findIndex(a => a.id === analysisId);
        if (index !== -1) {
          const updatedAnalyses = [...currentAnalyses];
          updatedAnalyses[index] = updatedAnalysis;
          this.analyses.set(updatedAnalyses);
          this.analysesSubject.next(updatedAnalyses);
        }
        return updatedAnalysis;
      }),
      catchError(error => {
        this.logger.error('Failed to regenerate semantic types', error, 'AnalysisService');
        return throwError(() => error);
      })
    );
  }

  private convertStoredAnalysisToFileAnalysis(stored: StoredAnalysis): FileAnalysis {
    return {
      id: stored.analysisId,
      fileName: stored.fileName,
      uploadTime: new Date(stored.timestamp),
      lastAnalyzedAt: new Date(stored.timestamp),
      classificationResults: stored.response,
      tableData: stored.data || [],
      dynamicColumns: Object.entries(stored.response.columnClassifications || {}).map(
        ([fieldName, classification]) => ({
          field: fieldName,
          header: fieldName,
          baseType: classification.baseType || (classification as any)['base_type'] || 'unknown',
          semanticType: classification.semanticType || (classification as any)['semantic_type'] || 'none',
          confidence: classification.confidence || 0,
          pattern: classification.pattern,
          isBuiltIn: classification.isBuiltIn ?? (classification as any)['is_built_in'],
        })
      ),
      originalData: stored.data || [],
      isExpanded: false,
    };
  }
}
