import { Injectable, inject, PLATFORM_ID } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import { LoggerService } from './logger.service';
import { EnvironmentService } from './environment.service';
import { API_ENDPOINTS, buildApiUrl } from '../config/api-endpoints.config';

export interface RuntimeConfig {
  maxFileSize: number;
  maxRows: number;
  apiUrl: string;

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

  // AWS Credentials (optional, for prefilling)
  awsAccessKeyId?: string;
  awsSecretAccessKey?: string;
  awsRegion?: string;
  awsModelId?: string;
}

/**
 * Service to manage runtime configuration loaded from the backend.
 * All configuration values come from docker-compose environment variables.
 */
@Injectable({
  providedIn: 'root',
})
export class ConfigService {
  private config: RuntimeConfig | null = null;
  private configPromise: Promise<RuntimeConfig> | null = null;
  private http = inject(HttpClient);
  private platformId = inject(PLATFORM_ID);
  private logger = inject(LoggerService);
  private environmentService = inject(EnvironmentService);

  // Default configuration from docker-compose environment variables
  private get defaultConfig(): RuntimeConfig {
    const envConfig = this.environmentService.getEnvironmentConfig();
    return {
      maxFileSize: envConfig.maxFileSize,
      maxRows: envConfig.maxRows,
      apiUrl: envConfig.apiUrl,

      // HTTP Service Configuration
      httpTimeoutMs: envConfig.httpTimeoutMs,
      httpRetryCount: envConfig.httpRetryCount,
      httpLongTimeoutMs: envConfig.httpLongTimeoutMs,

      // UI Configuration
      baseZIndex: envConfig.baseZIndex,

      // Semantic Type Configuration
      defaultHighThreshold: envConfig.defaultHighThreshold,
      defaultMediumThreshold: envConfig.defaultMediumThreshold,
      defaultLowThreshold: envConfig.defaultLowThreshold,
      highThresholdMin: envConfig.highThresholdMin,
      highThresholdMax: envConfig.highThresholdMax,
      mediumThresholdMin: envConfig.mediumThresholdMin,
      mediumThresholdMax: envConfig.mediumThresholdMax,
      lowThresholdMin: envConfig.lowThresholdMin,
      lowThresholdMax: envConfig.lowThresholdMax,

      // Animation and Delay Configuration
      notificationDelayMs: envConfig.notificationDelayMs,
      reanalysisDelayMs: envConfig.reanalysisDelayMs,

      // AWS defaults (SSR only; in browser these will be empty strings via EnvironmentService)
      awsAccessKeyId: envConfig.awsAccessKeyId,
      awsSecretAccessKey: envConfig.awsSecretAccessKey,
      awsRegion: envConfig.awsRegion,
      awsModelId: envConfig.awsModelId,
    };
  }

  /**
   * Loads configuration from the backend.
   * This should be called once during application initialization.
   */
  async loadConfig(): Promise<RuntimeConfig> {
    if (this.config) {
      return this.config;
    }

    if (this.configPromise) {
      return this.configPromise;
    }

    // During SSR/build, use default configuration
    if (!isPlatformBrowser(this.platformId)) {
      this.config = this.defaultConfig;
      return this.config;
    }

    // In browser: fetch backend config and merge over defaults
    this.configPromise = this.fetchConfig().then(remote => {
      const merged: RuntimeConfig = { ...this.defaultConfig, ...remote };
      this.config = merged;
      return merged;
    }).catch(err => {
      this.logger.warn('Failed to load configuration from backend, using defaults', err, 'ConfigService');
      this.config = this.defaultConfig;
      return this.config;
    });

    this.config = await this.configPromise;
    return this.config;
  }

  private async fetchConfig(): Promise<RuntimeConfig> {
    const url = buildApiUrl(API_ENDPOINTS.CONFIG, this.defaultConfig.apiUrl);
    const response = await firstValueFrom(this.http.get<RuntimeConfig>(url));
    if (response) {
      this.logger.info('Configuration loaded from backend', undefined, 'ConfigService');
      // Merge with defaults to ensure all properties exist
      return { ...this.defaultConfig, ...response };
    }
    throw new Error('Empty configuration response');
  }

  /**
   * Gets the current configuration.
   * Returns default config if not loaded yet.
   */
  getConfig(): RuntimeConfig {
    if (!this.config) {
      // Return default config during SSR or if not loaded
      if (!isPlatformBrowser(this.platformId)) {
        return this.defaultConfig;
      }
      throw new Error('Configuration not loaded. Call loadConfig() first.');
    }
    return this.config;
  }

  get maxFileSize(): number {
    return this.getConfig().maxFileSize;
  }

  get maxRows(): number {
    return this.getConfig().maxRows;
  }

  get apiUrl(): string {
    return this.getConfig().apiUrl;
  }
}
