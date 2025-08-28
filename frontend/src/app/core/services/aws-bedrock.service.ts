import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError, firstValueFrom } from 'rxjs';
import { retry, timeout, catchError } from 'rxjs/operators';
import { ConfigService } from './config.service';
import { LoggerService } from './logger.service';
import { API_ENDPOINTS, buildApiUrl } from '../config/api-endpoints.config';
import * as forge from 'node-forge';

export interface AwsCredentialsRequest {
  accessKeyId: string;
  secretAccessKey: string;
  region?: string;
  modelId?: string;
}

export interface RegionInfo {
  regionId: string;
  displayName: string;
}

export interface ModelInfo {
  modelId: string;
  modelName: string;
  provider: string;
  inputModalities: string[];
  outputModalities: string[];
  requiresInferenceProfile?: boolean;
}

export interface CredentialsValidationResponse {
  valid: boolean;
  message: string;
  regions?: RegionInfo[];
}

export interface ModelsResponse {
  region: string;
  models: ModelInfo[];
  count: number;
}

export interface ConfigureResponse {
  success: boolean;
  message: string;
  region?: string;
  modelId?: string;
}

export interface SemanticTypeGenerationRequest {
  typeName?: string;
  description: string;
  positiveContentExamples: string[];
  negativeContentExamples?: string[];
  positiveHeaderExamples: string[];
  negativeHeaderExamples?: string[];
  feedback?: string;
  checkExistingTypes?: boolean;
  proceedDespiteSimilarity?: boolean;
  generateExamplesForExistingType?: string;
}

export interface GenerateValidatedExamplesRequest {
  regexPattern: string;
  semanticTypeName: string;
  description?: string;
  pluginType?: string;
  existingPositiveExamples?: string[];
  existingNegativeExamples?: string[];
  userDescription?: string;
  generatePositiveOnly?: boolean;
  generateNegativeOnly?: boolean;
  isPatternImprovement?: boolean;
  isHeaderPatternImprovement?: boolean;
  maxRetries?: number;
}

export interface ValidationSummary {
  totalPositiveGenerated: number;
  totalNegativeGenerated: number;
  positiveExamplesValidated: number;
  negativeExamplesValidated: number;
  positiveExamplesFailed: number;
  negativeExamplesFailed: number;
}

export interface GeneratedValidatedExamplesResponse {
  positiveExamples: string[];
  negativeExamples: string[];
  attemptsUsed: number;
  validationSuccessful: boolean;
  error?: string;
  validationSummary: ValidationSummary;
  updatedRegexPattern?: string;
  updatedHeaderPatterns?: string[];
  rationale?: string;
}

export interface HeaderPattern {
  regExp: string;
  confidence: number;
  mandatory: boolean;
  positiveExamples: string[];
  negativeExamples: string[];
  rationale?: string;
}

export interface GeneratedSemanticType {
  resultType: 'generated' | 'existing' | 'error';
  semanticType?: string;
  description?: string;
  pluginType?: 'regex' | 'list';
  regexPattern?: string;
  listValues?: string[];
  positiveContentExamples?: string[];
  negativeContentExamples?: string[];
  positiveHeaderExamples?: string[];
  negativeHeaderExamples?: string[];
  confidenceThreshold?: number;
  headerPatterns?: HeaderPattern[];
  headerPattern?: string;
  priority?: number;
  explanation?: string;

  // Fields for existing type detection
  existingTypeMatch?: string;
  existingTypeDescription?: string;
  existingTypePattern?: string;
  existingTypeHeaderPatterns?: string[];
  existingTypeIsBuiltIn?: boolean;
  suggestedAction?: 'use_existing' | 'extend' | 'replace' | 'create_different';

  // backend now includes a correlationId for tracing
  correlationId?: string;
}

export interface AwsStatus {
  configured: boolean;
  message: string;
  region?: string;
  modelId?: string;
}

export interface ModelValidationResponse {
  accessible: boolean;
  modelId: string;
  region: string;
  message: string;
}

export interface AwsCredentialsStatusResponse {
  credentialsAvailable: boolean;
  storageType: string;
  storageStatus: string;
  canAccessS3: boolean;
  message: string;
  region?: string;
  accessKeyId?: string;
  secretAccessKey?: string;
}

export interface IndexingStatusResponse {
  indexing: boolean;  // Backend returns 'indexing' not 'isIndexing'
  totalTypes: number;
  indexedTypes: number;
  progress: number;
}

@Injectable({
  providedIn: 'root',
})
export class AwsBedrockService {
  private http = inject(HttpClient);
  private configService = inject(ConfigService);
  private logger = inject(LoggerService);
  private cachedPublicKey: CryptoKey | null = null;
  private cachedForgePublicKey: forge.pki.rsa.PublicKey | null = null;

