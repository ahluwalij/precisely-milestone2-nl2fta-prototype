import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpClient: jasmine.SpyObj<HttpClient>;

  beforeEach(() => {
    const httpSpy = jasmine.createSpyObj('HttpClient', ['post']);

    // Clear session storage before each test
    sessionStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        { provide: HttpClient, useValue: httpSpy },
      ],
    });

    service = TestBed.inject(AuthService);
    httpClient = TestBed.inject(HttpClient) as jasmine.SpyObj<HttpClient>;
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return false for unauthenticated user initially', () => {
    expect(service.isAuthenticated()).toBe(false);
  });

  describe('login', () => {
    it('should successfully authenticate with valid credentials', (done) => {
      const mockResponse = { success: true, sessionToken: 'test-token-123' };
      httpClient.post.and.returnValue(of(mockResponse));

      service.login('validpassword', 'testuser').subscribe(result => {
        expect(result).toBe(true);
        expect(service.isAuthenticated()).toBe(true);
        expect(service.getUsername()).toBe('testuser');
        done();
      });

      expect(httpClient.post).toHaveBeenCalledWith('/api/auth', { password: 'validpassword' });
    });

    it('should fail authentication with invalid credentials', (done) => {
      const mockResponse = { success: false };
      httpClient.post.and.returnValue(of(mockResponse));

      service.login('wrongpassword').subscribe(result => {
        expect(result).toBe(false);
        expect(service.isAuthenticated()).toBe(false);
        done();
      });
    });

    it('should handle HTTP errors gracefully', (done) => {
      httpClient.post.and.returnValue(throwError(() => new Error('Network error')));

      service.login('validpassword').subscribe(result => {
        expect(result).toBe(false);
        expect(service.isAuthenticated()).toBe(false);
        done();
      });
    });

    it('should reject passwords shorter than 8 characters', (done) => {
      service.login('short').subscribe(result => {
        expect(result).toBe(false);
        expect(httpClient.post).not.toHaveBeenCalled();
        done();
      });
    });

    it('should reject empty or invalid passwords', (done) => {
      service.login('').subscribe(result => {
        expect(result).toBe(false);
        expect(httpClient.post).not.toHaveBeenCalled();
        done();
      });
    });

    it('should trim whitespace from passwords', (done) => {
      const mockResponse = { success: true, sessionToken: 'test-token' };
      httpClient.post.and.returnValue(of(mockResponse));

      service.login('  validpassword  ').subscribe(result => {
        expect(result).toBe(true);
        expect(httpClient.post).toHaveBeenCalledWith('/api/auth', { password: 'validpassword' });
        done();
      });
    });
  });

  describe('logout', () => {
    it('should clear session data and update authentication status', () => {
      // Set up authenticated state
      sessionStorage.setItem('precisely-auth-session', JSON.stringify({ authenticated: true }));
      sessionStorage.setItem('precisely-auth-token', 'test-token');
      sessionStorage.setItem('precisely-username', 'testuser');

      service.logout();

      expect(sessionStorage.getItem('precisely-auth-session')).toBeNull();
      expect(sessionStorage.getItem('precisely-auth-token')).toBeNull();
      expect(sessionStorage.getItem('precisely-username')).toBeNull();
      expect(service.isAuthenticated()).toBe(false);
    });
  });

  describe('session management', () => {
    it('should recognize valid session', () => {
      const sessionData = {
        timestamp: Date.now(),
        authenticated: true,
        token: 'valid-token'
      };
      sessionStorage.setItem('precisely-auth-session', JSON.stringify(sessionData));

      expect(service.isAuthenticated()).toBe(true);
    });

    it('should reject expired session', () => {
      const expiredTimestamp = Date.now() - (25 * 60 * 60 * 1000); // 25 hours ago
      const sessionData = {
        timestamp: expiredTimestamp,
        authenticated: true,
        token: 'expired-token'
      };
      sessionStorage.setItem('precisely-auth-session', JSON.stringify(sessionData));

      expect(service.isAuthenticated()).toBe(false);
    });

    it('should handle corrupted session data', () => {
      sessionStorage.setItem('precisely-auth-session', 'invalid-json');
      expect(service.isAuthenticated()).toBe(false);
    });
  });

  describe('username management', () => {
    it('should return null when no username is stored', () => {
      expect(service.getUsername()).toBeNull();
    });

    it('should return stored username', () => {
      sessionStorage.setItem('precisely-username', 'testuser');
      expect(service.getUsername()).toBe('testuser');
    });
  });

  describe('authentication observable', () => {
    it('should emit authentication state changes', (done) => {
      let emissionCount = 0;
      const expectedStates = [false, true, false];

      service.authenticated$.subscribe(isAuthenticated => {
        expect(isAuthenticated).toBe(expectedStates[emissionCount]);
        emissionCount++;

        if (emissionCount === expectedStates.length) {
          done();
        }
      });

      // Trigger state changes
      const mockResponse = { success: true, sessionToken: 'test-token' };
      httpClient.post.and.returnValue(of(mockResponse));
      
      service.login('validpassword').subscribe(() => {
        service.logout();
      });
    });
  });
});