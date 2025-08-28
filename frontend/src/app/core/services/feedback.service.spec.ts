import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { FeedbackService, FeedbackData } from './feedback.service';
import { ConfigService } from './config.service';

describe('FeedbackService', () => {
  let service: FeedbackService;
  let httpClient: jasmine.SpyObj<HttpClient>;
  let configService: jasmine.SpyObj<ConfigService>;

  const mockFeedbackData: FeedbackData = {
    type: 'positive',
    feedback: 'This semantic type works perfectly for my use case!',
    semanticTypeName: 'CUSTOM_ID',
    description: 'Custom identifier for our system',
    pluginType: 'regex',
    regexPattern: '[A-Z]{3}-\\d{4}',
    headerPatterns: 'id,identifier,custom_id',
    username: 'testuser',
    timestamp: '2024-01-15T10:30:00Z'
  };

  beforeEach(() => {
    const httpSpy = jasmine.createSpyObj('HttpClient', ['post']);
    const configSpy = jasmine.createSpyObj('ConfigService', [], {
      apiUrl: '/api'
    });

    TestBed.configureTestingModule({
      providers: [
        FeedbackService,
        { provide: HttpClient, useValue: httpSpy },
        { provide: ConfigService, useValue: configSpy }
      ]
    });

    service = TestBed.inject(FeedbackService);
    httpClient = TestBed.inject(HttpClient) as jasmine.SpyObj<HttpClient>;
    configService = TestBed.inject(ConfigService) as jasmine.SpyObj<ConfigService>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('sendFeedback', () => {
    it('should successfully send positive feedback', (done) => {
      httpClient.post.and.returnValue(of(undefined));

      service.sendFeedback(mockFeedbackData).subscribe(() => {
        expect(httpClient.post).toHaveBeenCalledWith('/api/feedback', mockFeedbackData);
        done();
      });
    });

    it('should successfully send negative feedback', (done) => {
      const negativeFeedback: FeedbackData = {
        type: 'negative',
        feedback: 'This pattern does not work for European phone numbers',
        semanticTypeName: 'PHONE_NUMBER',
        description: 'Phone number validation',
        pluginType: 'regex',
        regexPattern: '\\d{3}-\\d{3}-\\d{4}',
        headerPatterns: 'phone,mobile,contact',
        username: 'eurouser',
        timestamp: '2024-01-15T15:45:00Z'
      };

      httpClient.post.and.returnValue(of(undefined));

      service.sendFeedback(negativeFeedback).subscribe(() => {
        expect(httpClient.post).toHaveBeenCalledWith('/api/feedback', negativeFeedback);
        expect(negativeFeedback.type).toBe('negative');
        done();
      });
    });

    it('should handle feedback without optional fields', (done) => {
      const minimalFeedback: FeedbackData = {
        type: 'positive',
        feedback: 'Good detection',
        semanticTypeName: 'EMAIL',
        description: 'Email address detection',
        pluginType: 'builtin',
        timestamp: '2024-01-15T12:00:00Z'
      };

      httpClient.post.and.returnValue(of(undefined));

      service.sendFeedback(minimalFeedback).subscribe(() => {
        expect(httpClient.post).toHaveBeenCalledWith('/api/feedback', minimalFeedback);
        expect(minimalFeedback.regexPattern).toBeUndefined();
        expect(minimalFeedback.headerPatterns).toBeUndefined();
        expect(minimalFeedback.username).toBeUndefined();
        done();
      });
    });

    it('should handle feedback with null username', (done) => {
      const feedbackWithNullUsername: FeedbackData = {
        type: 'negative',
        feedback: 'Anonymous feedback',
        semanticTypeName: 'DATE',
        description: 'Date format detection',
        pluginType: 'regex',
        username: null,
        timestamp: '2024-01-15T18:00:00Z'
      };

      httpClient.post.and.returnValue(of(undefined));

      service.sendFeedback(feedbackWithNullUsername).subscribe(() => {
        expect(httpClient.post).toHaveBeenCalledWith('/api/feedback', feedbackWithNullUsername);
        expect(feedbackWithNullUsername.username).toBeNull();
        done();
      });
    });

    it('should handle feedback submission errors', (done) => {
      const error = {
        status: 500,
        statusText: 'Internal Server Error',
        error: { message: 'Failed to save feedback' }
      };

      httpClient.post.and.returnValue(throwError(() => error));

      service.sendFeedback(mockFeedbackData).subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (thrownError) => {
          expect(thrownError).toBe(error);
          expect(httpClient.post).toHaveBeenCalledWith('/api/feedback', mockFeedbackData);
          done();
        }
      });
    });

    it('should handle network errors', (done) => {
      const networkError = new Error('Network connection failed');
      httpClient.post.and.returnValue(throwError(() => networkError));

      service.sendFeedback(mockFeedbackData).subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (error) => {
          expect(error.message).toBe('Network connection failed');
          done();
        }
      });
    });

    it('should use correct API endpoint', (done) => {
      httpClient.post.and.returnValue(of(undefined));

      service.sendFeedback(mockFeedbackData).subscribe(() => {
        expect(httpClient.post).toHaveBeenCalledWith('/api/feedback', jasmine.any(Object));
        done();
      });
    });

    it('should handle feedback with special characters in content', (done) => {
      const feedbackWithSpecialChars: FeedbackData = {
        type: 'positive',
        feedback: 'Works great with émojis 🎉 and spécial characters!',
        semanticTypeName: 'UNICODE_TEXT',
        description: 'Text with spécial charactérs',
        pluginType: 'regex',
        regexPattern: '[\\p{L}\\p{M}\\p{S}\\p{N}\\p{P}\\s]+',
        headerPatterns: 'tëxt,méssage,côntent',
        username: 'üsér_123',
        timestamp: '2024-01-15T20:15:30Z'
      };

      httpClient.post.and.returnValue(of(undefined));

      service.sendFeedback(feedbackWithSpecialChars).subscribe(() => {
        expect(httpClient.post).toHaveBeenCalledWith('/api/feedback', feedbackWithSpecialChars);
        done();
      });
    });

    it('should handle very long feedback content', (done) => {
      const longFeedback: FeedbackData = {
        type: 'negative',
        feedback: 'A'.repeat(5000), // Very long feedback
        semanticTypeName: 'LONG_TEXT',
        description: 'Testing with very long content',
        pluginType: 'regex',
        timestamp: '2024-01-15T21:00:00Z'
      };

      httpClient.post.and.returnValue(of(undefined));

      service.sendFeedback(longFeedback).subscribe(() => {
        expect(httpClient.post).toHaveBeenCalledWith('/api/feedback', longFeedback);
        expect(longFeedback.feedback.length).toBe(5000);
        done();
      });
    });
  });

  describe('interface validation', () => {
    it('should properly define FeedbackData interface structure', () => {
      const feedback: FeedbackData = {
        type: 'positive',
        feedback: 'Test feedback',
        semanticTypeName: 'TEST_TYPE',
        description: 'Test description',
        pluginType: 'regex',
        regexPattern: '^test$',
        headerPatterns: 'test,example',
        username: 'testuser',
        timestamp: '2024-01-01T00:00:00Z'
      };

      expect(feedback.type).toBe('positive');
      expect(feedback.feedback).toBe('Test feedback');
      expect(feedback.semanticTypeName).toBe('TEST_TYPE');
      expect(feedback.description).toBe('Test description');
      expect(feedback.pluginType).toBe('regex');
      expect(feedback.regexPattern).toBe('^test$');
      expect(feedback.headerPatterns).toBe('test,example');
      expect(feedback.username).toBe('testuser');
      expect(feedback.timestamp).toBe('2024-01-01T00:00:00Z');
    });

    it('should support both positive and negative feedback types', () => {
      const positiveFeedback: FeedbackData = {
        type: 'positive',
        feedback: 'Good',
        semanticTypeName: 'TEST',
        description: 'Test',
        pluginType: 'regex',
        timestamp: '2024-01-01T00:00:00Z'
      };

      const negativeFeedback: FeedbackData = {
        type: 'negative',
        feedback: 'Bad',
        semanticTypeName: 'TEST',
        description: 'Test',
        pluginType: 'regex',
        timestamp: '2024-01-01T00:00:00Z'
      };

      expect(positiveFeedback.type).toBe('positive');
      expect(negativeFeedback.type).toBe('negative');
    });

    it('should handle different plugin types', () => {
      const regexFeedback: FeedbackData = {
        type: 'positive',
        feedback: 'Regex works',
        semanticTypeName: 'REGEX_TYPE',
        description: 'Regex-based type',
        pluginType: 'regex',
        timestamp: '2024-01-01T00:00:00Z'
      };

      const builtinFeedback: FeedbackData = {
        type: 'positive',
        feedback: 'Builtin works',
        semanticTypeName: 'BUILTIN_TYPE',
        description: 'Built-in type',
        pluginType: 'builtin',
        timestamp: '2024-01-01T00:00:00Z'
      };

      expect(regexFeedback.pluginType).toBe('regex');
      expect(builtinFeedback.pluginType).toBe('builtin');
    });
  });

  describe('service integration', () => {
    it('should properly inject dependencies', () => {
      expect(service['http']).toBe(httpClient);
      expect(service['configService']).toBe(configService);
    });

    it('should build API URL correctly', (done) => {
      httpClient.post.and.returnValue(of(undefined));

      service.sendFeedback(mockFeedbackData).subscribe(() => {
        const expectedUrl = '/api/feedback';
        expect(httpClient.post).toHaveBeenCalledWith(expectedUrl, mockFeedbackData);
        done();
      });
    });

    it('should use configService.apiUrl for building endpoint', () => {
      expect(configService.apiUrl).toBe('/api');
    });
  });

  describe('edge cases', () => {
    it('should handle empty feedback string', (done) => {
      const emptyFeedback: FeedbackData = {
        type: 'positive',
        feedback: '',
        semanticTypeName: 'EMPTY_TEST',
        description: 'Testing empty feedback',
        pluginType: 'regex',
        timestamp: '2024-01-15T00:00:00Z'
      };

      httpClient.post.and.returnValue(of(undefined));

      service.sendFeedback(emptyFeedback).subscribe(() => {
        expect(httpClient.post).toHaveBeenCalledWith('/api/feedback', emptyFeedback);
        expect(emptyFeedback.feedback).toBe('');
        done();
      });
    });

    it('should handle semantic type names with special characters', (done) => {
      const specialTypeFeedback: FeedbackData = {
        type: 'negative',
        feedback: 'Issues with special type',
        semanticTypeName: 'CUSTOM@TYPE#1',
        description: 'Type with special chars',
        pluginType: 'regex',
        timestamp: '2024-01-15T00:00:00Z'
      };

      httpClient.post.and.returnValue(of(undefined));

      service.sendFeedback(specialTypeFeedback).subscribe(() => {
        expect(httpClient.post).toHaveBeenCalledWith('/api/feedback', specialTypeFeedback);
        expect(specialTypeFeedback.semanticTypeName).toBe('CUSTOM@TYPE#1');
        done();
      });
    });

    it('should handle regex patterns with escaped characters', (done) => {
      const regexFeedback: FeedbackData = {
        type: 'positive',
        feedback: 'Complex regex works well',
        semanticTypeName: 'COMPLEX_REGEX',
        description: 'Complex regex pattern',
        pluginType: 'regex',
        regexPattern: '(?i)^\\s*\\d{1,3}(\\.\\d{1,3}){3}\\s*$',
        timestamp: '2024-01-15T00:00:00Z'
      };

      httpClient.post.and.returnValue(of(undefined));

      service.sendFeedback(regexFeedback).subscribe(() => {
        expect(httpClient.post).toHaveBeenCalledWith('/api/feedback', regexFeedback);
        expect(regexFeedback.regexPattern).toContain('\\d');
        expect(regexFeedback.regexPattern).toContain('\\s');
        done();
      });
    });
  });
});