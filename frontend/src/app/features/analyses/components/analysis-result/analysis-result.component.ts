import { Component, Input, Output, EventEmitter, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { FileAnalysis } from '../../../../shared/models/file-analysis.model';
import { AnalysisService } from '../../../../core/services/analysis.service';
import { SemanticTypeService } from '../../../../core/services/semantic-type.service';
import { LoggerService } from '../../../../core/services/logger.service';

@Component({
  selector: 'app-analysis-result',
  standalone: true,
  imports: [CommonModule, TableModule, ButtonModule, TooltipModule],
  templateUrl: './analysis-result.component.html',
  styleUrl: './analysis-result.component.css',
})
export class AnalysisResultComponent {
  @Input({ required: true }) analysis!: FileAnalysis;
  @Output() remove = new EventEmitter<void>();

  analysisService = inject(AnalysisService);
  semanticTypeService = inject(SemanticTypeService);
  private logger = inject(LoggerService);

  onRemove(event: Event): void {
    event.stopPropagation();
    this.remove.emit();
  }

  isCustomSemanticType(semanticType: string, isBuiltIn?: boolean): boolean {
    if (!semanticType || semanticType === 'none') {
      return false;
    }
    
    // If isBuiltIn is explicitly true, it's NOT custom (predefined)
    // If isBuiltIn is explicitly false, it IS custom
    if (typeof isBuiltIn === 'boolean') {
      this.logger.debug('isCustomSemanticType resolved by isBuiltIn', { semanticType, isBuiltIn, isCustom: !isBuiltIn }, 'AnalysisResultComponent');
      return !isBuiltIn;
    }
    
    // Fallback to the service method if isBuiltIn is not available
    this.logger.debug('isBuiltIn undefined; falling back to service', { semanticType }, 'AnalysisResultComponent');
    return this.semanticTypeService.isCustomType(semanticType);
  }
}
