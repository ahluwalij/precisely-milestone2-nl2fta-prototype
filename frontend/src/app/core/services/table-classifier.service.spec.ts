import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { TableClassifierService } from './table-classifier.service';
import { ConfigService } from './config.service';
import { LoggerService } from './logger.service';
import { TableClassificationRequest } from './fta-classifier.service';

describe('TableClassifierService', () => {
  let service: TableClassifierService;
  let httpClient: jasmine.SpyObj<HttpClient>;
  let configService: jasmine.SpyObj<ConfigService>;
  let loggerService: jasmine.SpyObj<LoggerService>;

  const mockConfig = {
    baseZIndex: 10000,
    maxFileSize: 10485760,
    maxRows: 1000,
    apiUrl: '/api',
    httpTimeoutMs: 30000,
    httpRetryCount: 2,
    httpLongTimeoutMs: 60000,
    defaultHighThreshold: 95,
    defaultMediumThreshold: 80,
    defaultLowThreshold: 50,
    highThresholdMin: 90,
    highThresholdMax: 100,
    mediumThresholdMin: 70,
    mediumThresholdMax: 89,
    lowThresholdMin: 0,
    lowThresholdMax: 69,
    notificationDelayMs: 3000,
    reanalysisDelayMs: 1000,
  };

  const mockClassificationResponse = {
    table_name: 'test_table',
    column_classifications: {
      'name': {
        column_name: 'name',
        base_type: 'STRING',
        semantic_type: 'PERSON_NAME',
        confidence: 0.95,
        pattern: '[A-Za-z ]+',
        statistics: {
          sample_count: 100,
          null_count: 0,
          blank_count: 1,
          distinct_count: 95,
          min_length: 3,
          max_length: 25
        }
      }
    },
    processing_metadata: {
      total_columns: 1,
      analyzed_columns: 1,
      total_rows_processed: 100,
      processing_time_ms: 1500,
      fta_version: '1.0.0',
      locale_used: 'en-US'
    },
    analysis_id: 'test-analysis-123'
  };

  beforeEach(() => {
    const httpSpy = jasmine.createSpyObj('HttpClient', ['post', 'get', 'delete']);
    const configSpy = jasmine.createSpyObj('ConfigService', ['getConfig'], {
      apiUrl: '/api'
    });
    const loggerSpy = jasmine.createSpyObj('LoggerService', ['info', 'debug', 'error', 'warn']);

    configSpy.getConfig.and.returnValue(mockConfig);

    TestBed.configureTestingModule({
      providers: [
        TableClassifierService,
        { provide: HttpClient, useValue: httpSpy },
        { provide: ConfigService, useValue: configSpy },
        { provide: LoggerService, useValue: loggerSpy }
      ]
    });

    service = TestBed.inject(TableClassifierService);
    httpClient = TestBed.inject(HttpClient) as jasmine.SpyObj<HttpClient>;
    configService = TestBed.inject(ConfigService) as jasmine.SpyObj<ConfigService>;
    loggerService = TestBed.inject(LoggerService) as jasmine.SpyObj<LoggerService>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('classifyTable', () => {
    it('should successfully classify a table with proper request transformation', (done) => {
      const request: TableClassificationRequest = {
        tableName: 'test_table',
        columns: ['name', 'email'],
        data: [
          { name: 'John Doe', email: 'john@example.com' },
          { name: 'Jane Smith', email: 'jane@example.com' }
        ],
        maxSamples: 1000,
        locale: 'en-US',
        includeStatistics: true
      };

      httpClient.post.and.returnValue(of(mockClassificationResponse));

      service.classifyTable(request).subscribe({
        next: (response) => {
          // Verify the response is transformed to camelCase
          expect(response.tableName).toBe('test_table');
          expect(response.columnClassifications).toBeDefined();
          expect(response.columnClassifications['name']).toBeDefined();
          expect(response.columnClassifications['name'].baseType).toBe('STRING');
          expect(response.columnClassifications['name'].semanticType).toBe('PERSON_NAME');
          expect(response.processingMetadata.totalColumns).toBe(1);
          expect(response.analysisId).toBe('test-analysis-123');
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });

      // Verify the request was transformed to snake_case (allow additional properties)
      expect(httpClient.post).toHaveBeenCalledWith(
        '/api/classify/table',
        jasmine.objectContaining({
          table_name: 'test_table',
          columns: ['name', 'email'],
          data: request.data,
          max_samples: 1000,
          locale: 'en-US',
          include_statistics: true,
        })
      );
    });

    it('should handle classification errors gracefully', (done) => {
      const request: TableClassificationRequest = {
        tableName: 'test_table',
        columns: ['name'],
        data: [{ name: 'John' }]
      };

      const mockError = {
        status: 500,
        statusText: 'Internal Server Error',
        message: 'Classification failed',
        error: { detail: 'Backend processing error' }
      };

      httpClient.post.and.returnValue(throwError(() => mockError));

      service.classifyTable(request).subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (error) => {
          expect(error).toBeDefined();
          expect(loggerService.error).toHaveBeenCalledWith('Table classifier service error', mockError, 'TableClassifierService');
          done();
        }
      });
    });

    it('should use default config values when config is unavailable', (done) => {
      configService.getConfig.and.returnValue(undefined as any);
      httpClient.post.and.returnValue(of(mockClassificationResponse));

      const request: TableClassificationRequest = {
        tableName: 'test',
        columns: ['col1'],
        data: [{ col1: 'value' }]
      };

      service.classifyTable(request).subscribe({
        next: () => {
          // Should still work with default timeout/retry values
          expect(httpClient.post).toHaveBeenCalled();
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });
    });

    it('should use default timeout when httpTimeoutMs is 0', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpTimeoutMs: 0, httpRetryCount: 2 });
      httpClient.post.and.returnValue(of(mockClassificationResponse));

      const request: TableClassificationRequest = {
        tableName: 'test',
        columns: ['col1'],
        data: [{ col1: 'value' }]
      };

      service.classifyTable(request).subscribe({
        next: () => {
          expect(httpClient.post).toHaveBeenCalled();
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });
    });

    it('should use default retry count when httpRetryCount is 0', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpTimeoutMs: 30000, httpRetryCount: 0 });
      httpClient.post.and.returnValue(of(mockClassificationResponse));

      const request: TableClassificationRequest = {
        tableName: 'test',
        columns: ['col1'],
        data: [{ col1: 'value' }]
      };

      service.classifyTable(request).subscribe({
        next: () => {
          expect(httpClient.post).toHaveBeenCalled();
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });
    });
  });

  describe('analyzeTable', () => {
    it('should successfully analyze a file', (done) => {
      const mockFile = new File(['name,email\nJohn,john@test.com'], 'test.csv', { type: 'text/csv' });
      httpClient.post.and.returnValue(of(mockClassificationResponse));

      service.analyzeTable(mockFile, 'test_table', 1000, 'en-US').subscribe({
        next: (response) => {
          expect(response.tableName).toBe('test_table');
          expect(response.analysisId).toBe('test-analysis-123');
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });

      // Verify FormData was created correctly
      expect(httpClient.post).toHaveBeenCalledWith(
        '/api/table-classification/analyze',
        jasmine.any(FormData)
      );
    });

    it('should handle file analysis errors', (done) => {
      const mockFile = new File(['invalid,data'], 'invalid.csv', { type: 'text/csv' });
      const testError = new Error('File processing failed');
      httpClient.post.and.returnValue(throwError(() => testError));

      service.analyzeTable(mockFile).subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (error) => {
          expect(error.message).toContain('File processing failed');
          expect(loggerService.error).toHaveBeenCalledWith('Table classifier service error', testError, 'TableClassifierService');
          done();
        }
      });
    });

    it('should use default config values for long timeout when config is null', (done) => {
      configService.getConfig.and.returnValue(null as any);
      const mockFile = new File(['name\nJohn'], 'test.csv', { type: 'text/csv' });
      httpClient.post.and.returnValue(of(mockClassificationResponse));

      service.analyzeTable(mockFile).subscribe({
        next: (response) => {
          expect(response.tableName).toBe('test_table');
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });
    });

    it('should use default long timeout when httpLongTimeoutMs is 0', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpLongTimeoutMs: 0, httpRetryCount: 2 });
      const mockFile = new File(['name\nJane'], 'test.csv', { type: 'text/csv' });
      httpClient.post.and.returnValue(of(mockClassificationResponse));

      service.analyzeTable(mockFile).subscribe({
        next: (response) => {
          expect(response.tableName).toBe('test_table');
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });
    });

    it('should use default retry count when httpRetryCount is 0 for file analysis', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpLongTimeoutMs: 60000, httpRetryCount: 0 });
      const mockFile = new File(['name\nBob'], 'test.csv', { type: 'text/csv' });
      httpClient.post.and.returnValue(of(mockClassificationResponse));

      service.analyzeTable(mockFile).subscribe({
        next: (response) => {
          expect(response.tableName).toBe('test_table');
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });
    });
  });

  describe('getAllAnalyses', () => {
    it('should fetch all stored analyses', (done) => {
      const mockAnalyses = [
        {
          analysis_id: 'analysis-1',
          file_name: 'data1.csv',
          timestamp: '2024-01-01T00:00:00Z',
          response: mockClassificationResponse,
          data: [{ name: 'John' }]
        }
      ];

      httpClient.get.and.returnValue(of(mockAnalyses));

      service.getAllAnalyses().subscribe({
        next: (analyses) => {
          expect(analyses.length).toBe(1);
          expect(analyses[0].analysisId).toBe('analysis-1');
          expect(analyses[0].fileName).toBe('data1.csv');
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });

      expect(httpClient.get).toHaveBeenCalledWith('/api/analyses');
    });

    it('should handle errors when fetching analyses', (done) => {
      const testError = new Error('Failed to fetch analyses');
      httpClient.get.and.returnValue(throwError(() => testError));

      service.getAllAnalyses().subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (error) => {
          expect(error.message).toBe('Failed to fetch analyses');
          expect(loggerService.error).toHaveBeenCalledWith('Table classifier service error', testError, 'TableClassifierService');
          done();
        }
      });
    });

    it('should use default config values when config is null', (done) => {
      configService.getConfig.and.returnValue(null as any);
      const mockAnalyses = [
        {
          analysis_id: 'analysis-1',
          file_name: 'data1.csv',
          timestamp: '2024-01-01T00:00:00Z',
          response: mockClassificationResponse,
          data: [{ name: 'John' }]
        }
      ];
      httpClient.get.and.returnValue(of(mockAnalyses));

      service.getAllAnalyses().subscribe({
        next: (analyses) => {
          expect(analyses.length).toBe(1);
          expect(analyses[0].analysisId).toBe('analysis-1');
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });
    });

    it('should use default timeout when httpTimeoutMs is 0', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpTimeoutMs: 0, httpRetryCount: 2 });
      const mockAnalyses = [
        {
          analysis_id: 'analysis-2',
          file_name: 'data2.csv',
          timestamp: '2024-01-02T00:00:00Z',
          response: mockClassificationResponse,
          data: [{ name: 'Jane' }]
        }
      ];
      httpClient.get.and.returnValue(of(mockAnalyses));

      service.getAllAnalyses().subscribe({
        next: (analyses) => {
          expect(analyses.length).toBe(1);
          expect(analyses[0].analysisId).toBe('analysis-2');
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });
    });

    it('should use default retry count when httpRetryCount is 0', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpTimeoutMs: 30000, httpRetryCount: 0 });
      const mockAnalyses = [
        {
          analysis_id: 'analysis-3',
          file_name: 'data3.csv',
          timestamp: '2024-01-03T00:00:00Z',
          response: mockClassificationResponse,
          data: [{ name: 'Bob' }]
        }
      ];
      httpClient.get.and.returnValue(of(mockAnalyses));

      service.getAllAnalyses().subscribe({
        next: (analyses) => {
          expect(analyses.length).toBe(1);
          expect(analyses[0].analysisId).toBe('analysis-3');
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });
    });
  });

  describe('deleteAnalysis', () => {
    it('should successfully delete an analysis', (done) => {
      httpClient.delete.and.returnValue(of({}));

      service.deleteAnalysis('analysis-123').subscribe({
        next: () => {
          expect(httpClient.delete).toHaveBeenCalledWith('/api/analyses/analysis-123');
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });
    });

    it('should handle delete errors', (done) => {
      const testError = new Error('Delete failed');
      httpClient.delete.and.returnValue(throwError(() => testError));

      service.deleteAnalysis('analysis-123').subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (error) => {
          expect(error.message).toBe('Delete failed');
          expect(loggerService.error).toHaveBeenCalledWith('Table classifier service error', testError, 'TableClassifierService');
          done();
        }
      });
    });
  });

  describe('deleteAllAnalyses', () => {
    it('should successfully delete all analyses', (done) => {
      httpClient.delete.and.returnValue(of({}));

      service.deleteAllAnalyses().subscribe({
        next: () => {
          expect(httpClient.delete).toHaveBeenCalledWith('/api/analyses');
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });
    });
  });

  describe('reanalyzeWithUpdatedTypes', () => {
    it('should successfully trigger reanalysis', (done) => {
      httpClient.post.and.returnValue(of(mockClassificationResponse));

      service.reanalyzeWithUpdatedTypes('analysis-123').subscribe({
        next: (response) => {
          expect(response.analysisId).toBe('test-analysis-123');
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });

      expect(httpClient.post).toHaveBeenCalledWith('/api/table-classification/reanalyze/analysis-123', {});
    });
  });

  describe('response transformation', () => {
    it('should properly transform snake_case response to camelCase', (done) => {
      const snakeCaseResponse = {
        table_name: 'test',
        column_classifications: {
          'test_column': {
            column_name: 'test_column',
            base_type: 'STRING',
            semantic_type: 'CUSTOM',
            type_modifier: 'NULLABLE',
            confidence: 0.85
          }
        },
        processing_metadata: {
          total_columns: 1,
          analyzed_columns: 1,
          total_rows_processed: 50,
          processing_time_ms: 1200,
          fta_version: '2.0.0',
          locale_used: 'en-GB'
        }
      };

      httpClient.post.and.returnValue(of(snakeCaseResponse));

      const request: TableClassificationRequest = {
        tableName: 'test',
        columns: ['test_column'],
        data: [{ test_column: 'value' }]
      };

      service.classifyTable(request).subscribe({
        next: (response) => {
          expect(response.tableName).toBe('test');
          expect(response.columnClassifications['test_column'].columnName).toBe('test_column');
          expect(response.columnClassifications['test_column'].baseType).toBe('STRING');
          expect(response.columnClassifications['test_column'].semanticType).toBe('CUSTOM');
          expect(response.columnClassifications['test_column'].typeModifier).toBe('NULLABLE');
          expect(response.processingMetadata.totalColumns).toBe(1);
          expect(response.processingMetadata.analyzedColumns).toBe(1);
          expect(response.processingMetadata.totalRowsProcessed).toBe(50);
          expect(response.processingMetadata.processingTimeMs).toBe(1200);
          expect(response.processingMetadata.ftaVersion).toBe('2.0.0');
          expect(response.processingMetadata.localeUsed).toBe('en-GB');
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });
    });
  });
});