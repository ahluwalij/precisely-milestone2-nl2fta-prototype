package com.nl2fta.classifier.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Unit tests for {@link GlobalExceptionHandler}. Tests all exception handling methods and error
 * response building.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Unit Tests")
public class GlobalExceptionHandlerTest {

  @Mock private WebRequest webRequest;

  @Mock private BindingResult bindingResult;

  @InjectMocks private GlobalExceptionHandler exceptionHandler;

  @BeforeEach
  void setUp() {
    // Set default test environment values
    ReflectionTestUtils.setField(exceptionHandler, "environment", "test");
    ReflectionTestUtils.setField(exceptionHandler, "debugEnabled", false);
  }

  @Nested
  @DisplayName("IllegalArgumentException Handling Tests")
  class IllegalArgumentExceptionHandlingTests {

    @Test
    @DisplayName("Should handle IllegalArgumentException with proper response")
    void shouldHandleIllegalArgumentException() {
      // Given
      String errorMessage = "Invalid argument provided";
      IllegalArgumentException exception = new IllegalArgumentException(errorMessage);
      when(webRequest.getDescription(false)).thenReturn("uri=/api/test");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleIllegalArgumentException(exception, webRequest);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo(400);
      assertThat(response.getBody().getMessage()).isEqualTo(errorMessage);
      assertThat(response.getBody().getError()).isEqualTo("Bad Request");
      assertThat(response.getBody().getPath()).isEqualTo("/api/test");
      assertThat(response.getBody().getTimestamp()).isNotNull();
      assertThat(response.getBody().getTimestamp()).isBefore(LocalDateTime.now().plusSeconds(1));
    }

