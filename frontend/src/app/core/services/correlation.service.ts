import { Injectable } from '@angular/core';

const STORAGE_KEY = 'nl2fta-correlation-id';

@Injectable({ providedIn: 'root' })
export class CorrelationService {
  getCurrentId(): string | null {
    try {
      return sessionStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  }

  startNewFlow(id: string | null | undefined): void {
    if (!id) return;
    try {
      sessionStorage.setItem(STORAGE_KEY, id);
    } catch {
      /* ignore */
    }
  }

  clear(): void {
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore */
    }
  }
}


