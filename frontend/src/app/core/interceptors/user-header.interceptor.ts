import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../../auth/auth.service';
import { CorrelationService } from '../services/correlation.service';

export const userHeaderInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const correlation = inject(CorrelationService);
  const username = authService.getUsername();
  const correlationId = correlation.getCurrentId();
  
  // If username exists, add it to the request headers
  const headers: Record<string, string> = {};
  if (username) headers['X-Username'] = username;
  if (correlationId) headers['X-Correlation-Id'] = correlationId;

  if (Object.keys(headers).length > 0) {
    const clonedRequest = req.clone({ setHeaders: headers });
    return next(clonedRequest);
  }

  return next(req);
};