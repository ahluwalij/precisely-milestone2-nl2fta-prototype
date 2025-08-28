import { Injectable, signal, computed } from '@angular/core';
import {
  Example,
  ConfigFieldsEdited,
  ExampleListType,
} from '../interfaces/semantic-type-modal.interfaces';
import {
  GeneratedSemanticType,
  HeaderPattern,
} from '../../../../../../core/services/aws-bedrock.service';

@Injectable()
export class SemanticTypeModalStateService {
  // State signals
  private _activeStepIndex = signal(0);
  private _isGenerating = signal(false);
  private _isSaving = signal(false);
  private _generationError = signal('');
  private _saveError = signal('');
  private _isValidating = signal(false);
  private _dataValuesGenerationError = signal('');
  private _headerPatternsGenerationError = signal('');

  // Natural Language Step Data
  private _nlDescription = signal('');
  private _nlPositiveContentExamples = signal<Example[]>([
    { id: '1', value: '', removable: false },
  ]);
  private _nlNegativeContentExamples = signal<Example[]>([
    { id: '1n', value: '', removable: false },
  ]);
  private _nlPositiveHeaderExamples = signal<Example[]>([{ id: '1', value: '', removable: false }]);
  private _nlNegativeHeaderExamples = signal<Example[]>([
    { id: '1nh', value: '', removable: false },
  ]);

  // Configure Type Step Data
  private _configTypeName = signal('');
  private _configDescription = signal('');
  private _pluginType = signal<'regex' | 'list'>('regex');
  private _regexPattern = signal('');
  private _listValues = signal<Example[]>([{ id: '1', value: '', removable: false }]);
  private _confidenceThreshold = signal('98');
  private _headerPatterns = signal('');
  private _structuredHeaderPatterns = signal<HeaderPattern[]>([]);
  private _priority = signal(2000);
  private _configPositiveContentExamples = signal<Example[]>([]);
  private _configNegativeContentExamples = signal<Example[]>([]);
  private _configPositiveHeaderExamples = signal<Example[]>([]);
  private _configNegativeHeaderExamples = signal<Example[]>([]);

  // Generation state
  private _wasGenerated = signal(false);
  private _originalGeneratedData = signal<GeneratedSemanticType | null>(null);
  private _hasConfigChanges = signal(false);
  private _configFieldsEdited = signal<ConfigFieldsEdited>({
    name: false,
    description: false,
    positiveContentExamples: false,
    negativeContentExamples: false,
    positiveHeaderExamples: false,
    negativeHeaderExamples: false,
    pluginType: false,
    pattern: false,
    listValues: false,
    headerPatterns: false,
  });

  // More examples generation
  private _dataValuesMoreExamplesInput = signal('');
  private _headerPatternsMoreExamplesInput = signal('');
  private _isGeneratingMoreDataExamples = signal(false);
  private _isGeneratingMoreHeaderExamples = signal(false);

  // Focus tracking
  private _nlFirstPositiveContentFocused = signal(false);
  private _configFirstPositiveContentFocused = signal(false);


  // Example changes tracking
  private _hasExampleChanges = signal(false);
  private _dataExamplesChanged = signal(false);
  private _headerExamplesChanged = signal(false);
  
  // Original examples for comparison
  private _originalPositiveContentExamples = signal<string[]>([]);
  private _originalNegativeContentExamples = signal<string[]>([]);
  private _originalPositiveHeaderExamples = signal<string[]>([]);
  private _originalNegativeHeaderExamples = signal<string[]>([]);

  // Public readonly signals
  readonly activeStepIndex = this._activeStepIndex.asReadonly();
  readonly isGenerating = this._isGenerating.asReadonly();
  readonly isSaving = this._isSaving.asReadonly();
  readonly generationError = this._generationError.asReadonly();
  readonly saveError = this._saveError.asReadonly();
  readonly isValidating = this._isValidating.asReadonly();
  readonly dataValuesGenerationError = this._dataValuesGenerationError.asReadonly();
  readonly headerPatternsGenerationError = this._headerPatternsGenerationError.asReadonly();

