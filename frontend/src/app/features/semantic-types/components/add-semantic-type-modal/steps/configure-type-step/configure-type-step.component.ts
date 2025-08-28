import { Component, inject, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DropdownModule } from 'primeng/dropdown';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { TooltipModule } from 'primeng/tooltip';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { DialogService } from 'primeng/dynamicdialog';

import { SemanticTypeModalStateService } from '../../shared/services/semantic-type-modal-state.service';
import {
  Example,
  ExampleListType,
  ExcludeExampleListType,
  IncludeExampleListType,
  DropdownOption,
} from '../../shared/interfaces/semantic-type-modal.interfaces';
import {
  AwsBedrockService,
  GeneratedSemanticType,
} from '../../../../../../core/services/aws-bedrock.service';
import {
  FtaClassifierService,
  CustomSemanticType,
} from '../../../../../../core/services/fta-classifier.service';
import { LoggerService } from '../../../../../../core/services/logger.service';
import { CorrelationService } from '../../../../../../core/services/correlation.service';
import { FeedbackService } from '../../../../../../core/services/feedback.service';

@Component({
  selector: 'app-configure-type-step',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    InputTextModule,
    TextareaModule,
    DropdownModule,
    ButtonModule,
    MessageModule,
    TooltipModule,
    ToastModule,
  ],
  templateUrl: './configure-type-step.component.html',
  styleUrl: './configure-type-step.component.css',
  providers: [DialogService, MessageService],
})
export class ConfigureTypeStepComponent {
  @Output() typeSaved = new EventEmitter<void>();
  @Output() showSimilarityAlert = new EventEmitter<GeneratedSemanticType>();
  @Output() goBack = new EventEmitter<void>();

  stateService = inject(SemanticTypeModalStateService);
  private awsBedrockService = inject(AwsBedrockService);
  private ftaClassifierService = inject(FtaClassifierService);
  private logger = inject(LoggerService);
  private correlation = inject(CorrelationService);
  private dialogService = inject(DialogService);
  private feedbackService = inject(FeedbackService);
  private messageService = inject(MessageService);

  // Example pools for dynamic placeholders
  private readonly positiveContentExamples = [
    'Sophia',
    'John',
    'Maria',
    'David',
    'Emma',
    'Michael',
    'Sarah',
    'James',
  ];

  private readonly negativeContentExamples = [
    '123',
    'abc@email.com',
    '2023-01-01',
    'null',
    'N/A',
    '999-999-9999',
    'undefined',
  ];

  private readonly positiveHeaderExamples = [
    'first_name',
    'given_name',
    'fname',
    'name',
    'firstname',
    'customer_name',
  ];

  private readonly negativeHeaderExamples = [
    'last_name',
    'email',
    'age',
    'id',
    'phone',
    'address',
    'date_created',
  ];

  // Expose state service properties to template
  configTypeName = this.stateService.configTypeName;
  configDescription = this.stateService.configDescription;
  pluginType = this.stateService.pluginType;
  regexPattern = this.stateService.regexPattern;
  listValues = this.stateService.listValues;
  confidenceThreshold = this.stateService.confidenceThreshold;
  headerPatterns = this.stateService.headerPatterns;
  structuredHeaderPatterns = this.stateService.structuredHeaderPatterns;

  configPositiveContentExamples = this.stateService.configPositiveContentExamples;
  configNegativeContentExamples = this.stateService.configNegativeContentExamples;
  configPositiveHeaderExamples = this.stateService.configPositiveHeaderExamples;
  configNegativeHeaderExamples = this.stateService.configNegativeHeaderExamples;

  saveError = this.stateService.saveError;
  isSaving = this.stateService.isSaving;
  isGenerating = this.stateService.isGenerating;
  canSave = this.stateService.canSave;
  generationError = this.stateService.generationError;
  dataValuesGenerationError = this.stateService.dataValuesGenerationError;
  headerPatternsGenerationError = this.stateService.headerPatternsGenerationError;

  showRestoreButton = this.stateService.showRestoreButton;
  saveDisabledTooltip = this.stateService.saveDisabledTooltip;

  dataValuesMoreExamplesInput = this.stateService.dataValuesMoreExamplesInput;
  headerPatternsMoreExamplesInput = this.stateService.headerPatternsMoreExamplesInput;
  isGeneratingMoreDataExamples = this.stateService.isGeneratingMoreDataExamples;
  isGeneratingMoreHeaderExamples = this.stateService.isGeneratingMoreHeaderExamples;

