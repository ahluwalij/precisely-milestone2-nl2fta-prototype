import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { DynamicDialogRef, DynamicDialogConfig } from 'primeng/dynamicdialog';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { PasswordModule } from 'primeng/password';
import { ToastModule } from 'primeng/toast';
import { DropdownModule } from 'primeng/dropdown';
import { DividerModule } from 'primeng/divider';

import { MessageService } from 'primeng/api';
import {
  AwsBedrockService,
  AwsCredentialsRequest,
  RegionInfo,
  ModelInfo,
  IndexingStatusResponse,
} from '../../../core/services/aws-bedrock.service';
import { ConfigService } from '../../../core/services/config.service';
import { SemanticTypeService } from '../../../core/services/semantic-type.service';
  import { AnalysisService } from '../../../core/services/analysis.service';
import { LoggerService } from '../../../core/services/logger.service';

interface DropdownOption {
  label: string;
  value: string;
}

interface ApiError {
  error?: {
    message?: string;
  };
}

@Component({
  selector: 'app-aws-credentials-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    InputTextModule,
    ButtonModule,
    PasswordModule,
    ToastModule,
    DropdownModule,
    DividerModule,
  ],
  providers: [MessageService],
  templateUrl: './aws-credentials-modal.component.html',
  styleUrl: './aws-credentials-modal.component.css',
})
export class AwsCredentialsModalComponent implements OnInit {
  ref = inject(DynamicDialogRef);
  config = inject(DynamicDialogConfig);
  awsBedrockService = inject(AwsBedrockService);
  messageService = inject(MessageService);
  configService = inject(ConfigService);
  semanticTypeService = inject(SemanticTypeService);
  analysisService = inject(AnalysisService);
  private logger = inject(LoggerService);

  // Form data
  accessKeyId = '';
  secretAccessKey = '';
  selectedRegion: string | null = null;

  // UI state
  isValidating = false;
  isLoadingModels = false;
  isConfiguring = false;
  credentialsValidated = false;
  isIndexing = false;
  // Region filtering/validation state
  isValidatingRegions = false;
  private statusInterval?: NodeJS.Timeout;
  indexingProgress = 0;
  isConnected = false;

  // Data
  regions: RegionInfo[] = [];
  regionOptions: DropdownOption[] = [];
  models: ModelInfo[] = [];
  modelOptions: DropdownOption[] = [];
  selectedModel: string | null = null;

  // Model validation state
  modelValidationStatus: Map<string, boolean | null> = new Map();
  isValidatingModels = false;
  // Cache of accessible models per region after pre-validation
  private accessibleModelsByRegion = new Map<string, ModelInfo[]>();

  // Stored credentials for subsequent calls
  private validatedCredentials: AwsCredentialsRequest | null = null;

  // Internal storage for actual credential values
  private actualAccessKeyId = '';
  private actualSecretAccessKey = '';

  async ngOnInit() {
    try {
      // Prefer secure local fetch on production SSR server (does not exist under ng serve)
      const prefilled = await this.tryPrefillFromLocalServer();
      if (!prefilled) {
        await this.loadFromConfigOrStorage();
      }
    } catch {
      await this.loadFromConfigOrStorage();
    }
  }
  
  private async tryPrefillFromLocalServer(): Promise<boolean> {
    try {
      // Generate ephemeral RSA key pair (browser only)
      const keyPair = await crypto.subtle.generateKey(
        { name: 'RSA-OAEP', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
        true,
        ['encrypt', 'decrypt']
      );

      // Export public key to PEM
      const spki = await crypto.subtle.exportKey('spki', keyPair.publicKey);
      const spkiB64 = btoa(String.fromCharCode(...new Uint8Array(spki))
        .replace(/(.{1})/g, (m) => m));
      const pem = `-----BEGIN PUBLIC KEY-----\n${spkiB64.replace(/(.{64})/g, '$1\n')}\n-----END PUBLIC KEY-----\n`;

      const resp = await fetch('/local/aws/defaults', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ publicKeyPem: pem }),
      });

      if (!resp.ok) return false;
      const data = await resp.json();
      if (!data?.hasDefaults) return false;

      // Decrypt base64(ciphertext) using private key
      const decryptB64 = async (b64: string) => {
        const bytes = Uint8Array.from(atob(b64), c => c.charCodeAt(0));
        const plain = await crypto.subtle.decrypt({ name: 'RSA-OAEP' }, keyPair.privateKey, bytes);
        return new TextDecoder().decode(new Uint8Array(plain));
      };