  // Natural language step data
  readonly nlDescription = this._nlDescription.asReadonly();
  readonly nlPositiveContentExamples = this._nlPositiveContentExamples.asReadonly();
  readonly nlNegativeContentExamples = this._nlNegativeContentExamples.asReadonly();
  readonly nlPositiveHeaderExamples = this._nlPositiveHeaderExamples.asReadonly();
  readonly nlNegativeHeaderExamples = this._nlNegativeHeaderExamples.asReadonly();

  // Configure type step data
  readonly configTypeName = this._configTypeName.asReadonly();
  readonly configDescription = this._configDescription.asReadonly();
  readonly pluginType = this._pluginType.asReadonly();
  readonly regexPattern = this._regexPattern.asReadonly();
  readonly listValues = this._listValues.asReadonly();
  readonly confidenceThreshold = this._confidenceThreshold.asReadonly();
  readonly headerPatterns = this._headerPatterns.asReadonly();
  readonly structuredHeaderPatterns = this._structuredHeaderPatterns.asReadonly();
  readonly priority = this._priority.asReadonly();
  readonly configPositiveContentExamples = this._configPositiveContentExamples.asReadonly();
  readonly configNegativeContentExamples = this._configNegativeContentExamples.asReadonly();
  readonly configPositiveHeaderExamples = this._configPositiveHeaderExamples.asReadonly();
  readonly configNegativeHeaderExamples = this._configNegativeHeaderExamples.asReadonly();

  // Generation state
  readonly wasGenerated = this._wasGenerated.asReadonly();
  readonly originalGeneratedData = this._originalGeneratedData.asReadonly();
  readonly hasConfigChanges = this._hasConfigChanges.asReadonly();
  readonly configFieldsEdited = this._configFieldsEdited.asReadonly();

  // More examples generation
  readonly dataValuesMoreExamplesInput = this._dataValuesMoreExamplesInput.asReadonly();
  readonly headerPatternsMoreExamplesInput = this._headerPatternsMoreExamplesInput.asReadonly();
  readonly isGeneratingMoreDataExamples = this._isGeneratingMoreDataExamples.asReadonly();
  readonly isGeneratingMoreHeaderExamples = this._isGeneratingMoreHeaderExamples.asReadonly();

  // Focus tracking
  readonly nlFirstPositiveContentFocused = this._nlFirstPositiveContentFocused.asReadonly();
  readonly configFirstPositiveContentFocused = this._configFirstPositiveContentFocused.asReadonly();


  // Example changes tracking
  readonly hasExampleChanges = this._hasExampleChanges.asReadonly();
  readonly dataExamplesChanged = this._dataExamplesChanged.asReadonly();
  readonly headerExamplesChanged = this._headerExamplesChanged.asReadonly();

  // Computed properties
  readonly isConfigureStepDisabled = computed(() => !this._wasGenerated());

  readonly nlFirstPositiveContentNeedsGlow = computed(() => {
    const firstExample = this._nlPositiveContentExamples()[0];
    return firstExample && (!firstExample.value || !firstExample.value.trim());
  });

  readonly configFirstPositiveContentNeedsGlow = computed(() => {
    const firstExample = this._configPositiveContentExamples()[0];
    return !firstExample || !firstExample.value || !firstExample.value.trim();
  });

  readonly canGenerate = computed(() => {
    const hasDescription = this._nlDescription().trim().length > 0;
    return hasDescription && !this._isGenerating();
  });

  readonly canSave = computed(() => {
    const hasTypeName = this._configTypeName().trim().length > 0;
    const hasDescription = this._configDescription().trim().length > 0;
    const hasPattern =
      this._pluginType() === 'regex'
        ? this._regexPattern().trim().length > 0
        : this._listValues().some(v => v.value.trim().length > 0);
    const hasHeaderPattern = this._headerPatterns().trim().length > 0;

    // Check if examples have been changed
    const hasChangedExamples = this._dataExamplesChanged() || this._headerExamplesChanged();

    return hasTypeName && hasDescription && hasPattern && hasHeaderPattern && !this._isSaving() && !hasChangedExamples;
  });

