import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { map, catchError } from 'rxjs/operators';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly SESSION_KEY = 'precisely-auth-session';
  private readonly SESSION_TOKEN_KEY = 'precisely-auth-token';
  private readonly USERNAME_KEY = 'precisely-username';
  private readonly SESSION_TIMEOUT = 24 * 60 * 60 * 1000; // 24 hours
  private readonly DEV_PASSWORD = this.getDevPassword(); // Only used in development

  private authenticatedSubject = new BehaviorSubject<boolean>(this.isAuthenticated());
  public authenticated$ = this.authenticatedSubject.asObservable();

  private http = inject(HttpClient);

  constructor() {
    this.checkSession();
  }

  login(password: string, username?: string): Observable<boolean> {
    // Input validation
    if (!password || typeof password !== 'string') {
      return of(false);
    }

    // Trim whitespace and check minimum length
    const trimmedPassword = password.trim();
    if (trimmedPassword.length < 8) {
      return of(false);
    }

    // Always use the API to check password from .env.local
    return this.http
      .post<{ success: boolean; sessionToken?: string }>('/api/auth', { password: trimmedPassword })
      .pipe(
        map(response => {
          if (response.success && response.sessionToken) {
            const sessionData = {
              timestamp: Date.now(),
              authenticated: true,
              token: response.sessionToken,
            };

            sessionStorage.setItem(this.SESSION_KEY, JSON.stringify(sessionData));
            sessionStorage.setItem(this.SESSION_TOKEN_KEY, response.sessionToken);
            
            // Store username if provided
            if (username) {
              sessionStorage.setItem(this.USERNAME_KEY, username);
            }
            
            this.authenticatedSubject.next(true);
            return true;
          }
          return false;
        }),
        catchError(() => of(false))
      );
  }

  logout(): void {
    sessionStorage.removeItem(this.SESSION_KEY);
    sessionStorage.removeItem(this.SESSION_TOKEN_KEY);
    sessionStorage.removeItem(this.USERNAME_KEY);
    this.authenticatedSubject.next(false);
  }
  
  getUsername(): string | null {
    return sessionStorage.getItem(this.USERNAME_KEY);
  }

  isAuthenticated(): boolean {
    try {
      const sessionData = sessionStorage.getItem(this.SESSION_KEY);
      if (!sessionData) return false;

      const parsed = JSON.parse(sessionData);
      const isExpired = Date.now() - parsed.timestamp > this.SESSION_TIMEOUT;

      if (isExpired) {
        this.logout();
        return false;
      }

      return parsed.authenticated === true;
    } catch {
      return false;
    }
  }

  private checkSession(): void {
    if (!this.isAuthenticated()) {
      this.authenticatedSubject.next(false);
    }
  }

  private getDevPassword(): string {
    // For development, use a fallback since Angular can't access .env directly
    // In production, this should be configured via environment variables on the server
    return 'dev-password-please-change-in-production';
  }
}