  configFirstPositiveContentNeedsGlow = this.stateService.configFirstPositiveContentNeedsGlow;
  configFirstPositiveContentFocused = this.stateService.configFirstPositiveContentFocused;
  
  showDataValuesRegenerateButton = this.stateService.showDataValuesRegenerateButton;
  showHeaderPatternsRegenerateButton = this.stateService.showHeaderPatternsRegenerateButton;

  pluginTypeOptions: DropdownOption[] = [
    { label: 'Regular Expression', value: 'regex' },
    { label: 'List of Values', value: 'list' },
  ];

  /**
   * Returns a dynamic placeholder for an example field based on its index and type
   */
  getExamplePlaceholder(
    index: number,
    exampleType: 'positiveContent' | 'negativeContent' | 'positiveHeader' | 'negativeHeader'
  ): string {
    let examples: string[];

    switch (exampleType) {
      case 'positiveContent':
        examples = this.positiveContentExamples;
        break;
      case 'negativeContent':
        examples = this.negativeContentExamples;
        break;
      case 'positiveHeader':
        examples = this.positiveHeaderExamples;
        break;
      case 'negativeHeader':
        examples = this.negativeHeaderExamples;
        break;
      default:
        examples = this.positiveContentExamples;
    }

    const exampleValue = examples[index % examples.length];
    return `e.g. ${exampleValue}`;
  }

  onConfigTypeNameChange(value: string): void {
    this.stateService.setConfigTypeName(value);
  }

  onConfigDescriptionChange(value: string): void {
    this.stateService.setConfigDescription(value);
  }

  onPluginTypeChange(value: 'regex' | 'list'): void {
    this.stateService.setPluginType(value);

    // For demo purposes: Auto-populate list values when switching to "List of Values"
    if (value === 'list') {
      const currentDescription = this.stateService.configDescription().toLowerCase();
      const currentTypeName = this.stateService.configTypeName().toLowerCase();

      // Check if this is a First Name related type for demo
      const isFirstNameType =
        currentDescription.includes('first name') ||
        currentDescription.includes('firstname') ||
        currentTypeName.includes('first name') ||
        currentTypeName.includes('firstname');

      if (isFirstNameType) {
        // Pre-populate with First Name values for demo
        const firstNameValues = [
          'John',
          'Jane',
          'Michael',
          'Sarah',
          'David',
          'Emily',
          'Robert',
          'Jessica',
          'William',
          'Ashley',
          'Christopher',
          'Amanda',
          'Matthew',
          'Melissa',
          'Anthony',
          'Nicole',
        ];

        const listExamples = firstNameValues.map((value, index) => ({
          id: index === 0 ? '1' : Date.now().toString() + '_' + index,
          value: value,
          removable: true,
        }));

        this.stateService.setListValues(listExamples);
      } else {
        // For other types, provide generic list values
        const genericValues = ['Value1', 'Value2', 'Value3', 'Value4', 'Value5'];
        const listExamples = genericValues.map((value, index) => ({
          id: index === 0 ? '1' : Date.now().toString() + '_' + index,
          value: value,
          removable: true,
        }));

        this.stateService.setListValues(listExamples);
      }
    }
  }

  onRegexPatternChange(value: string): void {
    this.stateService.setRegexPattern(value);
  }

  onHeaderPatternsChange(value: string): void {
    this.stateService.setHeaderPatterns(value);
  }

  onConfidenceThresholdChange(value: string | number): void {
    this.stateService.setConfidenceThreshold(typeof value === 'number' ? value.toString() : value);
  }

  addExample(listType: ExampleListType): void {
    this.stateService.addExample(listType);
  }

  removeExample(id: string, listType: ExampleListType): void {
    this.stateService.removeExample(id, listType);
  }

  updateExample(id: string, value: string, listType: ExampleListType): void {
    this.stateService.updateExample(id, value, listType);
  }

  excludeExample(id: string, listType: ExcludeExampleListType): void {
    this.stateService.toggleExampleExclusion(id, listType as ExampleListType);
  }

  includeExample(id: string, listType: IncludeExampleListType): void {
    this.stateService.toggleExampleExclusion(id, listType as ExampleListType);
  }

  trackByExampleId(index: number, example: Example): string {
    return example.id;
  }

  onConfigFirstPositiveContentFocus(): void {
    this.stateService.setConfigFirstPositiveContentFocused(true);
  }

