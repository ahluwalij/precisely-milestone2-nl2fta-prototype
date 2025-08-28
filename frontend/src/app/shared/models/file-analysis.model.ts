import { TableClassificationResponse } from '../../core/services/fta-classifier.service';

export interface FileAnalysis {
  id: string;
  fileName: string;
  uploadTime: Date;
  lastAnalyzedAt: Date;
  classificationResults: TableClassificationResponse;
  tableData: Record<string, string | number | boolean | null>[];
  dynamicColumns: DynamicColumn[];
  originalData: Record<string, string | number | boolean | null>[];
  isExpanded: boolean;
}

export interface DynamicColumn {
  field: string;
  header: string;
  baseType: string;
  semanticType: string;
  confidence: number;
  pattern?: string;
  isBuiltIn?: boolean;
}
