import { Component, inject, Output, EventEmitter, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { TooltipModule } from 'primeng/tooltip';

import { SemanticTypeModalStateService } from '../../shared/services/semantic-type-modal-state.service';
import { Example, ExampleListType } from '../../shared/interfaces/semantic-type-modal.interfaces';

@Component({
  selector: 'app-natural-language-step',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    InputTextModule,
    TextareaModule,
    ButtonModule,
    MessageModule,
    TooltipModule,
  ],
  templateUrl: './natural-language-step.component.html',
  styleUrl: './natural-language-step.component.css',
})
export class NaturalLanguageStepComponent {
  @Input() awsConfigured = false;
  @Output() generateRequested = new EventEmitter<void>();
  @Output() openAwsConfig = new EventEmitter<void>();

  private stateService = inject(SemanticTypeModalStateService);

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
  nlDescription = this.stateService.nlDescription;
  nlPositiveContentExamples = this.stateService.nlPositiveContentExamples;
  nlNegativeContentExamples = this.stateService.nlNegativeContentExamples;
  nlPositiveHeaderExamples = this.stateService.nlPositiveHeaderExamples;
  nlNegativeHeaderExamples = this.stateService.nlNegativeHeaderExamples;

  generationError = this.stateService.generationError;
  isGenerating = this.stateService.isGenerating;
  canGenerate = this.stateService.canGenerate;

  nlFirstPositiveContentNeedsGlow = this.stateService.nlFirstPositiveContentNeedsGlow;
  nlFirstPositiveContentFocused = this.stateService.nlFirstPositiveContentFocused;

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

  onNlDescriptionChange(value: string): void {
    this.stateService.setNlDescription(value);
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

  trackByExampleId(index: number, example: Example): string {
    return example.id;
  }

  onNlFirstPositiveContentFocus(): void {
    this.stateService.setNlFirstPositiveContentFocused(true);
  }

  onNlFirstPositiveContentBlur(): void {
    this.stateService.setNlFirstPositiveContentFocused(false);
  }

  generateSemanticType(): void {
    this.generateRequested.emit();
  }

  openAwsConfigModal(): void {
    this.openAwsConfig.emit();
  }
}