  readonly saveDisabledTooltip = computed(() => {
    // Check for changed examples first
    if (this._dataExamplesChanged() && this._headerExamplesChanged()) {
      return 'You can\'t save because you\'ve changed both data value examples and header pattern examples. Please regenerate the rules to apply your changes.';
    }
    if (this._dataExamplesChanged()) {
      return 'You can\'t save because you\'ve changed the data value examples. Please regenerate the data rules to apply your changes.';
    }
    if (this._headerExamplesChanged()) {
      return 'You can\'t save because you\'ve changed the header pattern examples. Please regenerate the header rules to apply your changes.';
    }
    
    // Then check for missing required fields
    if (!this._configTypeName().trim()) return 'Type name is required';
    if (!this._configDescription().trim()) return 'Description is required';
    if (this._pluginType() === 'regex' && !this._regexPattern().trim()) return 'Regex pattern is required';
    if (this._pluginType() === 'list' && !this._listValues().some(v => v.value.trim())) return 'At least one list value is required';
    if (!this._headerPatterns().trim()) return 'Header patterns are required';
    return '';
  });

  readonly showDataValuesRegenerateButton = computed(() => {
    if (!this._wasGenerated() || this._activeStepIndex() !== 1) return false;
    
    // Show if there's input text OR if examples have been changed
    return this._dataValuesMoreExamplesInput().trim().length > 0 || this._dataExamplesChanged();
  });

  readonly showHeaderPatternsRegenerateButton = computed(() => {
    if (!this._wasGenerated() || this._activeStepIndex() !== 1) return false;
    
    // Show if there's input text OR if examples have been changed
    return this._headerPatternsMoreExamplesInput().trim().length > 0 || this._headerExamplesChanged();
  });

  readonly showRestoreButton = computed(() => {
    return this._wasGenerated() && this._activeStepIndex() === 1 && (this._hasConfigChanges() || this._hasExampleChanges());
  });

  // Update methods
  setActiveStepIndex(index: number): void {
    this._activeStepIndex.set(index);
  }

  setIsGenerating(value: boolean): void {
    this._isGenerating.set(value);
  }

  setIsSaving(value: boolean): void {
    this._isSaving.set(value);
  }

  setGenerationError(error: string): void {
    this._generationError.set(error);
  }

  setSaveError(error: string): void {
    this._saveError.set(error);
  }

  setIsValidating(value: boolean): void {
    this._isValidating.set(value);
  }

  setDataValuesGenerationError(error: string): void {
    this._dataValuesGenerationError.set(error);
  }

  setHeaderPatternsGenerationError(error: string): void {
    this._headerPatternsGenerationError.set(error);
  }

  // Natural language step updates
  setNlDescription(value: string): void {
    this._nlDescription.set(value);
  }

  setNlPositiveContentExamples(examples: Example[]): void {
    this._nlPositiveContentExamples.set(examples);
  }

  setNlNegativeContentExamples(examples: Example[]): void {
    this._nlNegativeContentExamples.set(examples);
  }

  setNlPositiveHeaderExamples(examples: Example[]): void {
    this._nlPositiveHeaderExamples.set(examples);
  }

  setNlNegativeHeaderExamples(examples: Example[]): void {
    this._nlNegativeHeaderExamples.set(examples);
  }

  // Configure type step updates
  setConfigTypeName(value: string): void {
    this._configTypeName.set(value);
    this.checkForChanges();
  }

  setConfigDescription(value: string): void {
    this._configDescription.set(value);
    this.checkForChanges();
  }

  setPluginType(value: 'regex' | 'list'): void {
    this._pluginType.set(value);
    this.checkForChanges();
  }

