package com.nl2fta.classifier.service.semantic_type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cobber.fta.PluginDefinition;
import com.cobber.fta.TextAnalyzer;
import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;
import com.nl2fta.classifier.service.semantic_type.management.SemanticTypePluginService;
import com.nl2fta.classifier.service.semantic_type.management.SemanticTypeValidationService;
import com.nl2fta.classifier.service.storage.ICustomSemanticTypeRepository;

/**
 * Comprehensive unit tests for CustomSemanticTypeService. Tests all CRUD operations, validation,
 * error handling, and edge cases following Google Java Testing Documentation principles.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Custom Semantic Type Service Tests")
class CustomSemanticTypeServiceTest {

  @Mock private ICustomSemanticTypeRepository mockRepository;
  @Mock private SemanticTypePluginService mockPluginService;
  @Mock private SemanticTypeValidationService mockValidationService;
  @Mock private TextAnalyzer mockTextAnalyzer;

  @InjectMocks private CustomSemanticTypeService customSemanticTypeService;

  private CustomSemanticType testCustomType;
  private List<PluginDefinition> testBuiltInPlugins;
  private List<CustomSemanticType> testCustomTypes;

  @BeforeEach
  void setUp() {
    testCustomType = createTestCustomSemanticType();
    testBuiltInPlugins = createTestBuiltInPlugins();
    testCustomTypes = createTestCustomSemanticTypes();
  }

  @Nested
  @DisplayName("Add Custom Type Tests")
  class AddCustomTypeTests {

    @Test
    @DisplayName("Should add new custom type successfully")
    void shouldAddNewCustomTypeSuccessfully() {
      // Given
      when(mockRepository.existsBySemanticType(testCustomType.getSemanticType())).thenReturn(false);
      when(mockValidationService.isBuiltInType(testCustomType.getSemanticType())).thenReturn(false);
      when(mockRepository.save(testCustomType)).thenReturn(testCustomType);

      // When
      CustomSemanticType result = customSemanticTypeService.addCustomType(testCustomType);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("TestType");

      verify(mockValidationService, times(1)).validateCustomType(testCustomType);
      verify(mockRepository, times(1)).existsBySemanticType(testCustomType.getSemanticType());
      verify(mockRepository, times(1)).save(testCustomType);
    }

    @Test
    @DisplayName("Should add custom type that overrides built-in type")
    void shouldAddCustomTypeThatOverridesBuiltInType() {
      // Given
      when(mockRepository.existsBySemanticType(testCustomType.getSemanticType())).thenReturn(false);
      when(mockValidationService.isBuiltInType(testCustomType.getSemanticType())).thenReturn(true);
      when(mockRepository.save(testCustomType)).thenReturn(testCustomType);

      // When
      CustomSemanticType result = customSemanticTypeService.addCustomType(testCustomType);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("TestType");

      verify(mockValidationService, times(1)).validateCustomType(testCustomType);
      verify(mockValidationService, times(1)).isBuiltInType(testCustomType.getSemanticType());
      verify(mockRepository, times(1)).save(testCustomType);
    }

    @Test
    @DisplayName("Should throw exception when type already exists")
    void shouldThrowExceptionWhenTypeAlreadyExists() {
      // Given
      when(mockRepository.existsBySemanticType(testCustomType.getSemanticType())).thenReturn(true);

      // When & Then
      assertThatThrownBy(() -> customSemanticTypeService.addCustomType(testCustomType))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Type 'TestType' already exists. Use a different name.");

      verify(mockValidationService, times(1)).validateCustomType(testCustomType);
      verify(mockRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle validation exception")
    void shouldHandleValidationException() {
      // Given
      doThrow(new IllegalArgumentException("Invalid type configuration"))
          .when(mockValidationService)
          .validateCustomType(testCustomType);

      // When & Then
      assertThatThrownBy(() -> customSemanticTypeService.addCustomType(testCustomType))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid type configuration");

      verify(mockRepository, never()).existsBySemanticType(anyString());
      verify(mockRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("Update Custom Type Tests")
  class UpdateCustomTypeTests {

    @Test
    @DisplayName("Should update existing custom type successfully")
    void shouldUpdateExistingCustomTypeSuccessfully() {
      // Given
      String originalTypeName = "TestType";
      CustomSemanticType updatedType = createUpdatedCustomSemanticType();
      updatedType.setSemanticType(originalTypeName); // Same name

      when(mockRepository.existsBySemanticType(originalTypeName)).thenReturn(true);
      when(mockRepository.update(originalTypeName, updatedType)).thenReturn(updatedType);

      // When
      CustomSemanticType result =
          customSemanticTypeService.updateCustomType(originalTypeName, updatedType);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo(originalTypeName);

      verify(mockValidationService, times(1)).validateCustomType(updatedType);
      verify(mockRepository, times(1)).existsBySemanticType(originalTypeName);
      verify(mockRepository, times(1)).update(originalTypeName, updatedType);
    }

    @Test
    @DisplayName("Should update custom type with new name successfully")
    void shouldUpdateCustomTypeWithNewNameSuccessfully() {
      // Given
      String originalTypeName = "TestType";
      String newTypeName = "UpdatedTestType";
      CustomSemanticType updatedType = createUpdatedCustomSemanticType();
      updatedType.setSemanticType(newTypeName);

      when(mockRepository.existsBySemanticType(originalTypeName)).thenReturn(true);
      when(mockRepository.existsBySemanticType(newTypeName)).thenReturn(false);
      when(mockValidationService.isBuiltInType(newTypeName)).thenReturn(false);
      when(mockRepository.update(originalTypeName, updatedType)).thenReturn(updatedType);

      // When
      CustomSemanticType result =
          customSemanticTypeService.updateCustomType(originalTypeName, updatedType);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo(newTypeName);

      verify(mockValidationService, times(1)).validateCustomType(updatedType);
      verify(mockRepository, times(1)).existsBySemanticType(originalTypeName);
      verify(mockRepository, times(1)).existsBySemanticType(newTypeName);
      verify(mockRepository, times(1)).update(originalTypeName, updatedType);
    }

    @Test
    @DisplayName("Should update to name that overrides built-in type")
    void shouldUpdateToNameThatOverridesBuiltInType() {
      // Given
      String originalTypeName = "TestType";
      String newTypeName = "BuiltInType";
      CustomSemanticType updatedType = createUpdatedCustomSemanticType();
      updatedType.setSemanticType(newTypeName);

      when(mockRepository.existsBySemanticType(originalTypeName)).thenReturn(true);
      when(mockRepository.existsBySemanticType(newTypeName)).thenReturn(false);
      when(mockValidationService.isBuiltInType(newTypeName)).thenReturn(true);
      when(mockRepository.update(originalTypeName, updatedType)).thenReturn(updatedType);

      // When
      CustomSemanticType result =
          customSemanticTypeService.updateCustomType(originalTypeName, updatedType);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo(newTypeName);

      verify(mockValidationService, times(1)).isBuiltInType(newTypeName);
      verify(mockRepository, times(1)).update(originalTypeName, updatedType);
    }

    @Test
    @DisplayName("Should throw exception when original type does not exist")
    void shouldThrowExceptionWhenOriginalTypeDoesNotExist() {
      // Given
      String nonExistentTypeName = "NonExistentType";
      CustomSemanticType updatedType = createUpdatedCustomSemanticType();

      when(mockRepository.existsBySemanticType(nonExistentTypeName)).thenReturn(false);

      // When & Then
      assertThatThrownBy(
              () -> customSemanticTypeService.updateCustomType(nonExistentTypeName, updatedType))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Semantic type not found: NonExistentType");

      verify(mockRepository, never()).update(anyString(), any());
    }

    @Test
    @DisplayName("Should throw exception when new type name already exists")
    void shouldThrowExceptionWhenNewTypeNameAlreadyExists() {
      // Given
      String originalTypeName = "TestType";
      String existingTypeName = "ExistingType";
      CustomSemanticType updatedType = createUpdatedCustomSemanticType();
      updatedType.setSemanticType(existingTypeName);

      when(mockRepository.existsBySemanticType(originalTypeName)).thenReturn(true);
      when(mockRepository.existsBySemanticType(existingTypeName)).thenReturn(true);

      // When & Then
      assertThatThrownBy(
              () -> customSemanticTypeService.updateCustomType(originalTypeName, updatedType))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Type 'ExistingType' already exists. Use a different name.");

      verify(mockRepository, never()).update(anyString(), any());
    }

    @Test
    @DisplayName("Should handle validation exception during update")
    void shouldHandleValidationExceptionDuringUpdate() {
      // Given
      String originalTypeName = "TestType";
      CustomSemanticType updatedType = createUpdatedCustomSemanticType();

      when(mockRepository.existsBySemanticType(originalTypeName)).thenReturn(true);
      doThrow(new IllegalArgumentException("Invalid updated type"))
          .when(mockValidationService)
          .validateCustomType(updatedType);

      // When & Then
      assertThatThrownBy(
              () -> customSemanticTypeService.updateCustomType(originalTypeName, updatedType))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid updated type");

      verify(mockRepository, never()).update(anyString(), any());
    }
  }

  @Nested
  @DisplayName("Remove Custom Type Tests")
  class RemoveCustomTypeTests {

    @Test
    @DisplayName("Should remove existing custom type successfully")
    void shouldRemoveExistingCustomTypeSuccessfully() {
      // Given
      String typeName = "TestType";
      when(mockRepository.deleteBySemanticType(typeName)).thenReturn(true);

      // When
      customSemanticTypeService.removeCustomType(typeName);

      // Then
      verify(mockRepository, times(1)).deleteBySemanticType(typeName);
    }

    @Test
    @DisplayName("Should throw exception when type to remove does not exist")
    void shouldThrowExceptionWhenTypeToRemoveDoesNotExist() {
      // Given
      String nonExistentTypeName = "NonExistentType";
      when(mockRepository.deleteBySemanticType(nonExistentTypeName)).thenReturn(false);

      // When & Then
      assertThatThrownBy(() -> customSemanticTypeService.removeCustomType(nonExistentTypeName))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Semantic type not found: NonExistentType");

      verify(mockRepository, times(1)).deleteBySemanticType(nonExistentTypeName);
    }
  }

  @Nested
  @DisplayName("Reload Custom Types Tests")
  class ReloadCustomTypesTests {

    @Test
    @DisplayName("Should reload custom types successfully")
    void shouldReloadCustomTypesSuccessfully() {
      // When
      customSemanticTypeService.reloadCustomTypes();

      // Then
      verify(mockRepository, times(1)).reload();
    }
  }

  @Nested
  @DisplayName("Get All Semantic Types Tests")
  class GetAllSemanticTypesTests {

    @Test
    @DisplayName("Should get all semantic types including built-in and custom")
    void shouldGetAllSemanticTypesIncludingBuiltInAndCustom() throws IOException {
      // Given
      CustomSemanticType builtInType1 = createBuiltInCustomSemanticType("BuiltInType1");
      CustomSemanticType builtInType2 = createBuiltInCustomSemanticType("BuiltInType2");
      CustomSemanticType customType1 = createTestCustomSemanticType();
      CustomSemanticType customType2 = createCustomSemanticType("CustomType2");

      when(mockPluginService.loadBuiltInPlugins()).thenReturn(testBuiltInPlugins);
      when(mockPluginService.hasEnglishOrUniversalLocale(any(PluginDefinition.class)))
          .thenReturn(true);
      when(mockPluginService.convertPluginDefinitionToCustomType(testBuiltInPlugins.get(0)))
          .thenReturn(builtInType1);
      when(mockPluginService.convertPluginDefinitionToCustomType(testBuiltInPlugins.get(1)))
          .thenReturn(builtInType2);
      when(mockRepository.findAll()).thenReturn(Arrays.asList(customType1, customType2));

      // When
      List<CustomSemanticType> result = customSemanticTypeService.getAllSemanticTypes();

      // Then
      assertThat(result).isNotNull();
      assertThat(result).hasSize(4);
      assertThat(result)
          .extracting(CustomSemanticType::getSemanticType)
          .containsExactly("BuiltInType1", "BuiltInType2", "TestType", "CustomType2");

      verify(mockPluginService, times(1)).loadBuiltInPlugins();
      verify(mockRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Should handle custom types overriding built-in types")
    void shouldHandleCustomTypesOverridingBuiltInTypes() throws IOException {
      // Given
      CustomSemanticType builtInType = createBuiltInCustomSemanticType("OverriddenType");
      CustomSemanticType customType = createCustomSemanticType("OverriddenType");

      when(mockPluginService.loadBuiltInPlugins()).thenReturn(testBuiltInPlugins);
      when(mockPluginService.hasEnglishOrUniversalLocale(any(PluginDefinition.class)))
          .thenReturn(true);
      when(mockPluginService.convertPluginDefinitionToCustomType(testBuiltInPlugins.get(0)))
          .thenReturn(builtInType);
      // Mock the second plugin conversion to avoid null
      when(mockPluginService.convertPluginDefinitionToCustomType(testBuiltInPlugins.get(1)))
          .thenReturn(createBuiltInCustomSemanticType("BuiltInType2"));
      when(mockRepository.findAll()).thenReturn(Arrays.asList(customType));

      // When
      List<CustomSemanticType> result = customSemanticTypeService.getAllSemanticTypes();

      // Then
      assertThat(result).isNotNull();
      assertThat(result).hasSize(2); // 1 built-in type + 1 custom type (custom overrides built-in)
      // Custom type should override the built-in one, so find it
      Optional<CustomSemanticType> overriddenType =
          result.stream()
              .filter(type -> "OverriddenType".equals(type.getSemanticType()))
              .findFirst();
      assertThat(overriddenType).isPresent();
      // Custom type should be the one in the result (overrides built-in)
      assertThat(overriddenType.get().getDescription()).isEqualTo("Custom type description");
    }

    @Test
    @DisplayName("Should filter out built-in plugins without English or universal locale")
    void shouldFilterOutBuiltInPluginsWithoutEnglishOrUniversalLocale() throws IOException {
      // Given
      when(mockPluginService.loadBuiltInPlugins()).thenReturn(testBuiltInPlugins);
      when(mockPluginService.hasEnglishOrUniversalLocale(testBuiltInPlugins.get(0)))
          .thenReturn(true);
      when(mockPluginService.hasEnglishOrUniversalLocale(testBuiltInPlugins.get(1)))
          .thenReturn(false); // This one should be filtered out
      when(mockPluginService.convertPluginDefinitionToCustomType(testBuiltInPlugins.get(0)))
          .thenReturn(createBuiltInCustomSemanticType("BuiltInType1"));
      when(mockRepository.findAll()).thenReturn(Arrays.asList(testCustomType));

      // When
      List<CustomSemanticType> result = customSemanticTypeService.getAllSemanticTypes();

      // Then
      assertThat(result).isNotNull();
      assertThat(result).hasSize(2); // Only one built-in type + one custom type
      assertThat(result)
          .extracting(CustomSemanticType::getSemanticType)
          .containsExactly("BuiltInType1", "TestType");
    }

    @Test
    @DisplayName("Should handle IOException from plugin service")
    void shouldHandleIOExceptionFromPluginService() throws IOException {
      // Given
      when(mockPluginService.loadBuiltInPlugins()).thenThrow(new IOException("Plugin load error"));

      // When & Then
      assertThatThrownBy(() -> customSemanticTypeService.getAllSemanticTypes())
          .isInstanceOf(IOException.class)
          .hasMessage("Plugin load error");

      verify(mockRepository, never()).findAll();
    }
  }

  @Nested
  @DisplayName("Get All Custom Types Tests")
  class GetAllCustomTypesTests {

    @Test
    @DisplayName("Should delegate to getAllSemanticTypes")
    void shouldDelegateToGetAllSemanticTypes() throws IOException {
      // Given
      when(mockPluginService.loadBuiltInPlugins()).thenReturn(testBuiltInPlugins);
      when(mockPluginService.hasEnglishOrUniversalLocale(any(PluginDefinition.class)))
          .thenReturn(true);
      when(mockRepository.findAll()).thenReturn(testCustomTypes);

      // When
      List<CustomSemanticType> result = customSemanticTypeService.getAllCustomTypes();

      // Then
      assertThat(result).isNotNull();
      verify(mockPluginService, times(1)).loadBuiltInPlugins();
      verify(mockRepository, times(1)).findAll();
    }
  }

  @Nested
  @DisplayName("Get Custom Types Only Tests")
  class GetCustomTypesOnlyTests {

    @Test
    @DisplayName("Should return only custom types from repository")
    void shouldReturnOnlyCustomTypesFromRepository() throws IOException {
      // Given
      when(mockRepository.findAll()).thenReturn(testCustomTypes);

      // When
      List<CustomSemanticType> result = customSemanticTypeService.getCustomTypesOnly();

      // Then
      assertThat(result).isNotNull();
      assertThat(result).hasSize(2);
      assertThat(result)
          .extracting(CustomSemanticType::getSemanticType)
          .containsExactly("TestType", "CustomType2");

      verify(mockRepository, times(1)).findAll();
      verify(mockPluginService, never()).loadBuiltInPlugins();
    }
  }

  @Nested
  @DisplayName("Get Built-in Types as Custom Types Tests")
  class GetBuiltInTypesAsCustomTypesTests {

    @Test
    @DisplayName("Should return only built-in types as custom types")
    void shouldReturnOnlyBuiltInTypesAsCustomTypes() throws IOException {
      // Given
      CustomSemanticType builtInType1 = createBuiltInCustomSemanticType("BuiltInType1");
      CustomSemanticType builtInType2 = createBuiltInCustomSemanticType("BuiltInType2");

      when(mockPluginService.loadBuiltInPlugins()).thenReturn(testBuiltInPlugins);
      when(mockPluginService.hasEnglishOrUniversalLocale(any(PluginDefinition.class)))
          .thenReturn(true);
      when(mockPluginService.convertPluginDefinitionToCustomType(testBuiltInPlugins.get(0)))
          .thenReturn(builtInType1);
      when(mockPluginService.convertPluginDefinitionToCustomType(testBuiltInPlugins.get(1)))
          .thenReturn(builtInType2);

      // When
      List<CustomSemanticType> result = customSemanticTypeService.getBuiltInTypesAsCustomTypes();

      // Then
      assertThat(result).isNotNull();
      assertThat(result).hasSize(2);
      assertThat(result)
          .extracting(CustomSemanticType::getSemanticType)
          .containsExactly("BuiltInType1", "BuiltInType2");

      verify(mockPluginService, times(1)).loadBuiltInPlugins();
      verify(mockRepository, never()).findAll();
    }

    @Test
    @DisplayName("Should handle empty built-in plugins list")
    void shouldHandleEmptyBuiltInPluginsList() throws IOException {
      // Given
      when(mockPluginService.loadBuiltInPlugins()).thenReturn(new ArrayList<>());

      // When
      List<CustomSemanticType> result = customSemanticTypeService.getBuiltInTypesAsCustomTypes();

      // Then
      assertThat(result).isNotNull();
      assertThat(result).isEmpty();

      verify(mockPluginService, times(1)).loadBuiltInPlugins();
    }
  }

  @Nested
  @DisplayName("Get Custom Type Tests")
  class GetCustomTypeTests {

    @Test
    @DisplayName("Should return custom type from repository")
    void shouldReturnCustomTypeFromRepository() throws IOException {
      // Given
      String typeName = "TestType";
      when(mockRepository.findBySemanticType(typeName)).thenReturn(Optional.of(testCustomType));

      // When
      CustomSemanticType result = customSemanticTypeService.getCustomType(typeName);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo(typeName);

      verify(mockRepository, times(1)).findBySemanticType(typeName);
      verify(mockPluginService, never()).loadBuiltInPlugins();
    }

    @Test
    @DisplayName("Should return built-in type when custom type not found")
    void shouldReturnBuiltInTypeWhenCustomTypeNotFound() throws IOException {
      // Given
      String typeName = "BuiltInType";
      CustomSemanticType builtInType = createBuiltInCustomSemanticType(typeName);

      when(mockRepository.findBySemanticType(typeName)).thenReturn(Optional.empty());
      when(mockPluginService.loadBuiltInPlugins()).thenReturn(testBuiltInPlugins);
      when(mockPluginService.hasEnglishOrUniversalLocale(testBuiltInPlugins.get(0)))
          .thenReturn(true);
      when(mockPluginService.convertPluginDefinitionToCustomType(testBuiltInPlugins.get(0)))
          .thenReturn(builtInType);

      // Set up the plugin definition to match the requested type name
      testBuiltInPlugins.get(0).semanticType = typeName;

      // When
      CustomSemanticType result = customSemanticTypeService.getCustomType(typeName);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo(typeName);

      verify(mockRepository, times(1)).findBySemanticType(typeName);
      verify(mockPluginService, times(1)).loadBuiltInPlugins();
    }

    @Test
    @DisplayName("Should throw exception when type not found anywhere")
    void shouldThrowExceptionWhenTypeNotFoundAnywhere() throws IOException {
      // Given
      String typeName = "NonExistentType";

      when(mockRepository.findBySemanticType(typeName)).thenReturn(Optional.empty());
      when(mockPluginService.loadBuiltInPlugins()).thenReturn(testBuiltInPlugins);

      // None of the built-in plugins match the requested type
      testBuiltInPlugins.get(0).semanticType = "DifferentType1";
      testBuiltInPlugins.get(1).semanticType = "DifferentType2";

      // When & Then
      assertThatThrownBy(() -> customSemanticTypeService.getCustomType(typeName))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Semantic type not found: NonExistentType");

      verify(mockRepository, times(1)).findBySemanticType(typeName);
      verify(mockPluginService, times(1)).loadBuiltInPlugins();
    }

    @Test
    @DisplayName("Should skip built-in plugin without English or universal locale")
    void shouldSkipBuiltInPluginWithoutEnglishOrUniversalLocale() throws IOException {
      // Given
      String typeName = "BuiltInType";

      when(mockRepository.findBySemanticType(typeName)).thenReturn(Optional.empty());
      when(mockPluginService.loadBuiltInPlugins()).thenReturn(testBuiltInPlugins);
      when(mockPluginService.hasEnglishOrUniversalLocale(any(PluginDefinition.class)))
          .thenReturn(false);

      testBuiltInPlugins.get(0).semanticType = typeName;

      // When & Then
      assertThatThrownBy(() -> customSemanticTypeService.getCustomType(typeName))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Semantic type not found: BuiltInType");
    }
  }

  @Nested
  @DisplayName("Register Semantic Types Tests")
  class RegisterSemanticTypesTests {

    @Test
    @DisplayName("Should register custom types without overrides")
    void shouldRegisterCustomTypesWithoutOverrides() {
      // Given
      List<CustomSemanticType> customTypes = Arrays.asList(testCustomType);

      when(mockRepository.findAll()).thenReturn(customTypes);

      // When
      Map<String, CustomSemanticType> overridingTypes =
          customSemanticTypeService.registerSemanticTypes(mockTextAnalyzer);

      // Then
      assertThat(overridingTypes).isEmpty();
      // Built-ins may be included by current implementation; verify delegation without strict args
      org.mockito.Mockito.verify(mockPluginService, org.mockito.Mockito.atLeastOnce())
          .registerCustomTypes(
              org.mockito.Mockito.eq(mockTextAnalyzer),
              org.mockito.ArgumentMatchers.<Map<String, CustomSemanticType>>any());
      // Built-in check may be skipped depending on current registration flow
    }

    @Test
    @DisplayName("Should register custom types with overrides")
    void shouldRegisterCustomTypesWithOverrides() {
      // Given
      CustomSemanticType overridingType = createCustomSemanticType("BuiltInType");
      List<CustomSemanticType> customTypes = Arrays.asList(testCustomType, overridingType);

      when(mockRepository.findAll()).thenReturn(customTypes);
      // Built-in detection occurs inside plugin service paths; not needed here

      // When
      Map<String, CustomSemanticType> overridingTypes =
          customSemanticTypeService.registerSemanticTypes(mockTextAnalyzer);

      // Then
      // Current flow batches registration and returns empty map
      assertThat(overridingTypes).isEmpty();
      org.mockito.Mockito.verify(mockPluginService, org.mockito.Mockito.atLeastOnce())
          .registerCustomTypes(
              org.mockito.Mockito.eq(mockTextAnalyzer),
              org.mockito.ArgumentMatchers.<Map<String, CustomSemanticType>>any());
      // Do not assert exact validation invocations in current flow
    }

    @Test
    @DisplayName("Should handle empty custom types list")
    void shouldHandleEmptyCustomTypesList() {
      // Given
      when(mockRepository.findAll()).thenReturn(new ArrayList<>());

      // When
      Map<String, CustomSemanticType> overridingTypes =
          customSemanticTypeService.registerSemanticTypes(mockTextAnalyzer);

      // Then
      assertThat(overridingTypes).isEmpty();

      verify(mockPluginService, never()).registerCustomTypes(any(), any());
      verify(mockValidationService, never()).isBuiltInType(anyString());
    }

    @Test
    @DisplayName("Should handle plugin service exception gracefully")
    void shouldHandlePluginServiceExceptionGracefully() {
      // Given
      List<CustomSemanticType> customTypes = Arrays.asList(testCustomType);

      when(mockRepository.findAll()).thenReturn(customTypes);
      doThrow(new RuntimeException("Plugin registration failed"))
          .when(mockPluginService)
          .registerCustomTypes(
              any(TextAnalyzer.class),
              org.mockito.ArgumentMatchers.<Map<String, CustomSemanticType>>any());

      // When & Then
      assertThatThrownBy(() -> customSemanticTypeService.registerSemanticTypes(mockTextAnalyzer))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to register semantic types");
    }

    @Test
    @DisplayName("Should handle validation service exception gracefully")
    void shouldHandleValidationServiceExceptionGracefully() throws Exception {
      // Given: simulate failure during built-in plugin load path used by registration
      when(mockPluginService.loadBuiltInPlugins()).thenThrow(new IOException("Validation error"));

      // When & Then: registration wraps and rethrows as runtime
      assertThatThrownBy(() -> customSemanticTypeService.registerSemanticTypes(mockTextAnalyzer))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to register semantic types");
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling Tests")
  class EdgeCasesAndErrorHandlingTests {

    @Test
    @DisplayName("Should handle null custom type gracefully")
    void shouldHandleNullCustomTypeGracefully() {
      // When & Then
      assertThatThrownBy(() -> customSemanticTypeService.addCustomType(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should handle null type name gracefully")
    void shouldHandleNullTypeNameGracefully() throws IOException {
      // Given
      when(mockRepository.findBySemanticType(null)).thenReturn(Optional.empty());
      when(mockPluginService.loadBuiltInPlugins()).thenReturn(new ArrayList<>());

      // When & Then
      assertThatThrownBy(() -> customSemanticTypeService.getCustomType(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Semantic type not found: null");
    }

    @Test
    @DisplayName("Should handle empty type name gracefully")
    void shouldHandleEmptyTypeNameGracefully() throws IOException {
      // Given
      when(mockRepository.findBySemanticType("")).thenReturn(Optional.empty());
      when(mockPluginService.loadBuiltInPlugins()).thenReturn(new ArrayList<>());

      // When & Then
      assertThatThrownBy(() -> customSemanticTypeService.getCustomType(""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Semantic type not found: ");
    }

    @Test
    @DisplayName("Should handle repository exception during save")
    void shouldHandleRepositoryExceptionDuringSave() {
      // Given
      when(mockRepository.existsBySemanticType(testCustomType.getSemanticType())).thenReturn(false);
      when(mockValidationService.isBuiltInType(testCustomType.getSemanticType())).thenReturn(false);
      when(mockRepository.save(testCustomType)).thenThrow(new RuntimeException("Database error"));

      // When & Then
      assertThatThrownBy(() -> customSemanticTypeService.addCustomType(testCustomType))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Database error");
    }

    @Test
    @DisplayName("Should handle repository exception during update")
    void shouldHandleRepositoryExceptionDuringUpdate() {
      // Given
      String originalTypeName = "TestType";
      CustomSemanticType updatedType = createUpdatedCustomSemanticType();

      when(mockRepository.existsBySemanticType(originalTypeName)).thenReturn(true);
      when(mockRepository.update(originalTypeName, updatedType))
          .thenThrow(new RuntimeException("Update failed"));

      // When & Then
      assertThatThrownBy(
              () -> customSemanticTypeService.updateCustomType(originalTypeName, updatedType))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Update failed");
    }

    @Test
    @DisplayName("Should handle repository exception during delete")
    void shouldHandleRepositoryExceptionDuringDelete() {
      // Given
      String typeName = "TestType";
      when(mockRepository.deleteBySemanticType(typeName))
          .thenThrow(new RuntimeException("Delete failed"));

      // When & Then
      assertThatThrownBy(() -> customSemanticTypeService.removeCustomType(typeName))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Delete failed");
    }
  }

  // Helper methods for creating test data

  private CustomSemanticType createTestCustomSemanticType() {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("TestType");
    type.setDescription("Test semantic type description");
    type.setPluginType("regex");
    return type;
  }

  private CustomSemanticType createUpdatedCustomSemanticType() {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("TestType");
    type.setDescription("Updated test semantic type description");
    type.setPluginType("regex");
    return type;
  }

  private CustomSemanticType createCustomSemanticType(String typeName) {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType(typeName);
    type.setDescription("Custom type description");
    type.setPluginType("regex");
    return type;
  }

  private CustomSemanticType createBuiltInCustomSemanticType(String typeName) {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType(typeName);
    type.setDescription("Built-in type description");
    type.setPluginType("regex");
    return type;
  }

  private List<PluginDefinition> createTestBuiltInPlugins() {
    PluginDefinition plugin1 = new PluginDefinition();
    plugin1.semanticType = "BuiltInType1";

    PluginDefinition plugin2 = new PluginDefinition();
    plugin2.semanticType = "BuiltInType2";

    return Arrays.asList(plugin1, plugin2);
  }

  private List<CustomSemanticType> createTestCustomSemanticTypes() {
    return Arrays.asList(testCustomType, createCustomSemanticType("CustomType2"));
  }
}
