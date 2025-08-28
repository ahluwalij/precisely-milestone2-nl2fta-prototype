import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { retry, timeout, catchError } from 'rxjs/operators';
import { CustomSemanticType } from './fta-classifier.service';
import { ConfigService } from './config.service';
import { API_ENDPOINTS, buildApiUrl } from '../config/api-endpoints.config';
import { LoggerService } from './logger.service';

@Injectable({
  providedIn: 'root',
})
export class SemanticTypeRepositoryService {
  private http = inject(HttpClient);
  private configService = inject(ConfigService);
  private logger = inject(LoggerService);

  private get apiUrl(): string {
    return this.configService.apiUrl;
  }

  private get config() {
    return this.configService.getConfig();
  }

  getAllSemanticTypes(): Observable<CustomSemanticType[]> {
    const url = buildApiUrl(API_ENDPOINTS.SEMANTIC_TYPES.GET_ALL, this.apiUrl);
    return this.http.get<CustomSemanticType[]>(url).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  getCustomSemanticTypesOnly(): Observable<CustomSemanticType[]> {
    const url = buildApiUrl(API_ENDPOINTS.SEMANTIC_TYPES.GET_CUSTOM, this.apiUrl);
    return this.http.get<CustomSemanticType[]>(url).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  addCustomSemanticType(customType: CustomSemanticType): Observable<CustomSemanticType> {
    const url = buildApiUrl(API_ENDPOINTS.SEMANTIC_TYPES.CREATE, this.apiUrl);
    return this.http.post<CustomSemanticType>(url, customType).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  updateCustomSemanticType(
    semanticType: string,
    customType: CustomSemanticType
  ): Observable<CustomSemanticType> {
    const url = buildApiUrl(API_ENDPOINTS.SEMANTIC_TYPES.UPDATE(semanticType), this.apiUrl);
    return this.http.put<CustomSemanticType>(url, customType).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  deleteSemanticType(semanticType: string): Observable<void> {
    const url = buildApiUrl(API_ENDPOINTS.SEMANTIC_TYPES.DELETE(semanticType), this.apiUrl);
    return this.http.delete<void>(url).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  reloadSemanticTypes(): Observable<{ message: string }> {
    const url = buildApiUrl(API_ENDPOINTS.SEMANTIC_TYPES.RELOAD, this.apiUrl);
    return this.http.post<{ message: string }>(url, {}).pipe(
      timeout(this.config?.httpTimeoutMs || 30000),
      retry(this.config?.httpRetryCount || 2),
      catchError(error => this.handleError(error))
    );
  }

  private handleError(error: unknown): Observable<never> {
    this.logger.error('Semantic type repository service error', error, 'SemanticTypeRepositoryService');
    return throwError(() => error);
  }
}