  setRegexPattern(value: string): void {
    this._regexPattern.set(value);
    this.checkForChanges();
  }

  setListValues(values: Example[]): void {
    this._listValues.set(values);
    this.checkForChanges();
  }

  setConfidenceThreshold(value: string): void {
    this._confidenceThreshold.set(value);
  }

  setHeaderPatterns(value: string): void {
    this._headerPatterns.set(value);
    this.checkForChanges();
  }

  setPriority(value: number): void {
    this._priority.set(value);
    this.checkForChanges();
  }

  setConfigPositiveContentExamples(examples: Example[]): void {
    this._configPositiveContentExamples.set(examples);
    this.checkForChanges();
  }

  setConfigNegativeContentExamples(examples: Example[]): void {
    this._configNegativeContentExamples.set(examples);
    this.checkForChanges();
  }

  setConfigPositiveHeaderExamples(examples: Example[]): void {
    this._configPositiveHeaderExamples.set(examples);
    this.checkForChanges();
  }

  setConfigNegativeHeaderExamples(examples: Example[]): void {
    this._configNegativeHeaderExamples.set(examples);
    this.checkForChanges();
  }

  // Generation state updates
  setWasGenerated(value: boolean): void {
    this._wasGenerated.set(value);
  }

  setOriginalGeneratedData(data: GeneratedSemanticType | null): void {
    this._originalGeneratedData.set(data);
  }

  setDataValuesMoreExamplesInput(value: string): void {
    this._dataValuesMoreExamplesInput.set(value);
  }

  setHeaderPatternsMoreExamplesInput(value: string): void {
    this._headerPatternsMoreExamplesInput.set(value);
  }

  setIsGeneratingMoreDataExamples(value: boolean): void {
    this._isGeneratingMoreDataExamples.set(value);
  }

  setIsGeneratingMoreHeaderExamples(value: boolean): void {
    this._isGeneratingMoreHeaderExamples.set(value);
  }

  setNlFirstPositiveContentFocused(value: boolean): void {
    this._nlFirstPositiveContentFocused.set(value);
  }

  setConfigFirstPositiveContentFocused(value: boolean): void {
    this._configFirstPositiveContentFocused.set(value);
  }


  setDataExamplesChanged(value: boolean): void {
    this._dataExamplesChanged.set(value);
  }

  setHeaderExamplesChanged(value: boolean): void {
    this._headerExamplesChanged.set(value);
  }

  setHasExampleChanges(value: boolean): void {
    this._hasExampleChanges.set(value);
  }

  setOriginalPositiveContentExamples(examples: string[]): void {
    this._originalPositiveContentExamples.set(examples);
  }

  setOriginalNegativeContentExamples(examples: string[]): void {
    this._originalNegativeContentExamples.set(examples);
  }

  setOriginalPositiveHeaderExamples(examples: string[]): void {
    this._originalPositiveHeaderExamples.set(examples);
  }

  setOriginalNegativeHeaderExamples(examples: string[]): void {
    this._originalNegativeHeaderExamples.set(examples);
  }

  // Helper methods
  addExample(listType: ExampleListType): void {
    const newExample: Example = {
      id: Date.now().toString(),
      value: '',
      removable: true,
    };

    switch (listType) {
      case 'nlPositiveContent':
        this._nlPositiveContentExamples.update(examples => [...examples, newExample]);
        break;
      case 'nlNegativeContent':
        this._nlNegativeContentExamples.update(examples => [...examples, newExample]);
        break;
      case 'nlPositiveHeader':
        this._nlPositiveHeaderExamples.update(examples => [...examples, newExample]);
        break;
      case 'nlNegativeHeader':
        this._nlNegativeHeaderExamples.update(examples => [...examples, newExample]);
        break;
      case 'configPositiveContent':
        this._configPositiveContentExamples.update(examples => [...examples, newExample]);
        this.checkForChanges();
        break;
      case 'configNegativeContent':
        this._configNegativeContentExamples.update(examples => [...examples, newExample]);
        this.checkForChanges();
        break;
      case 'configPositiveHeader':
        this._configPositiveHeaderExamples.update(examples => [...examples, newExample]);
        this.checkForChanges();
        break;
      case 'configNegativeHeader':
        this._configNegativeHeaderExamples.update(examples => [...examples, newExample]);
        this.checkForChanges();
        break;
      case 'listValues':
        this._listValues.update(examples => [...examples, newExample]);
        this.checkForChanges();
        break;
    }
  }

