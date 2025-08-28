import { Component, signal, inject, OnInit, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { DropdownModule } from 'primeng/dropdown';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { ChipModule } from 'primeng/chip';
import { ToastModule } from 'primeng/toast';
import { SemanticTypeService } from '../../../core/services/semantic-type.service';
import { FtaClassifierService } from '../../../core/services/fta-classifier.service';
import { AnalysisService } from '../../../core/services/analysis.service';
import { LoggerService } from '../../../core/services/logger.service';
import { ConfigService } from '../../../core/services/config.service';
import { SemanticTypeCardComponent } from './semantic-type-card/semantic-type-card.component';
import { AddSemanticTypeModalComponent } from './add-semantic-type-modal/add-semantic-type-modal.component';
import {
  ConfidenceRangeOption,
  ThresholdFilter,
  TypeFilterValue,
  PluginTypeFilterValue,
} from '../../../shared/models/semantic-type.model';

interface FilterOption {
  label: string;
  value: string;
  count?: number;
}

@Component({
  selector: 'app-semantic-types-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    InputTextModule,
    DropdownModule,
    MultiSelectModule,
    ChipModule,
    ToastModule,
    SemanticTypeCardComponent,
    AddSemanticTypeModalComponent,
  ],
  templateUrl: './semantic-types-list.component.html',
  styleUrl: './semantic-types-list.component.css',
  providers: [MessageService],
})
export class SemanticTypesListComponent implements OnInit {
  typesExpanded = signal(false);
  isLoadingTypes = signal(false);
  showAddTypeModal = signal(false);

  semanticTypeService = inject(SemanticTypeService);
  ftaClassifierService = inject(FtaClassifierService);
  analysisService = inject(AnalysisService);
  messageService = inject(MessageService);
  logger = inject(LoggerService);
  configService = inject(ConfigService);
  private platformId = inject(PLATFORM_ID);

  selectedTypeFilters: string[] = [];
  selectedPluginTypeFilters: string[] = [];
  selectedThresholdFilters: string[] = [];
  sortOptions = [
    { label: 'Time added', value: 'time' },
    { label: 'Name', value: 'name' },
  ];
  selectedSort: 'time' | 'name' = 'time';

  private get config() {
    return this.configService.getConfig();
  }

