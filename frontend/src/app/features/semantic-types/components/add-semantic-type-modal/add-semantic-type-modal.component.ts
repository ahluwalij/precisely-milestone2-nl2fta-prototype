import { Component, EventEmitter, Output, inject, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DialogModule } from 'primeng/dialog';
import { StepsModule } from 'primeng/steps';
import { MessageModule } from 'primeng/message';
import { ButtonModule } from 'primeng/button';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';

import { SemanticTypeModalStateService } from './shared/services/semantic-type-modal-state.service';
import { NaturalLanguageStepComponent } from './steps/natural-language-step/natural-language-step.component';
import { ConfigureTypeStepComponent } from './steps/configure-type-step/configure-type-step.component';
import { AwsCredentialsModalComponent } from '../../../aws-credentials/components/aws-credentials-modal.component';
import { MenuItem } from 'primeng/api';
import {
  GeneratedSemanticType,
  AwsBedrockService,
} from '../../../../core/services/aws-bedrock.service';
import { LoggerService } from '../../../../core/services/logger.service';
import { CorrelationService } from '../../../../core/services/correlation.service';
import { Example } from './shared/interfaces/semantic-type-modal.interfaces';

/**
 * Modal component for adding/updating semantic types.
 *
 * Always creates a new semantic type via AI generation.
 * If a type with the same name exists (custom or built-in), it will be replaced.
 */
@Component({
  selector: 'app-add-semantic-type-modal',
  standalone: true,
  imports: [
    CommonModule,
    DialogModule,
    StepsModule,
    MessageModule,
    ButtonModule,
    NaturalLanguageStepComponent,
    ConfigureTypeStepComponent,
  ],
  templateUrl: './add-semantic-type-modal.component.html',
  styleUrl: './add-semantic-type-modal.component.css',
  providers: [SemanticTypeModalStateService, DialogService],
})
export class AddSemanticTypeModalComponent implements OnInit {
  @Output() modalClose = new EventEmitter<void>();
  @Output() typeSaved = new EventEmitter<void>();

  private stateService = inject(SemanticTypeModalStateService);
  private awsBedrockService = inject(AwsBedrockService);
  private dialogService = inject(DialogService);
  private logger = inject(LoggerService);
  private correlation = inject(CorrelationService);

  visible = true;
  awsConfigured = false;
  private dialogRef: DynamicDialogRef | undefined;

  // Similar type detection state
  showSimilarTypeAlert = false;
  similarTypeResult: GeneratedSemanticType | null = null;
  private isProcessingSimilarType = false;

  // Expose state service properties to template
  activeStepIndex = this.stateService.activeStepIndex;
  isConfigureStepDisabled = this.stateService.isConfigureStepDisabled;
  isGenerating = this.stateService.isGenerating;
  canGenerate = this.stateService.canGenerate;

  stepItems: MenuItem[] = [{ label: 'Natural Language' }, { label: 'Configure Type' }];

  dialogTitle = computed(() => {
    const stepIndex = this.stateService.activeStepIndex();
    if (stepIndex === 0) {
      return 'Natural Language';
    } else if (stepIndex === 1) {
      return 'Configure Semantic Type';
    }
    return 'Add Semantic Type';
  });

  ngOnInit() {
    this.checkAwsStatus();
  }

  private async checkAwsStatus() {
    try {
      const status = await this.awsBedrockService.getAwsStatus().toPromise();
      this.awsConfigured = status?.configured || false;
    } catch {
      this.awsConfigured = false;
    }
  }

  openAwsConfigModal(): void {
    // Close current modal first
    this.visible = false;
    this.stateService.reset();
    this.modalClose.emit();

    // Open AWS credentials modal
    this.dialogRef = this.dialogService.open(AwsCredentialsModalComponent, {
      header: 'Configure AWS Bedrock',
      width: '70%',
      style: { 'max-width': '800px' },
      contentStyle: { overflow: 'auto' },
      baseZIndex: 10000,
      modal: true,
      dismissableMask: true,
      closeOnEscape: true,
      closable: true,
    });

    this.dialogRef.onClose.subscribe(
      async (result: { configured?: boolean; region?: string; modelId?: string } | null) => {
        if (result?.configured) {
          this.awsConfigured = true;
        }
      }
    );
  }