  onConfigFirstPositiveContentBlur(): void {
    this.stateService.setConfigFirstPositiveContentFocused(false);
  }

  handleListValuesPaste(event: ClipboardEvent): void {
    event.preventDefault();
    const pastedText = event.clipboardData?.getData('text');
    if (!pastedText) return;

    try {
      // Try to parse as JSON array
      const parsedArray = JSON.parse(pastedText);
      if (Array.isArray(parsedArray)) {
        const examples: Example[] = parsedArray.map((value: unknown, index: number) => ({
          id: index === 0 ? '1' : Date.now().toString() + index,
          value: String(value),
          removable: true,
        }));
        this.stateService.setListValues(examples);
      }
    } catch {
      // If parsing fails, treat as comma-separated values
      const values = pastedText
        .split(',')
        .map((v: string) => v.trim())
        .filter((v: string) => v);
      const examples: Example[] = values.map((value: string, index: number) => ({
        id: index === 0 ? '1' : Date.now().toString() + index,
        value: value,
        removable: index > 0,
      }));
      this.stateService.setListValues(examples);
    }
  }

  async generateMoreDataValueExamples(): Promise<void> {
    const userInput = this.dataValuesMoreExamplesInput().trim();
    const hasExampleChanges = this.stateService.dataExamplesChanged();
    
    // If no user input and no example changes, nothing to do
    if (!userInput && !hasExampleChanges) {
      return;
    }
    
    // Build description based on what changed
    let description = userInput;
    if (!userInput && hasExampleChanges) {
      // Auto-generate description based on example changes
      const currentPositive = this.configPositiveContentExamples().filter(e => e.value.trim());
      const currentNegative = this.configNegativeContentExamples().filter(e => e.value.trim());
      
      // Build a detailed description of the example changes
      const positiveExamplesList = currentPositive.map(e => e.value).join(', ');
      const negativeExamplesList = currentNegative.map(e => e.value).join(', ');
      
      description = `IMPORTANT: User has reorganized examples by including/excluding. DO NOT generate new examples. ONLY update the regex pattern.\n` +
                   `POSITIVE examples (MUST match ALL of these): ${positiveExamplesList}\n` +
                   `NEGATIVE examples (MUST NOT match ANY of these): ${negativeExamplesList}\n` +
                   `Update ONLY the regex pattern to match all positive examples and exclude all negative examples. Do not suggest or generate any new examples.`;
    }

    try {
      this.stateService.setIsGeneratingMoreDataExamples(true);
      this.stateService.setDataValuesGenerationError('');

      // Check AWS configuration
      const awsStatus = await this.awsBedrockService.checkAwsStatus().toPromise();
      if (!awsStatus?.configured) {
        this.stateService.setDataValuesGenerationError(
          'AWS Bedrock is not configured. Please configure your AWS credentials first.'
        );
        return;
      }

      // Get current state
      const currentRegexPattern = this.regexPattern();
      const currentSemanticTypeName = this.configTypeName() || 'CUSTOM.GENERATED';
      const currentDescription = this.configDescription() || '';
      const currentPluginType = this.pluginType();
      const currentPositiveExamples = this.configPositiveContentExamples()
        .map(ex => ex.value)
        .filter(v => v.trim().length > 0);
      const currentNegativeExamples = this.configNegativeContentExamples()
        .map(ex => ex.value)
        .filter(v => v.trim().length > 0);

      if (!currentRegexPattern) {
        this.stateService.setDataValuesGenerationError(
          'No regex pattern available for generating examples. Please ensure the pattern is configured.'
        );
        return;
      }

      // Call the backend with special request type for content pattern improvement
      const response = await this.awsBedrockService
        .generateValidatedExamples({
          regexPattern: currentRegexPattern,
          semanticTypeName: currentSemanticTypeName,
          description: currentDescription,
          pluginType: currentPluginType,
          existingPositiveExamples: currentPositiveExamples,
          existingNegativeExamples: currentNegativeExamples,
          userDescription: description,
          isPatternImprovement: true,
          maxRetries: 3,
        })
        .toPromise();

      if (!response || response.error) {
        this.stateService.setDataValuesGenerationError(
          `Failed to generate data value examples: ${response?.error || 'Unknown error occurred.'}`
        );
        return;
      }

      // Update the regex pattern if a new one was generated
      if (response.updatedRegexPattern && this.pluginType() === 'regex') {
        this.stateService.setRegexPattern(response.updatedRegexPattern);
        
        // Re-validate existing examples against the new pattern
        this.revalidateExamples(response.updatedRegexPattern);
      }

      // When user has reorganized examples, don't replace them with new ones
      // Only update the pattern and keep the user's examples
      if (!hasExampleChanges || userInput) {
        // Only replace examples if user hasn't reorganized them or has provided additional input
        if (response.positiveExamples && response.positiveExamples.length > 0) {
          const newPositives = response.positiveExamples
            .map((value, index) => ({
              id: index === 0 ? '1' : Date.now().toString() + '_pos_' + index,
              value: value,
              removable: true,
            }));

          this.stateService.setConfigPositiveContentExamples(newPositives);
        }

        if (response.negativeExamples && response.negativeExamples.length > 0) {
          const newNegatives = response.negativeExamples
            .map((value, index) => ({
              id: Date.now().toString() + '_neg_' + index,
              value: value,
              removable: true,
            }));

          this.stateService.setConfigNegativeContentExamples(newNegatives);
        }
      }

      this.stateService.setDataValuesMoreExamplesInput('');
      
      // Clear the example changed flag after successful regeneration
      this.stateService.setDataExamplesChanged(false);
    } catch (error: unknown) {
      this.logger.error(
        'Error improving pattern and generating examples',
        error,
        'ConfigureTypeStepComponent'
      );

      let errorMessage = 'Failed to generate data value examples. Please try again.';
      if (typeof error === 'object' && error !== null) {
        const errorObj = error as Record<string, unknown>;
        if (typeof errorObj['error'] === 'object' && errorObj['error'] !== null) {
          const nestedError = errorObj['error'] as Record<string, unknown>;
          if (typeof nestedError['message'] === 'string') {
            errorMessage = `Data value examples generation failed: ${nestedError['message']}`;
          }
        } else if (typeof errorObj['message'] === 'string') {
          errorMessage = `Data value examples generation failed: ${errorObj['message']}`;
        }
      }

      this.stateService.setDataValuesGenerationError(errorMessage);
    } finally {
      this.stateService.setIsGeneratingMoreDataExamples(false);
    }
  }

