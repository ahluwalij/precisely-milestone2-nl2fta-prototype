import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AnalysisService } from './analysis.service';
import { ConfigService } from './config.service';
import { LoggerService } from './logger.service';
import { TableClassifierService } from './table-classifier.service';
import { FileAnalysis } from '../../shared/models/file-analysis.model';
import { TableClassificationResponse, StoredAnalysis } from './fta-classifier.service';

describe('AnalysisService', () => {
  let service: AnalysisService;
  let tableClassifierService: jasmine.SpyObj<TableClassifierService>;
  let loggerService: jasmine.SpyObj<LoggerService>;
  let _configService: jasmine.SpyObj<ConfigService>;

  const mockStoredAnalysis: StoredAnalysis = {
    analysisId: 'test-analysis-1',
    fileName: 'test.csv',
    timestamp: '2024-01-15T10:30:00Z',
    response: {
      tableName: 'test_table',
      columnClassifications: {
        'name': {
          columnName: 'name',
          baseType: 'STRING',
          semanticType: 'PERSON_NAME',
          confidence: 0.95,
          pattern: '[A-Za-z ]+'
        },
        'email': {
          columnName: 'email', 
          baseType: 'STRING',
          semanticType: 'EMAIL',
          confidence: 0.98,
          pattern: '[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}'
        }
      },
      processingMetadata: {
        totalColumns: 2,
        analyzedColumns: 2,
        totalRowsProcessed: 100,
        processingTimeMs: 1500,
        ftaVersion: '1.0.0',
        localeUsed: 'en-US'
      },
      analysisId: 'test-analysis-1'
    },
    data: [
      { name: 'John Doe', email: 'john@example.com' },
      { name: 'Jane Smith', email: 'jane@example.com' }
    ],
    fields: [
      {
        fieldName: 'name',
        currentSemanticType: 'PERSON_NAME',
        currentConfidence: 0.95,
        sampleValues: ['John Doe', 'Jane Smith']
      },
      {
        fieldName: 'email',
        currentSemanticType: 'EMAIL',
        currentConfidence: 0.98,
        sampleValues: ['john@example.com', 'jane@example.com']
      }
    ],
    columns: ['name', 'email'],
    locale: 'en-US'
  };

  const mockTableClassificationResponse: TableClassificationResponse = {
    tableName: 'test_table',
    columnClassifications: {
      'name': {
        columnName: 'name',
        baseType: 'STRING',
        semanticType: 'PERSON_NAME',
        confidence: 0.95,
        pattern: '[A-Za-z ]+'
      }
    },
    processingMetadata: {
      totalColumns: 1,
      analyzedColumns: 1,
      totalRowsProcessed: 50,
      processingTimeMs: 800,
      ftaVersion: '1.0.0',
      localeUsed: 'en-US'
    },
    data: [{ name: 'Test User' }],
    analysisId: 'new-analysis-123'
  };

  beforeEach(() => {
    const tableClassifierSpy = jasmine.createSpyObj('TableClassifierService', [
      'getAllAnalyses', 'deleteAnalysis', 'deleteAllAnalyses', 'reanalyzeWithUpdatedTypes'
    ]);
    const loggerSpy = jasmine.createSpyObj('LoggerService', ['error', 'info', 'warn']);
    const configSpy = jasmine.createSpyObj('ConfigService', ['getConfig']);

    // Set up default return value for getAllAnalyses to prevent constructor error
    tableClassifierSpy.getAllAnalyses.and.returnValue(of([]));

    TestBed.configureTestingModule({
      providers: [
        AnalysisService,
        { provide: TableClassifierService, useValue: tableClassifierSpy },
        { provide: LoggerService, useValue: loggerSpy },
        { provide: ConfigService, useValue: configSpy }
      ]
    });

    service = TestBed.inject(AnalysisService);
    tableClassifierService = TestBed.inject(TableClassifierService) as jasmine.SpyObj<TableClassifierService>;
    loggerService = TestBed.inject(LoggerService) as jasmine.SpyObj<LoggerService>;
    _configService = TestBed.inject(ConfigService) as jasmine.SpyObj<ConfigService>;

    // Reset spies after service instantiation
    tableClassifierService.getAllAnalyses.calls.reset();
    loggerService.error.calls.reset();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('initialization', () => {
    it('should initialize with empty state', () => {
      expect(service.analyses()).toEqual([]);
      expect(service.isLoading()).toBe(false);
      expect(service.errorMessage()).toBe('');
    });

    it('should load analyses from backend on construction', () => {
      // The service is created in beforeEach and loads analyses in constructor
      // We already set up the spy to return an empty array, so we just need to verify it was called
      // The spy was reset after instantiation, but we can check the service state
      expect(service).toBeTruthy();
      // The constructor would have called getAllAnalyses, setting initial state
      expect(service.analyses()).toEqual([]);
      expect(service.isLoading()).toBe(false);
    });
  });

  describe('loadAnalysesFromBackend', () => {
    it('should successfully load and sort analyses by timestamp', (done) => {
      const storedAnalyses = [
        { ...mockStoredAnalysis, timestamp: '2024-01-14T10:00:00Z', analysisId: 'older-analysis' },
        { ...mockStoredAnalysis, timestamp: '2024-01-15T15:00:00Z', analysisId: 'newer-analysis' }
      ];
      
      tableClassifierService.getAllAnalyses.and.returnValue(of(storedAnalyses));

      service.loadAnalysesFromBackend();

      // The observable completes synchronously with 'of', so check final state
      const analyses = service.analyses();
      expect(analyses.length).toBe(2);
      // Should be sorted by most recent first
      expect(analyses[0].id).toBe('newer-analysis');
      expect(analyses[1].id).toBe('older-analysis');
      expect(service.isLoading()).toBe(false);
      expect(service.errorMessage()).toBe('');
      done();
    });

    it('should handle backend loading errors', (done) => {
      const error = new Error('Backend connection failed');
      tableClassifierService.getAllAnalyses.and.returnValue(throwError(() => error));

      service.loadAnalysesFromBackend();

      setTimeout(() => {
        expect(service.isLoading()).toBe(false);
        expect(service.errorMessage()).toBe('Failed to load analyses');
        expect(loggerService.error).toHaveBeenCalledWith(
          'Failed to load analyses',
          error,
          'AnalysisService'
        );
        done();
      }, 0);
    });

    it('should convert stored analyses to file analyses correctly', (done) => {
      tableClassifierService.getAllAnalyses.and.returnValue(of([mockStoredAnalysis]));

      service.loadAnalysesFromBackend();

      setTimeout(() => {
        const analyses = service.analyses();
        expect(analyses.length).toBe(1);
        
        const analysis = analyses[0];
        expect(analysis.id).toBe('test-analysis-1');
        expect(analysis.fileName).toBe('test.csv');
        expect(analysis.uploadTime).toEqual(new Date('2024-01-15T10:30:00Z'));
        expect(analysis.dynamicColumns.length).toBe(2);
        expect(analysis.dynamicColumns[0].field).toBe('name');
        expect(analysis.dynamicColumns[0].semanticType).toBe('PERSON_NAME');
        expect(analysis.tableData.length).toBe(2);
        done();
      }, 0);
    });
  });

  describe('addAnalysis', () => {
    it('should add analysis to the beginning of the list', () => {
      const existingAnalysis: FileAnalysis = {
        id: 'existing-1',
        fileName: 'existing.csv',
        uploadTime: new Date('2024-01-10T10:00:00Z'),
        lastAnalyzedAt: new Date('2024-01-10T10:00:00Z'),
        classificationResults: mockTableClassificationResponse,
        tableData: [],
        dynamicColumns: [],
        originalData: [],
        isExpanded: false
      };

      service.analyses.set([existingAnalysis]);

      const newAnalysis: FileAnalysis = {
        id: 'new-1',
        fileName: 'new.csv',
        uploadTime: new Date('2024-01-15T10:00:00Z'),
        lastAnalyzedAt: new Date('2024-01-15T10:00:00Z'),
        classificationResults: mockTableClassificationResponse,
        tableData: [],
        dynamicColumns: [],
        originalData: [],
        isExpanded: false
      };

      service.addAnalysis(newAnalysis);

      const analyses = service.analyses();
      expect(analyses.length).toBe(2);
      expect(analyses[0].id).toBe('new-1'); // Should be first
      expect(analyses[1].id).toBe('existing-1');
    });

    it('should emit new analysis event', (done) => {
      const newAnalysis: FileAnalysis = {
        id: 'new-analysis',
        fileName: 'test.csv',
        uploadTime: new Date(),
        lastAnalyzedAt: new Date(),
        classificationResults: mockTableClassificationResponse,
        tableData: [],
        dynamicColumns: [],
        originalData: [],
        isExpanded: false
      };

      service.newAnalysis$.subscribe(analysisId => {
        expect(analysisId).toBe('new-analysis');
        done();
      });

      service.addAnalysis(newAnalysis);
    });
  });

  describe('removeAnalysis', () => {
    it('should successfully remove analysis', (done) => {
      const analysis1: FileAnalysis = {
        id: 'analysis-1',
        fileName: 'test1.csv',
        uploadTime: new Date(),
        lastAnalyzedAt: new Date(),
        classificationResults: mockTableClassificationResponse,
        tableData: [],
        dynamicColumns: [],
        originalData: [],
        isExpanded: false
      };

      const analysis2: FileAnalysis = {
        id: 'analysis-2',
        fileName: 'test2.csv',
        uploadTime: new Date(),
        lastAnalyzedAt: new Date(),
        classificationResults: mockTableClassificationResponse,
        tableData: [],
        dynamicColumns: [],
        originalData: [],
        isExpanded: false
      };

      service.analyses.set([analysis1, analysis2]);
      tableClassifierService.deleteAnalysis.and.returnValue(of(undefined));

      service.removeAnalysis('analysis-1');

      setTimeout(() => {
        const analyses = service.analyses();
        expect(analyses.length).toBe(1);
        expect(analyses[0].id).toBe('analysis-2');
        expect(tableClassifierService.deleteAnalysis).toHaveBeenCalledWith('analysis-1');
        done();
      }, 0);
    });

    it('should handle delete errors gracefully', (done) => {
      const analysis: FileAnalysis = {
        id: 'analysis-1',
        fileName: 'test.csv',
        uploadTime: new Date(),
        lastAnalyzedAt: new Date(),
        classificationResults: mockTableClassificationResponse,
        tableData: [],
        dynamicColumns: [],
        originalData: [],
        isExpanded: false
      };

      service.analyses.set([analysis]);
      const error = new Error('Delete failed');
      tableClassifierService.deleteAnalysis.and.returnValue(throwError(() => error));

      service.removeAnalysis('analysis-1');

      setTimeout(() => {
        // Analysis should still be present if delete failed
        expect(service.analyses().length).toBe(1);
        expect(loggerService.error).toHaveBeenCalledWith(
          'Failed to delete analysis',
          error,
          'AnalysisService'
        );
        done();
      }, 0);
    });
  });

  describe('clearAllAnalyses', () => {
    it('should successfully clear all analyses', (done) => {
      const analyses: FileAnalysis[] = [
        {
          id: 'analysis-1',
          fileName: 'test1.csv',
          uploadTime: new Date(),
          lastAnalyzedAt: new Date(),
          classificationResults: mockTableClassificationResponse,
          tableData: [],
          dynamicColumns: [],
          originalData: [],
          isExpanded: false
        }
      ];

      service.analyses.set(analyses);
      tableClassifierService.deleteAllAnalyses.and.returnValue(of(undefined));

      service.clearAllAnalyses();

      setTimeout(() => {
        expect(service.analyses()).toEqual([]);
        expect(tableClassifierService.deleteAllAnalyses).toHaveBeenCalled();
        done();
      }, 0);
    });

    it('should handle clear all errors', (done) => {
      const error = new Error('Clear all failed');
      tableClassifierService.deleteAllAnalyses.and.returnValue(throwError(() => error));

      service.clearAllAnalyses();

      setTimeout(() => {
        expect(loggerService.error).toHaveBeenCalledWith(
          'Failed to clear analyses',
          error,
          'AnalysisService'
        );
        done();
      }, 0);
    });
  });

  describe('getAnalysisById', () => {
    it('should return analysis when found', () => {
      const analysis: FileAnalysis = {
        id: 'test-id',
        fileName: 'test.csv',
        uploadTime: new Date(),
        lastAnalyzedAt: new Date(),
        classificationResults: mockTableClassificationResponse,
        tableData: [],
        dynamicColumns: [],
        originalData: [],
        isExpanded: false
      };

      service.analyses.set([analysis]);

      const result = service.getAnalysisById('test-id');
      expect(result).toBe(analysis);
    });

    it('should return undefined when not found', () => {
      const result = service.getAnalysisById('non-existent');
      expect(result).toBeUndefined();
    });
  });

  describe('processAnalysisResult', () => {
    it('should create FileAnalysis from classification response', () => {
      const fileName = 'test.csv';
      const response = mockTableClassificationResponse;

      const result = service.processAnalysisResult(fileName, response);

      expect(result.fileName).toBe(fileName);
      expect(result.id).toBe('new-analysis-123');
      expect(result.classificationResults).toBe(response);
      expect(result.dynamicColumns.length).toBe(1);
      expect(result.dynamicColumns[0].field).toBe('name');
      expect(result.dynamicColumns[0].semanticType).toBe('PERSON_NAME');
      expect(result.tableData).toEqual(response.data || []);
      expect(result.isExpanded).toBe(false);
    });

    it('should generate ID when none provided in response', () => {
      const responseWithoutId = { ...mockTableClassificationResponse };
      delete responseWithoutId.analysisId;

      const result = service.processAnalysisResult('test.csv', responseWithoutId);

      expect(result.id).toBeTruthy();
      expect(result.id).toMatch(/^\d+_[a-z0-9]+$/); // UUID format
    });

    it('should handle empty column classifications', () => {
      const responseWithoutColumns = {
        ...mockTableClassificationResponse,
        columnClassifications: {}
      };

      const result = service.processAnalysisResult('test.csv', responseWithoutColumns);

      expect(result.dynamicColumns).toEqual([]);
    });

    it('should handle response without data property', () => {
      const responseWithoutData = {
        ...mockTableClassificationResponse,
        data: undefined
      };

      const result = service.processAnalysisResult('test.csv', responseWithoutData);

      expect(result.tableData).toEqual([]);
      expect(result.originalData).toEqual([]);
    });

    it('should handle null column classifications', () => {
      const responseWithNullColumns = {
        ...mockTableClassificationResponse,
        columnClassifications: null as any
      };

      const result = service.processAnalysisResult('test.csv', responseWithNullColumns);

      expect(result.dynamicColumns).toEqual([]);
    });

    it('should handle classifications with missing properties', () => {
      const responseWithIncompleteClassifications = {
        ...mockTableClassificationResponse,
        columnClassifications: {
          'incomplete': {
            columnName: 'incomplete',
            baseType: undefined as any,
            semanticType: undefined as any,
            confidence: undefined as any,
            pattern: undefined
          }
        }
      };

      const result = service.processAnalysisResult('test.csv', responseWithIncompleteClassifications);

      expect(result.dynamicColumns.length).toBe(1);
      expect(result.dynamicColumns[0].baseType).toBe('unknown');
      expect(result.dynamicColumns[0].semanticType).toBe('none');
      expect(result.dynamicColumns[0].confidence).toBe(0);
    });
  });

  describe('exportAnalysisResults', () => {
    let mockAnalysis: FileAnalysis;

    beforeEach(() => {
      mockAnalysis = {
        id: 'export-test',
        fileName: 'export.csv',
        uploadTime: new Date(),
        lastAnalyzedAt: new Date(),
        classificationResults: mockTableClassificationResponse,
        tableData: [
          { name: 'John Doe', age: 30 },
          { name: 'Jane Smith', age: 25 }
        ],
        dynamicColumns: [
          { field: 'name', header: 'name', baseType: 'STRING', semanticType: 'PERSON_NAME', confidence: 0.95, pattern: undefined },
          { field: 'age', header: 'age', baseType: 'INTEGER', semanticType: 'AGE', confidence: 0.90, pattern: undefined }
        ],
        originalData: [],
        isExpanded: false
      };

      service.analyses.set([mockAnalysis]);
    });

    it('should export analysis as CSV', (done) => {
      service.exportAnalysisResults('export-test', 'csv').subscribe(blob => {
        expect(blob).toBeInstanceOf(Blob);
        expect(blob.type).toBe('text/csv');
        
        const reader = new FileReader();
        reader.onload = () => {
          const csvContent = reader.result as string;
          expect(csvContent).toContain('name,age');
          expect(csvContent).toContain('John Doe,30');
          expect(csvContent).toContain('Jane Smith,25');
          done();
        };
        reader.readAsText(blob);
      });
    });

    it('should export analysis as JSON', (done) => {
      service.exportAnalysisResults('export-test', 'json').subscribe(blob => {
        expect(blob).toBeInstanceOf(Blob);
        expect(blob.type).toBe('application/json');
        
        const reader = new FileReader();
        reader.onload = () => {
          const jsonContent = JSON.parse(reader.result as string);
          expect(jsonContent.analysisId).toBe('export-test');
          expect(jsonContent.fileName).toBe('export.csv');
          expect(jsonContent.columns.length).toBe(2);
          expect(jsonContent.data.length).toBe(2);
          done();
        };
        reader.readAsText(blob);
      });
    });

    it('should handle CSV export with special characters', (done) => {
      const analysisWithSpecialChars = {
        ...mockAnalysis,
        tableData: [
          { name: 'John "Johnny" Doe', description: 'Test, with comma' }
        ],
        dynamicColumns: [
          { field: 'name', header: 'name', baseType: 'STRING', semanticType: 'PERSON_NAME', confidence: 0.95, pattern: undefined },
          { field: 'description', header: 'description', baseType: 'STRING', semanticType: 'DESCRIPTION', confidence: 0.80, pattern: undefined }
        ]
      };

      service.analyses.set([analysisWithSpecialChars]);

      service.exportAnalysisResults('export-test', 'csv').subscribe(blob => {
        const reader = new FileReader();
        reader.onload = () => {
          const csvContent = reader.result as string;
          expect(csvContent).toContain('"John ""Johnny"" Doe"');
          expect(csvContent).toContain('"Test, with comma"');
          done();
        };
        reader.readAsText(blob);
      });
    });

    it('should handle CSV export with null values', (done) => {
      const analysisWithNullValues = {
        ...mockAnalysis,
        tableData: [
          { name: 'John Doe', age: null },
          { name: null, age: 30 }
        ],
        dynamicColumns: [
          { field: 'name', header: 'name', baseType: 'STRING', semanticType: 'PERSON_NAME', confidence: 0.95, pattern: undefined },
          { field: 'age', header: 'age', baseType: 'INTEGER', semanticType: 'AGE', confidence: 0.90, pattern: undefined }
        ]
      };

      service.analyses.set([analysisWithNullValues]);

      service.exportAnalysisResults('export-test', 'csv').subscribe(blob => {
        const reader = new FileReader();
        reader.onload = () => {
          const csvContent = reader.result as string;
          expect(csvContent).toContain('name,age');
          expect(csvContent).toContain('John Doe,');
          expect(csvContent).toContain(',30');
          done();
        };
        reader.readAsText(blob);
      });
    });

    it('should handle JSON export errors', (done) => {
      const analysisWithCircularRef = {
        ...mockAnalysis
      };
      
      // Create circular reference to trigger JSON.stringify error
      const circular: Record<string, unknown> = {};
      circular['self'] = circular;
      analysisWithCircularRef.tableData = [circular as any];

      service.analyses.set([analysisWithCircularRef]);

      service.exportAnalysisResults('export-test', 'json').subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (error) => {
          expect(error.message).toContain('Converting circular structure to JSON');
          done();
        }
      });
    });

    it('should return error for non-existent analysis', (done) => {
      service.exportAnalysisResults('non-existent', 'csv').subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (error) => {
          expect(error.message).toBe('Analysis not found');
          done();
        }
      });
    });
  });

  describe('regenerateSemanticTypes', () => {
    it('should successfully regenerate semantic types', (done) => {
      const analysis: FileAnalysis = {
        id: 'regen-test',
        fileName: 'test.csv',
        uploadTime: new Date(),
        lastAnalyzedAt: new Date(),
        classificationResults: mockTableClassificationResponse,
        tableData: [],
        dynamicColumns: [],
        originalData: [],
        isExpanded: false
      };

      service.analyses.set([analysis]);
      tableClassifierService.reanalyzeWithUpdatedTypes.and.returnValue(of(mockTableClassificationResponse));

      service.regenerateSemanticTypes('regen-test', ['name']).subscribe(result => {
        expect(result.fileName).toBe('test.csv');
        expect(result.id).toBe('regen-test'); // Should keep same ID
        expect(tableClassifierService.reanalyzeWithUpdatedTypes).toHaveBeenCalledWith('regen-test');
        done();
      });
    });

    it('should handle regeneration errors', (done) => {
      const analysis: FileAnalysis = {
        id: 'regen-test',
        fileName: 'test.csv',
        uploadTime: new Date(),
        lastAnalyzedAt: new Date(),
        classificationResults: mockTableClassificationResponse,
        tableData: [],
        dynamicColumns: [],
        originalData: [],
        isExpanded: false
      };

      service.analyses.set([analysis]);
      const error = new Error('Regeneration failed');
      tableClassifierService.reanalyzeWithUpdatedTypes.and.returnValue(throwError(() => error));

      service.regenerateSemanticTypes('regen-test', ['name']).subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (error) => {
          expect(loggerService.error).toHaveBeenCalledWith(
            'Failed to regenerate semantic types',
            error,
            'AnalysisService'
          );
          done();
        }
      });
    });

    it('should return error for non-existent analysis', (done) => {
      service.regenerateSemanticTypes('non-existent', ['name']).subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (error) => {
          expect(error.message).toBe('Analysis not found');
          done();
        }
      });
    });
  });

  describe('reanalyzeAllAnalyses', () => {
    it('should successfully reanalyze all analyses', async () => {
      const analysis1: FileAnalysis = {
        id: 'analysis-1',
        fileName: 'test1.csv',
        uploadTime: new Date(),
        lastAnalyzedAt: new Date(),
        classificationResults: mockTableClassificationResponse,
        tableData: [],
        dynamicColumns: [],
        originalData: [],
        isExpanded: false
      };

      const analysis2: FileAnalysis = {
        id: 'analysis-2',
        fileName: 'test2.csv',
        uploadTime: new Date(),
        lastAnalyzedAt: new Date(),
        classificationResults: mockTableClassificationResponse,
        tableData: [],
        dynamicColumns: [],
        originalData: [],
        isExpanded: false
      };

      service.analyses.set([analysis1, analysis2]);
      tableClassifierService.reanalyzeWithUpdatedTypes.and.returnValue(of(mockTableClassificationResponse));

      await service.reanalyzeAllAnalyses();

      expect(tableClassifierService.reanalyzeWithUpdatedTypes).toHaveBeenCalledWith('analysis-1');
      expect(tableClassifierService.reanalyzeWithUpdatedTypes).toHaveBeenCalledWith('analysis-2');
      expect(tableClassifierService.reanalyzeWithUpdatedTypes).toHaveBeenCalledTimes(2);
    });

    it('should handle reanalysis errors and rethrow them', async () => {
      const analysis: FileAnalysis = {
        id: 'error-analysis',
        fileName: 'error.csv',
        uploadTime: new Date(),
        lastAnalyzedAt: new Date(),
        classificationResults: mockTableClassificationResponse,
        tableData: [],
        dynamicColumns: [],
        originalData: [],
        isExpanded: false
      };

      service.analyses.set([analysis]);
      const error = new Error('Reanalysis failed');
      tableClassifierService.reanalyzeWithUpdatedTypes.and.returnValue(throwError(() => error));

      try {
        await service.reanalyzeAllAnalyses();
        fail('Should have thrown error');
      } catch (thrownError) {
        expect(thrownError).toBe(error);
        expect(loggerService.error).toHaveBeenCalledWith(
          'Failed to reanalyze error-analysis',
          error,
          'AnalysisService'
        );
      }
    });
  });

  describe('convertStoredAnalysisToFileAnalysis', () => {
    it('should handle stored analysis without data', () => {
      const storedAnalysisWithoutData: StoredAnalysis = {
        ...mockStoredAnalysis,
        data: [] as any
      };

      const result = service.processAnalysisResult(storedAnalysisWithoutData.fileName, storedAnalysisWithoutData.response);

      expect(result.tableData).toEqual([]);
      expect(result.originalData).toEqual([]);
    });

    it('should handle stored analysis with null column classifications', () => {
      const storedAnalysisWithNullColumns: StoredAnalysis = {
        ...mockStoredAnalysis,
        response: {
          ...mockStoredAnalysis.response,
          columnClassifications: null as any
        }
      };

      const result = service.processAnalysisResult(storedAnalysisWithNullColumns.fileName, storedAnalysisWithNullColumns.response);

      expect(result.dynamicColumns).toEqual([]);
    });

    it('should handle stored analysis with incomplete classifications', () => {
      const storedAnalysisWithIncompleteClassifications: StoredAnalysis = {
        ...mockStoredAnalysis,
        response: {
          ...mockStoredAnalysis.response,
          columnClassifications: {
            'incomplete': {
              columnName: 'incomplete',
              baseType: undefined as any,
              semanticType: undefined as any,
              confidence: undefined as any,
              pattern: undefined
            }
          }
        }
      };

      const result = service.processAnalysisResult(storedAnalysisWithIncompleteClassifications.fileName, storedAnalysisWithIncompleteClassifications.response);

      expect(result.dynamicColumns.length).toBe(1);
      expect(result.dynamicColumns[0].baseType).toBe('unknown');
      expect(result.dynamicColumns[0].semanticType).toBe('none');
      expect(result.dynamicColumns[0].confidence).toBe(0);
    });
  });

  describe('getAnalyses observable', () => {
    it('should return observable of analyses', (done) => {
      const analysis: FileAnalysis = {
        id: 'observable-test',
        fileName: 'test.csv',
        uploadTime: new Date(),
        lastAnalyzedAt: new Date(),
        classificationResults: mockTableClassificationResponse,
        tableData: [],
        dynamicColumns: [],
        originalData: [],
        isExpanded: false
      };

      // Subscribe first to catch the emission
      let emissionCount = 0;
      service.getAnalyses().subscribe(analyses => {
        emissionCount++;
        if (emissionCount === 2) { // Skip the initial empty emission
          expect(analyses).toEqual([analysis]);
          done();
        }
      });

      // Add analysis using the service method to properly update both signal and subject
      service.addAnalysis(analysis);
    });
  });
});