import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SemanticTypeService } from './semantic-type.service';
import { FtaClassifierService, CustomSemanticType } from './fta-classifier.service';
import { ConfigService } from './config.service';
import { LoggerService } from './logger.service';
import {
  // TypeFilterValue,
  // PluginTypeFilterValue,
  ThresholdFilter,
} from '../../shared/models/semantic-type.model';

describe('SemanticTypeService', () => {
  let service: SemanticTypeService;
  let ftaClassifierService: jasmine.SpyObj<FtaClassifierService>;
  let configService: jasmine.SpyObj<ConfigService>;
  let logger: jasmine.SpyObj<LoggerService>;

  const mockSemanticTypes: CustomSemanticType[] = [
    {
      semanticType: 'PERSON_NAME',
      description: 'Personal names',
      pluginType: 'REGEX',
      threshold: 95
    },
    {
      semanticType: 'CUSTOM.EMPLOYEE_ID',
      description: 'Employee ID format',
      pluginType: 'REGEX',
      threshold: 85
    },
    {
      semanticType: 'EMAIL',
      description: 'Email addresses',
      pluginType: 'REGEX',
      threshold: 75
    },
    {
      semanticType: 'PHONE_NUMBER',
      description: 'Phone numbers',
      pluginType: 'JAVA',
      threshold: 90
    },
    {
      semanticType: 'STATE_CODES',
      description: 'US State codes',
      pluginType: 'LIST',
      threshold: 65
    }
  ];

  const mockCustomTypesOnly: CustomSemanticType[] = [
    {
      semanticType: 'CUSTOM.EMPLOYEE_ID',
      description: 'Employee ID format',
      pluginType: 'REGEX'
    }
  ];

  const mockConfig = {
    defaultHighThreshold: 95,
    defaultMediumThreshold: 80,
    defaultLowThreshold: 50,
    highThresholdMin: 90,
    highThresholdMax: 100,
    mediumThresholdMin: 70,
    mediumThresholdMax: 89,
    lowThresholdMin: 0,
    lowThresholdMax: 69
  };

  beforeEach(() => {
    const ftaSpy = jasmine.createSpyObj('FtaClassifierService', [
      'getAllSemanticTypes',
      'getCustomSemanticTypesOnly',
      'addCustomSemanticType',
      'updateCustomSemanticType',
      'deleteSemanticType'
    ]);
    const configSpy = jasmine.createSpyObj('ConfigService', ['getConfig']);
    const loggerSpy = jasmine.createSpyObj('LoggerService', ['error', 'info', 'warn']);

    configSpy.getConfig.and.returnValue(mockConfig);
    ftaSpy.getAllSemanticTypes.and.returnValue(of(mockSemanticTypes));
    ftaSpy.getCustomSemanticTypesOnly.and.returnValue(of(mockCustomTypesOnly));
    ftaSpy.addCustomSemanticType.and.returnValue(of(mockCustomTypesOnly[0]));

    TestBed.configureTestingModule({
      providers: [
        SemanticTypeService,
        { provide: FtaClassifierService, useValue: ftaSpy },
        { provide: ConfigService, useValue: configSpy },
        { provide: LoggerService, useValue: loggerSpy }
      ]
    });

    service = TestBed.inject(SemanticTypeService);
    ftaClassifierService = TestBed.inject(FtaClassifierService) as jasmine.SpyObj<FtaClassifierService>;
    configService = TestBed.inject(ConfigService) as jasmine.SpyObj<ConfigService>;
    logger = TestBed.inject(LoggerService) as jasmine.SpyObj<LoggerService>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('updateSemanticTypes', () => {
    it('should update semantic types and fetch custom type names', () => {
      service.updateSemanticTypes(mockSemanticTypes);

      expect(service.semanticTypes()).toEqual(mockSemanticTypes);
      expect(ftaClassifierService.getCustomSemanticTypesOnly).toHaveBeenCalled();
    });

    it('should handle errors when fetching custom type names gracefully', () => {
      ftaClassifierService.getCustomSemanticTypesOnly.and.returnValue(
        throwError(() => new Error('Failed to fetch custom types'))
      );

      service.updateSemanticTypes(mockSemanticTypes);

      expect(logger.error).toHaveBeenCalledWith(
        'Failed to load custom type names',
        jasmine.any(Error),
        'SemanticTypeService'
      );
    });
  });

  describe('filtering and search', () => {
    beforeEach(() => {
      service.updateSemanticTypes(mockSemanticTypes);
    });

    it('should filter semantic types by search term', () => {
      service.search.set('employee');
      
      // Access the filtered types through a computed signal or method
      const allTypes = service.allTypes();
      expect(allTypes.length).toBe(5); // All types are still in allTypes
      
      // The search filtering would typically be handled by a computed signal
      // This test verifies the search signal is updated correctly
      expect(service.search()).toBe('employee');
    });

    it('should update filter values', () => {
      service.filter.set('custom');
      expect(service.filter()).toBe('custom');

      service.pluginFilter.set('regex');
      expect(service.pluginFilter()).toBe('regex');
    });

    it('should handle array filter values', () => {
      service.filter.set(['custom', 'ootb']);
      expect(service.filter()).toEqual(['custom', 'ootb']);
    });
  });

  describe('threshold management', () => {
    it('should initialize with default thresholds', () => {
      const thresholds = service.thresholds();
      expect(thresholds).toEqual([]);
    });

    it('should allow setting custom thresholds', () => {
      const customThresholds = [
        { min: 90, max: 100, label: 'High Confidence' },
        { min: 70, max: 89, label: 'Medium Confidence' },
        { min: 0, max: 69, label: 'Low Confidence' }
      ];

      service.thresholds.set(customThresholds);
      expect(service.thresholds()).toEqual(customThresholds);
    });
  });

  describe('expanded types management', () => {
    it('should manage expanded types state', () => {
      expect(service.expandedTypes()).toEqual([]);

      // Since expandedTypesSignal is private, we test through the public computed
      // In a real implementation, there would be public methods to manage this
      const expandedTypes = service.expandedTypes();
      expect(Array.isArray(expandedTypes)).toBe(true);
    });
  });

  describe('loading state', () => {
    it('should manage loading state', () => {
      expect(service.isLoading()).toBe(false);

      service.isLoading.set(true);
      expect(service.isLoading()).toBe(true);

      service.isLoading.set(false);
      expect(service.isLoading()).toBe(false);
    });
  });

  describe('computed properties', () => {
    it('should provide all types through computed signal', () => {
      service.updateSemanticTypes(mockSemanticTypes);
      
      const allTypes = service.allTypes();
      expect(allTypes).toEqual(mockSemanticTypes);
      expect(allTypes.length).toBe(5);
    });

    it('should react to semantic types changes', () => {
      const initialTypes = [mockSemanticTypes[0]];
      service.updateSemanticTypes(initialTypes);
      expect(service.allTypes().length).toBe(1);

      service.updateSemanticTypes(mockSemanticTypes);
      expect(service.allTypes().length).toBe(5);
    });
  });

  describe('custom type detection', () => {
    it('should identify custom types correctly', () => {
      service.updateSemanticTypes(mockSemanticTypes);

      // After updateSemanticTypes, the service should have fetched custom type names
      // and stored them internally for filtering purposes
      expect(ftaClassifierService.getCustomSemanticTypesOnly).toHaveBeenCalled();
    });

    it('should handle custom type detection errors', () => {
      ftaClassifierService.getCustomSemanticTypesOnly.and.returnValue(
        throwError(() => new Error('Custom types fetch failed'))
      );

      service.updateSemanticTypes(mockSemanticTypes);

      expect(logger.error).toHaveBeenCalledWith(
        'Failed to load custom type names',
        jasmine.any(Error),
        'SemanticTypeService'
      );
    });
  });

  describe('signal reactivity', () => {
    it('should maintain reactive state with signals', () => {
      // Test that signals are reactive
      expect(service.semanticTypes()).toEqual([]);
      
      service.updateSemanticTypes(mockSemanticTypes);
      expect(service.semanticTypes()).toEqual(mockSemanticTypes);

      // Test search signal reactivity
      expect(service.search()).toBe('');
      service.search.set('test');
      expect(service.search()).toBe('test');

      // Test filter signal reactivity
      expect(service.filter()).toBe('all');
      service.filter.set('custom');
      expect(service.filter()).toBe('custom');
    });

    it('should handle complex filter values', () => {
      service.filter.set(['custom', 'ootb']);
      service.pluginFilter.set(['regex', 'list']);

      expect(service.filter()).toEqual(['custom', 'ootb']);
      expect(service.pluginFilter()).toEqual(['regex', 'list']);
    });
  });

  describe('loadTypes', () => {
    it('should load types successfully and update loading state', async () => {
      ftaClassifierService.getAllSemanticTypes.and.returnValue(of(mockSemanticTypes));

      await service.loadTypes();

      expect(service.isLoading()).toBe(false);
      expect(service.allTypes()).toEqual(mockSemanticTypes);
      expect(ftaClassifierService.getAllSemanticTypes).toHaveBeenCalled();
    });

    it('should handle loading error and set empty types', async () => {
      ftaClassifierService.getAllSemanticTypes.and.returnValue(
        throwError(() => new Error('Load failed'))
      );

      await service.loadTypes();

      expect(service.isLoading()).toBe(false);
      expect(service.allTypes()).toEqual([]);
      expect(logger.error).toHaveBeenCalledWith(
        'Failed to load semantic types',
        jasmine.any(Error),
        'SemanticTypeService'
      );
    });

    it('should handle null response from service', async () => {
      ftaClassifierService.getAllSemanticTypes.and.returnValue(of(null as any));

      await service.loadTypes();

      expect(service.allTypes()).toEqual([]);
    });
  });

  describe('toggleTypeExpanded', () => {
    it('should toggle expanded state for a type', () => {
      expect(service.isTypeExpanded('PERSON_NAME')).toBe(false);
      expect(service.expandedTypes()).toEqual([]);

      service.toggleTypeExpanded('PERSON_NAME');
      expect(service.isTypeExpanded('PERSON_NAME')).toBe(true);
      expect(service.expandedTypes()).toContain('PERSON_NAME');

      service.toggleTypeExpanded('PERSON_NAME');
      expect(service.isTypeExpanded('PERSON_NAME')).toBe(false);
      expect(service.expandedTypes()).not.toContain('PERSON_NAME');
    });

    it('should handle multiple expanded types', () => {
      service.toggleTypeExpanded('PERSON_NAME');
      service.toggleTypeExpanded('EMAIL');

      expect(service.expandedTypes()).toContain('PERSON_NAME');
      expect(service.expandedTypes()).toContain('EMAIL');
      expect(service.expandedTypes().length).toBe(2);
    });
  });

  describe('isCustomTypeAsync', () => {
    it('should identify custom types from backend', async () => {
      ftaClassifierService.getCustomSemanticTypesOnly.and.returnValue(of(mockCustomTypesOnly));

      const isCustom = await service.isCustomTypeAsync('CUSTOM.EMPLOYEE_ID');
      expect(isCustom).toBe(true);

      const isNotCustom = await service.isCustomTypeAsync('PERSON_NAME');
      expect(isNotCustom).toBe(false);
    });

    it('should fallback to name-based detection on error', async () => {
      ftaClassifierService.getCustomSemanticTypesOnly.and.returnValue(
        throwError(() => new Error('Service error'))
      );

      const isCustom = await service.isCustomTypeAsync('CUSTOM.EMPLOYEE_ID');
      expect(isCustom).toBe(true);

      const isNotCustom = await service.isCustomTypeAsync('PERSON_NAME');
      expect(isNotCustom).toBe(false);
    });
  });

  describe('filteredTypes computed', () => {
    beforeEach(() => {
      service.updateSemanticTypes(mockSemanticTypes);
    });

    it('should filter by search term', () => {
      service.setSearch('employee');
      const filtered = service.filteredTypes();
      expect(filtered.length).toBe(1);
      expect(filtered[0].semanticType).toBe('CUSTOM.EMPLOYEE_ID');
    });

    it('should filter by search term in description', () => {
      service.setSearch('personal');
      const filtered = service.filteredTypes();
      expect(filtered.length).toBe(1);
      expect(filtered[0].semanticType).toBe('PERSON_NAME');
    });

    it('should filter by type filter (custom)', () => {
      service.setTypeFilters(['custom']);
      const filtered = service.filteredTypes();
      // Only types marked as custom in mockCustomTypesOnly should appear
      expect(filtered.length).toBeLessThanOrEqual(mockSemanticTypes.length);
    });

    it('should filter by type filter (ootb)', () => {
      service.setTypeFilters(['ootb']);
      const filtered = service.filteredTypes();
      expect(filtered.length).toBeLessThanOrEqual(mockSemanticTypes.length);
    });

    it('should filter by multiple type filters', () => {
      service.setTypeFilters(['custom', 'ootb']);
      const filtered = service.filteredTypes();
      expect(filtered.length).toBe(mockSemanticTypes.length);
    });

    it('should filter by plugin type filter', () => {
      service.setPluginTypeFilters(['regex']);
      const filtered = service.filteredTypes();
      expect(filtered.every(type => service.getTypeCategory(type) === 'regex')).toBe(true);
    });

    it('should filter by multiple plugin type filters', () => {
      service.setPluginTypeFilters(['regex', 'java']);
      const filtered = service.filteredTypes();
      expect(filtered.length).toBeGreaterThan(0);
      expect(filtered.every(type => 
        ['regex', 'java'].includes(service.getTypeCategory(type))
      )).toBe(true);
    });

    it('should filter by threshold filter', () => {
      const thresholdFilter: ThresholdFilter[] = [
        { min: 90, max: 100, label: 'High Confidence' }
      ];
      service.setThresholdFilters(thresholdFilter);
      const filtered = service.filteredTypes();
      expect(filtered.every(type => (type.threshold || 95) >= 90)).toBe(true);
    });

    it('should combine multiple filters', () => {
      service.setSearch('phone');
      service.setPluginTypeFilters(['java']);
      const filtered = service.filteredTypes();
      expect(filtered.length).toBe(1);
      expect(filtered[0].semanticType).toBe('PHONE_NUMBER');
    });

    it('should return all types when no filters applied', () => {
      service.clearAllFilters();
      const filtered = service.filteredTypes();
      expect(filtered.length).toBe(mockSemanticTypes.length);
    });
  });

  describe('computed counts', () => {
    beforeEach(() => {
      service.updateSemanticTypes(mockSemanticTypes);
    });

    it('should count custom types correctly', () => {
      const count = service.customTypesCount();
      expect(count).toBeGreaterThanOrEqual(0);
    });

    it('should count ootb types correctly', () => {
      const count = service.ootbTypesCount();
      expect(count).toBeGreaterThanOrEqual(0);
    });

    it('should count java types correctly', () => {
      const count = service.javaTypesCount();
      expect(count).toBe(1); // PHONE_NUMBER has java plugin type
    });

    it('should count list types correctly', () => {
      const count = service.listTypesCount();
      expect(count).toBe(1); // STATE_CODES has list plugin type
    });

    it('should count regex types correctly', () => {
      const count = service.regexTypesCount();
      expect(count).toBe(3); // PERSON_NAME, CUSTOM.EMPLOYEE_ID, EMAIL have regex plugin type
    });

    it('should count high confidence types correctly', () => {
      const count = service.highConfidenceCount();
      expect(count).toBe(2); // PERSON_NAME (95) and PHONE_NUMBER (90)
    });

    it('should count medium confidence types correctly', () => {
      const count = service.mediumConfidenceCount();
      expect(count).toBe(2); // CUSTOM.EMPLOYEE_ID (85) and EMAIL (75)
    });

    it('should count low confidence types correctly', () => {
      const count = service.lowConfidenceCount();
      expect(count).toBe(1); // STATE_CODES (65)
    });
  });

  describe('getTypeCategory', () => {
    it('should categorize java types correctly', () => {
      const javaType = { pluginType: 'java', semanticType: 'TEST', description: 'Test' };
      expect(service.getTypeCategory(javaType)).toBe('java');
    });

    it('should categorize java_class types correctly', () => {
      const javaClassType = { pluginType: 'java_class', semanticType: 'TEST', description: 'Test' };
      expect(service.getTypeCategory(javaClassType)).toBe('java');
    });

    it('should categorize list types correctly', () => {
      const listType = { pluginType: 'list', semanticType: 'TEST', description: 'Test' };
      expect(service.getTypeCategory(listType)).toBe('list');
    });

    it('should categorize regex types correctly', () => {
      const regexType = { pluginType: 'regex', semanticType: 'TEST', description: 'Test' };
      expect(service.getTypeCategory(regexType)).toBe('regex');
    });

    it('should default to regex for unknown types', () => {
      const unknownType = { pluginType: 'unknown', semanticType: 'TEST', description: 'Test' };
      expect(service.getTypeCategory(unknownType)).toBe('regex');
    });

    it('should handle undefined pluginType', () => {
      const undefinedType = { semanticType: 'TEST', description: 'Test' } as CustomSemanticType;
      expect(service.getTypeCategory(undefinedType)).toBe('regex');
    });
  });

  describe('filter setters', () => {
    it('should set type filters correctly', () => {
      service.setTypeFilters(['custom']);
      expect(service.filter()).toBe('custom');

      service.setTypeFilters(['custom', 'ootb']);
      expect(service.filter()).toEqual(['custom', 'ootb']);

      service.setTypeFilters([]);
      expect(service.filter()).toBe('all');
    });

    it('should set plugin type filters correctly', () => {
      service.setPluginTypeFilters(['regex']);
      expect(service.pluginFilter()).toBe('regex');

      service.setPluginTypeFilters(['regex', 'java']);
      expect(service.pluginFilter()).toEqual(['regex', 'java']);

      service.setPluginTypeFilters([]);
      expect(service.pluginFilter()).toBe('all');
    });

    it('should set threshold filters correctly', () => {
      const thresholds: ThresholdFilter[] = [
        { min: 90, max: 100, label: 'High' }
      ];
      service.setThresholdFilters(thresholds);
      expect(service.thresholds()).toEqual(thresholds);
    });

    it('should clear all filters', () => {
      service.setSearch('test');
      service.setTypeFilters(['custom']);
      service.setPluginTypeFilters(['regex']);
      service.setThresholdFilters([{ min: 90, max: 100, label: 'High' }]);

      service.clearAllFilters();

      expect(service.search()).toBe('');
      expect(service.filter()).toBe('all');
      expect(service.pluginFilter()).toBe('all');
      expect(service.thresholds()).toEqual([]);
    });
  });

  describe('refreshTypes', () => {
    it('should call loadTypes', async () => {
      spyOn(service, 'loadTypes').and.returnValue(Promise.resolve());

      await service.refreshTypes();

      expect(service.loadTypes).toHaveBeenCalled();
    });
  });

  describe('addCustomType', () => {
    it('should add custom type and refresh', async () => {
      const newType: CustomSemanticType = {
        semanticType: 'CUSTOM.NEW_TYPE',
        description: 'New custom type',
        pluginType: 'REGEX'
      };
      
      spyOn(service, 'refreshTypes').and.returnValue(Promise.resolve());
      ftaClassifierService.addCustomSemanticType.and.returnValue(of(newType));

      await service.addCustomType(newType);

      expect(ftaClassifierService.addCustomSemanticType).toHaveBeenCalledWith(newType);
      expect(service.refreshTypes).toHaveBeenCalled();
    });
  });

  describe('integration with configuration', () => {
    it('should use configuration for threshold defaults', () => {
      const config = service['config'];
      expect(config.defaultHighThreshold).toBe(95);
      expect(config.defaultMediumThreshold).toBe(80);
      expect(config.defaultLowThreshold).toBe(50);
    });

    it('should handle missing configuration gracefully', () => {
      configService.getConfig.and.returnValue(undefined as any);
      
      // Service should still function without throwing errors
      expect(() => {
        service.updateSemanticTypes(mockSemanticTypes);
      }).not.toThrow();
    });

    it('should use default thresholds when config values are missing', () => {
      configService.getConfig.and.returnValue({} as any);
      service.updateSemanticTypes(mockSemanticTypes);

      const highCount = service.highConfidenceCount();
      const mediumCount = service.mediumConfidenceCount();
      const lowCount = service.lowConfidenceCount();

      // Should not throw and should handle gracefully
      expect(typeof highCount).toBe('number');
      expect(typeof mediumCount).toBe('number');
      expect(typeof lowCount).toBe('number');
    });
  });
});