  async generateMoreHeaderPatternExamples(): Promise<void> {
    const userInput = this.headerPatternsMoreExamplesInput().trim();
    const hasExampleChanges = this.stateService.headerExamplesChanged();
    
    // If no user input and no example changes, nothing to do
    if (!userInput && !hasExampleChanges) {
      return;
    }
    
    // Build description based on what changed
    let description = userInput;
    if (!userInput && hasExampleChanges) {
      // Auto-generate description based on example changes
      const currentPositive = this.configPositiveHeaderExamples().filter(e => e.value.trim());
      const currentNegative = this.configNegativeHeaderExamples().filter(e => e.value.trim());
      
      // Build a detailed description of the example changes
      const positiveExamplesList = currentPositive.map(e => e.value).join(', ');
      const negativeExamplesList = currentNegative.map(e => e.value).join(', ');
      
      description = `IMPORTANT: User has reorganized header examples by including/excluding. DO NOT generate new examples. ONLY update the header patterns.\n` +
                   `POSITIVE headers (MUST match ALL of these): ${positiveExamplesList}\n` +
                   `NEGATIVE headers (MUST NOT match ANY of these): ${negativeExamplesList}\n` +
                   `Update ONLY the header patterns to match all positive examples and exclude all negative examples. Do not suggest or generate any new examples.`;
    }

    try {
      this.stateService.setIsGeneratingMoreHeaderExamples(true);
      this.stateService.setHeaderPatternsGenerationError('');

      // Check AWS configuration
      const awsStatus = await this.awsBedrockService.checkAwsStatus().toPromise();
      if (!awsStatus?.configured) {
        this.stateService.setHeaderPatternsGenerationError(
          'AWS Bedrock is not configured. Please configure your AWS credentials first.'
        );
        return;
      }

      // Get current state
      const currentHeaderPatterns = this.headerPatterns();
      const currentSemanticTypeName = this.configTypeName() || 'CUSTOM.GENERATED';
      const currentDescription = this.configDescription() || '';
      const currentPositiveHeaderExamples = this.configPositiveHeaderExamples()
        .map(ex => ex.value)
        .filter(v => v.trim().length > 0);
      const currentNegativeHeaderExamples = this.configNegativeHeaderExamples()
        .map(ex => ex.value)
        .filter(v => v.trim().length > 0);

      if (!currentHeaderPatterns) {
        this.stateService.setHeaderPatternsGenerationError(
          'No header patterns available for generating examples. Please ensure the header patterns are configured.'
        );
        return;
      }

      // Call the backend with special request type for header pattern improvement
      const response = await this.awsBedrockService
        .generateValidatedExamples({
          regexPattern: currentHeaderPatterns,
          semanticTypeName: currentSemanticTypeName,
          description: currentDescription,
          existingPositiveExamples: currentPositiveHeaderExamples,
          existingNegativeExamples: currentNegativeHeaderExamples,
          userDescription: description + (userInput ? ' (for column headers)' : ''),
          isHeaderPatternImprovement: true,
          maxRetries: 3,
        })
        .toPromise();

      if (!response || response.error) {
        this.stateService.setHeaderPatternsGenerationError(
          `Failed to generate header pattern examples: ${response?.error || 'Unknown error occurred.'}`
        );
        return;
      }

      // Debug logging
      this.logger.info('Header pattern improvement response:', response, 'ConfigureTypeStepComponent');
      this.logger.info('Response positive examples:', response.positiveExamples, 'ConfigureTypeStepComponent');
      this.logger.info('Response negative examples:', response.negativeExamples, 'ConfigureTypeStepComponent');

      // Update the header patterns if new ones were generated
      if (response.updatedHeaderPatterns && response.updatedHeaderPatterns.length > 0) {
        const newPatternsString = response.updatedHeaderPatterns.join(', ');
        this.stateService.setHeaderPatterns(newPatternsString);
        this.logger.info('Updated header patterns to:', newPatternsString, 'ConfigureTypeStepComponent');
        
        // Re-validate existing header examples against the new patterns
        // Store original examples in case validation fails
        const originalPositives = [...this.configPositiveHeaderExamples()];
        this.revalidateHeaderExamples(response.updatedHeaderPatterns);
        
        // Check if we lost too many positive examples
        const remainingPositives = this.configPositiveHeaderExamples();
        const nonEmptyOriginal = originalPositives.filter(e => e.value.trim());
        const nonEmptyRemaining = remainingPositives.filter(e => e.value.trim());
        
        if (nonEmptyOriginal.length > 0 && nonEmptyRemaining.length === 0) {
          this.logger.error('All positive header examples were removed during validation!', null, 'ConfigureTypeStepComponent');
          this.logger.error('Original examples:', nonEmptyOriginal.map(e => e.value), 'ConfigureTypeStepComponent');
          this.logger.error('Generated patterns:', response.updatedHeaderPatterns, 'ConfigureTypeStepComponent');
          
          // Restore original examples and show error
          this.stateService.setConfigPositiveHeaderExamples(originalPositives);
          this.stateService.setHeaderPatternsGenerationError(
            'Generated header patterns do not match your positive examples. Please try with different examples or feedback.'
          );
          return;
        }
      }

      // When user has reorganized examples, don't replace them with new ones
      // Only update the pattern and keep the user's examples
      if (!hasExampleChanges || userInput) {
        // Only replace examples if user hasn't reorganized them or has provided additional input
        if (response.positiveExamples && response.positiveExamples.length > 0) {
          const newPositives = response.positiveExamples
            .map((value, index) => ({
              id: index === 0 ? '1' : Date.now().toString() + '_hpos_' + index,
              value: value,
              removable: true,
            }));

          this.stateService.setConfigPositiveHeaderExamples(newPositives);
          this.logger.info('Set positive header examples:', newPositives, 'ConfigureTypeStepComponent');
        } else {
          this.logger.warn('No positive examples in response', null, 'ConfigureTypeStepComponent');
        }

        if (response.negativeExamples && response.negativeExamples.length > 0) {
          const newNegatives = response.negativeExamples
            .map((value, index) => ({
              id: Date.now().toString() + '_hneg_' + index,
              value: value,
              removable: true,
            }));

          this.stateService.setConfigNegativeHeaderExamples(newNegatives);
          this.logger.info('Set negative header examples:', newNegatives, 'ConfigureTypeStepComponent');
        } else {
          this.logger.warn('No negative examples in response', null, 'ConfigureTypeStepComponent');
        }
      }

      this.stateService.setHeaderPatternsMoreExamplesInput('');
      
      // Clear the example changed flag after successful regeneration
      this.stateService.setHeaderExamplesChanged(false);
    } catch (error: unknown) {
      this.logger.error(
        'Error improving header patterns and generating examples',
        error,
        'ConfigureTypeStepComponent'
      );

      let errorMessage = 'Failed to generate header pattern examples. Please try again.';
      if (typeof error === 'object' && error !== null) {
        const errorObj = error as Record<string, unknown>;
        if (typeof errorObj['error'] === 'object' && errorObj['error'] !== null) {
          const nestedError = errorObj['error'] as Record<string, unknown>;
          if (typeof nestedError['message'] === 'string') {
            errorMessage = `Header pattern examples generation failed: ${nestedError['message']}`;
          }
        } else if (typeof errorObj['message'] === 'string') {
          errorMessage = `Header pattern examples generation failed: ${errorObj['message']}`;
        }
      }

      this.stateService.setHeaderPatternsGenerationError(errorMessage);
    } finally {
      this.stateService.setIsGeneratingMoreHeaderExamples(false);
    }
  }



