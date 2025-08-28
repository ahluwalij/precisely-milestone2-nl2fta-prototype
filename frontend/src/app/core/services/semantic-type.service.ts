import { Injectable, signal, computed, inject } from '@angular/core';
import { FtaClassifierService, CustomSemanticType } from './fta-classifier.service';
import { ConfigService } from './config.service';
import { LoggerService } from './logger.service';
import { firstValueFrom } from 'rxjs';
import {
  TypeFilterValue,
  PluginTypeFilterValue,
  ThresholdFilter,
} from '../../shared/models/semantic-type.model';

@Injectable({
  providedIn: 'root',
})
export class SemanticTypeService {
  private ftaClassifierService = inject(FtaClassifierService);
  private configService = inject(ConfigService);
  private logger = inject(LoggerService);

  // Signals
  readonly semanticTypes = signal<CustomSemanticType[]>([]);
  readonly search = signal<string>('');
  readonly filter = signal<TypeFilterValue | TypeFilterValue[]>('all');
  readonly pluginFilter = signal<PluginTypeFilterValue | PluginTypeFilterValue[]>('all');
  private readonly customTypeNames = signal<Set<string>>(new Set());
  private readonly expandedTypesSignal = signal<Set<string>>(new Set());

  // Computed properties
  readonly isLoading = signal<boolean>(false);
  readonly allTypes = computed(() => this.semanticTypes());
  readonly thresholds = signal<ThresholdFilter[]>([]);
  readonly expandedTypes = computed(() => Array.from(this.expandedTypesSignal()));

  // Sorting
  readonly sortBy = signal<'time' | 'name'>('time');

  private get config() {
    return this.configService.getConfig();
  }

  /**
   * Updates semantic types
   */
  updateSemanticTypes(types: CustomSemanticType[]): void {
    this.semanticTypes.set(types);
    // For legacy components/tests that rely on preloading custom-only names,
    // trigger a background refresh of custom type names. This does not affect
    // app behavior since filtering prefers isBuiltIn flags.
    this.updateCustomTypeNames(types);
  }

  /**
   * Updates the custom type names based on semantic types
   */
  private updateCustomTypeNames(types: CustomSemanticType[]): void {
    // Load actual custom types from backend to determine which are custom
    this.ftaClassifierService.getCustomSemanticTypesOnly().subscribe({
      next: (customTypes) => {
        const customNames = new Set<string>();
        customTypes.forEach(type => {
          customNames.add(type.semanticType);
        });
        this.customTypeNames.set(customNames);
      },
      error: (error) => {
        this.logger.error('Failed to load custom type names', error, 'SemanticTypeService');
        // Fallback to name-based detection
        const customNames = new Set<string>();
        types.forEach(type => {
          if (this.isCustomTypeName(type.semanticType)) {
            customNames.add(type.semanticType);
          }
        });
        this.customTypeNames.set(customNames);
      }
    });
  }

  /**
   * Determines if a semantic type name indicates a custom type
   */
  private isCustomTypeName(semanticType: string): boolean {
    return semanticType.startsWith('CUSTOM.');
  }

  /**
   * Determines if a semantic type is custom by checking with the FTA service
   */
  async isCustomTypeAsync(semanticType: string): Promise<boolean> {
    try {
      const customTypes = await firstValueFrom(
        this.ftaClassifierService.getCustomSemanticTypesOnly()
      );
      return customTypes.some(type => type.semanticType === semanticType);
    } catch {
      // Fallback to the name-based check
      return this.isCustomTypeName(semanticType);
    }
  }

  // Filtered types based on search and filters
  readonly filteredTypes = computed(() => {
    const types = this.allTypes();
    const searchTerm = this.search().toLowerCase();
    const typeFilter = this.filter();
    const pluginTypeFilter = this.pluginFilter();

    let filtered = types;

    // Search filter
    if (searchTerm) {
      filtered = filtered.filter(
        type =>
          type.semanticType.toLowerCase().includes(searchTerm) ||
          type.description?.toLowerCase().includes(searchTerm)
      );
    }

    // Type filter (custom vs ootb)
    if (typeFilter !== 'all') {
      const filters = Array.isArray(typeFilter) ? typeFilter : [typeFilter];
      filtered = filtered.filter(type => {
        const isCustomType = this.isCustomType(type.semanticType);
        return filters.some(filter => {
          return filter === 'custom' ? isCustomType : filter === 'ootb' ? !isCustomType : false;
        });
      });
    }

    // Plugin type filter
    if (pluginTypeFilter !== 'all') {
      const filters = Array.isArray(pluginTypeFilter) ? pluginTypeFilter : [pluginTypeFilter];
      filtered = filtered.filter(type => {
        const category = this.getTypeCategory(type);
        return filters.includes(category as PluginTypeFilterValue);
      });
    }

    // Threshold filter
    const thresholdFilters = this.thresholds();
    if (thresholdFilters.length > 0) {
      filtered = filtered.filter(type => {
        return thresholdFilters.some(
          threshold =>
            (type.threshold || this.config?.defaultHighThreshold || 95) >= threshold.min &&
            (type.threshold || this.config?.defaultHighThreshold || 95) <= threshold.max
        );
      });
    }

    return filtered;
  });

  // Sorted types based on selected sort option
  readonly sortedTypes = computed(() => {
    const items = this.filteredTypes();
    const sort = this.sortBy();
    if (sort === 'name') {
      return [...items].sort((a, b) =>
        a.semanticType.localeCompare(b.semanticType, undefined, { sensitivity: 'base' })
      );
    }
    // 'time' → sort by createdAt descending (newest first), fallback to stable order
    return [...items].sort((a, b) => {
      const ta = typeof a.createdAt === 'number' ? a.createdAt! : -1;
      const tb = typeof b.createdAt === 'number' ? b.createdAt! : -1;
      return tb - ta;
    });
  });