  removeExample(id: string, listType: ExampleListType): void {
    const removeFn = (examples: Example[]) => examples.filter(e => e.id !== id);

    switch (listType) {
      case 'nlPositiveContent':
        this._nlPositiveContentExamples.update(removeFn);
        break;
      case 'nlNegativeContent':
        this._nlNegativeContentExamples.update(removeFn);
        break;
      case 'nlPositiveHeader':
        this._nlPositiveHeaderExamples.update(removeFn);
        break;
      case 'nlNegativeHeader':
        this._nlNegativeHeaderExamples.update(removeFn);
        break;
      case 'configPositiveContent':
        this._configPositiveContentExamples.update(removeFn);
        this.checkForChanges();
        break;
      case 'configNegativeContent':
        this._configNegativeContentExamples.update(removeFn);
        this.checkForChanges();
        break;
      case 'configPositiveHeader':
        this._configPositiveHeaderExamples.update(removeFn);
        this.checkForChanges();
        break;
      case 'configNegativeHeader':
        this._configNegativeHeaderExamples.update(removeFn);
        this.checkForChanges();
        break;
      case 'listValues':
        this._listValues.update(removeFn);
        this.checkForChanges();
        break;
    }
  }

  updateExample(id: string, value: string, listType: ExampleListType): void {
    const updateFn = (examples: Example[]) =>
      examples.map(e => (e.id === id ? { ...e, value } : e));

    switch (listType) {
      case 'nlPositiveContent':
        this._nlPositiveContentExamples.update(updateFn);
        break;
      case 'nlNegativeContent':
        this._nlNegativeContentExamples.update(updateFn);
        break;
      case 'nlPositiveHeader':
        this._nlPositiveHeaderExamples.update(updateFn);
        break;
      case 'nlNegativeHeader':
        this._nlNegativeHeaderExamples.update(updateFn);
        break;
      case 'configPositiveContent':
        this._configPositiveContentExamples.update(updateFn);
        this.checkForChanges();
        break;
      case 'configNegativeContent':
        this._configNegativeContentExamples.update(updateFn);
        this.checkForChanges();
        break;
      case 'configPositiveHeader':
        this._configPositiveHeaderExamples.update(updateFn);
        this.checkForChanges();
        break;
      case 'configNegativeHeader':
        this._configNegativeHeaderExamples.update(updateFn);
        this.checkForChanges();
        break;
      case 'listValues':
        this._listValues.update(updateFn);
        this.checkForChanges();
        break;
    }
  }

