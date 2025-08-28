import { Injectable, inject } from '@angular/core';
import * as Papa from 'papaparse';
import { Observable, Subject } from 'rxjs';
import { ConfigService } from './config.service';
import { LoggerService } from './logger.service';
import { FtaClassifierService, TableClassificationRequest } from './fta-classifier.service';
import { AnalysisService } from './analysis.service';

export interface ParseResult {
  data: Record<string, string | number | boolean | null>[];
  columns: string[];
  fileName: string;
}

export interface FileType {
  extension: string;
  mimeTypes: string[];
  description: string;
}

@Injectable({
  providedIn: 'root',
})
export class FileUploadService {
  private configService = inject(ConfigService);
  private ftaService = inject(FtaClassifierService);
  private analysisService = inject(AnalysisService);
  private logger = inject(LoggerService);

  private get MAX_FILE_SIZE(): number {
    return this.configService.maxFileSize;
  }

  readonly supportedFileTypes: FileType[] = [
    {
      extension: 'csv',
      mimeTypes: ['text/csv', 'application/csv', 'application/vnd.ms-excel'],
      description: 'CSV files',
    },
    {
      extension: 'sql',
      mimeTypes: ['application/sql', 'text/plain', 'text/sql'],
      description: 'SQL database dumps',
    },
  ];

  processFile(file: File): Observable<void> {
    this.logger.info('[FILE-UPLOAD-SERVICE] Processing file', {
      name: file.name,
      size: file.size,
      type: file.type,
      extension: this.getFileExtension(file.name),
      isSQL: this.isSQLFile(file),
      isCSV: this.isCSVFile(file)
    }, 'FileUploadService');
    const subject = new Subject<void>();

    if (this.isSQLFile(file)) {
      this.logger.info('[FILE-UPLOAD-SERVICE] Processing as SQL file', undefined, 'FileUploadService');
      this.processSQLFile(file).subscribe({
        next: () => {
          this.logger.info('[FILE-UPLOAD-SERVICE] SQL file processed successfully', undefined, 'FileUploadService');
          subject.next();
          subject.complete();
        },
        error: error => {
          this.logger.error('[FILE-UPLOAD-SERVICE] SQL file processing error', error, 'FileUploadService');
          subject.error(error);
        },
      });
    } else {
      this.logger.info('[FILE-UPLOAD-SERVICE] Processing as CSV file', undefined, 'FileUploadService');
      this.processCSVFile(file).subscribe({
        next: () => {
          this.logger.info('[FILE-UPLOAD-SERVICE] CSV file processed successfully', undefined, 'FileUploadService');
          subject.next();
          subject.complete();
        },
        error: error => {
          this.logger.error('[FILE-UPLOAD-SERVICE] CSV file processing error', error, 'FileUploadService');
          subject.error(error);
        },
      });
    }

    return subject.asObservable();
  }

  processMultipleFiles(files: File[]): Observable<{ completed: number; total: number; errors: string[] }> {
    this.logger.info('[FILE-UPLOAD-SERVICE] Processing multiple files', { count: files.length }, 'FileUploadService');
    const subject = new Subject<{ completed: number; total: number; errors: string[] }>();
    
    let completed = 0;
    const total = files.length;
    const errors: string[] = [];
    
    // Process files sequentially to avoid overwhelming the server
    const processNextFile = (index: number) => {
      if (index >= files.length) {
        // All files processed
        this.logger.info('[FILE-UPLOAD-SERVICE] All files processed', { completed, total, errors }, 'FileUploadService');
        subject.next({ completed, total, errors });
        subject.complete();
        return;
      }
      
      const file = files[index];
      this.logger.info('[FILE-UPLOAD-SERVICE] Processing file', { index: index + 1, total, name: file.name }, 'FileUploadService');
      
      this.processFile(file).subscribe({
        next: () => {
          completed++;
          this.logger.info('[FILE-UPLOAD-SERVICE] File processed successfully', { name: file.name }, 'FileUploadService');
          subject.next({ completed, total, errors });
          processNextFile(index + 1);
        },
        error: error => {
          completed++;
          const errorMsg = `${file.name}: ${error.message || 'Processing failed'}`;
          errors.push(errorMsg);
          this.logger.error('[FILE-UPLOAD-SERVICE] File processing error', { errorMsg }, 'FileUploadService');
          subject.next({ completed, total, errors });
          processNextFile(index + 1);
        }
      });
    };
    
    // Start processing from the first file
    processNextFile(0);
    
    return subject.asObservable();
  }

  private processSQLFile(file: File): Observable<void> {
    const subject = new Subject<void>();

    this.ftaService.analyzeTable(file, undefined, this.configService.maxRows).subscribe({
      next: response => {
        const fileAnalysis = this.analysisService.processAnalysisResult(file.name, response);
        this.analysisService.addAnalysis(fileAnalysis);
        subject.next();
        subject.complete();
      },
      error: error => subject.error(error),
    });

    return subject.asObservable();
  }