  // Lifecycle hooks
  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      this.semanticTypeService.loadTypes();
    }
    this.initializeFilters();
    this.selectedSort = this.semanticTypeService.sortBy();
  }

  // Getters
  get typeFilterOptions(): FilterOption[] {
    return [
      { label: 'Custom', value: 'custom', count: this.semanticTypeService.customTypesCount() },
      { label: 'Out of the Box', value: 'ootb', count: this.semanticTypeService.ootbTypesCount() },
    ];
  }

  onSortChange(event: { value: 'time' | 'name' }): void {
    const value = event?.value ?? 'time';
    this.selectedSort = value;
    this.semanticTypeService.setSortBy(value);
  }

  get pluginTypeFilterOptions(): FilterOption[] {
    return [
      { label: 'Java', value: 'java', count: this.semanticTypeService.javaTypesCount() },
      { label: 'List', value: 'list', count: this.semanticTypeService.listTypesCount() },
      { label: 'Regex', value: 'regex', count: this.semanticTypeService.regexTypesCount() },
    ];
  }

  get thresholdFilterOptions(): ConfidenceRangeOption[] {
    return [
      {
        label: 'High (90-100%)',
        value: 'high',
        min: this.config?.highThresholdMin || 90,
        max: this.config?.highThresholdMax || 100,
        count: this.semanticTypeService.highConfidenceCount(),
      },
      {
        label: 'Medium (70-89%)',
        value: 'medium',
        min: this.config?.mediumThresholdMin || 70,
        max: this.config?.mediumThresholdMax || 89,
        count: this.semanticTypeService.mediumConfidenceCount(),
      },
      {
        label: 'Low (0-69%)',
        value: 'low',
        min: this.config?.lowThresholdMin || 0,
        max: this.config?.lowThresholdMax || 69,
        count: this.semanticTypeService.lowConfidenceCount(),
      },
    ];
  }

  // Public methods - UI actions
  toggleTypesExpanded(): void {
    this.typesExpanded.update(v => !v);
  }

  openAddTypeModal(): void {
    this.showAddTypeModal.set(true);
  }

  closeAddTypeModal(): void {
    this.showAddTypeModal.set(false);
  }

  async onTypeSaved(): Promise<void> {
    this.closeAddTypeModal();

    try {
      await this.semanticTypeService.loadTypes();

      this.messageService.add({
        severity: 'success',
        summary: 'Success',
        detail: 'Custom semantic type has been created successfully.',
      });

      await new Promise(resolve => setTimeout(resolve, this.config?.reanalysisDelayMs || 1000));
      await this.analysisService.reanalyzeAllAnalyses();

      this.messageService.add({
        severity: 'success',
        summary: 'Re-analysis Complete',
        detail: 'All existing analyses have been updated with the new semantic type.',
      });
    } catch (error) {
      this.logger.error(
        'Error during semantic type creation or re-analysis',
        error,
        'SemanticTypesListComponent'
      );
      this.messageService.add({
        severity: 'warn',
        summary: 'Partial Success',
        detail:
          'Semantic type created but automatic re-analysis failed. You may need to manually re-upload your files.',
      });
    }
  }

  onTypeDeleted(semanticType: string): void {
    this.ftaClassifierService.deleteSemanticType(semanticType).subscribe({
      next: async () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: `Custom semantic type "${semanticType}" has been deleted.`,
        });

        // Reload types to reflect the deletion
        await this.semanticTypeService.loadTypes();

        this.analysisService.reanalyzeAllAnalyses().catch(error => {
          this.logger.error(
            'Automatic re-analysis after deletion failed',
            error,
            'SemanticTypesListComponent'
          );
        });
      },
      error: error => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: error.message || 'Failed to delete the semantic type. Please try again.',
        });
      },
    });
  }

  // Public methods - Filter related
  hasActiveFilters(): boolean {
    const typeFilter = this.semanticTypeService.filter();
    const pluginFilter = this.semanticTypeService.pluginFilter();

    const hasTypeFilter = Array.isArray(typeFilter) ? typeFilter.length > 0 : typeFilter !== 'all';
    const hasPluginFilter = Array.isArray(pluginFilter)
      ? pluginFilter.length > 0
      : pluginFilter !== 'all';

    return (
      hasTypeFilter ||
      hasPluginFilter ||
      this.semanticTypeService.thresholds().length > 0 ||
      this.semanticTypeService.search() !== ''
    );
  }

  onTypeFilterChange(event: { value: string[] }): void {
    this.semanticTypeService.setTypeFilters(event.value as TypeFilterValue[]);
  }

  onPluginTypeFilterChange(event: { value: string[] }): void {
    this.semanticTypeService.setPluginTypeFilters(event.value as PluginTypeFilterValue[]);
  }

  onThresholdFilterChange(event: { value: string[] }): void {
    const selectedRanges = event.value
      .map(value => {
        const option = this.thresholdFilterOptions.find(opt => opt.value === value);
        return option ? { min: option.min, max: option.max, label: option.label } : null;
      })
      .filter(range => range !== null);

    this.semanticTypeService.setThresholdFilters(selectedRanges as ThresholdFilter[]);
  }

  getTypeFilterLabels(): string[] {
    const filter = this.semanticTypeService.filter();
    if (Array.isArray(filter)) {
      return filter.map(f => (f === 'custom' ? 'Custom' : f === 'ootb' ? 'OOTB' : f));
    }
    return filter === 'all' ? [] : [filter === 'custom' ? 'Custom' : 'OOTB'];
  }

  getPluginTypeFilterLabels(): string[] {
    const filter = this.semanticTypeService.pluginFilter();
    if (Array.isArray(filter)) {
      return filter.map(f => f.charAt(0).toUpperCase() + f.slice(1));
    }
    return filter === 'all' ? [] : [filter.charAt(0).toUpperCase() + filter.slice(1)];
  }

  getTypeFilterValues(): string[] {
    const filter = this.semanticTypeService.filter();
    return Array.isArray(filter) ? filter : filter === 'all' ? [] : [filter];
  }

  getPluginTypeFilterValues(): string[] {
    const filter = this.semanticTypeService.pluginFilter();
    return Array.isArray(filter) ? filter : filter === 'all' ? [] : [filter];
  }

  removeThresholdFilter(threshold: ThresholdFilter): void {
    const currentThresholds = this.semanticTypeService.thresholds();
    const updatedThresholds = currentThresholds.filter(
      t => !(t.min === threshold.min && t.max === threshold.max)
    );
    this.semanticTypeService.setThresholdFilters(updatedThresholds);

    this.selectedThresholdFilters = updatedThresholds
      .map(t => {
        if (t.min === 90 && t.max === 100) return 'high';
        if (t.min === 70 && t.max === 89) return 'medium';
        if (t.min === 0 && t.max === 69) return 'low';
        return '';
      })
      .filter(v => v !== '');
  }

  removeTypeFilter(filterValue: string): void {
    const currentFilters = this.selectedTypeFilters.filter(f => f !== filterValue);
    this.selectedTypeFilters = currentFilters;
    this.semanticTypeService.setTypeFilters(currentFilters as TypeFilterValue[]);
  }

  removePluginTypeFilter(filterValue: string): void {
    const currentFilters = this.selectedPluginTypeFilters.filter(f => f !== filterValue);
    this.selectedPluginTypeFilters = currentFilters;
    this.semanticTypeService.setPluginTypeFilters(currentFilters as PluginTypeFilterValue[]);
  }

  clearAllFilters(): void {
    this.semanticTypeService.clearAllFilters();
    this.selectedTypeFilters = [];
    this.selectedPluginTypeFilters = [];
    this.selectedThresholdFilters = [];
  }

  onClearTypeFilter(): void {
    this.selectedTypeFilters = [];
    this.semanticTypeService.setTypeFilters([]);
  }

  onClearPluginTypeFilter(): void {
    this.selectedPluginTypeFilters = [];
    this.semanticTypeService.setPluginTypeFilters([]);
  }

  onClearThresholdFilter(): void {
    this.selectedThresholdFilters = [];
    this.semanticTypeService.setThresholdFilters([]);
  }

  // Private methods
  private initializeFilters(): void {
    const typeFilter = this.semanticTypeService.filter();
    const pluginFilter = this.semanticTypeService.pluginFilter();

    this.selectedTypeFilters = Array.isArray(typeFilter)
      ? typeFilter
      : typeFilter === 'all'
        ? []
        : [typeFilter];
    this.selectedPluginTypeFilters = Array.isArray(pluginFilter)
      ? pluginFilter
      : pluginFilter === 'all'
        ? []
        : [pluginFilter];

    const thresholds = this.semanticTypeService.thresholds();
    this.selectedThresholdFilters = thresholds
      .map(threshold => {
        if (threshold.min === 90 && threshold.max === 100) return 'high';
        if (threshold.min === 70 && threshold.max === 89) return 'medium';
        if (threshold.min === 0 && threshold.max === 69) return 'low';
        return '';
      })
      .filter(v => v !== '');
  }
}
