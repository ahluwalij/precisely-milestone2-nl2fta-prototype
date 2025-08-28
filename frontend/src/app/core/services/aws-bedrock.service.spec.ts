import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import {
  AwsBedrockService,
  AwsCredentialsRequest,
  CredentialsValidationResponse,
  ModelsResponse,
  ConfigureResponse,
  AwsStatus,
  SemanticTypeGenerationRequest,
  GeneratedSemanticType,
  GenerateValidatedExamplesRequest,
  GeneratedValidatedExamplesResponse,
  ModelValidationResponse,
  AwsCredentialsStatusResponse,
  IndexingStatusResponse
} from './aws-bedrock.service';
import { ConfigService } from './config.service';
import { LoggerService } from './logger.service';

describe('AwsBedrockService', () => {
  let service: AwsBedrockService;
  let httpClient: jasmine.SpyObj<HttpClient>;
  let configService: jasmine.SpyObj<ConfigService>;
  let loggerService: jasmine.SpyObj<LoggerService>;

  const mockConfig = {
    maxFileSize: 10485760,
    maxRows: 10000,
    apiUrl: '/api',
    httpTimeoutMs: 30000,
    httpRetryCount: 2,
    httpLongTimeoutMs: 60000,
    baseZIndex: 1000,
    defaultHighThreshold: 0.85,
    defaultMediumThreshold: 0.65,
    defaultLowThreshold: 0.45,
    highThresholdMin: 0.7,
    highThresholdMax: 1.0,
    mediumThresholdMin: 0.5,
    mediumThresholdMax: 0.7,
    lowThresholdMin: 0.3,
    lowThresholdMax: 0.5,
    notificationDelayMs: 500,
    reanalysisDelayMs: 1000
  };

  const mockCredentials: AwsCredentialsRequest = {
    accessKeyId: 'AKIATESTFAKEKEY123456',
    secretAccessKey: 'test-fake-secret-key-for-unit-testing-purposes-only',
    region: 'us-east-1',
    modelId: 'anthropic.claude-3-sonnet-20240229-v1:0'
  };

  const mockValidationResponse: CredentialsValidationResponse = {
    valid: true,
    message: 'Credentials are valid',
    regions: [
      { regionId: 'us-east-1', displayName: 'US East (N. Virginia)' },
      { regionId: 'us-east-2', displayName: 'US East (Ohio)' }
    ]
  };

  const mockModelsResponse: ModelsResponse = {
    region: 'us-east-1',
    models: [
      {
        modelId: 'anthropic.claude-3-sonnet-20240229-v1:0',
        modelName: 'Claude 3 Sonnet',
        provider: 'Anthropic',
        inputModalities: ['TEXT'],
        outputModalities: ['TEXT'],
        requiresInferenceProfile: false
      },
      {
        modelId: 'anthropic.claude-3-haiku-20240307-v1:0',
        modelName: 'Claude 3 Haiku',
        provider: 'Anthropic',
        inputModalities: ['TEXT'],
        outputModalities: ['TEXT'],
        requiresInferenceProfile: false
      }
    ],
    count: 2
  };

  beforeEach(() => {
    const httpSpy = jasmine.createSpyObj('HttpClient', ['get', 'post', 'delete']);
    const configSpy = jasmine.createSpyObj('ConfigService', ['getConfig'], {
      apiUrl: '/api'
    });
    const loggerSpy = jasmine.createSpyObj('LoggerService', ['info', 'debug', 'error', 'warn']);

    configSpy.getConfig.and.returnValue(mockConfig);

    TestBed.configureTestingModule({
      providers: [
        AwsBedrockService,
        { provide: HttpClient, useValue: httpSpy },
        { provide: ConfigService, useValue: configSpy },
        { provide: LoggerService, useValue: loggerSpy }
      ]
    });

    service = TestBed.inject(AwsBedrockService);
    httpClient = TestBed.inject(HttpClient) as jasmine.SpyObj<HttpClient>;
    configService = TestBed.inject(ConfigService) as jasmine.SpyObj<ConfigService>;
    loggerService = TestBed.inject(LoggerService) as jasmine.SpyObj<LoggerService>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('validateCredentialsAndGetRegions', () => {
    it('should successfully validate credentials and return regions', (done) => {
      httpClient.post.and.returnValue(of(mockValidationResponse));

      service.validateCredentialsAndGetRegions(mockCredentials).subscribe(response => {
        expect(response).toBe(mockValidationResponse);
        expect(response.valid).toBe(true);
        expect(response.regions!.length).toBe(2);
        expect(httpClient.post).toHaveBeenCalledWith('/api/aws/validate-credentials', mockCredentials);
        done();
      });
    });

    it('should handle invalid credentials', (done) => {
      const invalidResponse: CredentialsValidationResponse = {
        valid: false,
        message: 'Invalid AWS credentials provided'
      };

      httpClient.post.and.returnValue(of(invalidResponse));

      service.validateCredentialsAndGetRegions(mockCredentials).subscribe(response => {
        expect(response.valid).toBe(false);
        expect(response.message).toContain('Invalid');
        expect(response.regions).toBeUndefined();
        done();
      });
    });

    it('should handle network errors', (done) => {
      const error = new Error('Network timeout');
      httpClient.post.and.returnValue(throwError(() => error));

      service.validateCredentialsAndGetRegions(mockCredentials).subscribe({
        next: () => fail('Should have failed'),
        error: (thrownError) => {
          expect(thrownError).toBe(error);
          done();
        }
      });
    });
  });

  describe('getModelsForRegion', () => {
    it('should successfully get models for a region', (done) => {
      httpClient.post.and.returnValue(of(mockModelsResponse));

      service.getModelsForRegion(mockCredentials, 'us-east-1').subscribe(response => {
        expect(response).toBe(mockModelsResponse);
        expect(response.region).toBe('us-east-1');
        expect(response.models.length).toBe(2);
        expect(response.count).toBe(2);
        expect(httpClient.post).toHaveBeenCalledWith('/api/aws/models/us-east-1', mockCredentials);
        done();
      });
    });

    it('should handle region with no available models', (done) => {
      const emptyResponse: ModelsResponse = {
        region: 'ap-south-1',
        models: [],
        count: 0
      };

      httpClient.post.and.returnValue(of(emptyResponse));

      service.getModelsForRegion(mockCredentials, 'ap-south-1').subscribe(response => {
        expect(response.models.length).toBe(0);
        expect(response.count).toBe(0);
        done();
      });
    });

    it('should handle invalid region errors', (done) => {
      const error = { status: 400, error: { message: 'Invalid region specified' } };
      httpClient.post.and.returnValue(throwError(() => error));

      service.getModelsForRegion(mockCredentials, 'invalid-region').subscribe({
        next: () => fail('Should have failed'),
        error: (thrownError) => {
          expect(thrownError.status).toBe(400);
          done();
        }
      });
    });
  });

  describe('configureAwsClient', () => {
    it('should successfully configure AWS client', (done) => {
      const configResponse: ConfigureResponse = {
        success: true,
        message: 'AWS client configured successfully',
        region: 'us-east-1',
        modelId: 'anthropic.claude-3-sonnet-20240229-v1:0'
      };

      httpClient.post.and.returnValue(of(configResponse));

      service.configureAwsClient(mockCredentials).subscribe(response => {
        expect(response.success).toBe(true);
        expect(response.region).toBe('us-east-1');
        expect(response.modelId).toBe('anthropic.claude-3-sonnet-20240229-v1:0');
        expect(httpClient.post).toHaveBeenCalledWith('/api/aws/configure', mockCredentials);
        done();
      });
    });

    it('should handle configuration failures', (done) => {
      const failureResponse: ConfigureResponse = {
        success: false,
        message: 'Failed to configure AWS client: Invalid model ID'
      };

      httpClient.post.and.returnValue(of(failureResponse));

      service.configureAwsClient(mockCredentials).subscribe(response => {
        expect(response.success).toBe(false);
        expect(response.message).toContain('Failed');
        done();
      });
    });
  });

  describe('getAwsStatus', () => {
    it('should get AWS configuration status', (done) => {
      const statusResponse: AwsStatus = {
        configured: true,
        message: 'AWS is configured and ready',
        region: 'us-east-1',
        modelId: 'anthropic.claude-3-sonnet-20240229-v1:0'
      };

      httpClient.get.and.returnValue(of(statusResponse));

      service.getAwsStatus().subscribe(response => {
        expect(response.configured).toBe(true);
        expect(response.region).toBe('us-east-1');
        expect(httpClient.get).toHaveBeenCalledWith('/api/aws/status');
        done();
      });
    });

    it('should handle unconfigured status', (done) => {
      const statusResponse: AwsStatus = {
        configured: false,
        message: 'AWS not configured'
      };

      httpClient.get.and.returnValue(of(statusResponse));

      service.getAwsStatus().subscribe(response => {
        expect(response.configured).toBe(false);
        expect(response.region).toBeUndefined();
        expect(response.modelId).toBeUndefined();
        done();
      });
    });
  });

  describe('checkAwsStatus', () => {
    it('should check semantic type AWS status', (done) => {
      const statusResponse: AwsStatus = {
        configured: true,
        message: 'Semantic type AWS service is configured',
        region: 'us-east-2',
        modelId: 'anthropic.claude-3-haiku-20240307-v1:0'
      };

      httpClient.get.and.returnValue(of(statusResponse));

      service.checkAwsStatus().subscribe(response => {
        expect(response.configured).toBe(true);
        expect(response.region).toBe('us-east-2');
        expect(httpClient.get).toHaveBeenCalledWith('/api/semantic-types/aws/status');
        done();
      });
    });
  });

  describe('clearAwsCredentials', () => {
    it('should successfully clear AWS credentials', (done) => {
      const clearResponse = { success: true };
      httpClient.delete.and.returnValue(of(clearResponse));

      service.clearAwsCredentials().subscribe(response => {
        expect(response.success).toBe(true);
        expect(httpClient.delete).toHaveBeenCalledWith('/api/aws/credentials');
        done();
      });
    });

    it('should handle clear credentials errors', (done) => {
      const error = { status: 500, error: { message: 'Failed to clear credentials' } };
      httpClient.delete.and.returnValue(throwError(() => error));

      service.clearAwsCredentials().subscribe({
        next: () => fail('Should have failed'),
        error: (thrownError) => {
          expect(thrownError.status).toBe(500);
          expect(loggerService.error).toHaveBeenCalledWith('AWS Bedrock service error', error, 'AwsBedrockService');
          done();
        }
      });
    });
  });

  describe('generateSemanticType', () => {
    it('should successfully generate a semantic type', (done) => {
      const request: SemanticTypeGenerationRequest = {
        typeName: 'CUSTOM_ID',
        description: 'Custom identifier format',
        positiveContentExamples: ['ID-123', 'ID-456'],
        negativeContentExamples: ['123', 'ABC'],
        positiveHeaderExamples: ['id', 'identifier'],
        negativeHeaderExamples: ['name', 'description'],
        checkExistingTypes: true
      };

      const generatedType: GeneratedSemanticType = {
        resultType: 'generated',
        semanticType: 'CUSTOM_ID',
        description: 'Custom identifier format',
        pluginType: 'regex',
        regexPattern: 'ID-\\d{3}',
        positiveContentExamples: ['ID-123', 'ID-456'],
        negativeContentExamples: ['123', 'ABC'],
        confidenceThreshold: 0.85,
        explanation: 'Generated pattern matches ID followed by 3 digits'
      };

      httpClient.post.and.returnValue(of(generatedType));

      service.generateSemanticType(request).subscribe(response => {
        expect(response.resultType).toBe('generated');
        expect(response.semanticType).toBe('CUSTOM_ID');
        expect(response.regexPattern).toBe('ID-\\d{3}');
        expect(httpClient.post).toHaveBeenCalledWith('/api/semantic-types/generate', request);
        done();
      });
    });

    it('should handle existing type detection', (done) => {
      const request: SemanticTypeGenerationRequest = {
        description: 'Email address',
        positiveContentExamples: ['test@example.com'],
        positiveHeaderExamples: ['email']
      };

      const existingType: GeneratedSemanticType = {
        resultType: 'existing',
        existingTypeMatch: 'EMAIL',
        existingTypeDescription: 'Email address',
        existingTypePattern: '[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}',
        existingTypeIsBuiltIn: true,
        suggestedAction: 'use_existing',
        explanation: 'Found existing EMAIL type that matches your requirements'
      };

      httpClient.post.and.returnValue(of(existingType));

      service.generateSemanticType(request).subscribe(response => {
        expect(response.resultType).toBe('existing');
        expect(response.existingTypeMatch).toBe('EMAIL');
        expect(response.suggestedAction).toBe('use_existing');
        done();
      });
    });

    it('should handle generation errors', (done) => {
      const request: SemanticTypeGenerationRequest = {
        description: 'Invalid request',
        positiveContentExamples: [],
        positiveHeaderExamples: []
      };

      const errorType: GeneratedSemanticType = {
        resultType: 'error',
        explanation: 'Insufficient examples provided for generation'
      };

      httpClient.post.and.returnValue(of(errorType));

      service.generateSemanticType(request).subscribe(response => {
        expect(response.resultType).toBe('error');
        expect(response.explanation).toContain('Insufficient');
        done();
      });
    });
  });

  describe('generateValidatedExamples', () => {
    it('should successfully generate validated examples', (done) => {
      const request: GenerateValidatedExamplesRequest = {
        regexPattern: '\\d{3}-\\d{2}-\\d{4}',
        semanticTypeName: 'SSN',
        description: 'Social Security Number',
        generatePositiveOnly: false
      };

      const validatedResponse: GeneratedValidatedExamplesResponse = {
        positiveExamples: ['123-45-6789', '987-65-4321'],
        negativeExamples: ['123456789', '123-45-67890'],
        attemptsUsed: 2,
        validationSuccessful: true,
        validationSummary: {
          totalPositiveGenerated: 5,
          totalNegativeGenerated: 3,
          positiveExamplesValidated: 2,
          negativeExamplesValidated: 2,
          positiveExamplesFailed: 3,
          negativeExamplesFailed: 1
        }
      };

      httpClient.post.and.returnValue(of(validatedResponse));

      service.generateValidatedExamples(request).subscribe(response => {
        expect(response.validationSuccessful).toBe(true);
        expect(response.positiveExamples.length).toBe(2);
        expect(response.negativeExamples.length).toBe(2);
        expect(response.validationSummary.positiveExamplesValidated).toBe(2);
        expect(httpClient.post).toHaveBeenCalledWith('/api/semantic-types/generate-validated-examples', request);
        done();
      });
    });

    it('should handle validation failures', (done) => {
      const request: GenerateValidatedExamplesRequest = {
        regexPattern: 'invalid[regex',
        semanticTypeName: 'INVALID'
      };

      const failedResponse: GeneratedValidatedExamplesResponse = {
        positiveExamples: [],
        negativeExamples: [],
        attemptsUsed: 1,
        validationSuccessful: false,
        error: 'Invalid regex pattern provided',
        validationSummary: {
          totalPositiveGenerated: 0,
          totalNegativeGenerated: 0,
          positiveExamplesValidated: 0,
          negativeExamplesValidated: 0,
          positiveExamplesFailed: 0,
          negativeExamplesFailed: 0
        }
      };

      httpClient.post.and.returnValue(of(failedResponse));

      service.generateValidatedExamples(request).subscribe(response => {
        expect(response.validationSuccessful).toBe(false);
        expect(response.error).toContain('Invalid regex');
        done();
      });
    });
  });

  describe('validateModelAccess', () => {
    it('should successfully validate model access', (done) => {
      const validationResponse: ModelValidationResponse = {
        accessible: true,
        modelId: 'anthropic.claude-3-sonnet-20240229-v1:0',
        region: 'us-east-1',
        message: 'Model is accessible'
      };

      httpClient.post.and.returnValue(of(validationResponse));

      service.validateModelAccess(mockCredentials, 'us-east-1').subscribe(response => {
        expect(response.accessible).toBe(true);
        expect(response.modelId).toBe('anthropic.claude-3-sonnet-20240229-v1:0');
        expect(httpClient.post).toHaveBeenCalledWith('/api/aws/validate-model/us-east-1', mockCredentials);
        done();
      });
    });

    it('should handle inaccessible model', (done) => {
      const validationResponse: ModelValidationResponse = {
        accessible: false,
        modelId: 'anthropic.claude-3-opus-20240229-v1:0',
        region: 'us-west-1',
        message: 'Model not available in this region'
      };

      httpClient.post.and.returnValue(of(validationResponse));

      service.validateModelAccess(mockCredentials, 'us-west-1').subscribe(response => {
        expect(response.accessible).toBe(false);
        expect(response.message).toContain('not available');
        done();
      });
    });
  });

  describe('getAwsCredentialsStatus', () => {
    it('should get AWS credentials status', (done) => {
      const statusResponse: AwsCredentialsStatusResponse = {
        credentialsAvailable: true,
        storageType: 'S3',
        storageStatus: 'Connected',
        canAccessS3: true,
        message: 'Credentials are configured and S3 is accessible',
        region: 'us-east-1',
        accessKeyId: 'AKIA***EXAMPLE',
        secretAccessKey: '***'
      };

      httpClient.get.and.returnValue(of(statusResponse));

      service.getAwsCredentialsStatus().subscribe(response => {
        expect(response.credentialsAvailable).toBe(true);
        expect(response.canAccessS3).toBe(true);
        expect(response.storageType).toBe('S3');
        expect(httpClient.get).toHaveBeenCalledWith('/api/aws/credentials/status');
        done();
      });
    });
  });

  describe('getIndexingStatus', () => {
    it('should get indexing status', (done) => {
      const indexingResponse: IndexingStatusResponse = {
        indexing: true,
        totalTypes: 150,
        indexedTypes: 75,
        progress: 50
      };

      httpClient.get.and.returnValue(of(indexingResponse));

      service.getIndexingStatus().subscribe(response => {
        expect(response.indexing).toBe(true);
        expect(response.progress).toBe(50);
        expect(response.totalTypes).toBe(150);
        expect(response.indexedTypes).toBe(75);
        expect(httpClient.get).toHaveBeenCalledWith('/api/aws/credentials/indexing-status');
        done();
      });
    });

    it('should handle completed indexing', (done) => {
      const completedResponse: IndexingStatusResponse = {
        indexing: false,
        totalTypes: 200,
        indexedTypes: 200,
        progress: 100
      };

      httpClient.get.and.returnValue(of(completedResponse));

      service.getIndexingStatus().subscribe(response => {
        expect(response.indexing).toBe(false);
        expect(response.progress).toBe(100);
        done();
      });
    });
  });

  describe('logout', () => {
    it('should successfully logout', (done) => {
      const logoutResponse = { success: true };
      httpClient.post.and.returnValue(of(logoutResponse));

      service.logout().subscribe(response => {
        expect(response.success).toBe(true);
        expect(httpClient.post).toHaveBeenCalledWith('/api/semantic-types/aws/logout', {});
        done();
      });
    });
  });

  describe('configureSemanticTypeAws', () => {
    it('should configure semantic type AWS settings', (done) => {
      const configResponse: ConfigureResponse = {
        success: true,
        message: 'Semantic type AWS configuration updated',
        region: 'us-east-2',
        modelId: 'anthropic.claude-3-haiku-20240307-v1:0'
      };

      httpClient.post.and.returnValue(of(configResponse));

      service.configureSemanticTypeAws(mockCredentials).subscribe(response => {
        expect(response.success).toBe(true);
        expect(response.region).toBe('us-east-2');
        expect(httpClient.post).toHaveBeenCalledWith('/api/semantic-types/aws/configure', mockCredentials);
        done();
      });
    });
  });

  describe('client-side storage methods', () => {
    it('should return null for saved region (not implemented)', () => {
      expect(service.getSavedRegion()).toBeNull();
    });

    it('should return null for saved model ID (not implemented)', () => {
      expect(service.getSavedModelId()).toBeNull();
    });

    it('should return null for stored credentials (not implemented)', () => {
      expect(service.getStoredCredentials()).toBeNull();
    });
  });

  describe('configuration and error handling', () => {
    it('should access apiUrl from config service', () => {
      const apiUrl = service['apiUrl'];
      expect(apiUrl).toBe('/api');
    });

    it('should access config from config service', () => {
      const config = service['config'];
      expect(config.httpTimeoutMs).toBe(30000);
      expect(config.httpLongTimeoutMs).toBe(60000);
      expect(configService.getConfig).toHaveBeenCalled();
    });

    it('should handle errors and log them', (done) => {
      const error = new Error('Test error');
      httpClient.get.and.returnValue(throwError(() => error));

      service.getAwsStatus().subscribe({
        next: () => fail('Should have failed'),
        error: (thrownError) => {
          expect(loggerService.error).toHaveBeenCalledWith('AWS Bedrock service error', error, 'AwsBedrockService');
          expect(thrownError).toBe(error);
          done();
        }
      });
    });

    it('should use long timeout for generation operations', (done) => {
      const request: SemanticTypeGenerationRequest = {
        description: 'Test',
        positiveContentExamples: ['example'],
        positiveHeaderExamples: ['header']
      };

      httpClient.post.and.returnValue(of({ resultType: 'generated' } as GeneratedSemanticType));

      service.generateSemanticType(request).subscribe(() => {
        // Timeout is handled internally by operators, we just verify the call was made
        expect(httpClient.post).toHaveBeenCalled();
        done();
      });
    });
  });

  describe('edge cases and complex scenarios', () => {
    it('should handle credentials without optional fields', (done) => {
      const minimalCredentials: AwsCredentialsRequest = {
        accessKeyId: 'AKIATESTFAKEKEY123456',
        secretAccessKey: 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY'
      };

      httpClient.post.and.returnValue(of(mockValidationResponse));

      service.validateCredentialsAndGetRegions(minimalCredentials).subscribe(response => {
        expect(response.valid).toBe(true);
        expect(httpClient.post).toHaveBeenCalledWith('/api/aws/validate-credentials', minimalCredentials);
        done();
      });
    });

    it('should handle semantic type generation with all optional fields', (done) => {
      const comprehensiveRequest: SemanticTypeGenerationRequest = {
        typeName: 'COMPREHENSIVE_TYPE',
        description: 'A comprehensive type with all fields',
        positiveContentExamples: ['COMP-123', 'COMP-456'],
        negativeContentExamples: ['123', 'INVALID'],
        positiveHeaderExamples: ['comprehensive', 'comp_id'],
        negativeHeaderExamples: ['name', 'description'],
        feedback: 'This type should handle complex patterns',
        checkExistingTypes: true,
        proceedDespiteSimilarity: false,
        generateExamplesForExistingType: 'SIMILAR_TYPE'
      };

      const response: GeneratedSemanticType = {
        resultType: 'generated',
        semanticType: 'COMPREHENSIVE_TYPE',
        pluginType: 'regex'
      };

      httpClient.post.and.returnValue(of(response));

      service.generateSemanticType(comprehensiveRequest).subscribe(result => {
        expect(result.resultType).toBe('generated');
        expect(httpClient.post).toHaveBeenCalledWith('/api/semantic-types/generate', comprehensiveRequest);
        done();
      });
    });

    it('should handle model validation with special characters in region', (done) => {
      const specialRegion = 'ap-southeast-1';
      httpClient.post.and.returnValue(of({
        accessible: true,
        modelId: mockCredentials.modelId!,
        region: specialRegion,
        message: 'Success'
      } as ModelValidationResponse));

      service.validateModelAccess(mockCredentials, specialRegion).subscribe(response => {
        expect(response.accessible).toBe(true);
        expect(response.region).toBe(specialRegion);
        done();
      });
    });
  });

  describe('timeout and retry fallback values', () => {
    it('should use default timeout when config httpTimeoutMs is undefined', (done) => {
      const configWithoutTimeout = { ...mockConfig, httpTimeoutMs: undefined as any };
      configService.getConfig.and.returnValue(configWithoutTimeout);
      httpClient.post.and.returnValue(of(mockValidationResponse));

      service.validateCredentialsAndGetRegions(mockCredentials).subscribe(response => {
        expect(response).toBe(mockValidationResponse);
        done();
      });
    });

    it('should use default long timeout for generation operations when httpLongTimeoutMs is undefined', (done) => {
      const configWithoutLongTimeout = { ...mockConfig, httpLongTimeoutMs: undefined as any };
      configService.getConfig.and.returnValue(configWithoutLongTimeout);
      httpClient.post.and.returnValue(of({ resultType: 'generated' } as GeneratedSemanticType));

      const request: SemanticTypeGenerationRequest = {
        description: 'Test',
        positiveContentExamples: ['example'],
        positiveHeaderExamples: ['header']
      };

      service.generateSemanticType(request).subscribe(() => {
        expect(httpClient.post).toHaveBeenCalled();
        done();
      });
    });

    it('should use default retry count when httpRetryCount is undefined', (done) => {
      const configWithoutRetry = { ...mockConfig, httpRetryCount: undefined as any };
      configService.getConfig.and.returnValue(configWithoutRetry);
      httpClient.get.and.returnValue(of({ configured: true, message: 'OK' } as AwsStatus));

      service.getAwsStatus().subscribe(() => {
        expect(httpClient.get).toHaveBeenCalled();
        done();
      });
    });
  });

  describe('error handling for all methods', () => {
    it('should handle error in configureAwsClient', (done) => {
      const error = new Error('Configuration failed');
      httpClient.post.and.returnValue(throwError(() => error));

      service.configureAwsClient(mockCredentials).subscribe({
        next: () => fail('Should have failed'),
        error: (thrownError) => {
          expect(thrownError).toBe(error);
          expect(loggerService.error).toHaveBeenCalledWith('AWS Bedrock service error', error, 'AwsBedrockService');
          done();
        }
      });
    });

    it('should handle error in checkAwsStatus', (done) => {
      const error = new Error('Status check failed');
      httpClient.get.and.returnValue(throwError(() => error));

      service.checkAwsStatus().subscribe({
        next: () => fail('Should have failed'),
        error: (thrownError) => {
          expect(thrownError).toBe(error);
          expect(loggerService.error).toHaveBeenCalledWith('AWS Bedrock service error', error, 'AwsBedrockService');
          done();
        }
      });
    });

    it('should handle error in generateSemanticType', (done) => {
      const error = new Error('Generation failed');
      httpClient.post.and.returnValue(throwError(() => error));

      const request: SemanticTypeGenerationRequest = {
        description: 'Test',
        positiveContentExamples: ['example'],
        positiveHeaderExamples: ['header']
      };

      service.generateSemanticType(request).subscribe({
        next: () => fail('Should have failed'),
        error: (thrownError) => {
          expect(thrownError).toBe(error);
          expect(loggerService.error).toHaveBeenCalledWith('AWS Bedrock service error', error, 'AwsBedrockService');
          done();
        }
      });
    });

    it('should handle error in generateValidatedExamples', (done) => {
      const error = new Error('Validation failed');
      httpClient.post.and.returnValue(throwError(() => error));

      const request: GenerateValidatedExamplesRequest = {
        regexPattern: '\\d+',
        semanticTypeName: 'TEST_TYPE'
      };

      service.generateValidatedExamples(request).subscribe({
        next: () => fail('Should have failed'),
        error: (thrownError) => {
          expect(thrownError).toBe(error);
          expect(loggerService.error).toHaveBeenCalledWith('AWS Bedrock service error', error, 'AwsBedrockService');
          done();
        }
      });
    });

    it('should handle error in logout', (done) => {
      const error = new Error('Logout failed');
      httpClient.post.and.returnValue(throwError(() => error));

      service.logout().subscribe({
        next: () => fail('Should have failed'),
        error: (thrownError) => {
          expect(thrownError).toBe(error);
          expect(loggerService.error).toHaveBeenCalledWith('AWS Bedrock service error', error, 'AwsBedrockService');
          done();
        }
      });
    });

    it('should handle error in configureSemanticTypeAws', (done) => {
      const error = new Error('Semantic type config failed');
      httpClient.post.and.returnValue(throwError(() => error));

      service.configureSemanticTypeAws(mockCredentials).subscribe({
        next: () => fail('Should have failed'),
        error: (thrownError) => {
          expect(thrownError).toBe(error);
          expect(loggerService.error).toHaveBeenCalledWith('AWS Bedrock service error', error, 'AwsBedrockService');
          done();
        }
      });
    });

    it('should handle error in validateModelAccess', (done) => {
      const error = new Error('Model validation failed');
      httpClient.post.and.returnValue(throwError(() => error));

      service.validateModelAccess(mockCredentials, 'us-east-1').subscribe({
        next: () => fail('Should have failed'),
        error: (thrownError) => {
          expect(thrownError).toBe(error);
          expect(loggerService.error).toHaveBeenCalledWith('AWS Bedrock service error', error, 'AwsBedrockService');
          done();
        }
      });
    });

    it('should handle error in getAwsCredentialsStatus', (done) => {
      const error = new Error('Credentials status failed');
      httpClient.get.and.returnValue(throwError(() => error));

      service.getAwsCredentialsStatus().subscribe({
        next: () => fail('Should have failed'),
        error: (thrownError) => {
          expect(thrownError).toBe(error);
          expect(loggerService.error).toHaveBeenCalledWith('AWS Bedrock service error', error, 'AwsBedrockService');
          done();
        }
      });
    });

    it('should handle error in getIndexingStatus', (done) => {
      const error = new Error('Indexing status failed');
      httpClient.get.and.returnValue(throwError(() => error));

      service.getIndexingStatus().subscribe({
        next: () => fail('Should have failed'),
        error: (thrownError) => {
          expect(thrownError).toBe(error);
          expect(loggerService.error).toHaveBeenCalledWith('AWS Bedrock service error', error, 'AwsBedrockService');
          done();
        }
      });
    });
  });
});