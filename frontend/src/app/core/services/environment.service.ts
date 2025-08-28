import { Injectable, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

export interface EnvironmentConfig {
  port: number;
  apiUrl: string;
  apiHost: string;
  maxFileSize: number;
  maxRows: number;
  nodeEnv: string;
  backendPort: number;

  // HTTP Service Configuration
  httpTimeoutMs: number;
  httpRetryCount: number;
  httpLongTimeoutMs: number;

  // UI Configuration
  baseZIndex: number;

  // Semantic Type Configuration
  defaultHighThreshold: number;
  defaultMediumThreshold: number;
  defaultLowThreshold: number;
  highThresholdMin: number;
  highThresholdMax: number;
  mediumThresholdMin: number;
  mediumThresholdMax: number;
  lowThresholdMin: number;
  lowThresholdMax: number;

  // Animation and Delay Configuration
  notificationDelayMs: number;
  reanalysisDelayMs: number;

  // Optional AWS defaults for autofill (SSR only)
  awsAccessKeyId?: string;
  awsSecretAccessKey?: string;
  awsRegion?: string;
  awsModelId?: string;
}

/**
 * Service to read environment configuration set by docker-compose.
 * These values are passed through from docker-compose.yml environment variables.
 */
@Injectable({
  providedIn: 'root',
})
export class EnvironmentService {
  private platformId = inject(PLATFORM_ID);

  private getEnvValue(key: string, defaultValue: string): string {
    if (isPlatformBrowser(this.platformId)) {
      // In browser, environment variables are not available
      // Configuration comes from the backend /api/v1/config endpoint
      return defaultValue;
    }

    // In Node.js/SSR context, read from process.env
    return (globalThis as { process?: { env?: Record<string, string> } }).process?.env?.[key] || defaultValue;
  }

  getEnvironmentConfig(): EnvironmentConfig {
    return {
      port: parseInt(this.getEnvValue('PORT', '4000'), 10),
      apiUrl: this.getEnvValue('API_URL', '/api'),
      apiHost: this.getEnvValue('API_HOST', 'http://backend:8081'),
      maxFileSize: parseInt(this.getEnvValue('MAX_FILE_SIZE', '10485760'), 10),
      maxRows: parseInt(this.getEnvValue('MAX_ROWS', '1000'), 10),
      nodeEnv: this.getEnvValue('NODE_ENV', 'production'),
      backendPort: parseInt(this.getEnvValue('BACKEND_PORT', '8081'), 10),

      // HTTP Service Configuration
      httpTimeoutMs: parseInt(this.getEnvValue('HTTP_TIMEOUT_MS', '30000'), 10),
      httpRetryCount: parseInt(this.getEnvValue('HTTP_RETRY_COUNT', '2'), 10),
      httpLongTimeoutMs: parseInt(this.getEnvValue('HTTP_LONG_TIMEOUT_MS', '60000'), 10),

      // UI Configuration
      baseZIndex: parseInt(this.getEnvValue('BASE_Z_INDEX', '10000'), 10),

      // Semantic Type Configuration
      defaultHighThreshold: parseInt(this.getEnvValue('DEFAULT_HIGH_THRESHOLD', '95'), 10),
      defaultMediumThreshold: parseInt(this.getEnvValue('DEFAULT_MEDIUM_THRESHOLD', '80'), 10),
      defaultLowThreshold: parseInt(this.getEnvValue('DEFAULT_LOW_THRESHOLD', '50'), 10),
      highThresholdMin: parseInt(this.getEnvValue('HIGH_THRESHOLD_MIN', '90'), 10),
      highThresholdMax: parseInt(this.getEnvValue('HIGH_THRESHOLD_MAX', '100'), 10),
      mediumThresholdMin: parseInt(this.getEnvValue('MEDIUM_THRESHOLD_MIN', '70'), 10),
      mediumThresholdMax: parseInt(this.getEnvValue('MEDIUM_THRESHOLD_MAX', '89'), 10),
      lowThresholdMin: parseInt(this.getEnvValue('LOW_THRESHOLD_MIN', '0'), 10),
      lowThresholdMax: parseInt(this.getEnvValue('LOW_THRESHOLD_MAX', '69'), 10),

      // Animation and Delay Configuration
      notificationDelayMs: parseInt(this.getEnvValue('NOTIFICATION_DELAY_MS', '1000'), 10),
      reanalysisDelayMs: parseInt(this.getEnvValue('REANALYSIS_DELAY_MS', '1000'), 10),

      // Optional AWS defaults for autofill
      awsAccessKeyId: this.getEnvValue('AWS_ACCESS_KEY_ID', ''),
      awsSecretAccessKey: this.getEnvValue('AWS_SECRET_ACCESS_KEY', ''),
      awsRegion: this.getEnvValue('AWS_REGION', ''),
      awsModelId: this.getEnvValue('AWS_BEDROCK_MODEL_ID', ''),
    };
  }

  get isDevelopment(): boolean {
    const config = this.getEnvironmentConfig();
    return config.nodeEnv === 'docker' || config.nodeEnv === 'development';
  }

  get isProduction(): boolean {
    return this.getEnvironmentConfig().nodeEnv === 'production';
  }
}