  // Computed counts
  readonly customTypesCount = computed(() => {
    return this.allTypes().filter(type => this.isCustomType(type.semanticType)).length;
  });

  readonly ootbTypesCount = computed(() => {
    return this.allTypes().filter(type => !this.isCustomType(type.semanticType)).length;
  });

  readonly javaTypesCount = computed(() => {
    return this.allTypes().filter(type => this.getTypeCategory(type) === 'java').length;
  });

  readonly listTypesCount = computed(() => {
    return this.allTypes().filter(type => this.getTypeCategory(type) === 'list').length;
  });

  readonly regexTypesCount = computed(() => {
    return this.allTypes().filter(type => this.getTypeCategory(type) === 'regex').length;
  });

  readonly highConfidenceCount = computed(
    () =>
      this.allTypes().filter(
        type =>
          (type.threshold || this.config?.defaultHighThreshold || 95) >=
            (this.config?.highThresholdMin || 90) &&
          (type.threshold || this.config?.defaultHighThreshold || 95) <=
            (this.config?.highThresholdMax || 100)
      ).length
  );

  readonly mediumConfidenceCount = computed(
    () =>
      this.allTypes().filter(
        type =>
          (type.threshold || this.config?.defaultHighThreshold || 95) >=
            (this.config?.mediumThresholdMin || 70) &&
          (type.threshold || this.config?.defaultHighThreshold || 95) <=
            (this.config?.mediumThresholdMax || 89)
      ).length
  );

  readonly lowConfidenceCount = computed(
    () =>
      this.allTypes().filter(
        type =>
          (type.threshold || this.config?.defaultHighThreshold || 95) >=
            (this.config?.lowThresholdMin || 0) &&
          (type.threshold || this.config?.defaultHighThreshold || 95) <=
            (this.config?.lowThresholdMax || 69)
      ).length
  );

  async loadTypes(): Promise<void> {
    this.isLoading.set(true);

    try {
      const types = await firstValueFrom(this.ftaClassifierService.getAllSemanticTypes());
      this.updateSemanticTypes(types || []);
    } catch (error) {
      this.logger.error('Failed to load semantic types', error, 'SemanticTypeService');
      this.updateSemanticTypes([]);
    } finally {
      this.isLoading.set(false);
    }
  }

  /**
   * Refresh types with retry to absorb eventual consistency from backend (e.g., S3 sync/indexing).
   * Stops early when the list size changes or max attempts reached.
   */
  async refreshTypesWithRetry(maxAttempts = 10, intervalMs = 700): Promise<void> {
    const originalCount = this.semanticTypes().length;
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      await this.loadTypes();
      const currentCount = this.semanticTypes().length;
      if (currentCount !== originalCount) return; // size changed → updated
      if (attempt < maxAttempts - 1) {
        await new Promise((r) => setTimeout(r, intervalMs));
      }
    }
  }

  toggleTypeExpanded(semanticType: string): void {
    const currentExpanded = this.expandedTypesSignal();
    const newExpanded = new Set(currentExpanded);

    if (newExpanded.has(semanticType)) {
      newExpanded.delete(semanticType);
    } else {
      newExpanded.add(semanticType);
    }

    this.expandedTypesSignal.set(newExpanded);
  }

  isTypeExpanded(semanticType: string): boolean {
    return this.expandedTypesSignal().has(semanticType);
  }

  isCustomType(semanticType: string): boolean {
    // Find the type and check its isBuiltIn flag
    // A type is custom if isBuiltIn is false or undefined
    const type = this.semanticTypes().find(t => t.semanticType === semanticType);
    return type ? !type.isBuiltIn : false;
  }

  getTypeCategory(type: CustomSemanticType): PluginTypeFilterValue {
    // Use the actual pluginType field from the semantic type object
    const pluginType = type.pluginType?.toLowerCase();

    if (pluginType === 'java' || pluginType === 'java_class') return 'java';
    if (pluginType === 'list') return 'list';
    if (pluginType === 'regex') return 'regex';

    // Fallback to regex for unknown types
    return 'regex';
  }

  setSearch(searchTerm: string): void {
    this.search.set(searchTerm);
  }

  setTypeFilters(filters: TypeFilterValue[]): void {
    if (filters.length === 0) {
      this.filter.set('all');
    } else if (filters.length === 1) {
      this.filter.set(filters[0]);
    } else {
      this.filter.set(filters);
    }
  }

  setPluginTypeFilters(filters: PluginTypeFilterValue[]): void {
    if (filters.length === 0) {
      this.pluginFilter.set('all');
    } else if (filters.length === 1) {
      this.pluginFilter.set(filters[0]);
    } else {
      this.pluginFilter.set(filters);
    }
  }

  setThresholdFilters(filters: ThresholdFilter[]): void {
    this.thresholds.set(filters);
  }

  setSortBy(value: 'time' | 'name'): void {
    this.sortBy.set(value);
  }

  clearAllFilters(): void {
    this.search.set('');
    this.filter.set('all');
    this.pluginFilter.set('all');
    this.thresholds.set([]);
  }

  /**
   * Refreshes the semantic types list (useful after adding new types)
   */
  async refreshTypes(): Promise<void> {
    await this.loadTypes();
  }

  /**
   * Adds a new custom semantic type and refreshes the list
   */
  async addCustomType(customType: CustomSemanticType): Promise<void> {
    await firstValueFrom(this.ftaClassifierService.addCustomSemanticType(customType));
    await this.refreshTypes();
  }
}