  toggleExampleExclusion(id: string, listType: ExampleListType): void {
    const _dataChanged = false;
    const _headerChanged = false;
    let exampleToMove: Example | undefined;

    // Find and remove the example from the source list
    switch (listType) {
      case 'configPositiveContent':
        exampleToMove = this._configPositiveContentExamples().find(e => e.id === id);
        if (exampleToMove) {
          this._configPositiveContentExamples.update(examples => examples.filter(e => e.id !== id));
          // Add to negative list
          const newExample: Example = {
            id: Date.now().toString(),
            value: exampleToMove.value,
            removable: true
          };
          this._configNegativeContentExamples.update(examples => [...examples, newExample]);
          const _dataChanged2 = true;
        }
        break;

      case 'configNegativeContent':
        exampleToMove = this._configNegativeContentExamples().find(e => e.id === id);
        if (exampleToMove) {
          this._configNegativeContentExamples.update(examples => examples.filter(e => e.id !== id));
          // Add to positive list
          const newExample: Example = {
            id: Date.now().toString(),
            value: exampleToMove.value,
            removable: true
          };
          this._configPositiveContentExamples.update(examples => [...examples, newExample]);
          const _dataChanged = true;
        }
        break;

      case 'configPositiveHeader':
        exampleToMove = this._configPositiveHeaderExamples().find(e => e.id === id);
        if (exampleToMove) {
          this._configPositiveHeaderExamples.update(examples => examples.filter(e => e.id !== id));
          // Add to negative list
          const newExample: Example = {
            id: Date.now().toString(),
            value: exampleToMove.value,
            removable: true
          };
          this._configNegativeHeaderExamples.update(examples => [...examples, newExample]);
          const _headerChanged2 = true;
        }
        break;

      case 'configNegativeHeader':
        exampleToMove = this._configNegativeHeaderExamples().find(e => e.id === id);
        if (exampleToMove) {
          this._configNegativeHeaderExamples.update(examples => examples.filter(e => e.id !== id));
          // Add to positive list
          const newExample: Example = {
            id: Date.now().toString(),
            value: exampleToMove.value,
            removable: true
          };
          this._configPositiveHeaderExamples.update(examples => [...examples, newExample]);
          const _headerChanged = true;
        }
        break;
    }

    // Don't set the flags here - let checkForChanges determine the actual state
    this.checkForChanges();
  }

