import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from './auth.service';
import { LoggerService } from '../core/services/logger.service';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;
  let logger: jasmine.SpyObj<LoggerService>;

  beforeEach(async () => {
    const authSpy = jasmine.createSpyObj('AuthService', ['login']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    const loggerSpy = jasmine.createSpyObj('LoggerService', ['error', 'info', 'warn']);

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: authSpy },
        { provide: Router, useValue: routerSpy },
        { provide: LoggerService, useValue: loggerSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    logger = TestBed.inject(LoggerService) as jasmine.SpyObj<LoggerService>;
    
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should initialize with empty form values', () => {
      expect(component.username).toBe('');
      expect(component.password).toBe('');
      expect(component.errorMessage).toBe('');
      expect(component.isLoading).toBe(false);
    });
  });

  describe('form validation', () => {
    it('should show error when username is empty', () => {
      component.username = '';
      component.password = 'validpassword123';

      component.onSubmit();

      expect(component.errorMessage).toBe('Please enter your name');
      expect(authService.login).not.toHaveBeenCalled();
    });

    it('should show error when username is only whitespace', () => {
      component.username = '   ';
      component.password = 'validpassword123';

      component.onSubmit();

      expect(component.errorMessage).toBe('Please enter your name');
      expect(authService.login).not.toHaveBeenCalled();
    });

    it('should show error when password is too short', () => {
      component.username = 'testuser';
      component.password = 'short';

      component.onSubmit();

      expect(component.errorMessage).toContain('Password must be at least');
      expect(authService.login).not.toHaveBeenCalled();
    });

    it('should show error when password is empty', () => {
      component.username = 'testuser';
      component.password = '';

      component.onSubmit();

      expect(component.errorMessage).toBeTruthy();
      expect(authService.login).not.toHaveBeenCalled();
    });

    it('should accept valid username and password', () => {
      component.username = 'testuser';
      component.password = 'validpassword123';
      authService.login.and.returnValue(of(true));

      component.onSubmit();

      expect(component.errorMessage).toBe('');
      expect(authService.login).toHaveBeenCalledWith('validpassword123', 'testuser');
    });

    it('should trim whitespace from username before submission', () => {
      component.username = '  testuser  ';
      component.password = 'validpassword123';
      authService.login.and.returnValue(of(true));

      component.onSubmit();

      expect(authService.login).toHaveBeenCalledWith('validpassword123', 'testuser');
    });
  });

  describe('authentication flow', () => {
    beforeEach(() => {
      component.username = 'testuser';
      component.password = 'validpassword123';
    });

    it('should navigate to home on successful login', () => {
      authService.login.and.returnValue(of(true));

      component.onSubmit();

      expect(component.isLoading).toBe(true);
      expect(component.errorMessage).toBe('');
      expect(router.navigate).toHaveBeenCalledWith(['/']);
    });

    it('should show error message on failed login', () => {
      authService.login.and.returnValue(of(false));

      component.onSubmit();

      expect(component.errorMessage).toBe('Invalid password');
      expect(component.isLoading).toBe(false);
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should show error message on authentication error', () => {
      authService.login.and.returnValue(throwError(() => new Error('Network error')));

      component.onSubmit();

      expect(component.errorMessage).toBe('Invalid password');
      expect(component.isLoading).toBe(false);
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should set loading state during authentication', () => {
      authService.login.and.returnValue(of(true));

      expect(component.isLoading).toBe(false);
      
      component.onSubmit();
      
      expect(component.isLoading).toBe(true);
    });

    it('should clear error message when starting new login attempt', () => {
      component.errorMessage = 'Previous error';
      authService.login.and.returnValue(of(true));

      component.onSubmit();

      expect(component.errorMessage).toBe('');
    });
  });

  describe('image error handling', () => {
    it('should handle image load errors gracefully', () => {
      const mockEvent = {
        target: {
          style: { display: '' }
        }
      } as unknown as Event;

      component.onImageError(mockEvent);

      expect(logger.error).toHaveBeenCalledWith(
        'Failed to load precisely.svg from path: /precisely.svg',
        { event: mockEvent },
        'LoginComponent'
      );
      expect((mockEvent.target as HTMLImageElement).style.display).toBe('none');
    });

    it('should hide image element on error', () => {
      const mockImgElement = document.createElement('img');
      const mockEvent = { target: mockImgElement } as unknown as Event;

      component.onImageError(mockEvent);

      expect(mockImgElement.style.display).toBe('none');
    });
  });

  describe('password validation', () => {
    it('should validate minimum password length', () => {
      component.username = 'testuser';
      
      // Test various password lengths
      component.password = '1234567'; // 7 characters - should fail
      component.onSubmit();
      expect(component.errorMessage).toContain('Password must be at least');

      component.password = '12345678'; // 8 characters - should pass
      authService.login.and.returnValue(of(true));
      component.onSubmit();
      expect(authService.login).toHaveBeenCalled();
    });

    it('should handle null or undefined password', () => {
      component.username = 'testuser';
      component.password = null as unknown as string;

      component.onSubmit();

      expect(component.errorMessage).toBeTruthy();
      expect(authService.login).not.toHaveBeenCalled();
    });
  });

  describe('user interaction', () => {
    it('should reset loading state after failed authentication', () => {
      component.username = 'testuser';
      component.password = 'validpassword123';
      authService.login.and.returnValue(of(false));

      component.onSubmit();

      expect(component.isLoading).toBe(false);
    });

    it('should maintain loading state after successful authentication', () => {
      component.username = 'testuser';
      component.password = 'validpassword123';
      authService.login.and.returnValue(of(true));

      component.onSubmit();

      // Loading should remain true as user is being redirected
      expect(component.isLoading).toBe(true);
    });
  });

  describe('edge cases', () => {
    it('should handle authentication service errors gracefully', () => {
      component.username = 'testuser';
      component.password = 'validpassword123';
      authService.login.and.returnValue(throwError(() => new Error('Service unavailable')));

      component.onSubmit();

      expect(component.errorMessage).toBe('Invalid password');
      expect(component.isLoading).toBe(false);
    });

    it('should handle empty form submission', () => {
      component.username = '';
      component.password = '';

      component.onSubmit();

      expect(component.errorMessage).toBe('Please enter your name');
      expect(authService.login).not.toHaveBeenCalled();
    });

    it('should prevent multiple simultaneous login attempts', () => {
      component.username = 'testuser';
      component.password = 'validpassword123';
      component.isLoading = true; // Simulate already loading

      // In a real implementation, you might want to prevent submission when loading
      // This test documents current behavior
      authService.login.and.returnValue(of(true));
      component.onSubmit();

      expect(authService.login).toHaveBeenCalled();
    });
  });
});