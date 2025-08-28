import { API_ENDPOINTS, buildApiUrl } from './api-endpoints.config';

describe('API_ENDPOINTS', () => {
  it('should contain all required endpoint configurations', () => {
    expect(API_ENDPOINTS.CONFIG).toBe('/config');
    expect(API_ENDPOINTS.CLASSIFY_TABLE).toBe('/classify/table');
    expect(API_ENDPOINTS.UPLOAD_FILE).toBe('/table-classification/analyze');
    expect(API_ENDPOINTS.REANALYZE).toBe('/table-classification/reanalyze');
  });

  it('should provide dynamic endpoints with proper parameter encoding', () => {
    const analysisId = '123e4567-e89b-12d3-a456-426614174000';
    expect(API_ENDPOINTS.REANALYZE_BY_ID(analysisId)).toBe(`/analyses/${analysisId}/reanalyze`);
  });

  describe('SEMANTIC_TYPES endpoints', () => {
    it('should provide correct semantic type endpoints', () => {
      expect(API_ENDPOINTS.SEMANTIC_TYPES.GET_ALL).toBe('/semantic-types');
      expect(API_ENDPOINTS.SEMANTIC_TYPES.GET_CUSTOM).toBe('/semantic-types/custom-only');
      expect(API_ENDPOINTS.SEMANTIC_TYPES.CREATE).toBe('/semantic-types');
      expect(API_ENDPOINTS.SEMANTIC_TYPES.RELOAD).toBe('/semantic-types/reload');
      expect(API_ENDPOINTS.SEMANTIC_TYPES.GENERATE).toBe('/semantic-types/generate');
    });

    it('should properly encode semantic type names in URLs', () => {
      const semanticType = 'person/name';
      const expectedEncoded = encodeURIComponent(semanticType);
      
      expect(API_ENDPOINTS.SEMANTIC_TYPES.UPDATE(semanticType))
        .toBe(`/semantic-types/${expectedEncoded}`);
      expect(API_ENDPOINTS.SEMANTIC_TYPES.DELETE(semanticType))
        .toBe(`/semantic-types/${expectedEncoded}`);
    });

    it('should handle special characters in semantic type names', () => {
      const complexSemanticType = 'person/email@domain.com';
      const expectedEncoded = encodeURIComponent(complexSemanticType);
      
      expect(API_ENDPOINTS.SEMANTIC_TYPES.UPDATE(complexSemanticType))
        .toBe(`/semantic-types/${expectedEncoded}`);
    });
  });

  describe('AWS endpoints', () => {
    it('should provide AWS configuration endpoints', () => {
      expect(API_ENDPOINTS.AWS.VALIDATE_CREDENTIALS).toBe('/aws/validate-credentials');
      expect(API_ENDPOINTS.AWS.CONFIGURE).toBe('/aws/configure');
      expect(API_ENDPOINTS.AWS.STATUS).toBe('/aws/status');
      expect(API_ENDPOINTS.AWS.DELETE_CREDENTIALS).toBe('/aws/credentials');
    });

    it('should generate region-specific model endpoints', () => {
      const region = 'us-east-1';
      expect(API_ENDPOINTS.AWS.GET_MODELS(region)).toBe(`/aws/models/${region}`);
      expect(API_ENDPOINTS.AWS.VALIDATE_MODEL(region)).toBe(`/aws/validate-model/${region}`);
    });

    it('should provide semantic type AWS endpoints', () => {
      expect(API_ENDPOINTS.AWS.CONFIGURE_ST).toBe('/semantic-types/aws/configure');
      expect(API_ENDPOINTS.AWS.STATUS_ST).toBe('/semantic-types/aws/status');
      expect(API_ENDPOINTS.AWS.LOGOUT_ST).toBe('/semantic-types/aws/logout');
    });
  });
});

describe('buildApiUrl', () => {
  describe('URL construction', () => {
    it('should build correct URL with standard inputs', () => {
      const endpoint = '/classify/table';
      const apiUrl = '/api';
      
      expect(buildApiUrl(endpoint, apiUrl)).toBe('/api/classify/table');
    });

    it('should handle apiUrl with trailing slash', () => {
      const endpoint = '/classify/table';
      const apiUrl = '/api/';
      
      expect(buildApiUrl(endpoint, apiUrl)).toBe('/api/classify/table');
    });

    it('should handle endpoint without leading slash', () => {
      const endpoint = 'classify/table';
      const apiUrl = '/api';
      
      expect(buildApiUrl(endpoint, apiUrl)).toBe('/api/classify/table');
    });

    it('should handle both apiUrl with trailing slash and endpoint without leading slash', () => {
      const endpoint = 'classify/table';
      const apiUrl = '/api/';
      
      expect(buildApiUrl(endpoint, apiUrl)).toBe('/api/classify/table');
    });
  });

  describe('edge cases', () => {
    it('should handle empty endpoint', () => {
      const endpoint = '';
      const apiUrl = '/api';
      
      expect(buildApiUrl(endpoint, apiUrl)).toBe('/api/');
    });

    it('should handle root endpoint', () => {
      const endpoint = '/';
      const apiUrl = '/api';
      
      expect(buildApiUrl(endpoint, apiUrl)).toBe('/api/');
    });

    it('should handle nested endpoints', () => {
      const endpoint = '/semantic-types/custom-only';
      const apiUrl = '/api/v1';
      
      expect(buildApiUrl(endpoint, apiUrl)).toBe('/api/v1/semantic-types/custom-only');
    });

    it('should handle complex apiUrl paths', () => {
      const endpoint = '/config';
      const apiUrl = '/api/v2/classifier';
      
      expect(buildApiUrl(endpoint, apiUrl)).toBe('/api/v2/classifier/config');
    });
  });

  describe('real-world scenarios', () => {
    it('should work with semantic type endpoints from config', () => {
      const apiUrl = '/api';
      
      expect(buildApiUrl(API_ENDPOINTS.SEMANTIC_TYPES.GET_ALL, apiUrl))
        .toBe('/api/semantic-types');
      expect(buildApiUrl(API_ENDPOINTS.SEMANTIC_TYPES.CREATE, apiUrl))
        .toBe('/api/semantic-types');
    });

    it('should work with dynamic endpoints', () => {
      const apiUrl = '/api';
      const region = 'us-west-2';
      
      expect(buildApiUrl(API_ENDPOINTS.AWS.GET_MODELS(region), apiUrl))
        .toBe('/api/aws/models/us-west-2');
    });

    it('should work with encoded semantic type endpoints', () => {
      const apiUrl = '/api';
      const semanticType = 'person/email';
      
      expect(buildApiUrl(API_ENDPOINTS.SEMANTIC_TYPES.UPDATE(semanticType), apiUrl))
        .toBe('/api/semantic-types/person%2Femail');
    });
  });
});