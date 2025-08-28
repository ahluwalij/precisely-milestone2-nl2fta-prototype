import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { PLATFORM_ID } from '@angular/core';
import { of, throwError } from 'rxjs';
import { ConfigService } from './config.service';
import { LoggerService } from './logger.service';
import { EnvironmentService } from './environment.service';

describe('ConfigService', () => {
  let service: ConfigService;
  let httpClient: jasmine.SpyObj<HttpClient>;
  let logger: jasmine.SpyObj<LoggerService>;
  let environmentService: jasmine.SpyObj<EnvironmentService>;

  const mockEnvironmentConfig = {
    port: 4000,
    apiUrl: '/api',
    apiHost: 'localhost',
    maxFileSize: 10485760,
    maxRows: 1000,
    nodeEnv: 'development',
    backendPort: 8080,
    httpTimeoutMs: 30000,
    httpRetryCount: 3,
    httpLongTimeoutMs: 60000,
    baseZIndex: 1000,
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

  const mockBackendConfig = {
    maxFileSize: 20971520,
    maxRows: 2000,
    apiUrl: '/api/v1',
    httpTimeoutMs: 45000,
    httpRetryCount: 5,
    httpLongTimeoutMs: 90000,
    baseZIndex: 2000,
    defaultHighThreshold: 98,
    defaultMediumThreshold: 85,
    defaultLowThreshold: 60,
    highThresholdMin: 95,
    highThresholdMax: 100,
    mediumThresholdMin: 75,
    mediumThresholdMax: 94,
    lowThresholdMin: 0,
    lowThresholdMax: 74,
    notificationDelayMs: 5000,
    reanalysisDelayMs: 2000,
    awsAccessKeyId: 'test-key',
    awsSecretAccessKey: 'test-secret',
    awsRegion: 'us-east-1',
    awsModelId: 'claude-v2',
  };

  beforeEach(() => {
    const httpSpy = jasmine.createSpyObj('HttpClient', ['get']);
    const loggerSpy = jasmine.createSpyObj('LoggerService', ['error', 'info', 'warn']);
    const envSpy = jasmine.createSpyObj('EnvironmentService', ['getEnvironmentConfig']);

    envSpy.getEnvironmentConfig.and.returnValue(mockEnvironmentConfig);

    TestBed.configureTestingModule({
      providers: [
        ConfigService,
        { provide: HttpClient, useValue: httpSpy },
        { provide: LoggerService, useValue: loggerSpy },
        { provide: EnvironmentService, useValue: envSpy },
        { provide: PLATFORM_ID, useValue: 'browser' },
      ],
    });

    service = TestBed.inject(ConfigService);
    httpClient = TestBed.inject(HttpClient) as jasmine.SpyObj<HttpClient>;
    logger = TestBed.inject(LoggerService) as jasmine.SpyObj<LoggerService>;
    environmentService = TestBed.inject(EnvironmentService) as jasmine.SpyObj<EnvironmentService>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return default config when no backend config is loaded', async () => {
    httpClient.get.and.returnValue(of({}));
    await service.loadConfig();
    
    const config = service.getConfig();
    
    expect(config.maxFileSize).toBe(mockEnvironmentConfig.maxFileSize);
    expect(config.apiUrl).toBe(mockEnvironmentConfig.apiUrl);
    expect(config.defaultHighThreshold).toBe(mockEnvironmentConfig.defaultHighThreshold);
  });

  it('should load config from backend successfully', async () => {
    httpClient.get.and.returnValue(of(mockBackendConfig));

    const config = await service.loadConfig();

    expect(httpClient.get).toHaveBeenCalledWith('/api/config');
    expect(config.maxFileSize).toBe(mockBackendConfig.maxFileSize);
    expect(config.awsAccessKeyId).toBe(mockBackendConfig.awsAccessKeyId);
  });

  it('should handle backend config load failure gracefully', async () => {
    httpClient.get.and.returnValue(throwError(() => new Error('Network error')));

    const config = await service.loadConfig();

    expect(logger.warn).toHaveBeenCalledWith(
      'Failed to load configuration from backend, using defaults',
      jasmine.any(Error),
      'ConfigService'
    );
    expect(config.maxFileSize).toBe(mockEnvironmentConfig.maxFileSize);
  });

  it('should return cached config on subsequent calls', async () => {
    httpClient.get.and.returnValue(of(mockBackendConfig));

    const config1 = await service.loadConfig();
    const config2 = await service.loadConfig();

    expect(httpClient.get).toHaveBeenCalledTimes(1);
    expect(config1).toBe(config2);
  });

  it('should provide correct API URL', async () => {
    httpClient.get.and.returnValue(of({}));
    await service.loadConfig();
    expect(service.apiUrl).toBe('/api');
  });

  it('should merge backend config with defaults correctly', async () => {
    const partialBackendConfig = {
      maxFileSize: 50000000,
      awsRegion: 'us-west-2',
    };
    
    httpClient.get.and.returnValue(of(partialBackendConfig));

    const config = await service.loadConfig();

    // Should use backend values where provided
    expect(config.maxFileSize).toBe(partialBackendConfig.maxFileSize);
    expect(config.awsRegion).toBe(partialBackendConfig.awsRegion);
    
    // Should use defaults for missing values
    expect(config.maxRows).toBe(mockEnvironmentConfig.maxRows);
    expect(config.httpTimeoutMs).toBe(mockEnvironmentConfig.httpTimeoutMs);
  });

  it('should handle invalid backend response', async () => {
    httpClient.get.and.returnValue(of(null));

    const config = await service.loadConfig();

    expect(config.maxFileSize).toBe(mockEnvironmentConfig.maxFileSize);
  });

  describe('SSR and platform handling', () => {
    beforeEach(() => {
      // Reset service state for each test
      service = TestBed.inject(ConfigService);
    });

    it('should use default config during SSR', async () => {
      // Set platform to server
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          ConfigService,
          { provide: HttpClient, useValue: httpClient },
          { provide: LoggerService, useValue: logger },
          { provide: EnvironmentService, useValue: environmentService },
          { provide: PLATFORM_ID, useValue: 'server' },
        ],
      });
      service = TestBed.inject(ConfigService);

      const config = await service.loadConfig();

      expect(httpClient.get).not.toHaveBeenCalled();
      expect(config.maxFileSize).toBe(mockEnvironmentConfig.maxFileSize);
    });

    it('should return default config in getConfig during SSR without loading', () => {
      // Set platform to server
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          ConfigService,
          { provide: HttpClient, useValue: httpClient },
          { provide: LoggerService, useValue: logger },
          { provide: EnvironmentService, useValue: environmentService },
          { provide: PLATFORM_ID, useValue: 'server' },
        ],
      });
      service = TestBed.inject(ConfigService);

      const config = service.getConfig();

      expect(config.maxFileSize).toBe(mockEnvironmentConfig.maxFileSize);
    });

    it('should throw error if getConfig called before loadConfig on browser', () => {
      expect(() => service.getConfig()).toThrowError('Configuration not loaded. Call loadConfig() first.');
    });
  });

  describe('property getters', () => {
    beforeEach(async () => {
      httpClient.get.and.returnValue(of(mockBackendConfig));
      await service.loadConfig();
    });

    it('should return maxFileSize from config', () => {
      expect(service.maxFileSize).toBe(mockBackendConfig.maxFileSize);
    });

    it('should return maxRows from config', () => {
      expect(service.maxRows).toBe(mockBackendConfig.maxRows);
    });

    it('should return apiUrl from config', () => {
      expect(service.apiUrl).toBe(mockBackendConfig.apiUrl);
    });
  });

  describe('concurrent loading', () => {
    it('should handle concurrent loadConfig calls correctly', async () => {
      httpClient.get.and.returnValue(of(mockBackendConfig));

      const promise1 = service.loadConfig();
      const promise2 = service.loadConfig();
      const promise3 = service.loadConfig();

      const [config1, config2, config3] = await Promise.all([promise1, promise2, promise3]);

      expect(httpClient.get).toHaveBeenCalledTimes(1);
      expect(config1).toBe(config2);
      expect(config2).toBe(config3);
    });
  });

  describe('error handling', () => {
    it('should handle empty response by throwing error and using defaults', async () => {
      httpClient.get.and.returnValue(of(null));

      const config = await service.loadConfig();

      expect(logger.warn).toHaveBeenCalledWith(
        'Failed to load configuration from backend, using defaults',
        jasmine.any(Error),
        'ConfigService'
      );
      expect(config.maxFileSize).toBe(mockEnvironmentConfig.maxFileSize);
    });

    it('should log info when backend config is successfully loaded', async () => {
      httpClient.get.and.returnValue(of(mockBackendConfig));

      await service.loadConfig();

      expect(logger.info).toHaveBeenCalledWith(
        'Configuration loaded from backend',
        undefined,
        'ConfigService'
      );
    });
  });

  describe('config merging', () => {
    it('should properly merge partial backend config with defaults', async () => {
      const partialConfig = {
        maxFileSize: 99999999,
        awsAccessKeyId: 'partial-key',
        // Missing many other properties
      };
      
      httpClient.get.and.returnValue(of(partialConfig));
      
      const config = await service.loadConfig();
      
      // Should use backend values where provided
      expect(config.maxFileSize).toBe(partialConfig.maxFileSize);
      expect(config.awsAccessKeyId).toBe(partialConfig.awsAccessKeyId);
      
      // Should use defaults for missing values
      expect(config.maxRows).toBe(mockEnvironmentConfig.maxRows);
      expect(config.httpTimeoutMs).toBe(mockEnvironmentConfig.httpTimeoutMs);
      expect(config.baseZIndex).toBe(mockEnvironmentConfig.baseZIndex);
    });
  });

  describe('defaultConfig getter', () => {
    it('should create default config from environment service', () => {
      // Access private getter through service methods
      const _config = service.getConfig;
      // This indirectly tests that defaultConfig getter works by testing the fallback
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          ConfigService,
          { provide: HttpClient, useValue: httpClient },
          { provide: LoggerService, useValue: logger },
          { provide: EnvironmentService, useValue: environmentService },
          { provide: PLATFORM_ID, useValue: 'server' },
        ],
      });
      const newService = TestBed.inject(ConfigService);
      
      const defaultConfig = newService.getConfig();
      
      expect(defaultConfig.maxFileSize).toBe(mockEnvironmentConfig.maxFileSize);
      expect(defaultConfig.apiUrl).toBe(mockEnvironmentConfig.apiUrl);
      expect(defaultConfig.httpTimeoutMs).toBe(mockEnvironmentConfig.httpTimeoutMs);
      expect(environmentService.getEnvironmentConfig).toHaveBeenCalled();
    });
  });
});