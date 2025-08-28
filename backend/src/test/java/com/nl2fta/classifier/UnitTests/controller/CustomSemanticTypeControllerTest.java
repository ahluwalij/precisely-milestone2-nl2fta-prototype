package com.nl2fta.classifier.controller.semantic_type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.fixtures.TestFixtures;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;

/**
 * Comprehensive unit tests for CustomSemanticTypeController. Tests all CRUD operations, validation,
 * error handling, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Custom Semantic Type Controller Tests")
class CustomSemanticTypeControllerTest {

  @Mock private CustomSemanticTypeService mockCustomSemanticTypeService;

  @InjectMocks private CustomSemanticTypeController controller;

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();
  }

  @Nested
  @DisplayName("Get All Custom Types Tests")
  class GetAllCustomTypesTests {

    @Test
    @DisplayName("Should successfully return all custom types")
    void shouldSuccessfullyReturnAllCustomTypes() throws IOException {
      // Given
      List<CustomSemanticType> expectedTypes =
          List.of(
              TestFixtures.createValidCustomSemanticType(),
              TestFixtures.createListTypeCustomSemanticType());

      when(mockCustomSemanticTypeService.getAllCustomTypes()).thenReturn(expectedTypes);

      // When
      ResponseEntity<List<CustomSemanticType>> response = controller.getAllCustomTypes();

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody()).hasSize(2);
      assertThat(response.getBody()).containsExactlyElementsOf(expectedTypes);

      verify(mockCustomSemanticTypeService, times(1)).getAllCustomTypes();
      verifyNoMoreInteractions(mockCustomSemanticTypeService);
    }

    @Test
    @DisplayName("Should return empty list when no custom types exist")
    void shouldReturnEmptyListWhenNoCustomTypesExist() throws IOException {
      // Given
      when(mockCustomSemanticTypeService.getAllCustomTypes()).thenReturn(Collections.emptyList());

      // When
      ResponseEntity<List<CustomSemanticType>> response = controller.getAllCustomTypes();

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody()).isEmpty();

      verify(mockCustomSemanticTypeService, times(1)).getAllCustomTypes();
    }

    @Test
    @DisplayName("Should handle IOException from service gracefully")
    void shouldHandleIOExceptionFromServiceGracefully() throws IOException {
      // Given
      when(mockCustomSemanticTypeService.getAllCustomTypes())
          .thenThrow(new IOException("Failed to load semantic types"));

      // When & Then
      assertThatThrownBy(() -> controller.getAllCustomTypes())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Failed to load semantic types")
          .hasCauseInstanceOf(IOException.class);

      verify(mockCustomSemanticTypeService, times(1)).getAllCustomTypes();
    }

    @Test
    @DisplayName("Should handle service exception gracefully")
    void shouldHandleServiceExceptionGracefully() throws IOException {
      // Given
      when(mockCustomSemanticTypeService.getAllCustomTypes())
          .thenThrow(new RuntimeException("Service unavailable"));

      // When & Then
      assertThatThrownBy(() -> controller.getAllCustomTypes())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Service unavailable");

      verify(mockCustomSemanticTypeService, times(1)).getAllCustomTypes();
    }

    @Test
    @DisplayName("Should handle large number of custom types efficiently")
    void shouldHandleLargeNumberOfCustomTypesEfficiently() throws IOException {
      // Given
      List<CustomSemanticType> largeTypesList = new ArrayList<>(1000);
      for (int i = 0; i < 1000; i++) {
        CustomSemanticType type = TestFixtures.createValidCustomSemanticType();
        type.setSemanticType("TYPE_" + i);
        largeTypesList.add(type);
      }

      when(mockCustomSemanticTypeService.getAllCustomTypes()).thenReturn(largeTypesList);

      // When
      long startTime = System.currentTimeMillis();
      ResponseEntity<List<CustomSemanticType>> response = controller.getAllCustomTypes();
      long endTime = System.currentTimeMillis();

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).hasSize(1000);
      assertThat(endTime - startTime).isLessThan(2000); // Should complete within 2 seconds
    }
  }

  @Nested
  @DisplayName("Get Custom Types Only Tests")
  class GetCustomTypesOnlyTests {

    @Test
    @DisplayName("Should successfully return only custom types")
    void shouldSuccessfullyReturnOnlyCustomTypes() {
      // Given
      List<CustomSemanticType> customTypesOnly =
          List.of(TestFixtures.createValidCustomSemanticType());

      when(mockCustomSemanticTypeService.getCustomTypesOnly()).thenReturn(customTypesOnly);

      // When
      ResponseEntity<List<CustomSemanticType>> response = controller.getCustomTypesOnly();

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody()).hasSize(1);
      assertThat(response.getBody()).containsExactlyElementsOf(customTypesOnly);

      verify(mockCustomSemanticTypeService, times(1)).getCustomTypesOnly();
      verifyNoMoreInteractions(mockCustomSemanticTypeService);
    }

    @Test
    @DisplayName("Should return empty list when no user-defined types exist")
    void shouldReturnEmptyListWhenNoUserDefinedTypesExist() {
      // Given
      when(mockCustomSemanticTypeService.getCustomTypesOnly()).thenReturn(Collections.emptyList());

      // When
      ResponseEntity<List<CustomSemanticType>> response = controller.getCustomTypesOnly();

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody()).isEmpty();

      verify(mockCustomSemanticTypeService, times(1)).getCustomTypesOnly();
    }

    @Test
    @DisplayName("Should handle service exception for custom types only")
    void shouldHandleServiceExceptionForCustomTypesOnly() {
      // Given
      when(mockCustomSemanticTypeService.getCustomTypesOnly())
          .thenThrow(new RuntimeException("Repository error"));

      // When & Then
      assertThatThrownBy(() -> controller.getCustomTypesOnly())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Repository error");

      verify(mockCustomSemanticTypeService, times(1)).getCustomTypesOnly();
    }
  }

  @Nested
  @DisplayName("Add Custom Semantic Type Tests")
  class AddCustomSemanticTypeTests {

    @Test
    @DisplayName("Should successfully add valid custom semantic type")
    void shouldSuccessfullyAddValidCustomSemanticType() {
      // Given
      CustomSemanticType inputType = TestFixtures.createValidCustomSemanticType();
      CustomSemanticType returnedType = TestFixtures.createValidCustomSemanticType();

      when(mockCustomSemanticTypeService.addCustomType(inputType)).thenReturn(returnedType);

      // When
      ResponseEntity<CustomSemanticType> response = controller.addCustomSemanticType(inputType);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody()).isEqualTo(returnedType);
      assertThat(response.getBody().getSemanticType()).isEqualTo("EMPLOYEE_ID");

      verify(mockCustomSemanticTypeService, times(1)).addCustomType(inputType);
      verifyNoMoreInteractions(mockCustomSemanticTypeService);
    }

    @Test
    @DisplayName("Should successfully add list type custom semantic type")
    void shouldSuccessfullyAddListTypeCustomSemanticType() {
      // Given
      CustomSemanticType listType = TestFixtures.createListTypeCustomSemanticType();

      when(mockCustomSemanticTypeService.addCustomType(listType)).thenReturn(listType);

      // When
      ResponseEntity<CustomSemanticType> response = controller.addCustomSemanticType(listType);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getSemanticType()).isEqualTo("USER_STATUS");
      assertThat(response.getBody().getPluginType()).isEqualTo("list");

      verify(mockCustomSemanticTypeService, times(1)).addCustomType(listType);
    }

    @Test
    @DisplayName("Should return bad request for invalid semantic type")
    void shouldReturnBadRequestForInvalidSemanticType() {
      // Given
      CustomSemanticType invalidType = TestFixtures.createInvalidCustomSemanticType();

      when(mockCustomSemanticTypeService.addCustomType(invalidType))
          .thenThrow(TestFixtures.createValidationException());

      // When
      ResponseEntity<CustomSemanticType> response = controller.addCustomSemanticType(invalidType);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

      verify(mockCustomSemanticTypeService, times(1)).addCustomType(invalidType);
    }

    @Test
    @DisplayName("Should return conflict when semantic type already exists")
    void shouldReturnConflictWhenSemanticTypeAlreadyExists() {
      // Given
      CustomSemanticType existingType = TestFixtures.createValidCustomSemanticType();

      when(mockCustomSemanticTypeService.addCustomType(existingType))
          .thenThrow(
              new IllegalArgumentException(
                  "Type 'EMPLOYEE_ID' already exists. Use a different name."));

      // When
      ResponseEntity<CustomSemanticType> response = controller.addCustomSemanticType(existingType);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

      verify(mockCustomSemanticTypeService, times(1)).addCustomType(existingType);
    }

    @Test
    @DisplayName("Should handle service exception during add operation")
    void shouldHandleServiceExceptionDuringAddOperation() {
      // Given
      CustomSemanticType validType = TestFixtures.createValidCustomSemanticType();

      when(mockCustomSemanticTypeService.addCustomType(validType))
          .thenThrow(new RuntimeException("Database connection failed"));

      // When & Then
      assertThatThrownBy(() -> controller.addCustomSemanticType(validType))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Database connection failed");

      verify(mockCustomSemanticTypeService, times(1)).addCustomType(validType);
    }

    @Test
    @DisplayName("Should handle null custom type gracefully")
    void shouldHandleNullCustomTypeGracefully() {
      // Given
      when(mockCustomSemanticTypeService.addCustomType(null))
          .thenThrow(new IllegalArgumentException("Custom type cannot be null"));

      // When
      ResponseEntity<CustomSemanticType> response = controller.addCustomSemanticType(null);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

      verify(mockCustomSemanticTypeService, times(1)).addCustomType(null);
    }
  }

  @Nested
  @DisplayName("Update Custom Semantic Type Tests")
  class UpdateCustomSemanticTypeTests {

    @Test
    @DisplayName("Should successfully update existing custom semantic type")
    void shouldSuccessfullyUpdateExistingCustomSemanticType() {
      // Given
      String semanticType = "EMPLOYEE_ID";
      CustomSemanticType updatedType = TestFixtures.createValidCustomSemanticType();
      updatedType.setDescription("Updated employee identification number");

      when(mockCustomSemanticTypeService.updateCustomType(semanticType, updatedType))
          .thenReturn(updatedType);

      // When
      ResponseEntity<CustomSemanticType> response =
          controller.updateCustomSemanticType(semanticType, updatedType);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody()).isEqualTo(updatedType);
      assertThat(response.getBody().getDescription())
          .isEqualTo("Updated employee identification number");

      verify(mockCustomSemanticTypeService, times(1)).updateCustomType(semanticType, updatedType);
      verifyNoMoreInteractions(mockCustomSemanticTypeService);
    }

    @Test
    @DisplayName("Should return not found when updating non-existent type")
    void shouldReturnNotFoundWhenUpdatingNonExistentType() {
      // Given
      String nonExistentType = "NON_EXISTENT_TYPE";
      CustomSemanticType updatedType = TestFixtures.createValidCustomSemanticType();

      when(mockCustomSemanticTypeService.updateCustomType(nonExistentType, updatedType))
          .thenThrow(new IllegalArgumentException("Semantic type not found: " + nonExistentType));

      // When
      ResponseEntity<CustomSemanticType> response =
          controller.updateCustomSemanticType(nonExistentType, updatedType);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

      verify(mockCustomSemanticTypeService, times(1))
          .updateCustomType(nonExistentType, updatedType);
    }

    @Test
    @DisplayName("Should return bad request for invalid update data")
    void shouldReturnBadRequestForInvalidUpdateData() {
      // Given
      String semanticType = "EMPLOYEE_ID";
      CustomSemanticType invalidType = TestFixtures.createInvalidCustomSemanticType();

      when(mockCustomSemanticTypeService.updateCustomType(semanticType, invalidType))
          .thenThrow(new IllegalArgumentException("Invalid semantic type definition"));

      // When
      ResponseEntity<CustomSemanticType> response =
          controller.updateCustomSemanticType(semanticType, invalidType);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

      verify(mockCustomSemanticTypeService, times(1)).updateCustomType(semanticType, invalidType);
    }

    @Test
    @DisplayName("Should handle type name change validation")
    void shouldHandleTypeNameChangeValidation() {
      // Given
      String originalType = "EMPLOYEE_ID";
      CustomSemanticType updatedType = TestFixtures.createValidCustomSemanticType();
      updatedType.setSemanticType("NEW_EMPLOYEE_ID"); // Changed semantic type name

      when(mockCustomSemanticTypeService.updateCustomType(originalType, updatedType))
          .thenThrow(
              new IllegalArgumentException(
                  "Type 'NEW_EMPLOYEE_ID' already exists. Use a different name."));

      // When
      ResponseEntity<CustomSemanticType> response =
          controller.updateCustomSemanticType(originalType, updatedType);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

      verify(mockCustomSemanticTypeService, times(1)).updateCustomType(originalType, updatedType);
    }

    @Test
    @DisplayName("Should handle service exception during update")
    void shouldHandleServiceExceptionDuringUpdate() {
      // Given
      String semanticType = "EMPLOYEE_ID";
      CustomSemanticType updatedType = TestFixtures.createValidCustomSemanticType();

      when(mockCustomSemanticTypeService.updateCustomType(semanticType, updatedType))
          .thenThrow(new RuntimeException("Update operation failed"));

      // When & Then
      assertThatThrownBy(() -> controller.updateCustomSemanticType(semanticType, updatedType))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Update operation failed");

      verify(mockCustomSemanticTypeService, times(1)).updateCustomType(semanticType, updatedType);
    }

    @Test
    @DisplayName("Should handle null semantic type parameter")
    void shouldHandleNullSemanticTypeParameter() {
      // Given
      CustomSemanticType updatedType = TestFixtures.createValidCustomSemanticType();

      when(mockCustomSemanticTypeService.updateCustomType(null, updatedType))
          .thenThrow(new IllegalArgumentException("Semantic type identifier cannot be null"));

      // When
      ResponseEntity<CustomSemanticType> response =
          controller.updateCustomSemanticType(null, updatedType);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should handle empty semantic type parameter")
    void shouldHandleEmptySemanticTypeParameter() {
      // Given
      String emptyType = "";
      CustomSemanticType updatedType = TestFixtures.createValidCustomSemanticType();

      when(mockCustomSemanticTypeService.updateCustomType(emptyType, updatedType))
          .thenThrow(new IllegalArgumentException("Semantic type not found: "));

      // When
      ResponseEntity<CustomSemanticType> response =
          controller.updateCustomSemanticType(emptyType, updatedType);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("Remove Custom Type Tests")
  class RemoveCustomTypeTests {

    @Test
    @DisplayName("Should successfully remove existing custom type")
    void shouldSuccessfullyRemoveExistingCustomType() {
      // Given
      String semanticType = "EMPLOYEE_ID";
      doNothing().when(mockCustomSemanticTypeService).removeCustomType(semanticType);

      // When
      ResponseEntity<Void> response = controller.removeCustomType(semanticType);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
      assertThat(response.getBody()).isNull();

      verify(mockCustomSemanticTypeService, times(1)).removeCustomType(semanticType);
      verifyNoMoreInteractions(mockCustomSemanticTypeService);
    }

    @Test
    @DisplayName("Should return not found when removing non-existent type")
    void shouldReturnNotFoundWhenRemovingNonExistentType() {
      // Given
      String nonExistentType = "NON_EXISTENT_TYPE";
      doThrow(new IllegalArgumentException("Semantic type not found: " + nonExistentType))
          .when(mockCustomSemanticTypeService)
          .removeCustomType(nonExistentType);

      // When
      ResponseEntity<Void> response = controller.removeCustomType(nonExistentType);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

      verify(mockCustomSemanticTypeService, times(1)).removeCustomType(nonExistentType);
    }

    @Test
    @DisplayName("Should handle service exception during removal")
    void shouldHandleServiceExceptionDuringRemoval() {
      // Given
      String semanticType = "EMPLOYEE_ID";
      doThrow(new RuntimeException("Database error during deletion"))
          .when(mockCustomSemanticTypeService)
          .removeCustomType(semanticType);

      // When & Then
      assertThatThrownBy(() -> controller.removeCustomType(semanticType))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Database error during deletion");

      verify(mockCustomSemanticTypeService, times(1)).removeCustomType(semanticType);
    }

    @Test
    @DisplayName("Should handle null semantic type parameter for removal")
    void shouldHandleNullSemanticTypeParameterForRemoval() {
      // Given
      doThrow(new IllegalArgumentException("Semantic type identifier cannot be null"))
          .when(mockCustomSemanticTypeService)
          .removeCustomType(null);

      // When
      ResponseEntity<Void> response = controller.removeCustomType(null);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

      verify(mockCustomSemanticTypeService, times(1)).removeCustomType(null);
    }

    @Test
    @DisplayName("Should handle empty semantic type parameter for removal")
    void shouldHandleEmptySemanticTypeParameterForRemoval() {
      // Given
      String emptyType = "";
      doThrow(new IllegalArgumentException("Semantic type not found: "))
          .when(mockCustomSemanticTypeService)
          .removeCustomType(emptyType);

      // When
      ResponseEntity<Void> response = controller.removeCustomType(emptyType);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

      verify(mockCustomSemanticTypeService, times(1)).removeCustomType(emptyType);
    }
  }

  @Nested
  @DisplayName("Reload Custom Types Tests")
  class ReloadCustomTypesTests {

    @Test
    @DisplayName("Should successfully reload custom types")
    void shouldSuccessfullyReloadCustomTypes() {
      // Given
      doNothing().when(mockCustomSemanticTypeService).reloadCustomTypes();

      // When
      ResponseEntity<Map<String, String>> response = controller.reloadCustomTypes();

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("message"))
          .isEqualTo("Custom semantic types reloaded successfully");

      verify(mockCustomSemanticTypeService, times(1)).reloadCustomTypes();
      verifyNoMoreInteractions(mockCustomSemanticTypeService);
    }

    @Test
    @DisplayName("Should handle reload exception gracefully")
    void shouldHandleReloadExceptionGracefully() {
      // Given
      doThrow(new RuntimeException("Failed to reload configuration"))
          .when(mockCustomSemanticTypeService)
          .reloadCustomTypes();

      // When & Then
      assertThatThrownBy(() -> controller.reloadCustomTypes())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Failed to reload configuration");

      verify(mockCustomSemanticTypeService, times(1)).reloadCustomTypes();
    }

    @Test
    @DisplayName("Should always return success message on successful reload")
    void shouldAlwaysReturnSuccessMessageOnSuccessfulReload() {
      // Given
      doNothing().when(mockCustomSemanticTypeService).reloadCustomTypes();

      // When
      ResponseEntity<Map<String, String>> response = controller.reloadCustomTypes();

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      Map<String, String> responseBody = response.getBody();
      assertThat(responseBody).isNotNull();
      assertThat(responseBody).containsKey("message");
      assertThat(responseBody.get("message")).contains("reloaded successfully");
    }
  }

  @Nested
  @DisplayName("Controller Integration and Edge Cases")
  class ControllerIntegrationTests {

    @Test
    @DisplayName("Should handle concurrent CRUD operations appropriately")
    void shouldHandleConcurrentCrudOperationsAppropriately() {
      // Given
      CustomSemanticType type1 = TestFixtures.createValidCustomSemanticType();
      CustomSemanticType type2 = TestFixtures.createListTypeCustomSemanticType();

      when(mockCustomSemanticTypeService.addCustomType(type1)).thenReturn(type1);
      when(mockCustomSemanticTypeService.addCustomType(type2)).thenReturn(type2);
      doNothing().when(mockCustomSemanticTypeService).removeCustomType("EMPLOYEE_ID");

      // When
      ResponseEntity<CustomSemanticType> addResponse1 = controller.addCustomSemanticType(type1);
      ResponseEntity<CustomSemanticType> addResponse2 = controller.addCustomSemanticType(type2);
      ResponseEntity<Void> removeResponse = controller.removeCustomType("EMPLOYEE_ID");

      // Then
      assertThat(addResponse1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(addResponse2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(removeResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

      verify(mockCustomSemanticTypeService, times(1)).addCustomType(type1);
      verify(mockCustomSemanticTypeService, times(1)).addCustomType(type2);
      verify(mockCustomSemanticTypeService, times(1)).removeCustomType("EMPLOYEE_ID");
    }

    @Test
    @DisplayName("Should maintain data consistency across operations")
    void shouldMaintainDataConsistencyAcrossOperations() throws IOException {
      // Given
      CustomSemanticType initialType = TestFixtures.createValidCustomSemanticType();
      CustomSemanticType updatedType = TestFixtures.createValidCustomSemanticType();
      updatedType.setDescription("Updated description");

      List<CustomSemanticType> allTypesInitial = List.of(initialType);
      List<CustomSemanticType> allTypesAfterUpdate = List.of(updatedType);

      when(mockCustomSemanticTypeService.addCustomType(initialType)).thenReturn(initialType);
      when(mockCustomSemanticTypeService.getAllCustomTypes())
          .thenReturn(allTypesInitial)
          .thenReturn(allTypesAfterUpdate);
      when(mockCustomSemanticTypeService.updateCustomType("EMPLOYEE_ID", updatedType))
          .thenReturn(updatedType);

      // When
      ResponseEntity<CustomSemanticType> addResponse =
          controller.addCustomSemanticType(initialType);
      ResponseEntity<List<CustomSemanticType>> getResponse1 = controller.getAllCustomTypes();
      ResponseEntity<CustomSemanticType> updateResponse =
          controller.updateCustomSemanticType("EMPLOYEE_ID", updatedType);
      ResponseEntity<List<CustomSemanticType>> getResponse2 = controller.getAllCustomTypes();

      // Then
      assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(getResponse1.getBody()).hasSize(1);
      assertThat(getResponse1.getBody().get(0).getDescription())
          .isEqualTo("Employee identification number");

      assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(getResponse2.getBody()).hasSize(1);
      assertThat(getResponse2.getBody().get(0).getDescription()).isEqualTo("Updated description");
    }

    @Test
    @DisplayName("Should handle request validation failures appropriately")
    void shouldHandleRequestValidationFailuresAppropriately() {
      // Given - Testing various validation scenarios
      CustomSemanticType validType = TestFixtures.createValidCustomSemanticType();

      when(mockCustomSemanticTypeService.addCustomType(any()))
          .thenThrow(new IllegalArgumentException("Validation failed"));

      // When
      ResponseEntity<CustomSemanticType> response = controller.addCustomSemanticType(validType);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should handle service layer timeout gracefully")
    void shouldHandleServiceLayerTimeoutGracefully() throws IOException {
      // Given
      when(mockCustomSemanticTypeService.getAllCustomTypes())
          .thenAnswer(
              invocation -> {
                Thread.sleep(1000); // Simulate delay
                throw new RuntimeException("Service timeout");
              });

      // When & Then
      assertThatThrownBy(() -> controller.getAllCustomTypes())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Service timeout");
    }

    @Test
    @DisplayName("Should preserve type data integrity during operations")
    void shouldPreserveTypeDataIntegrityDuringOperations() {
      // Given
      CustomSemanticType originalType = TestFixtures.createValidCustomSemanticType();
      String originalSemanticType = originalType.getSemanticType();
      String originalDescription = originalType.getDescription();

      when(mockCustomSemanticTypeService.addCustomType(any())).thenReturn(originalType);

      // When
      ResponseEntity<CustomSemanticType> response = controller.addCustomSemanticType(originalType);

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      CustomSemanticType returnedType = response.getBody();
      assertThat(returnedType).isNotNull();
      assertThat(returnedType.getSemanticType()).isEqualTo(originalSemanticType);
      assertThat(returnedType.getDescription()).isEqualTo(originalDescription);

      // Verify the original object wasn't modified
      assertThat(originalType.getSemanticType()).isEqualTo(originalSemanticType);
      assertThat(originalType.getDescription()).isEqualTo(originalDescription);
    }

    @Test
    @DisplayName("Should handle special characters in semantic type names")
    void shouldHandleSpecialCharactersInSemanticTypeNames() {
      // Given
      String specialTypeName = "TYPE_WITH$PECIAL#CHARS_AND.DOTS";
      CustomSemanticType specialType = TestFixtures.createValidCustomSemanticType();
      specialType.setSemanticType(specialTypeName);

      when(mockCustomSemanticTypeService.addCustomType(specialType)).thenReturn(specialType);
      doNothing().when(mockCustomSemanticTypeService).removeCustomType(specialTypeName);

      // When
      ResponseEntity<CustomSemanticType> addResponse =
          controller.addCustomSemanticType(specialType);
      ResponseEntity<Void> removeResponse = controller.removeCustomType(specialTypeName);

      // Then
      assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(addResponse.getBody().getSemanticType()).isEqualTo(specialTypeName);
      assertThat(removeResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

      verify(mockCustomSemanticTypeService, times(1)).addCustomType(specialType);
      verify(mockCustomSemanticTypeService, times(1)).removeCustomType(specialTypeName);
    }
  }
}