  private get apiUrl(): string {
    return this.configService.apiUrl;
  }

  private get config() {
    return this.configService.getConfig();
  }

  private async fetchAndImportPublicKey(): Promise<{ subtleKey?: CryptoKey; forgeKey?: forge.pki.rsa.PublicKey }> {
    if (this.cachedPublicKey || this.cachedForgePublicKey) {
      return { subtleKey: this.cachedPublicKey || undefined, forgeKey: this.cachedForgePublicKey || undefined };
    }
    const url = buildApiUrl('/aws/crypto/public-key', this.apiUrl);
    const resp = await firstValueFrom(this.http.get<{ publicKey: string }>(url));
    const pem = resp.publicKey || '';

    const canUseSubtle = typeof crypto !== 'undefined' && !!(crypto as any).subtle && (typeof isSecureContext === 'undefined' || isSecureContext);
    if (canUseSubtle) {
      try {
        const b64 = pem
          .replace('-----BEGIN PUBLIC KEY-----', '')
          .replace('-----END PUBLIC KEY-----', '')
          .replace(/\s+/g, '');
        const der = Uint8Array.from(atob(b64), c => c.charCodeAt(0));
        const key = await crypto.subtle.importKey(
          'spki',
          der,
          { name: 'RSA-OAEP', hash: 'SHA-256' },
          false,
          ['encrypt']
        );
        this.cachedPublicKey = key;
        return { subtleKey: key };
      } catch (e) {
        // Fall through to forge
        console.warn('WebCrypto importKey failed; falling back to node-forge', e);
      }
    }

    // node-forge fallback (works on HTTP and older browsers)
    const forgeKey = forge.pki.publicKeyFromPem(pem) as forge.pki.rsa.PublicKey;
    this.cachedForgePublicKey = forgeKey;
    return { forgeKey };
  }

  private async rsaEncryptBase64(plain: string): Promise<string> {
    const { subtleKey, forgeKey } = await this.fetchAndImportPublicKey();
    try {
      if (subtleKey) {
        const data = new TextEncoder().encode(plain);
        const cipher = await crypto.subtle.encrypt({ name: 'RSA-OAEP' }, subtleKey, data);
        const bytes = new Uint8Array(cipher);
        let binary = '';
        bytes.forEach(b => binary += String.fromCharCode(b));
        return btoa(binary);
      }
    } catch (e) {
      console.warn('WebCrypto encrypt failed; falling back to node-forge', e);
    }

    if (!forgeKey) throw new Error('RSA public key not available');
    const buffer = forge.util.createBuffer(forge.util.encodeUtf8(plain));
    const bytes = buffer.getBytes();
    // RSA-OAEP with SHA-256 using node-forge
    const encrypted = forgeKey.encrypt(bytes, 'RSA-OAEP', {
      md: forge.md.sha256.create(),
      mgf1: { md: forge.md.sha256.create() },
    });
    return forge.util.encode64(encrypted);
  }