  populateConfigFields(result: GeneratedSemanticType): void {
    this._configTypeName.set(result.semanticType || '');
    this._configDescription.set(result.description || '');

    if (result.pluginType) {
      this._pluginType.set(result.pluginType);

      if (result.pluginType === 'regex' && result.regexPattern) {
        this._regexPattern.set(result.regexPattern);
      } else if (result.pluginType === 'list' && result.listValues) {
        this._listValues.set([]);
        result.listValues.forEach((value: string, index: number) => {
          if (index === 0) {
            this._listValues.set([{ id: '1', value, removable: false }]);
          } else {
            this._listValues.update(list => [
              ...list,
              { id: (index + 1).toString(), value, removable: true },
            ]);
          }
        });
      }
    }

    this._confidenceThreshold.set((result.confidenceThreshold || 95).toString());
    this._priority.set(result.priority || 2000);

    // Process header patterns - display as comma-separated list
    let headerPatternsDisplay = '';
    const allPositiveHeaderExamples: string[] = [];
    const allNegativeHeaderExamples: string[] = [];

    if (result.headerPatterns && result.headerPatterns.length > 0) {
      // Store the structured patterns for internal use (with confidence, etc.)
      // No limit on header patterns during regeneration
      this._structuredHeaderPatterns.set(result.headerPatterns);

      // Display only the regex patterns as comma-separated list
      headerPatternsDisplay = result.headerPatterns.map(pattern => pattern.regExp).join(', ');
      
      // Extract all positive and negative examples from the patterns
      result.headerPatterns.forEach(pattern => {
        if (pattern.positiveExamples && pattern.positiveExamples.length > 0) {
          allPositiveHeaderExamples.push(...pattern.positiveExamples);
        }
        if (pattern.negativeExamples && pattern.negativeExamples.length > 0) {
          allNegativeHeaderExamples.push(...pattern.negativeExamples);
        }
      });
    } else if (result.headerPattern) {
      headerPatternsDisplay = result.headerPattern;
    }

    if (headerPatternsDisplay.trim()) {
      this.setHeaderPatterns(headerPatternsDisplay);
    }

    // Set examples if available
    if (result.positiveContentExamples?.length) {
      this._configPositiveContentExamples.set(
        result.positiveContentExamples.map((value: string, index: number) => ({
          id: index === 0 ? '1' : Date.now().toString() + index,
          value: value,
          removable: true,
        }))
      );
      // Store original examples
      this._originalPositiveContentExamples.set(
        result.positiveContentExamples.filter((v: string) => v.trim().length > 0)
      );
    }

    if (result.negativeContentExamples?.length) {
      this._configNegativeContentExamples.set(
        result.negativeContentExamples.map((value: string, index: number) => ({
          id: Date.now().toString() + index,
          value: value,
          removable: true,
        }))
      );
      // Store original examples
      this._originalNegativeContentExamples.set(
        result.negativeContentExamples.filter((v: string) => v.trim().length > 0)
      );
    }

    // Add any additional examples from the top-level arrays
    if (result.positiveHeaderExamples && result.positiveHeaderExamples.length > 0) {
      allPositiveHeaderExamples.push(...result.positiveHeaderExamples);
    }
    if (result.negativeHeaderExamples && result.negativeHeaderExamples.length > 0) {
      allNegativeHeaderExamples.push(...result.negativeHeaderExamples);
    }

    if (allPositiveHeaderExamples.length > 0) {
      // Remove duplicates and limit to 6 examples
      const uniquePositiveExamples = [...new Set(allPositiveHeaderExamples.filter(v => v.trim().length > 0))].slice(0, 6);
      
      this._configPositiveHeaderExamples.set(
        uniquePositiveExamples.map((value: string, index: number) => ({
          id: Date.now().toString() + index,
          value: value,
          removable: true,
        }))
      );
      // Store original examples
      this._originalPositiveHeaderExamples.set(uniquePositiveExamples);
    }

    if (allNegativeHeaderExamples.length > 0) {
      // Remove duplicates and limit to 6 examples  
      const uniqueNegativeExamples = [...new Set(allNegativeHeaderExamples.filter(v => v.trim().length > 0))].slice(0, 6);
      
      this._configNegativeHeaderExamples.set(
        uniqueNegativeExamples.map((value: string, index: number) => ({
          id: Date.now().toString() + index,
          value: value,
          removable: true,
        }))
      );
      // Store original examples
      this._originalNegativeHeaderExamples.set(uniqueNegativeExamples);
    }

    this._wasGenerated.set(true);
    this._originalGeneratedData.set(result);
    this._hasConfigChanges.set(false);

    this._configFieldsEdited.set({
      name: false,
      description: false,
      positiveContentExamples: false,
      negativeContentExamples: false,
      positiveHeaderExamples: false,
      negativeHeaderExamples: false,
      pluginType: false,
      pattern: false,
      listValues: false,
      headerPatterns: false,
    });
  }

  private checkForChanges(): void {
    if (!this._wasGenerated()) return;

    const original = this._originalGeneratedData();
    if (!original) return;

    const hasChanges =
      this._configTypeName() !== (original.semanticType || '') ||
      this._configDescription() !== (original.description || '') ||
      this._pluginType() !== original.pluginType ||
      (this._pluginType() === 'regex' && this._regexPattern() !== (original.regexPattern || '')) ||
      this._headerPatterns() !== this.buildHeaderPatternsFromOriginal(original);

    this._hasConfigChanges.set(hasChanges);
    
    // Check if examples have been restored to original state
    this.checkExampleChanges();
  }
  