  async saveSemanticType(): Promise<void> {
    try {
      this.stateService.setIsSaving(true);
      this.stateService.setSaveError('');

      // Build the semantic type object
      const semanticType: CustomSemanticType = {
        semanticType: this.stateService.configTypeName().trim(),
        description: this.stateService.configDescription().trim(),
        pluginType: this.stateService.pluginType(),
        baseType: 'string', // Set base type for semantic types
        content:
          this.stateService.pluginType() === 'regex'
            ? undefined
            : {
                type: 'list',
                values: this.stateService
                  .listValues()
                  .map((v: Example) => v.value.trim())
                  .filter((v: string) => v),
              },
        threshold: parseFloat(this.stateService.confidenceThreshold()),
        priority: this.stateService.priority(),
        validLocales: [
          {
            localeTag: 'en-US',
            headerRegExps: this.parseHeaderPatterns(this.stateService.headerPatterns()).map(
              (pattern: string, index: number, array: string[]) => ({
                regExp: pattern,
                confidence: 95,
                mandatory: array.length === 1 ? true : index === 0,
              })
            ),
            matchEntries:
              this.stateService.pluginType() === 'regex'
                ? [
                    {
                      regExpReturned: this.stateService.regexPattern().trim(),
                      isRegExpComplete: true,
                    },
                  ]
                : undefined,
          },
        ],
      };

      // Log the raw semantic type object being sent
      this.logger.info('Saving semantic type with raw object:', { ...semanticType, correlationId: this.correlation.getCurrentId() }, 'ConfigureTypeStepComponent');

      // Check if this is an update or new creation
      const typeName = semanticType.semanticType;
      let result: CustomSemanticType | undefined;

      try {
        // First, try to get the existing type to see if it exists
        const existingTypes = await this.ftaClassifierService
          .getCustomSemanticTypesOnly()
          .toPromise();
        const existingType = existingTypes?.find(t => t.semanticType === typeName);

        if (existingType) {
          // This is an update - use PUT endpoint

          result = await this.ftaClassifierService
            .updateCustomSemanticType(typeName, semanticType)
            .toPromise();
        } else {
          // This is a new type - use POST endpoint

          result = await this.ftaClassifierService.addCustomSemanticType(semanticType).toPromise();
        }
      } catch (error: unknown) {
        // If we get a "not found" error on update attempt, fall back to create
        if (
          typeof error === 'object' &&
          error !== null &&
          'status' in error &&
          (error as { status: number }).status === 404
        ) {
          result = await this.ftaClassifierService.addCustomSemanticType(semanticType).toPromise();
        } else {
          throw error; // Re-throw other errors
        }
      }

      if (result) {
        // Log the saved result
        this.logger.info('Successfully saved semantic type. Response:', { result, correlationId: this.correlation.getCurrentId() }, 'ConfigureTypeStepComponent');
        
        // Emit the success event
        this.typeSaved.emit();
      }
    } catch (error: unknown) {
      this.logger.error('Error saving semantic type', error, 'ConfigureTypeStepComponent');

      let errorMessage = 'Failed to save semantic type. Please try again.';
      if (typeof error === 'object' && error !== null) {
        const errorObj = error as Record<string, unknown>;
        if (typeof errorObj['error'] === 'object' && errorObj['error'] !== null) {
          const nestedError = errorObj['error'] as Record<string, unknown>;
          if (typeof nestedError['message'] === 'string') {
            errorMessage = this.processErrorMessage(nestedError['message']);
          }
        } else if (typeof errorObj['message'] === 'string') {
          errorMessage = this.processErrorMessage(errorObj['message']);
        }
      }

      this.stateService.setSaveError(errorMessage);
    } finally {
      this.stateService.setIsSaving(false);
    }
  }

