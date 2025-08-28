import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ConfigService } from './config.service';
import { buildApiUrl, API_ENDPOINTS } from '../config/api-endpoints.config';
import { CorrelationService } from './correlation.service';

export interface FeedbackData {
  type: 'positive' | 'negative';
  feedback: string; // Can be empty string for thumbs up/down without comment
  semanticTypeName: string;
  description: string;
  pluginType: string;
  regexPattern?: string;
  headerPatterns?: string;
  username?: string | null;
  timestamp: string;
}

@Injectable({
  providedIn: 'root',
})
export class FeedbackService {
  private http = inject(HttpClient);
  private configService = inject(ConfigService);
  private correlation = inject(CorrelationService);

  sendFeedback(feedbackData: FeedbackData): Observable<void> {
    const apiUrl = buildApiUrl(API_ENDPOINTS.FEEDBACK, this.configService.apiUrl);
    const correlationId = this.correlation.getCurrentId();
    const payload = correlationId ? { ...feedbackData, correlationId } : feedbackData;
    return this.http.post<void>(apiUrl, payload);
  }
}