  private processCSVFile(file: File): Observable<void> {
    this.logger.info('[FILE-UPLOAD-SERVICE] Starting CSV file processing', undefined, 'FileUploadService');
    const subject = new Subject<void>();

    this.parseCSV(file).subscribe({
      next: parseResult => {
        this.logger.info('[FILE-UPLOAD-SERVICE] CSV parsed successfully', {
          fileName: parseResult.fileName,
          columns: parseResult.columns,
          rowCount: parseResult.data.length,
          maxRows: this.configService.maxRows
        }, 'FileUploadService');
        const request: TableClassificationRequest = {
          tableName: parseResult.fileName.replace('.csv', ''),
          columns: parseResult.columns,
          data: parseResult.data.slice(0, this.configService.maxRows),
          includeStatistics: true,
          useAllSemanticTypes: true,
          customOnly: false,
        };
        this.logger.info('[FILE-UPLOAD-SERVICE] Sending classification request', {
          tableName: request.tableName,
          columns: request.columns,
          dataRows: request.data.length
        }, 'FileUploadService');

        this.ftaService.classifyTable(request).subscribe({
          next: response => {
            this.logger.info('[FILE-UPLOAD-SERVICE] Classification response received', response, 'FileUploadService');
            const fileAnalysis = this.analysisService.processAnalysisResult(
              parseResult.fileName,
              response
            );
            this.analysisService.addAnalysis(fileAnalysis);
            subject.next();
            subject.complete();
          },
          error: error => {
            this.logger.error('[FILE-UPLOAD-SERVICE] Classification error', error, 'FileUploadService');
            subject.error(error);
          },
        });
      },
      error: error => {
        this.logger.error('[FILE-UPLOAD-SERVICE] CSV parsing error', error, 'FileUploadService');
        subject.error(error);
      },
    });

    return subject.asObservable();
  }

  validateFile(file: File): string | null {
    if (!file) {
      return 'No file selected';
    }

    // Validate file object structure
    if (
      typeof file.size !== 'number' ||
      typeof file.name !== 'string' ||
      typeof file.type !== 'string'
    ) {
      return 'Invalid file object';
    }

    // Check file name
    if (file.name.trim().length === 0) {
      return 'File name cannot be empty';
    }

    // Check for suspicious file names
    if (file.name.includes('..') || file.name.includes('/') || file.name.includes('\\')) {
      return 'Invalid file name';
    }

    // Check file size limits
    if (file.size <= 0) {
      return 'File appears to be empty';
    }

    if (file.size > this.MAX_FILE_SIZE) {
      const sizeMB = Math.round(this.MAX_FILE_SIZE / 1048576);
      return `File size exceeds ${sizeMB}MB limit`;
    }

    const fileExtension = this.getFileExtension(file.name);
    const isValidExtension = this.supportedFileTypes.some(
      type => type.extension === fileExtension.toLowerCase()
    );

    if (!isValidExtension) {
      const supportedExtensions = this.supportedFileTypes.map(t => t.extension).join(', ');
      return `Please upload a supported file type: ${supportedExtensions}`;
    }

    return null;
  }

  getFileExtension(fileName: string): string {
    const lastDotIndex = fileName.lastIndexOf('.');
    if (lastDotIndex === -1 || lastDotIndex === fileName.length - 1) {
      return '';
    }
    return fileName.substring(lastDotIndex + 1);
  }

  isCSVFile(file: File): boolean {
    const extension = this.getFileExtension(file.name);
    return extension.toLowerCase() === 'csv';
  }

  isSQLFile(file: File): boolean {
    const extension = this.getFileExtension(file.name);
    return extension.toLowerCase() === 'sql';
  }

  parseCSV(file: File): Observable<ParseResult> {
    this.logger.info('[FILE-UPLOAD-SERVICE] Starting CSV parsing', undefined, 'FileUploadService');
    const subject = new Subject<ParseResult>();

    Papa.parse(file, {
      header: true,
      skipEmptyLines: true, // Skip empty lines
      encoding: 'UTF-8', // Handle UTF-8 with BOM
      dynamicTyping: true, // Automatically convert numeric values
      complete: (result: Papa.ParseResult<unknown>) => {
        this.logger.info('[FILE-UPLOAD-SERVICE] Papa.parse complete', {
          dataLength: result.data.length,
          errors: result.errors,
          meta: result.meta
        }, 'FileUploadService');
        if (result.errors.length > 0) {
          this.logger.warn('[FILE-UPLOAD-SERVICE] CSV parsing warnings/errors', result.errors, 'FileUploadService');
          // Only treat as fatal error if it's not just a delimiter detection warning
          const fatalErrors = result.errors.filter(err => 
            err.type !== 'Delimiter' && 
            err.code !== 'UndetectableDelimiter'
          );
          if (fatalErrors.length > 0) {
            this.logger.error('[FILE-UPLOAD-SERVICE] Fatal CSV parsing errors', fatalErrors, 'FileUploadService');
            subject.error(new Error('Error parsing CSV: ' + fatalErrors[0].message));
            return;
          }
        }

        const data = result.data as Record<string, string | number | boolean | null>[];
        if (data.length === 0) {
          this.logger.error('[FILE-UPLOAD-SERVICE] No data found in CSV', undefined, 'FileUploadService');
          subject.error(new Error('No data found in CSV file'));
          return;
        }

        const firstRow = data[0];
        this.logger.debug('[FILE-UPLOAD-SERVICE] First row', firstRow, 'FileUploadService');
        if (!firstRow || typeof firstRow !== 'object') {
          this.logger.error('[FILE-UPLOAD-SERVICE] Invalid first row format', undefined, 'FileUploadService');
          subject.error(new Error('Invalid CSV format'));
          return;
        }

        const columns = Object.keys(firstRow);
        this.logger.info('[FILE-UPLOAD-SERVICE] CSV parsed successfully', {
          columns,
          rowCount: data.length,
          fileName: file.name
        }, 'FileUploadService');
        subject.next({
          data,
          columns,
          fileName: file.name,
        });
        subject.complete();
      },
      error: (error: Error) => {
        this.logger.error('[FILE-UPLOAD-SERVICE] Papa.parse error', error, 'FileUploadService');
        subject.error(new Error('Error reading file: ' + error.message));
      },
    });

    return subject.asObservable();
  }

  getAcceptedFileTypes(): string {
    return this.supportedFileTypes.map(type => `.${type.extension}`).join(',');
  }
}
