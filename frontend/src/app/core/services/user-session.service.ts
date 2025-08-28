import { Injectable, inject } from '@angular/core';
import { AuthService } from '../../auth/auth.service';

@Injectable({
  providedIn: 'root',
})
export class UserSessionService {
  private authService = inject(AuthService);

  getUsername(): string | null {
    return this.authService.getUsername();
  }

  getUserContext(): { username: string | null; timestamp: number } {
    return {
      username: this.getUsername(),
      timestamp: Date.now(),
    };
  }
}