  saveButtonLabel(): string {
    // This is a simplified check - ideally we'd have a more robust way to detect edit mode
    // For now, always show "Add Type" since the logic will handle updates automatically
    return 'Add Type';
  }

  goBackToFirstStep(): void {
    this.goBack.emit();
  }

  restoreDefaults(): void {
    this.stateService.restoreDefaults();
  }

  /**
   * Validates an example against a regex pattern
   */
  private validateAgainstRegex(example: string, regexPattern: string): boolean {
    try {
      // Convert Java-style (?i) inline case-insensitive flag to JavaScript 'i' flag
      let jsPattern = regexPattern;
      let flags = '';
      
      if (regexPattern.startsWith('(?i)')) {
        jsPattern = regexPattern.substring(4); // Remove (?i)
        flags = 'i'; // Add case-insensitive flag
      }
      
      const regex = new RegExp(jsPattern, flags);
      return regex.test(example);
    } catch (error) {
      this.logger.error('Invalid regex pattern:', error, 'ConfigureTypeStepComponent');
      return false;
    }
  }


  /**
   * Re-validates existing examples against a new pattern and removes invalid ones
   */
  private revalidateExamples(regexPattern: string): void {
    // Re-validate positive examples - they should match the pattern
    const validatedPositives = this.configPositiveContentExamples()
      .filter(example => {
        const isValid = this.validateAgainstRegex(example.value, regexPattern);
        if (!isValid && example.value.trim()) {
          this.logger.info(`Removing invalid positive example: ${example.value}`, null, 'ConfigureTypeStepComponent');
        }
        return isValid || !example.value.trim(); // Keep empty examples
      });

    // Re-validate negative examples - they should NOT match the pattern
    const validatedNegatives = this.configNegativeContentExamples()
      .filter(example => {
        const matchesPattern = this.validateAgainstRegex(example.value, regexPattern);
        const isValid = !matchesPattern; // Negative examples should NOT match
        if (!isValid && example.value.trim()) {
          this.logger.info(`Removing invalid negative example: ${example.value} (matches pattern)`, null, 'ConfigureTypeStepComponent');
        }
        return isValid || !example.value.trim(); // Keep empty examples
      });

    // Update the examples if any were removed
    if (validatedPositives.length !== this.configPositiveContentExamples().length) {
      this.stateService.setConfigPositiveContentExamples(validatedPositives);
    }
    if (validatedNegatives.length !== this.configNegativeContentExamples().length) {
      this.stateService.setConfigNegativeContentExamples(validatedNegatives);
    }
  }

