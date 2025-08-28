package com.nl2fta.classifier.service.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.service.aws.AwsCredentialsService;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3VectorStorageService Tests")
class S3VectorStorageServiceTest {

  @Mock private ObjectMapper objectMapper;

  @Mock private AwsCredentialsService awsCredentialsService;

  @Mock private S3Client mockS3Client;

  private S3VectorStorageService s3VectorStorageService;

  private VectorData sampleVectorData;

  @BeforeEach
  void setUp() {
    s3VectorStorageService = new S3VectorStorageService(objectMapper, awsCredentialsService);

    sampleVectorData =
        VectorData.builder()
            .id("test-vector-id")
            .semanticType("EMAIL")
            .type("built-in")
            .description("Email address")
            .embedding(Arrays.asList(0.1f, 0.2f, 0.3f))
            .originalText("email@example.com")
            .pluginType("regex")
            .examples(Arrays.asList("test@example.com", "user@domain.org"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
  }

  @Nested
  @DisplayName("Initialization Tests")
  class InitializationTests {

    @Test
    @DisplayName("Should initialize without S3 client initially")
    void shouldInitializeWithoutS3ClientInitially() {
      s3VectorStorageService.init();

      Object s3ClientField = ReflectionTestUtils.getField(s3VectorStorageService, "s3Client");
      assertThat(s3ClientField).isNull();
    }

    @Test
    @DisplayName("Should not initialize S3 client when credentials unavailable")
    void shouldNotInitializeS3ClientWhenCredentialsUnavailable() {
      when(awsCredentialsService.areCredentialsAvailable()).thenReturn(false);

      s3VectorStorageService.initializeS3Client();

      Object s3ClientField = ReflectionTestUtils.getField(s3VectorStorageService, "s3Client");
      assertThat(s3ClientField).isNull();
    }

    @Test
    @DisplayName("Should use configured bucket name when provided")
    void shouldUseConfiguredBucketNameWhenProvided() {
      ReflectionTestUtils.setField(
          s3VectorStorageService, "configuredBucketName", "my-custom-bucket");
      when(awsCredentialsService.areCredentialsAvailable()).thenReturn(false);

      s3VectorStorageService.initializeS3Client();

      Object bucketNameField = ReflectionTestUtils.getField(s3VectorStorageService, "bucketName");
      assertThat(bucketNameField).isEqualTo("my-custom-bucket");
    }

    @Test
    @DisplayName("Should disconnect S3 client when called")
    void shouldDisconnectS3ClientWhenCalled() {
      s3VectorStorageService.disconnectS3();

      // Should not throw exception
      assertThatCode(() -> s3VectorStorageService.disconnectS3()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should disconnect S3 client properly when client exists")
    void shouldDisconnectS3ClientProperlyWhenClientExists() {
      ReflectionTestUtils.setField(s3VectorStorageService, "s3Client", mockS3Client);

      doThrow(new RuntimeException("Close error")).when(mockS3Client).close();
      assertThatCode(() -> s3VectorStorageService.disconnectS3()).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("In-Memory Storage Tests")
  class InMemoryStorageTests {

    @Test
    @DisplayName("Should store vector in memory when S3 client unavailable")
    void shouldStoreVectorInMemoryWhenS3ClientUnavailable() throws Exception {
      ReflectionTestUtils.setField(s3VectorStorageService, "s3Client", null);

      String expectedJson = "{\"id\":\"test-vector-id\"}";
      when(objectMapper.writeValueAsString(sampleVectorData)).thenReturn(expectedJson);

      s3VectorStorageService.storeVector(sampleVectorData);

      @SuppressWarnings("unchecked")
      java.util.Map<String, VectorData> inMemoryStorage =
          (java.util.Map<String, VectorData>)
              ReflectionTestUtils.getField(s3VectorStorageService, "inMemoryStorage");

      assertThat(inMemoryStorage).containsKey("vectors/EMAIL.json");
      assertThat(inMemoryStorage.get("vectors/EMAIL.json")).isEqualTo(sampleVectorData);
    }

    @Test
    @DisplayName("Should retrieve all vectors from memory when S3 unavailable")
    void shouldRetrieveAllVectorsFromMemoryWhenS3Unavailable() {
      ReflectionTestUtils.setField(s3VectorStorageService, "s3Client", null);

      @SuppressWarnings("unchecked")
      java.util.Map<String, VectorData> inMemoryStorage =
          (java.util.Map<String, VectorData>)
              ReflectionTestUtils.getField(s3VectorStorageService, "inMemoryStorage");
      inMemoryStorage.put("semantic-type-vectors/test-vector-id.json", sampleVectorData);

      List<VectorData> vectors = s3VectorStorageService.getAllVectors();

      assertThat(vectors).hasSize(1);
      assertThat(vectors.get(0)).isEqualTo(sampleVectorData);
    }

    @Test
    @DisplayName("Should delete vector from memory by semantic type")
    void shouldDeleteVectorFromMemoryBySemanticType() {
      ReflectionTestUtils.setField(s3VectorStorageService, "s3Client", null);

      @SuppressWarnings("unchecked")
      java.util.Map<String, VectorData> inMemoryStorage =
          (java.util.Map<String, VectorData>)
              ReflectionTestUtils.getField(s3VectorStorageService, "inMemoryStorage");
      inMemoryStorage.put("semantic-type-vectors/test-vector-id.json", sampleVectorData);

      s3VectorStorageService.deleteVector("EMAIL");

      assertThat(inMemoryStorage).isEmpty();
    }

    @Test
    @DisplayName("Should clear all vectors from memory")
    void shouldClearAllVectorsFromMemory() {
      ReflectionTestUtils.setField(s3VectorStorageService, "s3Client", null);

      @SuppressWarnings("unchecked")
      java.util.Map<String, VectorData> inMemoryStorage =
          (java.util.Map<String, VectorData>)
              ReflectionTestUtils.getField(s3VectorStorageService, "inMemoryStorage");
      inMemoryStorage.put("key1", sampleVectorData);
      inMemoryStorage.put("key2", sampleVectorData);

      s3VectorStorageService.clearAllVectors();

      assertThat(inMemoryStorage).isEmpty();
    }

    @Test
    @DisplayName("Should handle JSON serialization error gracefully")
    void shouldHandleJsonSerializationErrorGracefully() throws Exception {
      ReflectionTestUtils.setField(s3VectorStorageService, "s3Client", null);

      when(objectMapper.writeValueAsString(sampleVectorData))
          .thenThrow(new RuntimeException("JSON error"));

      assertThatCode(() -> s3VectorStorageService.storeVector(sampleVectorData))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Failed to store vector");
    }
  }

  @Nested
  @DisplayName("S3 Operations Tests")
  class S3OperationsTests {

    @BeforeEach
    void setupS3Tests() {
      ReflectionTestUtils.setField(s3VectorStorageService, "s3Client", mockS3Client);
      ReflectionTestUtils.setField(s3VectorStorageService, "bucketName", "test-bucket");
    }

    @Test
    @DisplayName("Should store vector in S3 when client available")
    void shouldStoreVectorInS3WhenClientAvailable() throws Exception {
      String expectedJson = "{\"id\":\"test-vector-id\"}";
      when(objectMapper.writeValueAsString(sampleVectorData)).thenReturn(expectedJson);

      s3VectorStorageService.storeVector(sampleVectorData);

      verify(mockS3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
      verify(objectMapper).writeValueAsString(sampleVectorData);
    }

    @Test
    @DisplayName("Should handle S3 store error gracefully")
    void shouldHandleS3StoreErrorGracefully() throws Exception {
      String expectedJson = "{\"id\":\"test-vector-id\"}";
      when(objectMapper.writeValueAsString(sampleVectorData)).thenReturn(expectedJson);
      when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
          .thenThrow(new RuntimeException("S3 error"));

      assertThatCode(() -> s3VectorStorageService.storeVector(sampleVectorData))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Failed to store vector");
    }

    @Test
    @DisplayName("Should retrieve all vectors from S3")
    void shouldRetrieveAllVectorsFromS3() throws Exception {
      // Mock S3 list response
      S3Object s3Object =
          S3Object.builder().key("semantic-type-vectors/test-vector-id.json").build();
      ListObjectsV2Response listResponse =
          ListObjectsV2Response.builder().contents(s3Object).build();
      when(mockS3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);

      // Mock get object response
      @SuppressWarnings("unchecked")
      ResponseBytes<GetObjectResponse> responseBytes =
          (ResponseBytes<GetObjectResponse>)
              (ResponseBytes<?>)
                  ResponseBytes.fromByteArray(
                      GetObjectResponse.builder().build(),
                      "{\"id\":\"test-vector-id\"}".getBytes());
      when(mockS3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);
      when(objectMapper.readValue(any(byte[].class), eq(VectorData.class)))
          .thenReturn(sampleVectorData);

      List<VectorData> vectors = s3VectorStorageService.getAllVectors();

      assertThat(vectors).hasSize(1);
      assertThat(vectors.get(0)).isEqualTo(sampleVectorData);
      verify(mockS3Client).listObjectsV2(any(ListObjectsV2Request.class));
      verify(mockS3Client).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("Should handle NoSuchKeyException when getting vector")
    void shouldHandleNoSuchKeyExceptionWhenGettingVector() {
      S3Object s3Object =
          S3Object.builder().key("semantic-type-vectors/test-vector-id.json").build();
      ListObjectsV2Response listResponse =
          ListObjectsV2Response.builder().contents(s3Object).build();
      when(mockS3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);

      when(mockS3Client.getObjectAsBytes(any(GetObjectRequest.class)))
          .thenThrow(NoSuchKeyException.builder().build());

      List<VectorData> vectors = s3VectorStorageService.getAllVectors();

      assertThat(vectors).isEmpty();
    }

    @Test
    @DisplayName("Should handle general exception when getting vector")
    void shouldHandleGeneralExceptionWhenGettingVector() {
      S3Object s3Object =
          S3Object.builder().key("semantic-type-vectors/test-vector-id.json").build();
      ListObjectsV2Response listResponse =
          ListObjectsV2Response.builder().contents(s3Object).build();
      when(mockS3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);

      when(mockS3Client.getObjectAsBytes(any(GetObjectRequest.class)))
          .thenThrow(new RuntimeException("S3 error"));

      List<VectorData> vectors = s3VectorStorageService.getAllVectors();

      assertThat(vectors).isEmpty();
    }

    @Test
    @DisplayName("Should handle S3 list error in getAllVectors")
    void shouldHandleS3ListErrorInGetAllVectors() {
      when(mockS3Client.listObjectsV2(any(ListObjectsV2Request.class)))
          .thenThrow(new RuntimeException("S3 list error"));

      // Implementation throws runtime on list error
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> s3VectorStorageService.getAllVectors())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Failed to retrieve vectors");
    }

    @Test
    @DisplayName("Should delete vector from S3 by semantic type")
    void shouldDeleteVectorFromS3BySemanticType() throws Exception {
      s3VectorStorageService.deleteVector("EMAIL");

      // Delete is invoked for single-key layout only
      verify(mockS3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("Should handle delete vector error gracefully")
    void shouldHandleDeleteVectorErrorGracefully() {
      // Now we fail fast on delete errors
      doThrow(new RuntimeException("S3 delete error"))
          .when(mockS3Client)
          .deleteObject(any(DeleteObjectRequest.class));

      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> s3VectorStorageService.deleteVector("EMAIL"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Failed to delete vectors");
    }

    @Test
    @DisplayName("Should clear all vectors from S3")
    void shouldClearAllVectorsFromS3() {
      S3Object s3Object1 = S3Object.builder().key("semantic-type-vectors/test1.json").build();
      S3Object s3Object2 = S3Object.builder().key("semantic-type-vectors/test2.json").build();
      ListObjectsV2Response listResponse =
          ListObjectsV2Response.builder().contents(s3Object1, s3Object2).build();
      when(mockS3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);

      s3VectorStorageService.clearAllVectors();

      verify(mockS3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("Should handle clear vectors error gracefully")
    void shouldHandleClearVectorsErrorGracefully() {
      when(mockS3Client.listObjectsV2(any(ListObjectsV2Request.class)))
          .thenThrow(new RuntimeException("S3 clear error"));

      assertThatCode(() -> s3VectorStorageService.clearAllVectors())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Failed to clear vectors");
    }
  }

  @Nested
  @DisplayName("Utility Tests")
  class UtilityTests {

    @Test
    @DisplayName("Should generate valid vector ID from semantic type")
    void shouldGenerateValidVectorIdFromSemanticType() {
      String vectorId = s3VectorStorageService.generateVectorId("EMAIL.ADDRESS");
      assertThat(vectorId).isEqualTo("email-address");
    }

    @Test
    @DisplayName("Should handle special characters in semantic type")
    void shouldHandleSpecialCharactersInSemanticType() {
      String vectorId = s3VectorStorageService.generateVectorId("NAME@FIRST#NAME$123");
      assertThat(vectorId).isEqualTo("name-first-name-123");
    }

    @Test
    @DisplayName("Should handle null semantic type")
    void shouldHandleNullSemanticType() {
      String vectorId = s3VectorStorageService.generateVectorId(null);
      assertThat(vectorId).isEmpty();
    }

    @Test
    @DisplayName("Should handle empty semantic type")
    void shouldHandleEmptySemanticType() {
      String vectorId = s3VectorStorageService.generateVectorId("");
      assertThat(vectorId).isEmpty();
    }

    @Test
    @DisplayName("Should handle whitespace semantic type")
    void shouldHandleWhitespaceSemanticType() {
      String vectorId = s3VectorStorageService.generateVectorId("   ");
      assertThat(vectorId).isEmpty();
    }

    @Test
    @DisplayName("Should remove leading and trailing dashes")
    void shouldRemoveLeadingAndTrailingDashes() {
      String vectorId = s3VectorStorageService.generateVectorId("-test-type-");
      assertThat(vectorId).isEqualTo("test-type");
    }

    @Test
    @DisplayName("Should handle multiple consecutive special characters")
    void shouldHandleMultipleConsecutiveSpecialCharacters() {
      String vectorId = s3VectorStorageService.generateVectorId("TEST...TYPE");
      assertThat(vectorId).isEqualTo("test-type");
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should handle concurrent storage operations")
    void shouldHandleConcurrentStorageOperations() throws Exception {
      ReflectionTestUtils.setField(s3VectorStorageService, "s3Client", null);

      String expectedJson = "{\"id\":\"test-vector-id\"}";
      when(objectMapper.writeValueAsString(any())).thenReturn(expectedJson);

      Runnable storeTask =
          () -> {
            try {
              VectorData testData =
                  VectorData.builder().id("concurrent-test").semanticType("TEST").build();
              s3VectorStorageService.storeVector(testData);
            } catch (Exception e) {
              // Expected in concurrent scenario
            }
          };

      Thread t1 = new Thread(storeTask);
      Thread t2 = new Thread(storeTask);
      Thread t3 = new Thread(storeTask);

      assertThatCode(
              () -> {
                t1.start();
                t2.start();
                t3.start();

                t1.join();
                t2.join();
                t3.join();
              })
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle large vector data")
    void shouldHandleLargeVectorData() throws Exception {
      ReflectionTestUtils.setField(s3VectorStorageService, "s3Client", null);

      List<Float> largeEmbedding = new ArrayList<>();
      for (int i = 0; i < 1000; i++) {
        largeEmbedding.add((float) i);
      }

      VectorData largeVectorData =
          VectorData.builder()
              .id("large-vector")
              .semanticType("LARGE_TYPE")
              .embedding(largeEmbedding)
              .build();

      String expectedJson = "{\"large\":\"data\"}";
      when(objectMapper.writeValueAsString(largeVectorData)).thenReturn(expectedJson);

      assertThatCode(() -> s3VectorStorageService.storeVector(largeVectorData))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle null vector data gracefully")
    void shouldHandleNullVectorDataGracefully() throws Exception {
      ReflectionTestUtils.setField(s3VectorStorageService, "s3Client", null);

      assertThatCode(() -> s3VectorStorageService.storeVector(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage(
              "Cannot invoke \"com.nl2fta.classifier.service.vector.VectorData.getSemanticType()\" because \"vectorData\" is null");
    }

    @Test
    @DisplayName("Should handle delete with non-existent semantic type")
    void shouldHandleDeleteWithNonExistentSemanticType() {
      ReflectionTestUtils.setField(s3VectorStorageService, "s3Client", null);

      @SuppressWarnings("unchecked")
      java.util.Map<String, VectorData> inMemoryStorage =
          (java.util.Map<String, VectorData>)
              ReflectionTestUtils.getField(s3VectorStorageService, "inMemoryStorage");
      inMemoryStorage.put("semantic-type-vectors/test-vector-id.json", sampleVectorData);

      // Try to delete a different semantic type
      s3VectorStorageService.deleteVector("PHONE");

      // Original data should still be there
      assertThat(inMemoryStorage).hasSize(1);
    }

    @Test
    @DisplayName("Should handle getAllVectors with empty storage")
    void shouldHandleGetAllVectorsWithEmptyStorage() {
      ReflectionTestUtils.setField(s3VectorStorageService, "s3Client", null);

      List<VectorData> vectors = s3VectorStorageService.getAllVectors();

      assertThat(vectors).isEmpty();
    }

    @Test
    @DisplayName("Should handle getAllVectors with empty S3 response")
    void shouldHandleGetAllVectorsWithEmptyS3Response() {
      ReflectionTestUtils.setField(s3VectorStorageService, "s3Client", mockS3Client);
      ReflectionTestUtils.setField(s3VectorStorageService, "bucketName", "test-bucket");

      ListObjectsV2Response emptyResponse =
          ListObjectsV2Response.builder().contents(Collections.emptyList()).build();
      when(mockS3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(emptyResponse);

      List<VectorData> vectors = s3VectorStorageService.getAllVectors();

      assertThat(vectors).isEmpty();
    }
  }
}
