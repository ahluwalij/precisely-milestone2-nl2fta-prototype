import { TestBed } from '@angular/core/testing';
import { PLATFORM_ID } from '@angular/core';
import { EnvironmentService } from './environment.service';

describe('EnvironmentService', () => {
  let service: EnvironmentService;

  describe('in browser environment', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          EnvironmentService,
          { provide: PLATFORM_ID, useValue: 'browser' },
        ],
      });
      service = TestBed.inject(EnvironmentService);
    });

    it('should be created', () => {
      expect(service).toBeTruthy();
    });

    it('should return development status based on location', () => {
      expect(typeof service.isDevelopment).toBe('boolean');
    });

    it('should return default environment config for browser', () => {
      const config = service.getEnvironmentConfig();

      expect(config).toBeDefined();
      expect(typeof config.port).toBe('number');
      expect(typeof config.apiUrl).toBe('string');
      expect(typeof config.maxFileSize).toBe('number');
      expect(typeof config.maxRows).toBe('number');
      expect(typeof config.httpTimeoutMs).toBe('number');
      expect(typeof config.baseZIndex).toBe('number');
    });

    it('should return reasonable default values', () => {
      const config = service.getEnvironmentConfig();

      expect(config.port).toBeGreaterThan(0);
      expect(config.maxFileSize).toBeGreaterThan(0);
      expect(config.maxRows).toBeGreaterThan(0);
      expect(config.httpTimeoutMs).toBeGreaterThan(0);
      expect(config.httpRetryCount).toBeGreaterThanOrEqual(0);
      expect(config.baseZIndex).toBeGreaterThan(0);
    });

    it('should return threshold configurations within valid ranges', () => {
      const config = service.getEnvironmentConfig();

      expect(config.defaultHighThreshold).toBeGreaterThanOrEqual(0);
      expect(config.defaultHighThreshold).toBeLessThanOrEqual(100);
      expect(config.defaultMediumThreshold).toBeGreaterThanOrEqual(0);
      expect(config.defaultMediumThreshold).toBeLessThanOrEqual(100);
      expect(config.defaultLowThreshold).toBeGreaterThanOrEqual(0);
      expect(config.defaultLowThreshold).toBeLessThanOrEqual(100);

      expect(config.highThresholdMin).toBeLessThanOrEqual(config.highThresholdMax);
      expect(config.mediumThresholdMin).toBeLessThanOrEqual(config.mediumThresholdMax);
      expect(config.lowThresholdMin).toBeLessThanOrEqual(config.lowThresholdMax);
    });

    it('should return delay configurations as positive numbers', () => {
      const config = service.getEnvironmentConfig();

      expect(config.notificationDelayMs).toBeGreaterThan(0);
      expect(config.reanalysisDelayMs).toBeGreaterThan(0);
    });
  });

  describe('in server environment', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          EnvironmentService,
          { provide: PLATFORM_ID, useValue: 'server' },
        ],
      });
      service = TestBed.inject(EnvironmentService);
    });

    it('should handle server-side rendering', () => {
      const config = service.getEnvironmentConfig();
      expect(config).toBeDefined();
    });

    it('should not be development in server environment by default', () => {
      expect(service.isDevelopment).toBe(false);
    });
  });

  describe('configuration consistency', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          EnvironmentService,
          { provide: PLATFORM_ID, useValue: 'browser' },
        ],
      });
      service = TestBed.inject(EnvironmentService);
    });

    it('should return the same config on multiple calls', () => {
      const config1 = service.getEnvironmentConfig();
      const config2 = service.getEnvironmentConfig();

      expect(config1).toEqual(config2);
    });

    it('should have logical threshold hierarchy', () => {
      const config = service.getEnvironmentConfig();

      // High threshold should be higher than medium
      expect(config.defaultHighThreshold).toBeGreaterThanOrEqual(config.defaultMediumThreshold);
      
      // Medium threshold should be higher than low
      expect(config.defaultMediumThreshold).toBeGreaterThanOrEqual(config.defaultLowThreshold);
    });

    it('should have reasonable timeout values', () => {
      const config = service.getEnvironmentConfig();

      // Long timeout should be longer than regular timeout
      expect(config.httpLongTimeoutMs).toBeGreaterThanOrEqual(config.httpTimeoutMs);
      
      // Both should be reasonable values (not too small, not too large)
      expect(config.httpTimeoutMs).toBeGreaterThan(1000); // At least 1 second
      expect(config.httpTimeoutMs).toBeLessThan(300000); // Less than 5 minutes
    });
  });
});