  private checkExampleChanges(): void {
    // Compare current examples with original examples
    const currentPositiveContent = this._configPositiveContentExamples().map(e => e.value).filter(v => v.trim()).sort();
    const currentNegativeContent = this._configNegativeContentExamples().map(e => e.value).filter(v => v.trim()).sort();
    const currentPositiveHeader = this._configPositiveHeaderExamples().map(e => e.value).filter(v => v.trim()).sort();
    const currentNegativeHeader = this._configNegativeHeaderExamples().map(e => e.value).filter(v => v.trim()).sort();
    
    const originalPositiveContent = [...this._originalPositiveContentExamples()].sort();
    const originalNegativeContent = [...this._originalNegativeContentExamples()].sort();
    const originalPositiveHeader = [...this._originalPositiveHeaderExamples()].sort();
    const originalNegativeHeader = [...this._originalNegativeHeaderExamples()].sort();
    
    // Check if data examples match original
    const dataExamplesMatch = 
      this.arraysEqual(currentPositiveContent, originalPositiveContent) &&
      this.arraysEqual(currentNegativeContent, originalNegativeContent);
      
    // Check if header examples match original
    const headerExamplesMatch = 
      this.arraysEqual(currentPositiveHeader, originalPositiveHeader) &&
      this.arraysEqual(currentNegativeHeader, originalNegativeHeader);
    
    // Update the change tracking signals
    this._dataExamplesChanged.set(!dataExamplesMatch);
    this._headerExamplesChanged.set(!headerExamplesMatch);
    this._hasExampleChanges.set(!dataExamplesMatch || !headerExamplesMatch);
  }
  
  private arraysEqual(a: string[], b: string[]): boolean {
    if (a.length !== b.length) return false;
    for (let i = 0; i < a.length; i++) {
      if (a[i] !== b[i]) return false;
    }
    return true;
  }

  private buildHeaderPatternsFromOriginal(original: GeneratedSemanticType): string {
    if (original.headerPatterns && original.headerPatterns.length > 0) {
      return original.headerPatterns.map(hp => hp.regExp).join(', ');
    } else if (original.headerPattern) {
      return original.headerPattern;
    }
    return '';
  }

  restoreDefaults(): void {
    const original = this._originalGeneratedData();
    if (original) {
      this.populateConfigFields(original);
      // Clear any changes flags - this is important to re-enable the save button
      this._hasExampleChanges.set(false);
      this._dataExamplesChanged.set(false);
      this._headerExamplesChanged.set(false);
      this._dataValuesMoreExamplesInput.set('');
      this._headerPatternsMoreExamplesInput.set('');
    }
  }


  reset(): void {
    this._activeStepIndex.set(0);
    this._isGenerating.set(false);
    this._isSaving.set(false);
    this._generationError.set('');
    this._saveError.set('');
    this._isValidating.set(false);
    this._dataValuesGenerationError.set('');
    this._headerPatternsGenerationError.set('');

    this._nlDescription.set('');
    this._nlPositiveContentExamples.set([{ id: '1', value: '', removable: false }]);
    this._nlNegativeContentExamples.set([{ id: '1n', value: '', removable: false }]);
    this._nlPositiveHeaderExamples.set([{ id: '1', value: '', removable: false }]);
    this._nlNegativeHeaderExamples.set([{ id: '1nh', value: '', removable: false }]);

    this._configTypeName.set('');
    this._configDescription.set('');
    this._pluginType.set('regex');
    this._regexPattern.set('');
    this._listValues.set([{ id: '1', value: '', removable: false }]);
    this._confidenceThreshold.set('98');
    this._headerPatterns.set('');
    this._structuredHeaderPatterns.set([]);
    this._priority.set(2000);
    this._configPositiveContentExamples.set([]);
    this._configNegativeContentExamples.set([]);
    this._configPositiveHeaderExamples.set([]);
    this._configNegativeHeaderExamples.set([]);

    this._wasGenerated.set(false);
    this._originalGeneratedData.set(null);
    this._hasConfigChanges.set(false);
    this._configFieldsEdited.set({
      name: false,
      description: false,
      positiveContentExamples: false,
      negativeContentExamples: false,
      positiveHeaderExamples: false,
      negativeHeaderExamples: false,
      pluginType: false,
      pattern: false,
      listValues: false,
      headerPatterns: false,
    });

    this._dataValuesMoreExamplesInput.set('');
    this._headerPatternsMoreExamplesInput.set('');
    this._isGeneratingMoreDataExamples.set(false);
    this._isGeneratingMoreHeaderExamples.set(false);
    this._nlFirstPositiveContentFocused.set(false);
    this._configFirstPositiveContentFocused.set(false);
  }
}
