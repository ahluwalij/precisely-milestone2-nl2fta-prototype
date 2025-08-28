import { TestBed } from '@angular/core/testing';
import { PLATFORM_ID } from '@angular/core';
import { of } from 'rxjs';
import { LoggerService } from './logger.service';
import { EnvironmentService } from './environment.service';
import { HttpClient } from '@angular/common/http';

describe('LoggerService', () => {
  let service: LoggerService;
  let _environmentServiceSpy: jasmine.SpyObj<EnvironmentService>;
  let _httpSpy: jasmine.SpyObj<HttpClient>;
  let consoleSpy: jasmine.Spy;
  let consoleWarnSpy: jasmine.Spy;
  let consoleErrorSpy: jasmine.Spy;

  beforeEach(() => {
    const envSpy = jasmine.createSpyObj('EnvironmentService', [], {
      isDevelopment: false,
    });
    const httpClientSpy = jasmine.createSpyObj('HttpClient', ['post']);
    httpClientSpy.post.and.returnValue(of({}));

    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: 'browser' },
        { provide: EnvironmentService, useValue: envSpy },
        { provide: HttpClient, useValue: httpClientSpy },
      ],
    });

    service = TestBed.inject(LoggerService);
    _environmentServiceSpy = TestBed.inject(EnvironmentService) as jasmine.SpyObj<EnvironmentService>;
    _httpSpy = TestBed.inject(HttpClient) as jasmine.SpyObj<HttpClient>;

    consoleSpy = spyOn(console, 'log');
    consoleWarnSpy = spyOn(console, 'warn');
    consoleErrorSpy = spyOn(console, 'error');
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should log info messages with console.log', () => {
    const message = 'Test info message';
    service.info(message);

    expect(consoleSpy).toHaveBeenCalledTimes(1);
    const loggedMessage = consoleSpy.calls.argsFor(0)[0];
    const parsedLog = JSON.parse(loggedMessage);

    expect(parsedLog.level).toBe('info');
    expect(parsedLog.message).toBe(message);
    expect(parsedLog.application).toBe('nl2fta-frontend');
  });

  it('should log warn messages with console.warn', () => {
    const message = 'Test warning message';
    service.warn(message);

    expect(consoleWarnSpy).toHaveBeenCalledTimes(1);
    const loggedMessage = consoleWarnSpy.calls.argsFor(0)[0];
    const parsedLog = JSON.parse(loggedMessage);

    expect(parsedLog.level).toBe('warn');
    expect(parsedLog.message).toBe(message);
  });

  it('should log error messages with console.error', () => {
    const message = 'Test error message';
    service.error(message);

    expect(consoleErrorSpy).toHaveBeenCalledTimes(1);
    const loggedMessage = consoleErrorSpy.calls.argsFor(0)[0];
    const parsedLog = JSON.parse(loggedMessage);

    expect(parsedLog.level).toBe('error');
    expect(parsedLog.message).toBe(message);
  });

  it('should include timestamp in log entries', () => {
    const beforeTime = Date.now();
    service.info('Test message');
    const afterTime = Date.now();

    const loggedMessage = consoleSpy.calls.argsFor(0)[0];
    const parsedLog = JSON.parse(loggedMessage);

    expect(parsedLog.timestamp).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
    const timestampMs = new Date(parsedLog.timestamp).getTime();
    expect(timestampMs).toBeGreaterThanOrEqual(beforeTime);
    expect(timestampMs).toBeLessThanOrEqual(afterTime);
  });

  it('should call debug method without errors', () => {
    service.debug('Debug message');
    // Just verify the method can be called without throwing errors
    expect(true).toBe(true);
  });
});