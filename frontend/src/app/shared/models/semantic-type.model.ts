export interface CreateSemanticTypeForm {
  semanticTypeName: string;
  description: string;
  positiveExamples: string[];
  negativeExamples: string[];
  pluginType: 'REGEX' | 'LIST';
  regexPattern?: string;
  listValues: string[];
  confidenceThreshold?: number;
  headerPatterns?: string;
}

export type TypeFilter = 'all' | 'custom' | 'ootb' | string[];
export type TypeFilterValue = 'all' | 'custom' | 'ootb';

export type PluginTypeFilter = 'all' | 'java' | 'list' | 'regex' | string[];
export type PluginTypeFilterValue = 'all' | 'java' | 'list' | 'regex';

export interface ThresholdFilter {
  min: number;
  max: number;
  label: string;
}

export interface ConfidenceRangeOption {
  label: string;
  value: string;
  min: number;
  max: number;
  count?: number;
}
