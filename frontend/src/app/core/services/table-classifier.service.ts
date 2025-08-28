import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { retry, timeout, catchError, map } from 'rxjs/operators';
import { ConfigService } from './config.service';
import {
  TableClassificationRequest,
  TableClassificationResponse,
  StoredAnalysis,
  FieldData,
} from './fta-classifier.service';
import { API_ENDPOINTS, buildApiUrl } from '../config/api-endpoints.config';
import { LoggerService } from './logger.service';

@Injectable({
  providedIn: 'root',
})
export class TableClassifierService {
  private configService = inject(ConfigService);
  private http = inject(HttpClient);
  private logger = inject(LoggerService);

  private get config() {
    return this.configService.getConfig();
  }

  private get apiUrl(): string {
    return this.configService.apiUrl;
  }

  classifyTable(request: TableClassificationRequest): Observable<TableClassificationResponse> {
    const url = buildApiUrl(API_ENDPOINTS.CLASSIFY_TABLE, this.apiUrl);
    this.logger.info('[TABLE-CLASSIFIER] Classifying table', {
      url,
      tableName: request.tableName,
      columns: request.columns,
      dataRows: request.data?.length,
      includeStatistics: request.includeStatistics,
      useAllSemanticTypes: request.useAllSemanticTypes,
      customOnly: request.customOnly
    }, 'TableClassifierService');
    
    // Transform camelCase to snake_case for backend compatibility
    const transformedRequest = {
      table_name: request.tableName,
      columns: request.columns,
      data: request.data,
      max_samples: request.maxSamples,
      locale: request.locale,
      include_statistics: request.includeStatistics ?? true,
      // Ensure frontend uses combined semantic types (converted built-ins + customs)
      use_all_semantic_types: request.useAllSemanticTypes ?? true,
      custom_only: request.customOnly ?? false
    };
    this.logger.debug('[TABLE-CLASSIFIER] Transformed request', transformedRequest, 'TableClassifierService');
    
    return this.http.post<unknown>(url, transformedRequest).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      map(response => {
        this.logger.debug('[TABLE-CLASSIFIER] Response received', response, 'TableClassifierService');
        const transformed = this.transformResponseToCamelCase(response);
        this.logger.debug('[TABLE-CLASSIFIER] Transformed response', transformed, 'TableClassifierService');
        return transformed;
      }),
      catchError(error => {
        this.logger.error('[TABLE-CLASSIFIER] Request failed', error, 'TableClassifierService');
        this.logger.error('[TABLE-CLASSIFIER] Error details', {
          status: error.status,
          statusText: error.statusText,
          message: error.message,
          url: error.url,
          error: error.error
        }, 'TableClassifierService');
        return this.handleError(error);
      })
    );
  }

  analyzeTable(
    file: File,
    tableName?: string,
    maxSamples?: number,
    locale?: string
  ): Observable<TableClassificationResponse> {
    const formData = new FormData();
    formData.append('file', file);
    
    if (tableName) {
      formData.append('tableName', tableName);
    }
    if (maxSamples !== undefined) {
      formData.append('maxSamples', maxSamples.toString());
    }
    if (locale) {
      formData.append('locale', locale);
    }

    const url = buildApiUrl(API_ENDPOINTS.UPLOAD_FILE, this.apiUrl);
    return this.http.post<unknown>(url, formData).pipe(
      timeout(this.config?.httpLongTimeoutMs || 60000),
      retry(this.config?.httpRetryCount || 2),
      map(response => this.transformResponseToCamelCase(response)),
      catchError(error => this.handleError(error))
    );
  }

  reanalyzeWithUpdatedTypes(analysisId: string): Observable<TableClassificationResponse> {
    const url = buildApiUrl(`${API_ENDPOINTS.REANALYZE}/${encodeURIComponent(analysisId)}`, this.apiUrl);
    return this.http.post<unknown>(url, {}).pipe(
      timeout(this.config?.httpLongTimeoutMs || 60000),
      retry(this.config?.httpRetryCount || 2),
      map(response => this.transformResponseToCamelCase(response)),
      catchError(error => this.handleError(error))
    );
  }

  getAllAnalyses(): Observable<StoredAnalysis[]> {
    const url = buildApiUrl(API_ENDPOINTS.GET_ANALYSES, this.apiUrl);
    return this.http.get<unknown[]>(url).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      map(analyses => analyses.map(analysis => this.transformStoredAnalysisToCamelCase(analysis))),
      catchError(error => this.handleError(error))
    );
  }

  deleteAllAnalyses(): Observable<void> {
    const url = buildApiUrl(API_ENDPOINTS.DELETE_ANALYSES, this.apiUrl);
    return this.http.delete<void>(url).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  deleteAnalysis(analysisId: string): Observable<void> {
    const url = buildApiUrl(`${API_ENDPOINTS.DELETE_ANALYSES}/${encodeURIComponent(analysisId)}`, this.apiUrl);
    return this.http.delete<void>(url).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  private handleError(error: unknown): Observable<never> {
    this.logger.error('Table classifier service error', error, 'TableClassifierService');
    return throwError(() => error);
  }

  private transformResponseToCamelCase(response: unknown): TableClassificationResponse {
    if (!response || typeof response !== 'object') {
      return {
        tableName: '',
        columnClassifications: {},
        processingMetadata: {
          totalColumns: 0,
          analyzedColumns: 0,
          totalRowsProcessed: 0,
          processingTimeMs: 0,
          ftaVersion: '',
          localeUsed: ''
        }
      };
    }
    const responseObj = response as Record<string, unknown>;
    const processingMetadata = responseObj['processing_metadata'] as Record<string, unknown>;
    const transformed: TableClassificationResponse = {
      tableName: (responseObj['table_name'] as string) || (responseObj['tableName'] as string) || '',
      columnClassifications: {},
      processingMetadata: {
        totalColumns: processingMetadata?.['total_columns'] as number,
        analyzedColumns: processingMetadata?.['analyzed_columns'] as number,
        totalRowsProcessed: processingMetadata?.['total_rows_processed'] as number,
        processingTimeMs: processingMetadata?.['processing_time_ms'] as number,
        ftaVersion: processingMetadata?.['fta_version'] as string,
        localeUsed: processingMetadata?.['locale_used'] as string,
      },
      data: this.transformDataToRecords(responseObj['data'] as unknown[][] | Record<string, unknown>[]),
      analysisId: responseObj['analysis_id'] as string,
    };

    // Transform column classifications
    if (responseObj['column_classifications']) {
      for (const [key, value] of Object.entries(responseObj['column_classifications'] as Record<string, unknown>)) {
        const valueObj = value as Record<string, unknown>;
        const statistics = valueObj['statistics'] as Record<string, unknown>;
        transformed.columnClassifications[key] = {
          columnName: valueObj['column_name'] as string,
          baseType: valueObj['base_type'] as string,
          semanticType: valueObj['semantic_type'] as string,
          typeModifier: valueObj['type_modifier'] as string,
          confidence: valueObj['confidence'] as number,
          pattern: valueObj['pattern'] as string,
          description: valueObj['description'] as string,
          isBuiltIn: valueObj['is_built_in'] as boolean,
          statistics: statistics ? {
            sampleCount: statistics['sample_count'] as number,
            nullCount: statistics['null_count'] as number,
            blankCount: statistics['blank_count'] as number,
            distinctCount: statistics['distinct_count'] as number,
            minValue: statistics['min_value'] as string,
            maxValue: statistics['max_value'] as string,
            minLength: statistics['min_length'] as number,
            maxLength: statistics['max_length'] as number,
            mean: statistics['mean'] as number,
            standardDeviation: statistics['standard_deviation'] as number,
            cardinality: statistics['cardinality'] as number,
            uniqueness: statistics['uniqueness'] as number,
          } : undefined,
        };
      }
    }

    return transformed;
  }

  private transformStoredAnalysisToCamelCase(analysis: unknown): StoredAnalysis {
    const analysisObj = analysis as Record<string, unknown>;
    return {
      analysisId: (analysisObj['analysis_id'] as string) || (analysisObj['analysisId'] as string),
      fileName: (analysisObj['file_name'] as string) || (analysisObj['fileName'] as string),
      timestamp: analysisObj['timestamp'] as string,
      response: this.transformResponseToCamelCase(analysisObj['response']),
      fields: this.transformFieldsData(analysisObj['fields']),
      columns: (analysisObj['columns'] as string[]) || [],
      data: this.transformDataToRecords(analysisObj['data'] as unknown[][] | Record<string, unknown>[]),
      locale: (analysisObj['locale'] as string) || 'en-US'
    };
  }

  private transformDataToRecords(data: unknown[][] | Record<string, unknown>[] | undefined): Record<string, string | number | boolean | null>[] {
    if (!data || !Array.isArray(data)) {
      return [];
    }
    
    if (data.length === 0) {
      return [];
    }
    
    // Check if data is already in record format (array of objects)
    if (typeof data[0] === 'object' && !Array.isArray(data[0])) {
      // Data is already in the correct format, just ensure proper typing
      return data as Record<string, string | number | boolean | null>[];
    }
    
    // Otherwise, assume it's a 2D array with headers in the first row
    const headers = data[0] as string[];
    const records: Record<string, string | number | boolean | null>[] = [];
    
    for (let i = 1; i < data.length; i++) {
      const row = data[i] as unknown[];
      const record: Record<string, string | number | boolean | null> = {};
      
      headers.forEach((header, index) => {
        const value = row[index];
        record[header] = value as string | number | boolean | null;
      });
      
      records.push(record);
    }
    
    return records;
  }

  private transformFieldsData(fields: unknown): FieldData[] {
    if (!fields || !Array.isArray(fields)) {
      return [];
    }
    
    return fields.map(field => {
      if (typeof field === 'string') {
        // Legacy format - just field names
        return {
          fieldName: field,
          currentSemanticType: '',
          currentConfidence: 0,
          sampleValues: []
        };
      } else {
        // New format with full FieldData
        const fieldObj = field as Record<string, unknown>;
        return {
          fieldName: fieldObj['fieldName'] as string || '',
          currentSemanticType: fieldObj['currentSemanticType'] as string || '',
          currentConfidence: fieldObj['currentConfidence'] as number || 0,
          sampleValues: (fieldObj['sampleValues'] as string[]) || []
        };
      }
    });
  }
}