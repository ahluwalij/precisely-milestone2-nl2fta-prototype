import { Injectable, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { EnvironmentService } from './environment.service';

export interface LogEntry {
  level: 'info' | 'warn' | 'error' | 'debug';
  message: string;
  data?: unknown;
  timestamp: string;
  source?: string;
  username?: string | null;
}

@Injectable({
  providedIn: 'root',
})
export class LoggerService {
  private platformId = inject(PLATFORM_ID);
  private environmentService = inject(EnvironmentService);
  private http = inject(HttpClient);

  private get isDevelopment(): boolean {
    return (
      this.environmentService.isDevelopment ||
      (isPlatformBrowser(this.platformId) && window.location.hostname === 'localhost')
    );
  }

  private getUsername(): string | null {
    try {
      return sessionStorage.getItem('precisely-username');
    } catch {
      return null;
    }
  }

  private logToContainer(entry: LogEntry): void {
    // Add username to log entry
    const enrichedEntry = {
      ...entry,
      username: this.getUsername(),
      application: 'nl2fta-frontend',
    };

    const logMessage = JSON.stringify(enrichedEntry);

    // Send to Docker logs via stdout/stderr
    if (entry.level === 'error') {
      console.error(logMessage);
    } else if (entry.level === 'warn') {
      console.warn(logMessage);
    } else {
      console.log(logMessage);
    }

    // Also send to backend for centralized logging (best-effort)
    this.sendToBackend(enrichedEntry);
  }

  private sendToBackend(entry: LogEntry & { username: string | null; application: string }): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    // Construct the API URL directly
    // In browser context, we'll use the current origin with the backend port
    const baseUrl = `${window.location.protocol}//${window.location.hostname}:8081`;
    const apiUrl = `${baseUrl}/api/logs`;
    
    // Ensure data is always an object for backend compatibility
    const payload = {
      ...entry,
      data: entry.data && typeof entry.data === 'object' ? entry.data : entry.data ? { value: entry.data } : null
    };
    
    this.http.post(apiUrl, payload).subscribe({
      error: (err) => {
        // Silently fail - we don't want logging failures to break the app
        console.error('Failed to send log to backend:', err);
      },
    });
  }

  info(message: string, data?: unknown, source?: string): void {
    const entry: LogEntry = {
      level: 'info',
      message,
      data,
      timestamp: new Date().toISOString(),
      source,
    };

    this.logToContainer(entry);
  }

  warn(message: string, data?: unknown, source?: string): void {
    const entry: LogEntry = {
      level: 'warn',
      message,
      data,
      timestamp: new Date().toISOString(),
      source,
    };

    this.logToContainer(entry);
  }

  error(message: string, data?: unknown, source?: string): void {
    const entry: LogEntry = {
      level: 'error',
      message,
      data,
      timestamp: new Date().toISOString(),
      source,
    };

    this.logToContainer(entry);
  }

  debug(message: string, data?: unknown, source?: string): void {
    if (this.isDevelopment) {
      const entry: LogEntry = {
        level: 'debug',
        message,
        data,
        timestamp: new Date().toISOString(),
        source,
      };

      this.logToContainer(entry);
    }
  }
}