  /**
   * Re-validates header examples against new header patterns
   */
  private revalidateHeaderExamples(headerPatterns: string[]): void {
    this.logger.info('Revalidating header examples against patterns:', headerPatterns, 'ConfigureTypeStepComponent');
    
    // Re-validate positive header examples - they should match at least one pattern
    const validatedPositives = this.configPositiveHeaderExamples()
      .filter(example => {
        if (!example.value.trim()) {
          return true; // Keep empty examples
        }
        
        const matchesAnyPattern = headerPatterns.some(pattern => {
          const matches = this.validateAgainstRegex(example.value, pattern);
          this.logger.debug(`Testing '${example.value}' against pattern '${pattern}': ${matches}`, null, 'ConfigureTypeStepComponent');
          return matches;
        });
        
        if (!matchesAnyPattern) {
          this.logger.warn(`REMOVING positive header example '${example.value}' - doesn't match any pattern`, null, 'ConfigureTypeStepComponent');
          this.logger.warn('Available patterns:', headerPatterns, 'ConfigureTypeStepComponent');
        }
        
        return matchesAnyPattern;
      });

    // Re-validate negative header examples - they should NOT match any pattern
    const validatedNegatives = this.configNegativeHeaderExamples()
      .filter(example => {
        const matchesAnyPattern = headerPatterns.some(pattern => 
          this.validateAgainstRegex(example.value, pattern)
        );
        const isValid = !matchesAnyPattern; // Negative examples should NOT match any pattern
        if (!isValid && example.value.trim()) {
          this.logger.info(`Removing invalid negative header example: ${example.value} (matches pattern)`, null, 'ConfigureTypeStepComponent');
        }
        return isValid || !example.value.trim(); // Keep empty examples
      });

    // Update the examples if any were removed
    if (validatedPositives.length !== this.configPositiveHeaderExamples().length) {
      this.stateService.setConfigPositiveHeaderExamples(validatedPositives);
    }
    if (validatedNegatives.length !== this.configNegativeHeaderExamples().length) {
      this.stateService.setConfigNegativeHeaderExamples(validatedNegatives);
    }
  }


