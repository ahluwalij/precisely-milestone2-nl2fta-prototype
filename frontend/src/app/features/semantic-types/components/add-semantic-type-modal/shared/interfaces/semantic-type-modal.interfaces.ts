export interface Example {
  id: string;
  value: string;
  removable: boolean;
}

export interface ApiError {
  error?: {
    message?: string;
  };
  message?: string;
}

export interface ConfigFieldsEdited {
  name: boolean;
  description: boolean;
  positiveContentExamples: boolean;
  negativeContentExamples: boolean;
  positiveHeaderExamples: boolean;
  negativeHeaderExamples: boolean;
  pluginType: boolean;
  pattern: boolean;
  listValues: boolean;
  headerPatterns: boolean;
}

export type ExampleListType =
  | 'nlPositiveContent'
  | 'nlNegativeContent'
  | 'nlPositiveHeader'
  | 'nlNegativeHeader'
  | 'configPositiveContent'
  | 'configNegativeContent'
  | 'configPositiveHeader'
  | 'configNegativeHeader'
  | 'listValues';

export type ExcludeExampleListType =
  | 'nlPositiveContent'
  | 'nlPositiveHeader'
  | 'configPositiveContent'
  | 'configPositiveHeader';

export type IncludeExampleListType =
  | 'nlNegativeContent'
  | 'nlNegativeHeader'
  | 'configNegativeContent'
  | 'configNegativeHeader';

export interface NaturalLanguageStepData {
  description: string;
  positiveContentExamples: Example[];
  negativeContentExamples: Example[];
  positiveHeaderExamples: Example[];
  negativeHeaderExamples: Example[];
}

export interface ConfigureTypeStepData {
  typeName: string;
  description: string;
  pluginType: 'regex' | 'list';
  regexPattern: string;
  listValues: Example[];
  confidenceThreshold: string;
  headerPatterns: string;
  positiveContentExamples: Example[];
  negativeContentExamples: Example[];
  positiveHeaderExamples: Example[];
  negativeHeaderExamples: Example[];
}

export interface SemanticTypeModalState {
  // Step management
  activeStepIndex: number;
  isGenerating: boolean;
  isSaving: boolean;
  generationError: string;
  saveError: string;

  // Natural language step data
  naturalLanguageData: NaturalLanguageStepData;

  // Configure type step data
  configureTypeData: ConfigureTypeStepData;

  // Generation and validation state
  wasGenerated: boolean;
  hasConfigChanges: boolean;
  configFieldsEdited: ConfigFieldsEdited;

  // More examples generation
  dataValuesMoreExamplesInput: string;
  headerPatternsMoreExamplesInput: string;
  isGeneratingMoreExamples: boolean;

  // Focus tracking
  nlFirstPositiveContentFocused: boolean;
  configFirstPositiveContentFocused: boolean;

  // Regeneration feedback
  regenerateFeedback: string;

  // Validation state
  isValidating: boolean;
}

export interface DropdownOption {
  label: string;
  value: string;
}
