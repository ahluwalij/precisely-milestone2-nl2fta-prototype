import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { 
  FtaClassifierService, 
  TableClassificationRequest, 
  TableClassificationResponse, 
  CustomSemanticType,
  StoredAnalysis 
} from './fta-classifier.service';
import { ConfigService } from './config.service';
import { SemanticTypeRepositoryService } from './semantic-type-repository.service';
import { TableClassifierService } from './table-classifier.service';

describe('FtaClassifierService', () => {
  let service: FtaClassifierService;
  let httpClient: jasmine.SpyObj<HttpClient>;
  let configService: jasmine.SpyObj<ConfigService>;
  let semanticTypeRepository: jasmine.SpyObj<SemanticTypeRepositoryService>;
  let tableClassifier: jasmine.SpyObj<TableClassifierService>;

  const mockTableClassificationRequest: TableClassificationRequest = {
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

  const mockTableClassificationResponse: TableClassificationResponse = {
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
    analysisId: 'test-analysis-123'
  };

  const mockCustomSemanticType: CustomSemanticType = {
    semanticType: 'CUSTOM_TYPE',
    description: 'A custom semantic type for testing',
    pluginType: 'regex',
    threshold: 0.8,
    baseType: 'STRING',
    content: {
      type: 'regex',
      reference: '[A-Z]{3}-\\d{4}'
    }
  };

  const mockStoredAnalysis: StoredAnalysis = {
    analysisId: 'stored-analysis-1',
    fileName: 'test-data.csv',
    timestamp: '2024-01-15T10:30:00Z',
    response: mockTableClassificationResponse,
    fields: [
      {
        fieldName: 'name',
        currentSemanticType: 'PERSON_NAME',
        currentConfidence: 0.95,
        sampleValues: ['John Doe', 'Jane Smith']
      }
    ],
    columns: ['name', 'email'],
    data: [
      { name: 'John Doe', email: 'john@example.com' }
    ],
    locale: 'en-US'
  };

  beforeEach(() => {
    const httpSpy = jasmine.createSpyObj('HttpClient', ['get', 'post', 'put', 'delete']);
    const configSpy = jasmine.createSpyObj('ConfigService', ['getConfig'], {
      apiUrl: '/api'
    });
    const semanticTypeSpy = jasmine.createSpyObj('SemanticTypeRepositoryService', [
      'getAllSemanticTypes',
      'getCustomSemanticTypesOnly',
      'addCustomSemanticType',
      'updateCustomSemanticType',
      'deleteSemanticType',
      'reloadSemanticTypes'
    ]);
    const tableClassifierSpy = jasmine.createSpyObj('TableClassifierService', [
      'classifyTable',
      'analyzeTable',
      'reanalyzeWithUpdatedTypes',
      'getAllAnalyses',
      'deleteAllAnalyses'
    ]);

    configSpy.getConfig.and.returnValue({
      apiUrl: '/api',
      httpTimeoutMs: 30000,
      httpRetryCount: 2
    });

    TestBed.configureTestingModule({
      providers: [
        FtaClassifierService,
        { provide: HttpClient, useValue: httpSpy },
        { provide: ConfigService, useValue: configSpy },
        { provide: SemanticTypeRepositoryService, useValue: semanticTypeSpy },
        { provide: TableClassifierService, useValue: tableClassifierSpy }
      ]
    });

    service = TestBed.inject(FtaClassifierService);
    httpClient = TestBed.inject(HttpClient) as jasmine.SpyObj<HttpClient>;
    configService = TestBed.inject(ConfigService) as jasmine.SpyObj<ConfigService>;
    semanticTypeRepository = TestBed.inject(SemanticTypeRepositoryService) as jasmine.SpyObj<SemanticTypeRepositoryService>;
    tableClassifier = TestBed.inject(TableClassifierService) as jasmine.SpyObj<TableClassifierService>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('configuration access', () => {
    it('should access apiUrl from config service', () => {
      const apiUrl = service['apiUrl'];
      expect(apiUrl).toBe('/api');
    });

    it('should access config from config service', () => {
      const config = service['config'];
      expect(config.apiUrl).toBe('/api');
      expect(configService.getConfig).toHaveBeenCalled();
    });
  });

  describe('table classification delegation', () => {
    it('should delegate classifyTable to TableClassifierService', (done) => {
      tableClassifier.classifyTable.and.returnValue(of(mockTableClassificationResponse));

      service.classifyTable(mockTableClassificationRequest).subscribe(response => {
        expect(response).toBe(mockTableClassificationResponse);
        expect(tableClassifier.classifyTable).toHaveBeenCalledWith(mockTableClassificationRequest);
        done();
      });
    });

    it('should delegate analyzeTable to TableClassifierService', (done) => {
      const mockFile = new File(['name,email\\nJohn,john@test.com'], 'test.csv', { type: 'text/csv' });
      tableClassifier.analyzeTable.and.returnValue(of(mockTableClassificationResponse));

      service.analyzeTable(mockFile, 'test_table', 1000, 'en-US').subscribe(response => {
        expect(response).toBe(mockTableClassificationResponse);
        expect(tableClassifier.analyzeTable).toHaveBeenCalledWith(mockFile, 'test_table', 1000, 'en-US');
        done();
      });
    });

    it('should delegate reanalyzeWithUpdatedTypes to TableClassifierService', (done) => {
      tableClassifier.reanalyzeWithUpdatedTypes.and.returnValue(of(mockTableClassificationResponse));

      service.reanalyzeWithUpdatedTypes('analysis-123').subscribe(response => {
        expect(response).toBe(mockTableClassificationResponse);
        expect(tableClassifier.reanalyzeWithUpdatedTypes).toHaveBeenCalledWith('analysis-123');
        done();
      });
    });
  });

  describe('semantic type repository delegation', () => {
    it('should delegate getAllSemanticTypes to SemanticTypeRepositoryService', (done) => {
      const mockSemanticTypes = [mockCustomSemanticType];
      semanticTypeRepository.getAllSemanticTypes.and.returnValue(of(mockSemanticTypes));

      service.getAllSemanticTypes().subscribe(types => {
        expect(types).toBe(mockSemanticTypes);
        expect(semanticTypeRepository.getAllSemanticTypes).toHaveBeenCalled();
        done();
      });
    });

    it('should delegate getCustomSemanticTypesOnly to SemanticTypeRepositoryService', (done) => {
      const mockCustomTypes = [mockCustomSemanticType];
      semanticTypeRepository.getCustomSemanticTypesOnly.and.returnValue(of(mockCustomTypes));

      service.getCustomSemanticTypesOnly().subscribe(types => {
        expect(types).toBe(mockCustomTypes);
        expect(semanticTypeRepository.getCustomSemanticTypesOnly).toHaveBeenCalled();
        done();
      });
    });

    it('should delegate addCustomSemanticType to SemanticTypeRepositoryService', (done) => {
      semanticTypeRepository.addCustomSemanticType.and.returnValue(of(mockCustomSemanticType));

      service.addCustomSemanticType(mockCustomSemanticType).subscribe(type => {
        expect(type).toBe(mockCustomSemanticType);
        expect(semanticTypeRepository.addCustomSemanticType).toHaveBeenCalledWith(mockCustomSemanticType);
        done();
      });
    });

    it('should delegate updateCustomSemanticType to SemanticTypeRepositoryService', (done) => {
      const updatedType = { ...mockCustomSemanticType, description: 'Updated description' };
      semanticTypeRepository.updateCustomSemanticType.and.returnValue(of(updatedType));

      service.updateCustomSemanticType('CUSTOM_TYPE', updatedType).subscribe(type => {
        expect(type).toBe(updatedType);
        expect(semanticTypeRepository.updateCustomSemanticType).toHaveBeenCalledWith('CUSTOM_TYPE', updatedType);
        done();
      });
    });

    it('should delegate deleteSemanticType to SemanticTypeRepositoryService', (done) => {
      semanticTypeRepository.deleteSemanticType.and.returnValue(of(undefined));

      service.deleteSemanticType('CUSTOM_TYPE').subscribe(() => {
        expect(semanticTypeRepository.deleteSemanticType).toHaveBeenCalledWith('CUSTOM_TYPE');
        done();
      });
    });

    it('should delegate reloadSemanticTypes to SemanticTypeRepositoryService', (done) => {
      const mockResponse = { message: 'Semantic types reloaded successfully' };
      semanticTypeRepository.reloadSemanticTypes.and.returnValue(of(mockResponse));

      service.reloadSemanticTypes().subscribe(response => {
        expect(response).toBe(mockResponse);
        expect(semanticTypeRepository.reloadSemanticTypes).toHaveBeenCalled();
        done();
      });
    });
  });

  describe('analysis management delegation', () => {
    it('should delegate getAllAnalyses to TableClassifierService', (done) => {
      const mockAnalyses = [mockStoredAnalysis];
      tableClassifier.getAllAnalyses.and.returnValue(of(mockAnalyses));

      service.getAllAnalyses().subscribe(analyses => {
        expect(analyses).toBe(mockAnalyses);
        expect(tableClassifier.getAllAnalyses).toHaveBeenCalled();
        done();
      });
    });

    it('should delegate deleteAllAnalyses to TableClassifierService', (done) => {
      tableClassifier.deleteAllAnalyses.and.returnValue(of(undefined));

      service.deleteAllAnalyses().subscribe(() => {
        expect(tableClassifier.deleteAllAnalyses).toHaveBeenCalled();
        done();
      });
    });
  });

  describe('error handling', () => {
    it('should have handleError method for internal use', () => {
      const error = new Error('Test error');
      const errorObservable = service['handleError'](error);
      
      errorObservable.subscribe({
        next: () => {
          fail('Should not emit next value');
        },
        error: (thrownError) => {
          expect(thrownError).toBe(error);
        }
      });
    });
  });

  describe('service integration', () => {
    it('should properly inject all required services', () => {
      expect(service['http']).toBe(httpClient);
      expect(service['configService']).toBe(configService);
      expect(service['semanticTypeRepository']).toBe(semanticTypeRepository);
      expect(service['tableClassifier']).toBe(tableClassifier);
    });

    it('should maintain consistency in delegation patterns', () => {
      // Verify that all methods that should delegate do so without additional processing
      const tableClassifierMethods = [
        'classifyTable',
        'analyzeTable', 
        'reanalyzeWithUpdatedTypes',
        'getAllAnalyses',
        'deleteAllAnalyses'
      ];

      const semanticRepositoryMethods = [
        'getAllSemanticTypes',
        'getCustomSemanticTypesOnly',
        'addCustomSemanticType',
        'updateCustomSemanticType',
        'deleteSemanticType',
        'reloadSemanticTypes'
      ];

      // All methods should be present on the service
      tableClassifierMethods.forEach(method => {
        expect(typeof (service as any)[method]).toBe('function');
      });

      semanticRepositoryMethods.forEach(method => {
        expect(typeof (service as any)[method]).toBe('function');
      });
    });
  });

  describe('type interfaces validation', () => {
    it('should properly define TableClassificationRequest interface', () => {
      // This test ensures the interface structure is maintained
      const request: TableClassificationRequest = {
        tableName: 'test',
        columns: ['col1'],
        data: [{ col1: 'value' }],
        maxSamples: 100,
        locale: 'en-US',
        includeStatistics: true
      };

      expect(request.tableName).toBe('test');
      expect(request.columns.length).toBe(1);
      expect(request.data.length).toBe(1);
      expect(request.maxSamples).toBe(100);
      expect(request.locale).toBe('en-US');
      expect(request.includeStatistics).toBe(true);
    });

    it('should properly define TableClassificationResponse interface', () => {
      const response: TableClassificationResponse = {
        tableName: 'test',
        columnClassifications: {
          'col1': {
            columnName: 'col1',
            baseType: 'STRING',
            semanticType: 'GENERIC',
            confidence: 0.8
          }
        },
        processingMetadata: {
          totalColumns: 1,
          analyzedColumns: 1,
          totalRowsProcessed: 100,
          processingTimeMs: 1000,
          ftaVersion: '1.0.0',
          localeUsed: 'en-US'
        },
        analysisId: 'test-123'
      };

      expect(response.tableName).toBe('test');
      expect(response.columnClassifications['col1'].baseType).toBe('STRING');
      expect(response.processingMetadata.totalColumns).toBe(1);
      expect(response.analysisId).toBe('test-123');
    });

    it('should properly define CustomSemanticType interface', () => {
      const customType: CustomSemanticType = {
        semanticType: 'TEST_TYPE',
        description: 'Test description',
        pluginType: 'regex',
        threshold: 0.9,
        baseType: 'STRING',
        content: {
          type: 'regex',
          reference: 'test-pattern'
        }
      };

      expect(customType.semanticType).toBe('TEST_TYPE');
      expect(customType.pluginType).toBe('regex');
      expect(customType.threshold).toBe(0.9);
      expect(customType.content?.type).toBe('regex');
    });

    it('should properly define StoredAnalysis interface', () => {
      const storedAnalysis: StoredAnalysis = {
        analysisId: 'stored-1',
        fileName: 'test.csv',
        timestamp: '2024-01-01T00:00:00Z',
        response: mockTableClassificationResponse,
        fields: [],
        columns: ['col1'],
        data: [{ col1: 'value' }],
        locale: 'en-US'
      };

      expect(storedAnalysis.analysisId).toBe('stored-1');
      expect(storedAnalysis.fileName).toBe('test.csv');
      expect(storedAnalysis.columns.length).toBe(1);
      expect(storedAnalysis.data.length).toBe(1);
      expect(storedAnalysis.locale).toBe('en-US');
    });
  });
});