      const decryptedAccessKeyId = await decryptB64(data.accessKeyId);
      const decryptedSecretAccessKey = await decryptB64(data.secretAccessKey);

      this.actualAccessKeyId = decryptedAccessKeyId;
      this.actualSecretAccessKey = decryptedSecretAccessKey;
      this.accessKeyId = decryptedAccessKeyId;
      this.secretAccessKey = decryptedSecretAccessKey;

      if (data.region) this.selectedRegion = data.region;
      if (data.modelId) this.selectedModel = data.modelId;

      return true;
    } catch (e) {
      // Local server endpoint may not exist under ng serve; ignore
      return false;
    }
  }
  
  private async loadFromConfigOrStorage() {
    // Fall back to config service or stored credentials
    const runtimeConfig = this.configService.getConfig();
    
    // Pre-fill AWS credentials from environment if available
    if (runtimeConfig.awsAccessKeyId && runtimeConfig.awsSecretAccessKey) {
      this.actualAccessKeyId = runtimeConfig.awsAccessKeyId;
      this.actualSecretAccessKey = runtimeConfig.awsSecretAccessKey;
      this.accessKeyId = runtimeConfig.awsAccessKeyId;
      this.secretAccessKey = runtimeConfig.awsSecretAccessKey;
      
      // Also pre-fill region and model if provided in environment
      if (runtimeConfig.awsRegion) {
        this.selectedRegion = runtimeConfig.awsRegion;
      }
      if (runtimeConfig.awsModelId) {
        this.selectedModel = runtimeConfig.awsModelId;
      }
    } else {
      // Fall back to stored credentials if no environment credentials
      const storedCredentials = this.awsBedrockService.getStoredCredentials();
      if (storedCredentials) {
        this.actualAccessKeyId = storedCredentials.accessKeyId;
        this.actualSecretAccessKey = storedCredentials.secretAccessKey;
        this.accessKeyId = storedCredentials.accessKeyId;
        this.secretAccessKey = storedCredentials.secretAccessKey;
      }
      
      // Pre-fill saved values if available
      const savedRegion = this.awsBedrockService.getSavedRegion();
      const savedModelId = this.awsBedrockService.getSavedModelId();

      if (savedRegion) {
        this.selectedRegion = savedRegion;
      }
      if (savedModelId) {
        this.selectedModel = savedModelId;
      }
    }
  }

  /**
   * Filters regions to only allowed US regions and keeps only those with at least one accessible Anthropic model.
   * Also pre-populates models/options for the selected region.
   */
  private async filterRegionsByAnthropicAccess(): Promise<void> {
    if (!this.validatedCredentials) return;

    // Allowed regions per requirement
    const allowed = new Set(['us-east-1', 'us-east-2', 'us-west-1', 'us-west-2']);

    // Reset caches/state
    this.isValidatingRegions = true;
    this.regionOptions = [];
    this.accessibleModelsByRegion.clear();

    const candidateRegions = this.regions.filter(r => allowed.has(r.regionId));

    const excludedDueToNoAccess: string[] = [];
    const accessibleRegionEntries: { info: RegionInfo; models: ModelInfo[] }[] = [];

    for (const region of candidateRegions) {
      try {
        const resp = await this.awsBedrockService.getModelsForRegionEncrypted(this.validatedCredentials, region.regionId);
        // ONLY support Claude Sonnet 4
        const sonnet4Candidates: ModelInfo[] = (resp?.models || []).filter(
          m => m.provider === 'Anthropic' && (
            (m.modelId && m.modelId.toLowerCase().includes('claude-sonnet-4')) ||
            (m.modelName && m.modelName.toLowerCase().includes('sonnet 4'))
          )
        );

        if (!sonnet4Candidates.length) {
          excludedDueToNoAccess.push(region.regionId);
          continue;
        }

        // Validate access for Claude Sonnet 4 in this region (stop early on first accessible)
        const validationResults = await Promise.all(
          sonnet4Candidates.map(async m => {
            try {
              const res = await this.awsBedrockService.validateModelAccessEncrypted(
                { ...this.validatedCredentials!, modelId: m.modelId },
                region.regionId
              );
              return res?.accessible ? m : null;
            } catch {
              return null;
            }
          })
        );

        const accessibleSonnet4 = validationResults.filter(Boolean) as ModelInfo[];
        if (accessibleSonnet4.length) {
          // Keep only Sonnet 4 models
          accessibleRegionEntries.push({ info: region, models: accessibleSonnet4 });
          this.accessibleModelsByRegion.set(region.regionId, accessibleSonnet4);
        } else {
          excludedDueToNoAccess.push(region.regionId);
        }
      } catch {
        excludedDueToNoAccess.push(region.regionId);
      }
    }

    // Finalize regions/options
    this.regions = accessibleRegionEntries.map(e => e.info);
    this.regionOptions = accessibleRegionEntries.map(e => ({
      label: e.info.displayName,
      value: e.info.regionId,
    }));

    // Keep selectedRegion if still valid, else pick first accessible
    if (!this.selectedRegion || !this.regions.find(r => r.regionId === this.selectedRegion)) {
      this.selectedRegion = this.regions[0]?.regionId || null;
    }

    // Populate models/options for current selection from cache
    const accessibleForSelected = this.selectedRegion
      ? this.accessibleModelsByRegion.get(this.selectedRegion) || []
      : [];
    // Only Sonnet 4 models are cached
    this.models = accessibleForSelected;
    this.modelValidationStatus.clear();
    accessibleForSelected.forEach(m => this.modelValidationStatus.set(m.modelId, true));
    this.modelOptions = accessibleForSelected
      .slice()
      .sort((a, b) => a.modelName.localeCompare(b.modelName))
      .map(model => ({ label: model.modelName, value: model.modelId }));

    // Auto-select default model if needed
    if (!this.selectedModel && accessibleForSelected.length) {
      // Pick the first Claude Sonnet 4 candidate
      this.selectedModel = accessibleForSelected[0].modelId;
    } else if (this.selectedModel && !accessibleForSelected.find(m => m.modelId === this.selectedModel)) {
      this.selectedModel = accessibleForSelected[0]?.modelId || null;
    }

    // Success toast after finishing filtering
    this.messageService.add({
      severity: 'success',
      summary: 'Success',
      detail: 'AWS credentials validated successfully',
    });

    this.isValidatingRegions = false;
  }
  /**
   * Validates credentials and loads available regions
   */
  async validateCredentials() {
    // Accept values from either secure internal fields or the visible fields (used by unit tests)
    const effectiveAccessKeyId = this.actualAccessKeyId || this.accessKeyId;
    const effectiveSecretAccessKey = this.actualSecretAccessKey || this.secretAccessKey;

    if (!effectiveAccessKeyId || !effectiveSecretAccessKey) {
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Please enter both Access Key ID and Secret Access Key',
      });
      return;
    }

    this.isValidating = true;
    this.isValidatingRegions = true; // lock region UI until regions are filtered/validated
    const credentials: AwsCredentialsRequest = {
      accessKeyId: effectiveAccessKeyId,
      secretAccessKey: effectiveSecretAccessKey,
    };

    // Normalize internal storage so subsequent steps use the same values
    this.actualAccessKeyId = effectiveAccessKeyId;
    this.actualSecretAccessKey = effectiveSecretAccessKey;

    try {
      const response = await this.awsBedrockService.validateCredentialsAndGetRegionsEncrypted(credentials);

      if (response?.valid && response.regions) {
        this.credentialsValidated = true;
        this.validatedCredentials = credentials;
        this.regions = response.regions;

        // New behavior: filter to allowed regions and only show ones with Anthropic access
        await this.filterRegionsByAnthropicAccess();

        // If no regions remain, surface error and reset selection
        if (this.regionOptions.length === 0) {
          this.selectedRegion = null;
          this.messageService.add({
            severity: 'error',
            summary: 'No Accessible Regions',
            detail: 'No regions have access to the required model. Please enable model access in AWS Bedrock or try different credentials.',
          });
        }
      } else {
        this.messageService.add({
          severity: 'error',
          summary: 'Validation Failed',
          detail: response?.message || 'Invalid AWS credentials',
        });
      }
    } catch (error) {
      const err = error as ApiError;
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: err.error?.message || 'Failed to validate AWS credentials',
      });
    } finally {
      this.isValidating = false;
      this.isValidatingRegions = false;
    }
  }

  /**
   * Loads available models when region is selected
   */
  async loadModelsForRegion() {
    if (!this.selectedRegion || !this.validatedCredentials) {
      return;
    }

    // Use cached models from initial validation instead of re-fetching
    const cachedModels = this.accessibleModelsByRegion.get(this.selectedRegion);
    
    if (cachedModels) {
      // Use cached data - no need to show loading state since it's instant
      this.models = cachedModels;
      this.modelValidationStatus.clear();
      
      // Mark all cached models as accessible (they were already validated)
      cachedModels.forEach(m => this.modelValidationStatus.set(m.modelId, true));
      
      // Create model options
      this.modelOptions = cachedModels
        .slice()
        .sort((a, b) => a.modelName.localeCompare(b.modelName))
        .map(model => ({
          label: model.modelName,
          value: model.modelId,
        }));
      
      // Handle model selection
      if (this.selectedModel && cachedModels.find(m => m.modelId === this.selectedModel)) {
        // Keep the selected model
      } else if (cachedModels.length === 1) {
        // Auto-select if only one model
        this.selectedModel = cachedModels[0].modelId;
      } else if (cachedModels.length > 0) {
        // Select first available model
        this.selectedModel = cachedModels[0].modelId;
      } else {
        this.selectedModel = null;
      }
      
      return; // Exit early - no need for API calls
    }

    // Fallback path should rarely be used; keep as-is for safety
    this.isLoadingModels = true;
    this.models = [];
    this.modelOptions = [];
    this.selectedModel = null;
    this.modelValidationStatus.clear();

    try {
      const response = await this.awsBedrockService.getModelsForRegionEncrypted(this.validatedCredentials, this.selectedRegion);

      if (response?.models) {
        // ONLY support Claude Sonnet 4
        this.models = response.models.filter(
          m => m.provider === 'Anthropic' && (
            (m.modelId && m.modelId.toLowerCase().includes('claude-sonnet-4')) ||
            (m.modelName && m.modelName.toLowerCase().includes('sonnet 4'))
          )
        );

        // Validate model access for each model
        this.isValidatingModels = true;
        await this.validateModelAccess();

        // Create options only for accessible models
        const accessibleModels = this.models.filter(
          model => this.modelValidationStatus.get(model.modelId) === true
        );

        if (accessibleModels.length === 0) {
          this.messageService.add({
            severity: 'warn',
            summary: 'No Accessible Models',
            detail: `No Anthropic models are accessible in ${this.selectedRegion}. Please enable model access in AWS Bedrock console or select a different region.`,
          });

          // Clear the model options
          this.modelOptions = [];
          this.selectedModel = null;
        } else {
          this.modelOptions = accessibleModels
            .sort((a, b) => a.modelName.localeCompare(b.modelName))
            .map(model => ({
              label: model.modelName,
              value: model.modelId,
            }));
        }

        // If we have a saved model that's still available and accessible, select it
        if (this.selectedModel && accessibleModels.find(m => m.modelId === this.selectedModel)) {
          // Keep the selected model
        } else if (accessibleModels.length === 1) {
          // Auto-select if only one model
          this.selectedModel = accessibleModels[0].modelId;
        } else if (accessibleModels.length > 0) {
          // Only Sonnet 4 candidates remain
          this.selectedModel = accessibleModels[0].modelId;
        }
      }
    } catch (error) {
      const err = error as ApiError;
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: err.error?.message || 'Failed to load models for the selected region',
      });
    } finally {
      this.isLoadingModels = false;
      this.isValidatingModels = false;
    }
  }

  /**
   * Validates access to each model
   */
  private async validateModelAccess() {
    if (!this.validatedCredentials || !this.selectedRegion) {
      return;
    }

    // Models that are known to generally work without explicit access grants
    const commonlyAccessibleModels = new Set([
      'amazon.titan-text-express-v1',
      'amazon.titan-text-lite-v1',
      'amazon.titan-embed-text-v1',
      'amazon.titan-embed-text-v2:0',
    ]);

    // Only validate Anthropic models and other models that require access
    const modelsToValidate = this.models.filter(
      model => !commonlyAccessibleModels.has(model.modelId)
    );

    // Validate each model in parallel (with a limit to avoid rate limiting)
    const batchSize = 5;
    for (let i = 0; i < modelsToValidate.length; i += batchSize) {
      const batch = modelsToValidate.slice(i, i + batchSize);

      const validationPromises = batch.map(async model => {
        try {
          const response = await this.awsBedrockService.validateModelAccessEncrypted(
            { ...this.validatedCredentials!, modelId: model.modelId },
            this.selectedRegion!
          );

          this.modelValidationStatus.set(model.modelId, response?.accessible || false);

          if (!response?.accessible) {
            // Model is not accessible, status already set to false
          }
        } catch {
          this.modelValidationStatus.set(model.modelId, false);
        }
      });

      await Promise.all(validationPromises);
    }

    // Mark commonly accessible models as available
    this.models
      .filter(model => commonlyAccessibleModels.has(model.modelId))
      .forEach(model => this.modelValidationStatus.set(model.modelId, true));
  }

  /**
   * Handles region selection change
   */
  onRegionChange() {
    // Load models and validate access whenever region changes
    void this.loadModelsForRegion();
  }

  /**
   * Configures AWS Bedrock with the selected settings
   */
  async configure() {
    if (!this.validatedCredentials || !this.selectedRegion) {
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Please complete all steps before configuring',
      });
      return;
    }

    // Require a selected model and validate access before configuring
    if (!this.selectedModel) {
      this.messageService.add({
        severity: 'error',
        summary: 'Model Required',
        detail: 'Select an accessible model for the chosen region before configuring',
      });
      return;
    }

    // Immediately show loading and disable controls
    this.isConfiguring = true;

    try {
      const access = await this.awsBedrockService.validateModelAccessEncrypted(
        { ...this.validatedCredentials, modelId: this.selectedModel },
        this.selectedRegion
      );
      if (!access?.accessible) {
        this.messageService.add({
          severity: 'error',
          summary: 'Model Access Denied',
          detail: access?.message || 'You do not have access to the selected model in this region',
        });
        this.isConfiguring = false; // revert state on validation failure
        return;
      }
    } catch (error) {
      const err = error as ApiError;
      this.messageService.add({
        severity: 'error',
        summary: 'Model Validation Error',
        detail: err.error?.message || 'Failed to validate model access for the selected region',
      });
      this.isConfiguring = false; // revert state on validation error
      return;
    }

    const fullCredentials: AwsCredentialsRequest = {
      ...this.validatedCredentials,
      region: this.selectedRegion,
    };

    try {
      // Configure AWS client (encrypted)
      const response = await this.awsBedrockService.configureAwsClientEncrypted(fullCredentials);

      if (!response?.success) {
        this.messageService.add({
          severity: 'error',
          summary: 'Configuration Failed',
          detail: response?.message || 'Failed to configure AWS Bedrock',
        });
        return;
      }

      // Configuration successful - don't set isConfiguring to false yet
      // Keep the button disabled until we know if indexing is needed
      
      // Show initial success message
      this.messageService.add({
        severity: 'success',
        summary: 'Success',
        detail: 'AWS Bedrock initialized successfully',
      });
      
      // Start polling indexing status
      this.pollIndexingStatus();
      
      // Refresh semantic types and await completion before closing to avoid race with parent
      try {
        await this.semanticTypeService.refreshTypes();
      } catch {}
      
    } catch (error) {
      const err = error as ApiError;
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: err.error?.message || 'Failed to configure AWS Bedrock',
      });
      this.isConfiguring = false;
    }
  }
  
  /**
   * Handle successful configuration (no indexing needed)
   */
  private async handleConfigurationSuccess(): Promise<void> {
    this.isConnected = true;
    this.isIndexing = false;
    this.isConfiguring = false;
    
    this.messageService.add({
      severity: 'success',
      summary: 'Success',
      detail: 'AWS Bedrock initialized successfully',
    });
    
    // Immediately reanalyze all stored analyses so UI reflects new types
    try {
      await this.analysisService.reanalyzeAllAnalyses();
    } catch (err) {
      this.logger.warn('Failed to trigger reanalysis after AWS configuration', err as unknown, 'AwsCredentialsModalComponent');
    }

    // Close dialog after showing message
    setTimeout(() => {
      this.ref.close({
        configured: true,
        region: this.selectedRegion,
        modelId: this.selectedModel,
      });
    }, 1500);
  }


  /**
   * Gets the count of accessible models
   */
  get accessibleModelCount(): number {
    if (this.isValidatingModels || this.isLoadingModels) {
      return 0;
    }

    return this.models.filter(model => this.modelValidationStatus.get(model.modelId) === true)
      .length;
  }

  /**
   * Checks if the form is complete and valid
   */
  get isFormValid(): boolean {
    return (
      this.credentialsValidated &&
      !!this.selectedRegion &&
      !!this.selectedModel &&
      !this.isValidating &&
      !this.isConfiguring &&
      !this.isLoadingModels &&
      !this.isValidatingModels &&
      !this.isValidatingRegions
    );
  }

  /**
   * Gets the display value for secure inputs (shows only last 3 characters)
   */
  getDisplayValue(field: 'accessKeyId' | 'secretAccessKey'): string {
    const actualValue = field === 'accessKeyId' ? this.actualAccessKeyId : this.actualSecretAccessKey;
    
    if (!actualValue) {
      return '';
    }
    
    if (actualValue.length <= 3) {
      return '•'.repeat(actualValue.length);
    }
    
    return '•'.repeat(actualValue.length - 3) + actualValue.slice(-3);
  }

  /**
   * Handles input changes for secure fields
   */
  onInputChange(field: 'accessKeyId' | 'secretAccessKey', event: any): void {
    const value = event.target.value;
    
    if (field === 'accessKeyId') {
      this.actualAccessKeyId = value;
      this.accessKeyId = value;
    } else {
      this.actualSecretAccessKey = value;
      this.secretAccessKey = value;
    }
    
    // Reset validation state when credentials change
    if (this.credentialsValidated) {
      this.credentialsValidated = false;
      this.validatedCredentials = null;
      this.regions = [];
      this.regionOptions = [];
      this.selectedRegion = null;
    }
  }
  
  /**
   * Get button label based on current state
   */
  get buttonLabel(): string {
    if (this.isConnected) return 'Connected';
    if (this.isConfiguring) return 'Configuring...';
    if (this.isIndexing) {
      // Don't show 0% - just show "Indexing..."
      return this.indexingProgress > 0 ? `Indexing... ${this.indexingProgress}%` : 'Indexing...';
    }
    return 'Configure AWS Bedrock';
  }
  
  /**
   * Get button icon based on current state
   */
  get buttonIcon(): string {
    if (this.isConnected) return 'pi pi-check';
    if (this.isConfiguring || this.isIndexing) return 'pi pi-spinner';
    return 'pi pi-cog';
  }

  /**
   * Closes the dialog without saving
   */
  closeModal(): void {
    // Don't allow closing while indexing is in progress
    if (this.isIndexing) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Indexing in Progress',
        detail: 'Please wait for indexing to complete',
      });
      return;
    }
    
    // Clear any running interval
    if (this.statusInterval) {
      clearInterval(this.statusInterval);
    }
    
    this.ref.close();
  }
  
  /**
   * Poll indexing status continuously until completion
   */
  private pollIndexingStatus(): void {
    let pollCount = 0;
    const maxInitialPolls = 3; // Poll 3 times initially to check if indexing is needed
    
    // Use interval for continuous polling
    const statusInterval = setInterval(async () => {
      try {
        const status = await firstValueFrom(this.awsBedrockService.getIndexingStatus());
        pollCount++;
        
        // Update UI based on backend state
        if (status.indexing) {
          // Transition from configuring -> indexing
          if (this.isConfiguring) {
            this.isConfiguring = false;
          }
          // Show indexing progress
          if (!this.isIndexing) {
            this.isIndexing = true;
          }
          this.indexingProgress = Math.round(status.progress * 100);
        } else if (this.isIndexing) {
          // Was indexing, now complete
          clearInterval(statusInterval);
          await this.handleIndexingComplete(status);
        } else if (status.totalTypes > 0 && status.indexedTypes === status.totalTypes) {
          // Already indexed, no indexing needed
          clearInterval(statusInterval);
          await this.handleConfigurationSuccess();
        } else if (pollCount >= maxInitialPolls && !status.indexing) {
          // After initial polls, if not indexing and no types to index, consider it successful
          clearInterval(statusInterval);
          await this.handleConfigurationSuccess();
        }
        // Otherwise keep polling - backend might start indexing later
        
      } catch (error) {
        clearInterval(statusInterval);
        this.isConfiguring = false; // Reset on error
        await this.handleConfigurationSuccess();
      }
    }, 1000); // Poll every second
    
    // Store interval ID for cleanup if modal is closed
    this.statusInterval = statusInterval;
  }
  
  /**
   * Handle indexing completion
   */
  private async handleIndexingComplete(status: IndexingStatusResponse): Promise<void> {
    this.isIndexing = false;
    this.isConnected = true;
    
    if (status.indexedTypes > 0) {
      this.messageService.add({
        severity: 'success',
        summary: 'Indexing Complete',
        detail: `Successfully indexed ${status.indexedTypes} semantic types`,
      });
    }
    
    // Reanalyze after indexing to pull in new types
    try {
      await this.analysisService.reanalyzeAllAnalyses();
    } catch (err) {
      this.logger.warn('Failed to trigger reanalysis after indexing', err as unknown, 'AwsCredentialsModalComponent');
    }

    // Close dialog after showing message
    setTimeout(() => {
      this.ref.close({
        configured: true,
        region: this.selectedRegion,
        modelId: this.selectedModel,
      });
    }, 1500);
  }
  
}
