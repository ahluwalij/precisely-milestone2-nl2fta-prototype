package com.nl2fta.classifier.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.service.aws.AwsCredentialsService;
import com.nl2fta.classifier.service.storage.HybridCustomSemanticTypeRepository;
import com.nl2fta.classifier.service.vector.VectorIndexInitializationService;

@WebMvcTest(AwsCredentialsController.class)
@DisplayName("AWS Credentials Controller Tests")
public class AwsCredentialsControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AwsCredentialsService awsCredentialsService;

  @MockitoBean private HybridCustomSemanticTypeRepository hybridRepository;

  @MockitoBean private VectorIndexInitializationService vectorIndexService;

  @Nested
  @DisplayName("GET /api/aws/credentials/status")
  class GetStatusTests {

    @Test
    @DisplayName("Should return status when credentials are available")
    public void testGetStatusWithCredentials() throws Exception {
      when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
      when(awsCredentialsService.getRegion()).thenReturn("us-east-1");
      when(awsCredentialsService.getCurrentAccessKeyId()).thenReturn("test-key");
      when(awsCredentialsService.getCurrentSecretAccessKey()).thenReturn("test-secret");
      when(hybridRepository.getStorageStatus()).thenReturn("Using S3 storage");
      when(hybridRepository.isUsingS3Storage()).thenReturn(true);

      mockMvc
          .perform(get("/api/aws/credentials/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.credentials_available", is(true)))
          .andExpect(jsonPath("$.storage_type", is("S3")))
          .andExpect(jsonPath("$.storage_status", is("Using S3 storage")))
          .andExpect(jsonPath("$.can_access_s3", is(true)))
          .andExpect(jsonPath("$.region", is("us-east-1")))
          .andExpect(jsonPath("$.access_key_id").doesNotExist())
          .andExpect(jsonPath("$.secret_access_key").doesNotExist())
          .andExpect(jsonPath("$.message", containsString("AWS credentials are configured")));
    }

    @Test
    @DisplayName("Should return status when credentials are not available")
    public void testGetStatusWithoutCredentials() throws Exception {
      when(awsCredentialsService.areCredentialsAvailable()).thenReturn(false);
      when(awsCredentialsService.getRegion()).thenReturn(null);
      when(hybridRepository.getStorageStatus())
          .thenReturn("No AWS credentials - using file storage");
      when(hybridRepository.isUsingS3Storage()).thenReturn(false);

      mockMvc
          .perform(get("/api/aws/credentials/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.credentials_available", is(false)))
          .andExpect(jsonPath("$.storage_type", is("NONE")))
          .andExpect(jsonPath("$.storage_status", is("No AWS credentials - using file storage")))
          .andExpect(jsonPath("$.can_access_s3", is(false)))
          .andExpect(jsonPath("$.message", containsString("AWS credentials not configured")))
          .andExpect(jsonPath("$.access_key_id").doesNotExist())
          .andExpect(jsonPath("$.secret_access_key").doesNotExist());
    }

    @Test
    @DisplayName("Should handle credentials available but not using S3")
    public void testGetStatusWithCredentialsButNotUsingS3() throws Exception {
      when(awsCredentialsService.areCredentialsAvailable()).thenReturn(true);
      when(awsCredentialsService.getRegion()).thenReturn("eu-west-1");
      when(awsCredentialsService.getCurrentAccessKeyId()).thenReturn("akia456");
      when(awsCredentialsService.getCurrentSecretAccessKey()).thenReturn("secret456");
      when(hybridRepository.getStorageStatus()).thenReturn("File storage preferred");
      when(hybridRepository.isUsingS3Storage()).thenReturn(false);

      mockMvc
          .perform(get("/api/aws/credentials/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.credentials_available", is(true)))
          .andExpect(jsonPath("$.storage_type", is("NONE")))
          .andExpect(jsonPath("$.storage_status", is("File storage preferred")))
          .andExpect(jsonPath("$.can_access_s3", is(true)))
          .andExpect(jsonPath("$.region", is("eu-west-1")))
          .andExpect(jsonPath("$.access_key_id").doesNotExist())
          .andExpect(jsonPath("$.secret_access_key").doesNotExist());
    }
  }

  @Nested
  @DisplayName("GET /api/aws/credentials/indexing-status")
  class GetIndexingStatusTests {

    @Test
    @DisplayName("Should return indexing status when indexing is in progress")
    public void testGetIndexingStatusInProgress() throws Exception {
      when(vectorIndexService.isIndexing()).thenReturn(true);
      when(vectorIndexService.getTotalTypesToIndex()).thenReturn(100);
      when(vectorIndexService.getIndexedTypesCount()).thenReturn(45);

      mockMvc
          .perform(get("/api/aws/credentials/indexing-status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.indexing", is(true)))
          .andExpect(jsonPath("$.total_types", is(100)))
          .andExpect(jsonPath("$.indexed_types", is(45)))
          .andExpect(jsonPath("$.progress", is(0.45)));
    }

    @Test
    @DisplayName("Should return indexing status when indexing is complete")
    public void testGetIndexingStatusComplete() throws Exception {
      when(vectorIndexService.isIndexing()).thenReturn(false);
      when(vectorIndexService.getTotalTypesToIndex()).thenReturn(100);
      when(vectorIndexService.getIndexedTypesCount()).thenReturn(100);

      mockMvc
          .perform(get("/api/aws/credentials/indexing-status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.indexing", is(false)))
          .andExpect(jsonPath("$.total_types", is(100)))
          .andExpect(jsonPath("$.indexed_types", is(100)))
          .andExpect(jsonPath("$.progress", is(1.0)));
    }

    @Test
    @DisplayName("Should return indexing status when no types to index")
    public void testGetIndexingStatusNoTypes() throws Exception {
      when(vectorIndexService.isIndexing()).thenReturn(false);
      when(vectorIndexService.getTotalTypesToIndex()).thenReturn(0);
      when(vectorIndexService.getIndexedTypesCount()).thenReturn(0);

      mockMvc
          .perform(get("/api/aws/credentials/indexing-status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.indexing", is(false)))
          .andExpect(jsonPath("$.total_types", is(0)))
          .andExpect(jsonPath("$.indexed_types", is(0)))
          .andExpect(jsonPath("$.progress", is(0.0)));
    }
  }
}
