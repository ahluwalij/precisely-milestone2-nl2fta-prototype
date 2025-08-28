import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { SemanticTypeRepositoryService } from './semantic-type-repository.service';
import { ConfigService } from './config.service';
import { LoggerService } from './logger.service';
import { CustomSemanticType } from './fta-classifier.service';

describe('SemanticTypeRepositoryService', () => {
  let service: SemanticTypeRepositoryService;
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

  const mockCustomSemanticType: CustomSemanticType = {
    semanticType: 'CUSTOM_ID',
    description: 'Custom identifier type',
    pluginType: 'regex',
    threshold: 0.85,
    baseType: 'STRING',
    content: {
      type: 'regex',
      reference: '[A-Z]{3}-\\d{4}'
    },
    validLocales: [
      {
        localeTag: 'en-US',
        headerRegExps: [
          {
            regExp: '(?i)(custom|id|identifier)',
            confidence: 0.9
          }
        ],
        matchEntries: [
          {
            regExpReturned: '[A-Z]{3}-\\d{4}',
            isRegExpComplete: true
          }
        ]
      }
    ],
    documentation: [
      {
        source: 'Custom',
        reference: 'Internal documentation'
      }
    ],
    priority: 100,
    localeSensitive: false,
    minSamples: 10
  };

  const mockSemanticTypes: CustomSemanticType[] = [
    mockCustomSemanticType,
    {
      semanticType: 'BUILT_IN_TYPE',
      description: 'Built-in type',
      pluginType: 'builtin',
      threshold: 0.9,
      baseType: 'STRING'
    }
  ];

  const mockCustomTypesOnly: CustomSemanticType[] = [
    mockCustomSemanticType
  ];

  beforeEach(() => {
    const httpSpy = jasmine.createSpyObj('HttpClient', ['get', 'post', 'put', 'delete']);
    const configSpy = jasmine.createSpyObj('ConfigService', ['getConfig'], {
      apiUrl: '/api'
    });
    const loggerSpy = jasmine.createSpyObj('LoggerService', ['info', 'debug', 'error', 'warn']);

    configSpy.getConfig.and.returnValue(mockConfig);

    TestBed.configureTestingModule({
      providers: [
        SemanticTypeRepositoryService,
        { provide: HttpClient, useValue: httpSpy },
        { provide: ConfigService, useValue: configSpy },
        { provide: LoggerService, useValue: loggerSpy }
      ]
    });

    service = TestBed.inject(SemanticTypeRepositoryService);
    httpClient = TestBed.inject(HttpClient) as jasmine.SpyObj<HttpClient>;
    configService = TestBed.inject(ConfigService) as jasmine.SpyObj<ConfigService>;
    loggerService = TestBed.inject(LoggerService) as jasmine.SpyObj<LoggerService>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getAllSemanticTypes', () => {
    it('should successfully fetch all semantic types', (done) => {
      httpClient.get.and.returnValue(of(mockSemanticTypes));

      service.getAllSemanticTypes().subscribe(types => {
        expect(types).toBe(mockSemanticTypes);
        expect(types.length).toBe(2);
        expect(types[0].semanticType).toBe('CUSTOM_ID');
        expect(types[1].semanticType).toBe('BUILT_IN_TYPE');
        expect(httpClient.get).toHaveBeenCalledWith('/api/semantic-types');
        done();
      });
    });

    it('should handle errors when fetching all semantic types', (done) => {
      const error = new Error('Network error');
      httpClient.get.and.returnValue(throwError(() => error));

      service.getAllSemanticTypes().subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (thrownError) => {
          expect(thrownError).toBeDefined();
          expect(loggerService.error).toHaveBeenCalledWith('Semantic type repository service error', error, 'SemanticTypeRepositoryService');
          done();
        }
      });
    });

    it('should use default timeout when config unavailable', (done) => {
      configService.getConfig.and.returnValue(undefined as any);
      httpClient.get.and.returnValue(of(mockSemanticTypes));

      service.getAllSemanticTypes().subscribe(types => {
        expect(types).toBe(mockSemanticTypes);
        done();
      });
    });

    it('should use default timeout when httpTimeoutMs is undefined', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpRetryCount: 2 });
      httpClient.get.and.returnValue(of(mockSemanticTypes));

      service.getAllSemanticTypes().subscribe(types => {
        expect(types).toBe(mockSemanticTypes);
        done();
      });
    });

    it('should use default retry count when httpRetryCount is undefined', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpTimeoutMs: 30000 });
      httpClient.get.and.returnValue(of(mockSemanticTypes));

      service.getAllSemanticTypes().subscribe(types => {
        expect(types).toBe(mockSemanticTypes);
        done();
      });
    });
  });

  describe('getCustomSemanticTypesOnly', () => {
    it('should successfully fetch custom semantic types only', (done) => {
      httpClient.get.and.returnValue(of(mockCustomTypesOnly));

      service.getCustomSemanticTypesOnly().subscribe(types => {
        expect(types).toBe(mockCustomTypesOnly);
        expect(types.length).toBe(1);
        expect(types[0].semanticType).toBe('CUSTOM_ID');
        expect(httpClient.get).toHaveBeenCalledWith('/api/semantic-types/custom-only');
        done();
      });
    });

    it('should handle errors when fetching custom types only', (done) => {
      const error = new Error('Access denied');
      httpClient.get.and.returnValue(throwError(() => error));

      service.getCustomSemanticTypesOnly().subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (thrownError) => {
          expect(thrownError.message).toBe('Access denied');
          expect(loggerService.error).toHaveBeenCalledWith('Semantic type repository service error', error, 'SemanticTypeRepositoryService');
          done();
        }
      });
    });

    it('should use default config values when config properties are undefined', (done) => {
      configService.getConfig.and.returnValue({} as any);
      httpClient.get.and.returnValue(of(mockCustomTypesOnly));

      service.getCustomSemanticTypesOnly().subscribe(types => {
        expect(types).toBe(mockCustomTypesOnly);
        done();
      });
    });

    it('should use default timeout when config is null', (done) => {
      configService.getConfig.and.returnValue(null as any);
      httpClient.get.and.returnValue(of(mockCustomTypesOnly));

      service.getCustomSemanticTypesOnly().subscribe(types => {
        expect(types).toBe(mockCustomTypesOnly);
        done();
      });
    });

    it('should use default timeout when httpTimeoutMs is 0', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpTimeoutMs: 0 });
      httpClient.get.and.returnValue(of(mockCustomTypesOnly));

      service.getCustomSemanticTypesOnly().subscribe(types => {
        expect(types).toBe(mockCustomTypesOnly);
        done();
      });
    });

    it('should use default retry count when httpRetryCount is 0', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpRetryCount: 0 });
      httpClient.get.and.returnValue(of(mockCustomTypesOnly));

      service.getCustomSemanticTypesOnly().subscribe(types => {
        expect(types).toBe(mockCustomTypesOnly);
        done();
      });
    });
  });

  describe('addCustomSemanticType', () => {
    it('should successfully add a custom semantic type', (done) => {
      const newType: CustomSemanticType = {
        semanticType: 'NEW_CUSTOM_TYPE',
        description: 'A new custom type',
        pluginType: 'regex',
        threshold: 0.8,
        baseType: 'STRING',
        content: {
          type: 'regex',
          reference: 'NEW-\\d{3}'
        }
      };

      httpClient.post.and.returnValue(of(newType));

      service.addCustomSemanticType(newType).subscribe(createdType => {
        expect(createdType).toBe(newType);
        expect(createdType.semanticType).toBe('NEW_CUSTOM_TYPE');
        expect(httpClient.post).toHaveBeenCalledWith('/api/semantic-types', newType);
        done();
      });
    });

    it('should handle validation errors when adding custom type', (done) => {
      const invalidType: CustomSemanticType = {
        semanticType: '',
        description: '',
        pluginType: 'invalid'
      };


      const error = { 
        status: 400, 
        error: { message: 'Validation failed' } 
      };
      httpClient.post.and.returnValue(throwError(() => error));

      service.addCustomSemanticType(invalidType).subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (thrownError) => {
          expect(thrownError.status).toBe(400);
          expect(loggerService.error).toHaveBeenCalledWith('Semantic type repository service error', error, 'SemanticTypeRepositoryService');
          done();
        }
      });
    });

    it('should include complex type configuration', (done) => {
      const complexType: CustomSemanticType = {
        semanticType: 'COMPLEX_TYPE',
        description: 'Complex type with all features',
        pluginType: 'regex',
        threshold: 0.95,
        baseType: 'STRING',
        content: {
          type: 'regex',
          reference: '[A-Z]{2}\\d{8}',
          values: ['AB12345678', 'CD87654321']
        },
        validLocales: [
          {
            localeTag: 'en-US',
            headerRegExps: [
              {
                regExp: '(?i)(complex|special)',
                confidence: 0.8,
                mandatory: true
              }
            ],
            matchEntries: [
              {
                regExpReturned: '[A-Z]{2}\\d{8}',
                isRegExpComplete: true
              }
            ]
          }
        ],
        documentation: [
          {
            source: 'Internal',
            reference: 'Complex type specification'
          }
        ],
        priority: 200,
        localeSensitive: true,
        minSamples: 20,
        minMaxPresent: true,
        minimum: 'AA00000000',
        maximum: 'ZZ99999999',
        pluginOptions: 'case_sensitive=true',
        backout: 'GENERIC',
        invalidList: ['INVALID', 'TEST'],
        ignoreList: ['IGNORE', 'SKIP']
      };

      httpClient.post.and.returnValue(of(complexType));

      service.addCustomSemanticType(complexType).subscribe(createdType => {
        expect(createdType.validLocales).toBeDefined();
        expect(createdType.validLocales![0].localeTag).toBe('en-US');
        expect(createdType.documentation![0].source).toBe('Internal');
        expect(createdType.priority).toBe(200);
        expect(createdType.localeSensitive).toBe(true);
        expect(createdType.invalidList).toContain('INVALID');
        expect(createdType.ignoreList).toContain('IGNORE');
        done();
      });
    });

    it('should use default config values when config properties are undefined', (done) => {
      configService.getConfig.and.returnValue({} as any);
      const newType: CustomSemanticType = {
        semanticType: 'TEST_TYPE',
        description: 'Test type',
        pluginType: 'regex'
      };
      httpClient.post.and.returnValue(of(newType));

      service.addCustomSemanticType(newType).subscribe(type => {
        expect(type).toBe(newType);
        done();
      });
    });

    it('should use default config values when config is null', (done) => {
      configService.getConfig.and.returnValue(null as any);
      const newType: CustomSemanticType = {
        semanticType: 'TEST_TYPE_NULL',
        description: 'Test type null config',
        pluginType: 'regex'
      };
      httpClient.post.and.returnValue(of(newType));

      service.addCustomSemanticType(newType).subscribe(type => {
        expect(type).toBe(newType);
        done();
      });
    });

    it('should use default timeout when httpTimeoutMs is 0', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpTimeoutMs: 0 });
      const newType: CustomSemanticType = {
        semanticType: 'TEST_TYPE_TIMEOUT',
        description: 'Test type zero timeout',
        pluginType: 'regex'
      };
      httpClient.post.and.returnValue(of(newType));

      service.addCustomSemanticType(newType).subscribe(type => {
        expect(type).toBe(newType);
        done();
      });
    });

    it('should use default retry count when httpRetryCount is 0', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpRetryCount: 0 });
      const newType: CustomSemanticType = {
        semanticType: 'TEST_TYPE_RETRY',
        description: 'Test type zero retry',
        pluginType: 'regex'
      };
      httpClient.post.and.returnValue(of(newType));

      service.addCustomSemanticType(newType).subscribe(type => {
        expect(type).toBe(newType);
        done();
      });
    });
  });

  describe('updateCustomSemanticType', () => {
    it('should successfully update a custom semantic type', (done) => {
      const updatedType: CustomSemanticType = {
        ...mockCustomSemanticType,
        description: 'Updated description',
        threshold: 0.9
      };

      httpClient.put.and.returnValue(of(updatedType));

      service.updateCustomSemanticType('CUSTOM_ID', updatedType).subscribe(type => {
        expect(type).toBe(updatedType);
        expect(type.description).toBe('Updated description');
        expect(type.threshold).toBe(0.9);
        expect(httpClient.put).toHaveBeenCalledWith('/api/semantic-types/CUSTOM_ID', updatedType);
        done();
      });
    });

    it('should properly encode semantic type in URL', (done) => {
      const typeWithSpecialChars = 'CUSTOM@TYPE#1';
      const updatedType: CustomSemanticType = {
        semanticType: typeWithSpecialChars,
        description: 'Type with special characters',
        pluginType: 'regex'
      };

      httpClient.put.and.returnValue(of(updatedType));

      service.updateCustomSemanticType(typeWithSpecialChars, updatedType).subscribe(() => {
        expect(httpClient.put).toHaveBeenCalledWith('/api/semantic-types/CUSTOM%40TYPE%231', updatedType);
        done();
      });
    });

    it('should handle update errors', (done) => {

      const error = { 
        status: 404, 
        error: { message: 'Semantic type not found' } 
      };
      httpClient.put.and.returnValue(throwError(() => error));

      service.updateCustomSemanticType('NON_EXISTENT', mockCustomSemanticType).subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (thrownError) => {
          expect(thrownError.status).toBe(404);
          expect(loggerService.error).toHaveBeenCalledWith('Semantic type repository service error', error, 'SemanticTypeRepositoryService');
          done();
        }
      });
    });

    it('should use default config values when config properties are undefined', (done) => {
      configService.getConfig.and.returnValue({} as any);
      const updatedType: CustomSemanticType = {
        semanticType: 'TEST_TYPE',
        description: 'Updated type',
        pluginType: 'regex'
      };
      httpClient.put.and.returnValue(of(updatedType));

      service.updateCustomSemanticType('TEST_TYPE', updatedType).subscribe(type => {
        expect(type).toBe(updatedType);
        done();
      });
    });

    it('should use default config values when config is null', (done) => {
      configService.getConfig.and.returnValue(null as any);
      const updatedType: CustomSemanticType = {
        semanticType: 'TEST_TYPE_NULL',
        description: 'Updated type null config',
        pluginType: 'regex'
      };
      httpClient.put.and.returnValue(of(updatedType));

      service.updateCustomSemanticType('TEST_TYPE_NULL', updatedType).subscribe(type => {
        expect(type).toBe(updatedType);
        done();
      });
    });

    it('should use default timeout when httpTimeoutMs is null', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpTimeoutMs: null as any });
      const updatedType: CustomSemanticType = {
        semanticType: 'TEST_TYPE_TIMEOUT',
        description: 'Updated type null timeout',
        pluginType: 'regex'
      };
      httpClient.put.and.returnValue(of(updatedType));

      service.updateCustomSemanticType('TEST_TYPE_TIMEOUT', updatedType).subscribe(type => {
        expect(type).toBe(updatedType);
        done();
      });
    });

    it('should use default retry count when httpRetryCount is null', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpRetryCount: null as any });
      const updatedType: CustomSemanticType = {
        semanticType: 'TEST_TYPE_RETRY',
        description: 'Updated type null retry',
        pluginType: 'regex'
      };
      httpClient.put.and.returnValue(of(updatedType));

      service.updateCustomSemanticType('TEST_TYPE_RETRY', updatedType).subscribe(type => {
        expect(type).toBe(updatedType);
        done();
      });
    });
  });

  describe('deleteSemanticType', () => {
    it('should successfully delete a semantic type', (done) => {
      httpClient.delete.and.returnValue(of(undefined));

      service.deleteSemanticType('CUSTOM_ID').subscribe(() => {
        expect(httpClient.delete).toHaveBeenCalledWith('/api/semantic-types/CUSTOM_ID');
        done();
      });
    });

    it('should properly encode semantic type in delete URL', (done) => {
      const typeWithSpaces = 'CUSTOM TYPE WITH SPACES';
      httpClient.delete.and.returnValue(of(undefined));

      service.deleteSemanticType(typeWithSpaces).subscribe(() => {
        expect(httpClient.delete).toHaveBeenCalledWith('/api/semantic-types/CUSTOM%20TYPE%20WITH%20SPACES');
        done();
      });
    });

    it('should handle delete errors', (done) => {

      const error = { 
        status: 403, 
        error: { message: 'Cannot delete built-in type' } 
      };
      httpClient.delete.and.returnValue(throwError(() => error));

      service.deleteSemanticType('BUILT_IN_TYPE').subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (thrownError) => {
          expect(thrownError.status).toBe(403);
          expect(loggerService.error).toHaveBeenCalledWith('Semantic type repository service error', error, 'SemanticTypeRepositoryService');
          done();
        }
      });
    });

    it('should use default config values when config properties are undefined', (done) => {
      configService.getConfig.and.returnValue({} as any);
      httpClient.delete.and.returnValue(of(undefined));

      service.deleteSemanticType('TEST_TYPE').subscribe(() => {
        expect(httpClient.delete).toHaveBeenCalledWith('/api/semantic-types/TEST_TYPE');
        done();
      });
    });

    it('should use default config values when config is null', (done) => {
      configService.getConfig.and.returnValue(null as any);
      httpClient.delete.and.returnValue(of(undefined));

      service.deleteSemanticType('TEST_TYPE_NULL').subscribe(() => {
        expect(httpClient.delete).toHaveBeenCalledWith('/api/semantic-types/TEST_TYPE_NULL');
        done();
      });
    });

    it('should use default timeout when httpTimeoutMs is null', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpTimeoutMs: null as any });
      httpClient.delete.and.returnValue(of(undefined));

      service.deleteSemanticType('TEST_TYPE_TIMEOUT').subscribe(() => {
        expect(httpClient.delete).toHaveBeenCalledWith('/api/semantic-types/TEST_TYPE_TIMEOUT');
        done();
      });
    });

    it('should use default retry count when httpRetryCount is null', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpRetryCount: null as any });
      httpClient.delete.and.returnValue(of(undefined));

      service.deleteSemanticType('TEST_TYPE_RETRY').subscribe(() => {
        expect(httpClient.delete).toHaveBeenCalledWith('/api/semantic-types/TEST_TYPE_RETRY');
        done();
      });
    });
  });

  describe('reloadSemanticTypes', () => {
    it('should successfully reload semantic types', (done) => {
      const reloadResponse = { message: 'Semantic types reloaded successfully' };
      httpClient.post.and.returnValue(of(reloadResponse));

      service.reloadSemanticTypes().subscribe(response => {
        expect(response).toBe(reloadResponse);
        expect(response.message).toBe('Semantic types reloaded successfully');
        expect(httpClient.post).toHaveBeenCalledWith('/api/semantic-types/reload', {});
        done();
      });
    });

    it('should handle reload errors', (done) => {

      const error = { 
        status: 500, 
        error: { message: 'Failed to reload semantic types' } 
      };
      httpClient.post.and.returnValue(throwError(() => error));

      service.reloadSemanticTypes().subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (thrownError) => {
          expect(thrownError.status).toBe(500);
          expect(loggerService.error).toHaveBeenCalledWith('Semantic type repository service error', error, 'SemanticTypeRepositoryService');
          done();
        }
      });
    });

    it('should use default config values when config properties are undefined', (done) => {
      configService.getConfig.and.returnValue({} as any);
      const reloadResponse = { message: 'Reloaded successfully' };
      httpClient.post.and.returnValue(of(reloadResponse));

      service.reloadSemanticTypes().subscribe(response => {
        expect(response).toBe(reloadResponse);
        done();
      });
    });

    it('should use default config values when config is null', (done) => {
      configService.getConfig.and.returnValue(null as any);
      const reloadResponse = { message: 'Reloaded with null config' };
      httpClient.post.and.returnValue(of(reloadResponse));

      service.reloadSemanticTypes().subscribe(response => {
        expect(response).toBe(reloadResponse);
        done();
      });
    });

    it('should use default timeout when httpTimeoutMs is null', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpTimeoutMs: null as any });
      const reloadResponse = { message: 'Reloaded with null timeout' };
      httpClient.post.and.returnValue(of(reloadResponse));

      service.reloadSemanticTypes().subscribe(response => {
        expect(response).toBe(reloadResponse);
        done();
      });
    });

    it('should use default retry count when httpRetryCount is null', (done) => {
      configService.getConfig.and.returnValue({ ...mockConfig, httpRetryCount: null as any });
      const reloadResponse = { message: 'Reloaded with null retry' };
      httpClient.post.and.returnValue(of(reloadResponse));

      service.reloadSemanticTypes().subscribe(response => {
        expect(response).toBe(reloadResponse);
        done();
      });
    });
  });

  describe('configuration and URL building', () => {
    it('should access apiUrl from config service', () => {
      const apiUrl = service['apiUrl'];
      expect(apiUrl).toBe('/api');
    });

    it('should access config from config service', () => {
      const config = service['config'];
      expect(config.httpTimeoutMs).toBe(30000);
      expect(config.httpRetryCount).toBe(2);
      expect(configService.getConfig).toHaveBeenCalled();
    });

    it('should build correct API URLs for all endpoints', (done) => {
      httpClient.get.and.returnValue(of([]));
      httpClient.post.and.returnValue(of({}));
      httpClient.put.and.returnValue(of({}));
      httpClient.delete.and.returnValue(of(undefined));

      // Test all endpoint URL construction
      service.getAllSemanticTypes().subscribe(() => {
        expect(httpClient.get).toHaveBeenCalledWith('/api/semantic-types');
      });

      service.getCustomSemanticTypesOnly().subscribe(() => {
        expect(httpClient.get).toHaveBeenCalledWith('/api/semantic-types/custom-only');
      });

      service.addCustomSemanticType(mockCustomSemanticType).subscribe(() => {
        expect(httpClient.post).toHaveBeenCalledWith('/api/semantic-types', mockCustomSemanticType);
      });

      service.updateCustomSemanticType('TEST_TYPE', mockCustomSemanticType).subscribe(() => {
        expect(httpClient.put).toHaveBeenCalledWith('/api/semantic-types/TEST_TYPE', mockCustomSemanticType);
      });

      service.deleteSemanticType('DELETE_TYPE').subscribe(() => {
        expect(httpClient.delete).toHaveBeenCalledWith('/api/semantic-types/DELETE_TYPE');
      });

      service.reloadSemanticTypes().subscribe(() => {
        expect(httpClient.post).toHaveBeenCalledWith('/api/semantic-types/reload', {});
        done();
      });
    });
  });

  describe('error handling', () => {
    it('should log errors and rethrow them', (done) => {

      const error = new Error('Test error');
      httpClient.get.and.returnValue(throwError(() => error));

      service.getAllSemanticTypes().subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (thrownError) => {
          expect(loggerService.error).toHaveBeenCalledWith('Semantic type repository service error', error, 'SemanticTypeRepositoryService');
          expect(thrownError).toBe(error);
          done();
        }
      });
    });

    it('should handle network timeout errors', (done) => {

      const timeoutError = new Error('Timeout');
      httpClient.get.and.returnValue(throwError(() => timeoutError));

      service.getAllSemanticTypes().subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (thrownError) => {
          expect(thrownError.message).toBe('Timeout');
          expect(loggerService.error).toHaveBeenCalledWith('Semantic type repository service error', timeoutError, 'SemanticTypeRepositoryService');
          done();
        }
      });
    });
  });
});