  validateCredentialsAndGetRegions(
    credentials: AwsCredentialsRequest
  ): Observable<CredentialsValidationResponse> {
    const url = buildApiUrl(API_ENDPOINTS.AWS.VALIDATE_CREDENTIALS, this.apiUrl);
    return this.http.post<CredentialsValidationResponse>(url, credentials).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  async validateCredentialsAndGetRegionsEncrypted(
    credentials: AwsCredentialsRequest
  ): Promise<CredentialsValidationResponse> {
    try {
      const body = {
        accessKeyId: await this.rsaEncryptBase64(credentials.accessKeyId),
        secretAccessKey: await this.rsaEncryptBase64(credentials.secretAccessKey),
        region: credentials.region,
      };
      const url = buildApiUrl('/aws/crypto/validate-credentials', this.apiUrl);
      return await firstValueFrom(this.http.post<CredentialsValidationResponse>(url, body));
    } catch (error) {
      throw error as any;
    }
  }

  getModelsForRegion(
    credentials: AwsCredentialsRequest,
    region: string
  ): Observable<ModelsResponse> {
    const url = buildApiUrl(API_ENDPOINTS.AWS.GET_MODELS(region), this.apiUrl);
    return this.http.post<ModelsResponse>(url, credentials).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  async getModelsForRegionEncrypted(
    credentials: AwsCredentialsRequest,
    region: string
  ): Promise<ModelsResponse> {
    try {
      const body = {
        accessKeyId: await this.rsaEncryptBase64(credentials.accessKeyId),
        secretAccessKey: await this.rsaEncryptBase64(credentials.secretAccessKey),
        region,
      };
      const url = buildApiUrl(`/aws/crypto/models/${region}`, this.apiUrl);
      return await firstValueFrom(this.http.post<ModelsResponse>(url, body));
    } catch (error) {
      throw error as any;
    }
  }

  configureAwsClient(credentials: AwsCredentialsRequest): Observable<ConfigureResponse> {
    const url = buildApiUrl(API_ENDPOINTS.AWS.CONFIGURE, this.apiUrl);
    return this.http.post<ConfigureResponse>(url, credentials).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  async configureAwsClientEncrypted(
    credentials: AwsCredentialsRequest
  ): Promise<ConfigureResponse> {
    try {
      const body = {
        accessKeyId: await this.rsaEncryptBase64(credentials.accessKeyId),
        secretAccessKey: await this.rsaEncryptBase64(credentials.secretAccessKey),
        region: credentials.region,
      };
      const url = buildApiUrl('/aws/crypto/configure', this.apiUrl);
      return await firstValueFrom(this.http.post<ConfigureResponse>(url, body));
    } catch (error) {
      throw error as any;
    }
  }

  getAwsStatus(): Observable<AwsStatus> {
    const url = buildApiUrl(API_ENDPOINTS.AWS.STATUS, this.apiUrl);
    return this.http.get<AwsStatus>(url).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  checkAwsStatus(): Observable<AwsStatus> {
    const url = buildApiUrl(API_ENDPOINTS.AWS.STATUS_ST, this.apiUrl);
    return this.http.get<AwsStatus>(url).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  clearAwsCredentials(): Observable<{ success: boolean }> {
    const url = buildApiUrl(API_ENDPOINTS.AWS.DELETE_CREDENTIALS, this.apiUrl);
    return this.http.delete<{ success: boolean }>(url).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  generateSemanticType(request: SemanticTypeGenerationRequest): Observable<GeneratedSemanticType> {
    const url = buildApiUrl(API_ENDPOINTS.SEMANTIC_TYPES.GENERATE, this.apiUrl);
    return this.http.post<GeneratedSemanticType>(url, request).pipe(
      timeout(this.config?.httpLongTimeoutMs || 60000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  generateValidatedExamples(
    request: GenerateValidatedExamplesRequest
  ): Observable<GeneratedValidatedExamplesResponse> {
    const url = buildApiUrl(API_ENDPOINTS.SEMANTIC_TYPES.GENERATE_VALIDATED_EXAMPLES, this.apiUrl);
    return this.http.post<GeneratedValidatedExamplesResponse>(url, request).pipe(
      timeout(this.config?.httpLongTimeoutMs || 60000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  logout(): Observable<{ success: boolean }> {
    const url = buildApiUrl(API_ENDPOINTS.AWS.LOGOUT_ST, this.apiUrl);
    return this.http.post<{ success: boolean }>(url, {}).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  configureSemanticTypeAws(credentials: AwsCredentialsRequest): Observable<ConfigureResponse> {
    const url = buildApiUrl(API_ENDPOINTS.AWS.CONFIGURE_ST, this.apiUrl);
    return this.http.post<ConfigureResponse>(url, credentials).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  validateModelAccess(
    credentials: AwsCredentialsRequest,
    region: string
  ): Observable<ModelValidationResponse> {
    const url = buildApiUrl(API_ENDPOINTS.AWS.VALIDATE_MODEL(region), this.apiUrl);
    return this.http.post<ModelValidationResponse>(url, credentials).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  async validateModelAccessEncrypted(
    credentials: AwsCredentialsRequest,
    region: string
  ): Promise<ModelValidationResponse> {
    try {
      const body = {
        accessKeyId: await this.rsaEncryptBase64(credentials.accessKeyId),
        secretAccessKey: await this.rsaEncryptBase64(credentials.secretAccessKey),
        region,
        modelId: credentials.modelId,
      };
      const url = buildApiUrl(`/aws/crypto/validate-model/${region}`, this.apiUrl);
      return await firstValueFrom(this.http.post<ModelValidationResponse>(url, body));
    } catch (error) {
      throw error as any;
    }
  }

  getAwsCredentialsStatus(): Observable<AwsCredentialsStatusResponse> {
    const url = buildApiUrl(API_ENDPOINTS.AWS.CREDENTIALS_STATUS, this.apiUrl);
    return this.http.get<AwsCredentialsStatusResponse>(url).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }
  
  getIndexingStatus(): Observable<IndexingStatusResponse> {
    const url = buildApiUrl(API_ENDPOINTS.AWS.INDEXING_STATUS, this.apiUrl);
    return this.http.get<IndexingStatusResponse>(url).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  // Client-side storage methods (these don't call backend)
  getSavedRegion(): string | null {
    return null;
  }

  getSavedModelId(): string | null {
    return null;
  }

  getStoredCredentials(): AwsCredentialsRequest | null {
    return null;
  }

  private handleError(error: unknown): Observable<never> {
    this.logger.error('AWS Bedrock service error', error, 'AwsBedrockService');
    return throwError(() => error);
  }
}