  async onGenerateRequested(skipExistingCheck: boolean = false): Promise<void> {
    // Prevent generation if we're already processing a similar type alert
    if (this.isProcessingSimilarType) {
      return;
    }

    try {
      this.stateService.setIsGenerating(true);
      this.stateService.setGenerationError('');

      // Collect natural language input data
      const description = this.stateService.nlDescription().trim();
      const positiveContentExamples = this.stateService
        .nlPositiveContentExamples()
        .map((e: Example) => e.value.trim())
        .filter((v: string) => v.length > 0);
      const negativeContentExamples = this.stateService
        .nlNegativeContentExamples()
        .map((e: Example) => e.value.trim())
        .filter((v: string) => v.length > 0);
      const positiveHeaderExamples = this.stateService
        .nlPositiveHeaderExamples()
        .map((e: Example) => e.value.trim())
        .filter((v: string) => v.length > 0);
      const negativeHeaderExamples = this.stateService
        .nlNegativeHeaderExamples()
        .map((e: Example) => e.value.trim())
        .filter((v: string) => v.length > 0);

      // Call the AWS Bedrock service to generate semantic type
      const result: GeneratedSemanticType | undefined = await this.awsBedrockService
        .generateSemanticType({
          description,
          positiveContentExamples,
          negativeContentExamples,
          positiveHeaderExamples,
          negativeHeaderExamples,
          checkExistingTypes: !skipExistingCheck,
          proceedDespiteSimilarity: skipExistingCheck,
        })
        .toPromise();

      if (result) {
        // Persist correlationId for this flow to propagate to subsequent save/feedback
        if (result.correlationId) {
          this.correlation.startNewFlow(result.correlationId);
        }
        this.handleGenerationResult(result);
      } else {
        this.logger.error(
          'No result received from semantic type generation',
          null,
          'AddSemanticTypeModalComponent'
        );
        this.stateService.setGenerationError(
          'No data received from the generation service. Please try again.'
        );
      }
    } catch (error: unknown) {
      this.logger.error('Error generating semantic type', error, 'AddSemanticTypeModalComponent');

      let errorMessage = 'Failed to generate semantic type. Please try again.';
      if (typeof error === 'object' && error !== null) {
        const errorObj = error as Record<string, unknown>;
        if (typeof errorObj['error'] === 'object' && errorObj['error'] !== null) {
          const nestedError = errorObj['error'] as Record<string, unknown>;
          if (typeof nestedError['message'] === 'string') {
            errorMessage = `Generation failed: ${nestedError['message']}`;
          }
        } else if (typeof errorObj['message'] === 'string') {
          errorMessage = `Generation failed: ${errorObj['message']}`;
        }
      }

      this.stateService.setGenerationError(errorMessage);
    } finally {
      this.stateService.setIsGenerating(false);
      this.isProcessingSimilarType = false;
    }
  }

  private handleGenerationResult(result: GeneratedSemanticType): void {
    if (result.resultType === 'existing') {
      this.handleExistingTypeResult(result);
    } else {
      // Generated or error result - proceed normally
      this.stateService.populateConfigFields(result);
      this.stateService.setActiveStepIndex(1);
    }
  }

  onStepChange(stepIndex: number): void {
    // Allow backward navigation (from step 1 to step 0) at any time
    if (stepIndex < this.stateService.activeStepIndex()) {
      this.stateService.setActiveStepIndex(stepIndex);
      return;
    }

    // Enforce that Configure Type step can only be accessed after generation
    if (stepIndex === 1 && !this.stateService.wasGenerated()) {
      // Don't allow forward navigation to step 1 if no generation has happened
      return;
    }

    this.stateService.setActiveStepIndex(stepIndex);

    // Initialize default examples for Configure Type step if empty
    if (stepIndex === 1) {
      if (this.stateService.configPositiveContentExamples().length === 0) {
        this.stateService.setConfigPositiveContentExamples([
          { id: '1c', value: '', removable: true },
        ]);
      }
      if (this.stateService.configNegativeContentExamples().length === 0) {
        this.stateService.setConfigNegativeContentExamples([
          { id: '1cn', value: '', removable: true },
        ]);
      }
      if (this.stateService.configPositiveHeaderExamples().length === 0) {
        this.stateService.setConfigPositiveHeaderExamples([
          { id: '1ch', value: '', removable: true },
        ]);
      }
      if (this.stateService.configNegativeHeaderExamples().length === 0) {
        this.stateService.setConfigNegativeHeaderExamples([
          { id: '1cnh', value: '', removable: true },
        ]);
      }
    }
  }

  closeModal(): void {
    this.visible = false;
    this.stateService.reset();
    this.modalClose.emit();
  }

  onTypeSaved(): void {
    this.visible = false;
    this.stateService.reset();
    this.typeSaved.emit();
  }

  onGoBack(): void {
    this.stateService.setActiveStepIndex(0);
  }

  // Similar type alert methods (if needed)
  showSimilarTypeAlertValue(): boolean {
    return this.showSimilarTypeAlert;
  }

  similarTypeResultValue(): GeneratedSemanticType | null {
    return this.similarTypeResult;
  }

  dismissSimilarTypeAlert(): void {
    this.showSimilarTypeAlert = false;
    this.similarTypeResult = null;
    this.isProcessingSimilarType = false;
  }

  createDifferentType(): void {
    this.showSimilarTypeAlert = false;
    this.similarTypeResult = null;
    this.isProcessingSimilarType = false;

    // Reset generation state to allow user to modify and try again
    this.stateService.setWasGenerated(false);
    this.stateService.setOriginalGeneratedData(null);
    this.stateService.setGenerationError('');
    this.stateService.setActiveStepIndex(0);

    // Clear any config fields that might have been populated
    this.stateService.setConfigTypeName('');
    this.stateService.setConfigDescription('');
    this.stateService.setRegexPattern('');
    this.stateService.setHeaderPatterns('');
    this.stateService.setConfigPositiveContentExamples([]);
    this.stateService.setConfigNegativeContentExamples([]);
    this.stateService.setConfigPositiveHeaderExamples([]);
    this.stateService.setConfigNegativeHeaderExamples([]);

    // Immediately trigger generation with existing natural language input, skipping existing type check
    this.onGenerateRequested(true);
  }

  private handleExistingTypeResult(result: GeneratedSemanticType): void {
    if (result.existingTypeMatch) {
      this.isProcessingSimilarType = true;
      this.similarTypeResult = result;
      this.showSimilarTypeAlert = true;
    } else {
      // No similar type found, proceed normally
      this.stateService.populateConfigFields(result);
      this.stateService.setActiveStepIndex(1);
    }
  }

  handleSimilarityAlert(result: GeneratedSemanticType): void {
    // Reuse the existing similarity alert handling logic
    this.handleExistingTypeResult(result);
  }
}