    @Test
    @DisplayName("Should handle IllegalArgumentException with null message")
    void shouldHandleIllegalArgumentExceptionWithNullMessage() {
      // Given
      IllegalArgumentException exception = new IllegalArgumentException((String) null);
      when(webRequest.getDescription(false)).thenReturn("uri=/api/null-test");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleIllegalArgumentException(exception, webRequest);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().getMessage()).isNull();
    }
  }

  @Nested
  @DisplayName("MethodArgumentNotValidException Handling Tests")
  class MethodArgumentNotValidExceptionHandlingTests {

    @Test
    @DisplayName("Should handle validation exception with field errors")
    void shouldHandleValidationExceptionWithFieldErrors() {
      // Given
      FieldError fieldError1 = new FieldError("user", "email", "Email is required");
      FieldError fieldError2 = new FieldError("user", "age", "Age must be positive");

      MethodArgumentNotValidException exception =
          new MethodArgumentNotValidException(
              org.springframework.core.MethodParameter.forExecutable(
                  this.getClass().getMethods()[0], 0),
              bindingResult);
      when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError1, fieldError2));
      when(webRequest.getDescription(false)).thenReturn("uri=/api/users");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleValidationExceptions(exception, webRequest);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo(400);
      assertThat(response.getBody().getError()).isEqualTo("Validation Failed");
      assertThat(response.getBody().getMessage()).isEqualTo("Invalid request data");
      assertThat(response.getBody().getValidationErrors()).hasSize(2);
      assertThat(response.getBody().getValidationErrors())
          .containsEntry("email", "Email is required");
      assertThat(response.getBody().getValidationErrors())
          .containsEntry("age", "Age must be positive");
    }

    @Test
    @DisplayName("Should handle validation exception with no errors")
    void shouldHandleValidationExceptionWithNoErrors() {
      // Given
      MethodArgumentNotValidException exception =
          new MethodArgumentNotValidException(
              org.springframework.core.MethodParameter.forExecutable(
                  this.getClass().getMethods()[0], 0),
              bindingResult);
      when(bindingResult.getAllErrors()).thenReturn(List.of());
      when(webRequest.getDescription(false)).thenReturn("uri=/api/test");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleValidationExceptions(exception, webRequest);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().getValidationErrors()).isEmpty();
    }
  }

  @Nested
  @DisplayName("ResourceNotFoundException Handling Tests")
  class ResourceNotFoundExceptionHandlingTests {

    @Test
    @DisplayName("Should handle ResourceNotFoundException with proper response")
    void shouldHandleResourceNotFoundException() {
      // Given
      String errorMessage = "User not found with id: 123";
      ResourceNotFoundException exception = new ResourceNotFoundException(errorMessage);
      when(webRequest.getDescription(false)).thenReturn("uri=/api/users/123");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleResourceNotFoundException(exception, webRequest);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo(404);
      assertThat(response.getBody().getMessage()).isEqualTo(errorMessage);
      assertThat(response.getBody().getError()).isEqualTo("Not Found");
      assertThat(response.getBody().getPath()).isEqualTo("/api/users/123");
    }

    @Test
    @DisplayName("Should handle ResourceNotFoundException with formatted message")
    void shouldHandleResourceNotFoundExceptionWithFormattedMessage() {
      // Given
      ResourceNotFoundException exception = new ResourceNotFoundException("Product", "id", 456);
      when(webRequest.getDescription(false)).thenReturn("uri=/api/products/456");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleResourceNotFoundException(exception, webRequest);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody().getMessage()).isEqualTo("Product not found with id : '456'");
    }
  }

  @Nested
  @DisplayName("NoResourceFoundException Handling Tests")
  class NoResourceFoundExceptionHandlingTests {

    @Test
    @DisplayName("Should handle NoResourceFoundException with regular path")
    void shouldHandleNoResourceFoundException() {
      // Given
      NoResourceFoundException exception =
          new NoResourceFoundException(HttpMethod.POST, "/api/nonexistent");
      when(webRequest.getDescription(false)).thenReturn("uri=/api/nonexistent");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleNotFoundExceptions(exception, webRequest);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo(404);
      assertThat(response.getBody().getMessage()).isEqualTo("The requested resource was not found");
      assertThat(response.getBody().getError()).isEqualTo("Not Found");
    }

    @Test
    @DisplayName("Should handle NoResourceFoundException with ignored path")
    void shouldHandleNoResourceFoundExceptionWithIgnoredPath() {
      // Given
      NoResourceFoundException exception =
          new NoResourceFoundException(HttpMethod.GET, "/favicon.ico");
      when(webRequest.getDescription(false)).thenReturn("uri=/favicon.ico");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleNotFoundExceptions(exception, webRequest);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody().getMessage()).isEqualTo("Resource not found");
    }

    @Test
    @DisplayName("Should handle NoResourceFoundException with chrome devtools path")
    void shouldHandleNoResourceFoundExceptionWithDevToolsPath() {
      // Given
      NoResourceFoundException exception =
          new NoResourceFoundException(
              HttpMethod.GET, "/.well-known/appspecific/com.chrome.devtools.json");
      when(webRequest.getDescription(false))
          .thenReturn("uri=/.well-known/appspecific/com.chrome.devtools.json");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleNotFoundExceptions(exception, webRequest);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody().getMessage()).isEqualTo("Resource not found");
    }

    @Test
    @DisplayName("Should handle NoHandlerFoundException with proper message formatting")
    void shouldHandleNoHandlerFoundException() {
      // Given
      NoHandlerFoundException exception =
          new NoHandlerFoundException(
              "POST", "/api/invalid", new org.springframework.http.HttpHeaders());
      when(webRequest.getDescription(false)).thenReturn("uri=/api/invalid");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleNotFoundExceptions(exception, webRequest);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody().getMessage()).isEqualTo("No endpoint POST /api/invalid");
    }
  }

  @Nested
  @DisplayName("Global Exception Handling Tests")
  class GlobalExceptionHandlingTests {

    @Test
    @DisplayName("Should handle general exception in production environment")
    void shouldHandleGeneralExceptionInProduction() {
      // Given
      ReflectionTestUtils.setField(exceptionHandler, "environment", "production");
      ReflectionTestUtils.setField(exceptionHandler, "debugEnabled", false);

      RuntimeException exception = new RuntimeException("Unexpected error");
      when(webRequest.getDescription(false)).thenReturn("uri=/api/error");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleGlobalException(exception, webRequest);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo(500);
      assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
      assertThat(response.getBody().getError()).isEqualTo("Internal Server Error");
      assertThat(response.getBody().getDebugMessage()).isNull(); // No debug info in production
    }

    @Test
    @DisplayName("Should handle general exception in development with debug enabled")
    void shouldHandleGeneralExceptionInDevelopmentWithDebug() {
      // Given
      ReflectionTestUtils.setField(exceptionHandler, "environment", "development");
      ReflectionTestUtils.setField(exceptionHandler, "debugEnabled", true);

      RuntimeException exception = new RuntimeException("Debug error message");
      when(webRequest.getDescription(false)).thenReturn("uri=/api/debug");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleGlobalException(exception, webRequest);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody().getDebugMessage()).isEqualTo("Debug error message");
    }

    @Test
    @DisplayName("Should handle general exception in test environment with debug enabled")
    void shouldHandleGeneralExceptionInTestWithDebug() {
      // Given
      ReflectionTestUtils.setField(exceptionHandler, "environment", "test");
      ReflectionTestUtils.setField(exceptionHandler, "debugEnabled", true);

      IllegalStateException exception = new IllegalStateException("Test state error");
      when(webRequest.getDescription(false)).thenReturn("uri=/api/test");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleGlobalException(exception, webRequest);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody().getDebugMessage()).isEqualTo("Test state error");
    }

    @Test
    @DisplayName("Should handle general exception with debug disabled")
    void shouldHandleGeneralExceptionWithDebugDisabled() {
      // Given
      ReflectionTestUtils.setField(exceptionHandler, "environment", "development");
      ReflectionTestUtils.setField(exceptionHandler, "debugEnabled", false);

      NullPointerException exception = new NullPointerException("Null pointer");
      when(webRequest.getDescription(false)).thenReturn("uri=/api/null");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleGlobalException(exception, webRequest);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody().getDebugMessage()).isNull();
    }
  }

  @Nested
  @DisplayName("Helper Method Tests")
  class HelperMethodTests {

    @Test
    @DisplayName("Should extract path from web request correctly")
    void shouldExtractPathFromWebRequest() {
      // Given
      when(webRequest.getDescription(false)).thenReturn("uri=/api/users/123");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleIllegalArgumentException(
              new IllegalArgumentException("test"), webRequest);

      // Then
      assertThat(response.getBody().getPath()).isEqualTo("/api/users/123");
    }

    @Test
    @DisplayName("Should handle path extraction with complex URI")
    void shouldHandlePathExtractionWithComplexUri() {
      // Given
      when(webRequest.getDescription(false))
          .thenReturn("uri=/api/v1/users/123?param=value&other=test");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleIllegalArgumentException(
              new IllegalArgumentException("test"), webRequest);

      // Then
      assertThat(response.getBody().getPath())
          .isEqualTo("/api/v1/users/123?param=value&other=test");
    }

    @Test
    @DisplayName("Should handle path extraction with no uri prefix")
    void shouldHandlePathExtractionWithNoUriPrefix() {
      // Given
      when(webRequest.getDescription(false)).thenReturn("/api/test");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleIllegalArgumentException(
              new IllegalArgumentException("test"), webRequest);

      // Then
      assertThat(response.getBody().getPath()).isEqualTo("/api/test");
    }
  }

  @Nested
  @DisplayName("ErrorResponse Builder Tests")
  class ErrorResponseBuilderTests {

    @Test
    @DisplayName("Should build complete error response")
    void shouldBuildCompleteErrorResponse() {
      // Given
      String message = "Test error message";
      IllegalArgumentException exception = new IllegalArgumentException(message);
      when(webRequest.getDescription(false)).thenReturn("uri=/api/complete");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleIllegalArgumentException(exception, webRequest);

      // Then
      GlobalExceptionHandler.ErrorResponse errorResponse = response.getBody();
      assertThat(errorResponse).isNotNull();
      assertThat(errorResponse.getTimestamp()).isNotNull();
      assertThat(errorResponse.getStatus()).isEqualTo(400);
      assertThat(errorResponse.getError()).isEqualTo("Bad Request");
      assertThat(errorResponse.getMessage()).isEqualTo(message);
      assertThat(errorResponse.getPath()).isEqualTo("/api/complete");
      assertThat(errorResponse.getValidationErrors()).isNull();
      assertThat(errorResponse.getDebugMessage()).isNull();
    }

    @Test
    @DisplayName("Should build error response with validation errors")
    void shouldBuildErrorResponseWithValidationErrors() {
      // Given
      FieldError fieldError = new FieldError("user", "name", "Name is required");
      MethodArgumentNotValidException exception =
          new MethodArgumentNotValidException(
              org.springframework.core.MethodParameter.forExecutable(
                  this.getClass().getMethods()[0], 0),
              bindingResult);
      when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));
      when(webRequest.getDescription(false)).thenReturn("uri=/api/validation");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleValidationExceptions(exception, webRequest);

      // Then
      assertThat(response.getBody()).isNotNull();
      GlobalExceptionHandler.ErrorResponse errorResponse = response.getBody();
      assertThat(errorResponse).isNotNull();
      assertThat(errorResponse.getValidationErrors()).isNotNull();
      assertThat(errorResponse.getValidationErrors()).hasSize(1);
      assertThat(errorResponse.getValidationErrors()).containsEntry("name", "Name is required");
    }

    @Test
    @DisplayName("Should build error response with debug message when enabled")
    void shouldBuildErrorResponseWithDebugMessage() {
      // Given
      ReflectionTestUtils.setField(exceptionHandler, "environment", "development");
      ReflectionTestUtils.setField(exceptionHandler, "debugEnabled", true);

      RuntimeException exception = new RuntimeException("Debug information");
      when(webRequest.getDescription(false)).thenReturn("uri=/api/debug");

      // When
      ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
          exceptionHandler.handleGlobalException(exception, webRequest);

      // Then
      assertThat(response.getBody()).isNotNull();
      GlobalExceptionHandler.ErrorResponse errorResponse = response.getBody();
      assertThat(errorResponse).isNotNull();
      assertThat(errorResponse.getDebugMessage()).isEqualTo("Debug information");
    }
  }
}
