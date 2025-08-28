import { TestBed } from '@angular/core/testing';
import { UserSessionService } from './user-session.service';
import { AuthService } from '../../auth/auth.service';

describe('UserSessionService', () => {
  let service: UserSessionService;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    const authSpy = jasmine.createSpyObj('AuthService', ['getUsername']);

    TestBed.configureTestingModule({
      providers: [
        UserSessionService,
        { provide: AuthService, useValue: authSpy }
      ]
    });

    service = TestBed.inject(UserSessionService);
    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getUsername', () => {
    it('should return username from auth service when user is logged in', () => {
      const mockUsername = 'testuser';
      authService.getUsername.and.returnValue(mockUsername);

      const result = service.getUsername();

      expect(result).toBe(mockUsername);
      expect(authService.getUsername).toHaveBeenCalled();
    });

    it('should return null when no user is logged in', () => {
      authService.getUsername.and.returnValue(null);

      const result = service.getUsername();

      expect(result).toBeNull();
      expect(authService.getUsername).toHaveBeenCalled();
    });

    it('should delegate directly to auth service', () => {
      authService.getUsername.and.returnValue('admin');

      service.getUsername();

      expect(authService.getUsername).toHaveBeenCalledTimes(1);
    });
  });

  describe('getUserContext', () => {
    it('should return user context with username and timestamp when user is logged in', () => {
      const mockUsername = 'contextuser';
      const mockTimestamp = 1640995200000; // Fixed timestamp for testing
      
      authService.getUsername.and.returnValue(mockUsername);
      spyOn(Date, 'now').and.returnValue(mockTimestamp);

      const context = service.getUserContext();

      expect(context.username).toBe(mockUsername);
      expect(context.timestamp).toBe(mockTimestamp);
      expect(authService.getUsername).toHaveBeenCalled();
    });

    it('should return user context with null username when no user is logged in', () => {
      const mockTimestamp = 1640995200000;
      
      authService.getUsername.and.returnValue(null);
      spyOn(Date, 'now').and.returnValue(mockTimestamp);

      const context = service.getUserContext();

      expect(context.username).toBeNull();
      expect(context.timestamp).toBe(mockTimestamp);
    });

    it('should always include current timestamp', () => {
      const beforeTimestamp = Date.now();
      authService.getUsername.and.returnValue('timestampuser');

      const context = service.getUserContext();
      const afterTimestamp = Date.now();

      expect(context.timestamp).toBeGreaterThanOrEqual(beforeTimestamp);
      expect(context.timestamp).toBeLessThanOrEqual(afterTimestamp);
    });

    it('should create new timestamp on each call', () => {
      authService.getUsername.and.returnValue('user');

      const context1 = service.getUserContext();
      // Small delay to ensure different timestamps
      const context2 = service.getUserContext();

      expect(context2.timestamp).toBeGreaterThanOrEqual(context1.timestamp);
    });

    it('should have correct context structure', () => {
      authService.getUsername.and.returnValue('structureuser');

      const context = service.getUserContext();

      expect(context).toEqual({
        username: jasmine.any(String),
        timestamp: jasmine.any(Number)
      });
      expect(Object.keys(context)).toEqual(['username', 'timestamp']);
    });
  });

  describe('service integration', () => {
    it('should properly inject AuthService', () => {
      expect(service['authService']).toBe(authService);
    });

    it('should maintain consistency between getUsername and getUserContext', () => {
      const testUsername = 'consistencytest';
      authService.getUsername.and.returnValue(testUsername);

      const directUsername = service.getUsername();
      const contextUsername = service.getUserContext().username;

      expect(directUsername).toBe(contextUsername);
      expect(directUsername).toBe(testUsername);
    });
  });

  describe('edge cases', () => {
    it('should handle empty string username', () => {
      authService.getUsername.and.returnValue('');

      const username = service.getUsername();
      const context = service.getUserContext();

      expect(username).toBe('');
      expect(context.username).toBe('');
    });

    it('should handle whitespace-only username', () => {
      const whitespaceUsername = '   ';
      authService.getUsername.and.returnValue(whitespaceUsername);

      const username = service.getUsername();
      const context = service.getUserContext();

      expect(username).toBe(whitespaceUsername);
      expect(context.username).toBe(whitespaceUsername);
    });

    it('should handle username with special characters', () => {
      const specialUsername = 'user@domain.com';
      authService.getUsername.and.returnValue(specialUsername);

      const username = service.getUsername();
      const context = service.getUserContext();

      expect(username).toBe(specialUsername);
      expect(context.username).toBe(specialUsername);
    });

    it('should handle very long usernames', () => {
      const longUsername = 'a'.repeat(1000);
      authService.getUsername.and.returnValue(longUsername);

      const username = service.getUsername();
      const context = service.getUserContext();

      expect(username).toBe(longUsername);
      expect(context.username).toBe(longUsername);
      expect(username!.length).toBe(1000);
    });
  });

  describe('performance', () => {
    it('should not cache username results', () => {
      authService.getUsername.and.returnValues('user1', 'user2', 'user3');

      const call1 = service.getUsername();
      const call2 = service.getUsername();
      const call3 = service.getUsername();

      expect(call1).toBe('user1');
      expect(call2).toBe('user2');
      expect(call3).toBe('user3');
      expect(authService.getUsername).toHaveBeenCalledTimes(3);
    });

    it('should create fresh context on each getUserContext call', () => {
      authService.getUsername.and.returnValue('freshuser');

      const context1 = service.getUserContext();
      const context2 = service.getUserContext();

      expect(context1).not.toBe(context2); // Different object references
      expect(context1.username).toBe(context2.username); // Same username
      expect(context2.timestamp >= context1.timestamp).toBe(true); // Later or equal timestamp
    });
  });
});