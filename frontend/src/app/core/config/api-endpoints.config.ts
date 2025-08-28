/**
 * Centralized API endpoint configuration
 * This file defines all API endpoints used by the frontend
 * and ensures consistency across development and production environments
 */

export const API_ENDPOINTS = {
  // Configuration
  CONFIG: '/config',
  
  // Table Classification
  CLASSIFY_TABLE: '/classify/table',
  UPLOAD_FILE: '/table-classification/analyze',
  REANALYZE: '/table-classification/reanalyze',
  
  // Analyses Management
  GET_ANALYSES: '/analyses',
  DELETE_ANALYSES: '/analyses',
  REANALYZE_BY_ID: (analysisId: string) => `/analyses/${analysisId}/reanalyze`,
  
  // Semantic Types
  SEMANTIC_TYPES: {
    GET_ALL: '/semantic-types',
    GET_CUSTOM: '/semantic-types/custom-only',
    CREATE: '/semantic-types',
    UPDATE: (semanticType: string) => `/semantic-types/${encodeURIComponent(semanticType)}`,
    DELETE: (semanticType: string) => `/semantic-types/${encodeURIComponent(semanticType)}`,
    RELOAD: '/semantic-types/reload',
    GENERATE: '/semantic-types/generate',
    GENERATE_VALIDATED_EXAMPLES: '/semantic-types/generate-validated-examples',
  },
  
  // AWS Integration
  AWS: {
    VALIDATE_CREDENTIALS: '/aws/validate-credentials',
    GET_MODELS: (region: string) => `/aws/models/${region}`,
    CONFIGURE: '/aws/configure',
    STATUS: '/aws/status',
    DELETE_CREDENTIALS: '/aws/credentials',
    VALIDATE_MODEL: (region: string) => `/aws/validate-model/${region}`,
    CREDENTIALS_STATUS: '/aws/credentials/status',
    INDEXING_STATUS: '/aws/credentials/indexing-status',
    
    // Semantic Type Generation AWS endpoints
    CONFIGURE_ST: '/semantic-types/aws/configure',
    STATUS_ST: '/semantic-types/aws/status',
    LOGOUT_ST: '/semantic-types/aws/logout',
  },
  
  // Feedback
  FEEDBACK: '/feedback',
} as const;

/**
 * Helper function to build full API URL
 * @param endpoint - The endpoint path from API_ENDPOINTS
 * @param apiUrl - The base API URL (e.g., '/api')
 * @returns The complete API URL
 */
export function buildApiUrl(endpoint: string, apiUrl: string): string {
  // Ensure apiUrl doesn't end with slash and endpoint starts with slash
  const cleanApiUrl = apiUrl.endsWith('/') ? apiUrl.slice(0, -1) : apiUrl;
  const cleanEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;
  return `${cleanApiUrl}${cleanEndpoint}`;
}