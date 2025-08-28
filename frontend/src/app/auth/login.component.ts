import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { PasswordModule } from 'primeng/password';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';

import { AuthService } from './auth.service';
import { LoggerService } from '../core/services/logger.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    PasswordModule,
    ButtonModule,
    CardModule,
    InputTextModule,
    MessageModule,
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  private authService = inject(AuthService);
  private router = inject(Router);
  private logger = inject(LoggerService);

  username = '';
  password = '';
  errorMessage = '';
  isLoading = false;

  onSubmit(): void {
    if (!this.username || !this.username.trim()) {
      this.errorMessage = 'Please enter your name';
      return;
    }

    const validationError = this.validatePassword(this.password);
    if (validationError) {
      this.errorMessage = validationError;
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.login(this.password, this.username.trim()).subscribe({
      next: success => {
        if (success) {
          this.router.navigate(['/']);
        } else {
          this.errorMessage = 'Invalid password';
          this.isLoading = false;
        }
      },
      error: () => {
        this.errorMessage = 'Invalid password';
        this.isLoading = false;
      },
    });
  }

  onImageError(event: Event): void {
    this.logger.error(
      'Failed to load precisely.svg from path: /precisely.svg',
      { event },
      'LoginComponent'
    );
    // Fallback to show text if image fails
    const imgElement = event.target as HTMLImageElement;
    imgElement.style.display = 'none';

    // Show text fallback
    const logoText = document.querySelector('.logo-text') as HTMLElement;
    if (logoText) {
      logoText.style.display = 'block';
    }
  }

  private validatePassword(password: string): string | null {
    if (!password || typeof password !== 'string') {
      return 'Please enter the password';
    }

    const trimmedPassword = password.trim();
    if (trimmedPassword.length === 0) {
      return 'Please enter the password';
    }

    if (trimmedPassword.length < 8) {
      return 'Password must be at least 8 characters long';
    }

    if (trimmedPassword.length > 128) {
      return 'Password is too long';
    }

    return null;
  }
}