  // Helper methods
  private findExampleById(id: string, listType: string): Example | undefined {
    switch (listType) {
      case 'configPositiveContent':
        return this.stateService.configPositiveContentExamples().find(e => e.id === id);
      case 'configPositiveHeader':
        return this.stateService.configPositiveHeaderExamples().find(e => e.id === id);
      case 'configNegativeContent':
        return this.stateService.configNegativeContentExamples().find(e => e.id === id);
      case 'configNegativeHeader':
        return this.stateService.configNegativeHeaderExamples().find(e => e.id === id);
      default:
        return undefined;
    }
  }

  private getCorrespondingNegativeListType(listType: ExcludeExampleListType): string | null {
    switch (listType) {
      case 'configPositiveContent':
        return 'configNegativeContent';
      case 'configPositiveHeader':
        return 'configNegativeHeader';
      default:
        return null;
    }
  }

  private getCorrespondingPositiveListType(listType: IncludeExampleListType): string | null {
    switch (listType) {
      case 'configNegativeContent':
        return 'configPositiveContent';
      case 'configNegativeHeader':
        return 'configPositiveHeader';
      default:
        return null;
    }
  }

  private parseHeaderPatterns(patterns: string): string[] {
    if (!patterns.trim()) {
      return [];
    }

    // Split by comma and clean up each pattern
    return patterns
      .split(',')
      .map(pattern => pattern.trim())
      .filter(pattern => pattern.length > 0);
  }

  private processErrorMessage(errorMessage: string): string {
    // Handle specific error cases
    if (errorMessage.includes('already exists')) {
      return `A semantic type with this name already exists. Please choose a different name.`;
    }
    if (errorMessage.includes('invalid pattern')) {
      return `The regex pattern is invalid. Please check your regular expression syntax.`;
    }
    if (errorMessage.includes('validation failed')) {
      return `Validation failed. Please check that all required fields are filled correctly.`;
    }

    return `Save failed: ${errorMessage}`;
  }

  onThumbsUp(): void {
    this.openFeedbackDialog('positive');
  }

  onThumbsDown(): void {
    this.openFeedbackDialog('negative');
  }

  private openFeedbackDialog(feedbackType: 'positive' | 'negative'): void {
    import('../../../feedback-popover/feedback-popover.component').then(({ FeedbackPopoverComponent }) => {
      const ref = this.dialogService.open(FeedbackPopoverComponent, {
        header: feedbackType === 'positive' ? 'What went well?' : 'What could be improved?',
        width: '500px',
        modal: true,
        data: {
          feedbackType: feedbackType,
          semanticTypeName: this.configTypeName(),
          description: this.configDescription(),
        },
      });

      ref.onClose.subscribe((result: { feedbackText: string; skipped: boolean } | undefined) => {
        if (result && !result.skipped) {
          this.sendFeedback(feedbackType, result.feedbackText || '');
        }
      });
    });
  }

  private sendFeedback(feedbackType: 'positive' | 'negative', feedbackText: string): void {
    const feedbackData = {
      type: feedbackType,
      feedback: feedbackText,
      semanticTypeName: this.configTypeName(),
      description: this.configDescription(),
      pluginType: this.pluginType(),
      regexPattern: this.regexPattern(),
      headerPatterns: this.headerPatterns(),
      username: sessionStorage.getItem('precisely-username'),
      timestamp: new Date().toISOString(),
    };

    this.logger.info('User feedback submitted', feedbackData, 'ConfigureTypeStepComponent');
    
    // Send feedback to backend
    this.feedbackService.sendFeedback(feedbackData).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Feedback Sent',
          detail: `Thank you for your ${feedbackType} feedback! We appreciate your input.`,
          life: 4000
        });
      },
      error: (error) => {
        this.logger.error('Failed to send feedback', error, 'ConfigureTypeStepComponent');
        this.messageService.add({
          severity: 'error',
          summary: 'Feedback Failed',
          detail: 'Failed to send your feedback. Please try again.',
          life: 4000
        });
      },
    });
  }

}
