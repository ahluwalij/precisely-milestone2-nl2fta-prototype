import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { ConfigService } from './config.service';
import { SemanticTypeRepositoryService } from './semantic-type-repository.service';
import { TableClassifierService } from './table-classifier.service';

export interface TableClassificationRequest {
  tableName: string;
  columns: string[];
  data: Record<string, string | number | boolean | null>[];
  maxSamples?: number;
  locale?: string;
  includeStatistics?: boolean;
  customOnly?: boolean;
  useAllSemanticTypes?: boolean;
}

export interface ColumnClassification {
  columnName: string;
  baseType: string;
  semanticType: string;
  typeModifier?: string;
  confidence: number;
  pattern?: string;
  description?: string;
  isBuiltIn?: boolean;
  statistics?: {
    sampleCount: number;
    nullCount: number;
    blankCount: number;
    distinctCount: number;
    minValue?: string;
    maxValue?: string;
    minLength?: number;
    maxLength?: number;
    mean?: number;
    standardDeviation?: number;
    cardinality?: number;
    uniqueness?: number;
  };
}

export interface TableClassificationResponse {
  tableName: string;
  columnClassifications: { [key: string]: ColumnClassification };
  processingMetadata: {
    totalColumns: number;
    analyzedColumns: number;
    totalRowsProcessed: number;
    processingTimeMs: number;
    ftaVersion: string;
    localeUsed: string;
  };
  data?: Record<string, string | number | boolean | null>[];
  analysisId?: string;
}

@Injectable({
  providedIn: 'root',
})
export class FtaClassifierService {
  private http = inject(HttpClient);
  private configService = inject(ConfigService);
  private semanticTypeRepository = inject(SemanticTypeRepositoryService);
  private tableClassifier = inject(TableClassifierService);

  // Mock data moved to dedicated services

  private get apiUrl(): string {
    return this.configService.apiUrl;
  }

  private get config() {
    return this.configService.getConfig();
  }

  // Delegate to TableClassifierService
  classifyTable(request: TableClassificationRequest): Observable<TableClassificationResponse> {
    return this.tableClassifier.classifyTable(request);
  }

  // Delegate to TableClassifierService
  analyzeTable(
    file: File,
    tableName?: string,
    maxSamples?: number,
    locale?: string
  ): Observable<TableClassificationResponse> {
    return this.tableClassifier.analyzeTable(file, tableName, maxSamples, locale);
  }

  // Delegate to TableClassifierService
  reanalyzeWithUpdatedTypes(analysisId: string): Observable<TableClassificationResponse> {
    return this.tableClassifier.reanalyzeWithUpdatedTypes(analysisId);
  }

  // Delegate to SemanticTypeRepositoryService
  getAllSemanticTypes(): Observable<CustomSemanticType[]> {
    return this.semanticTypeRepository.getAllSemanticTypes();
  }

  getCustomSemanticTypesOnly(): Observable<CustomSemanticType[]> {
    return this.semanticTypeRepository.getCustomSemanticTypesOnly();
  }

  addCustomSemanticType(customType: CustomSemanticType): Observable<CustomSemanticType> {
    return this.semanticTypeRepository.addCustomSemanticType(customType);
  }

  updateCustomSemanticType(
    semanticType: string,
    customType: CustomSemanticType
  ): Observable<CustomSemanticType> {
    return this.semanticTypeRepository.updateCustomSemanticType(semanticType, customType);
  }

  deleteSemanticType(semanticType: string): Observable<void> {
    return this.semanticTypeRepository.deleteSemanticType(semanticType);
  }

  reloadSemanticTypes(): Observable<{ message: string }> {
    return this.semanticTypeRepository.reloadSemanticTypes();
  }

  // Delegate to TableClassifierService
  getAllAnalyses(): Observable<StoredAnalysis[]> {
    return this.tableClassifier.getAllAnalyses();
  }

  deleteAllAnalyses(): Observable<void> {
    return this.tableClassifier.deleteAllAnalyses();
  }

  // Helper method moved to TableClassifierService

  private handleError(error: unknown): Observable<never> {
    return throwError(() => error);
  }
}

export interface StoredAnalysis {
  analysisId: string;
  fileName: string;
  timestamp: string;
  response: TableClassificationResponse;
  fields: FieldData[];
  columns: string[];
  data: Record<string, string | number | boolean | null>[];
  locale: string;
}

export interface FieldData {
  fieldName: string;
  currentSemanticType: string;
  currentConfidence: number;
  sampleValues: string[];
}

export interface CustomSemanticType {
  semanticType: string;
  description: string;
  pluginType: string;
  validLocales?: LocaleConfig[];
  threshold?: number;
  baseType?: string;
  documentation?: Documentation[];
  content?: ContentConfig;
  clazz?: string;
  signature?: string;
  minimum?: string;
  maximum?: string;
  minSamples?: number;
  minMaxPresent?: boolean;
  localeSensitive?: boolean;
  priority?: number;
  pluginOptions?: string;
  backout?: string;
  invalidList?: string[];
  ignoreList?: string[];
  isBuiltIn?: boolean;  // Tracks if this originated from a built-in FTA type
  createdAt?: number;   // milliseconds since epoch; 0 for built-in
}

export interface LocaleConfig {
  localeTag?: string;
  headerRegExps?: HeaderRegExp[];
  matchEntries?: MatchEntry[];
}

export interface HeaderRegExp {
  regExp: string;
  confidence?: number;
  mandatory?: boolean;
}

export interface MatchEntry {
  regExpReturned: string;
  isRegExpComplete?: boolean;
}

export interface Documentation {
  source?: string;
  reference?: string;
}

export interface ContentConfig {
  type?: string;
  reference?: string;
  values?: string[];
}
