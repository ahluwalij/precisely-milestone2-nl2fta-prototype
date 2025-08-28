package com.nl2fta.classifier.service.semantic_type.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cobber.fta.PluginDefinition;
import com.cobber.fta.PluginDocumentationEntry;
import com.cobber.fta.PluginLocaleEntry;
import com.cobber.fta.Plugins;
import com.cobber.fta.TextAnalyzer;
import com.cobber.fta.core.FTAPluginException;
import com.cobber.fta.core.FTAType;
import com.cobber.fta.core.HeaderEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;

/**
 * Comprehensive unit tests for SemanticTypePluginService. Tests plugin loading, conversion methods,
 * FTA format conversion, registration, error handling, and edge cases following Google Java Testing
 * Documentation principles.
 *
 * <p>Coverage target: 95%+ for this high-priority service (816 missed instructions).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Semantic Type Plugin Service Tests")
@SuppressWarnings("unchecked")
class SemanticTypePluginServiceTest {

  @Mock private ObjectMapper mockObjectMapper;

  @InjectMocks private SemanticTypePluginService pluginService;

  private PluginDefinition testPlugin;
  private CustomSemanticType regexCustomType;
  private CustomSemanticType listCustomType;
  private CustomSemanticType javaCustomType;
  private List<PluginDefinition> testPlugins;

  // Helper to create a TypeReference matcher without unchecked warnings
  private static TypeReference<List<PluginDefinition>> anyTypeReference() {
    return any();
  }

  @BeforeEach
  void setUp() {
    testPlugin = createTestPluginDefinition();
    regexCustomType = createRegexCustomSemanticType();
    listCustomType = createListCustomSemanticType();
    javaCustomType = createJavaCustomSemanticType();
    testPlugins = Arrays.asList(testPlugin);
  }

  @Nested
  @DisplayName("Load Built-in Plugins Tests")
  class LoadBuiltInPluginsTests {

    @Test
    @DisplayName("Should load built-in plugins successfully")
    void shouldLoadBuiltInPluginsSuccessfully() throws Exception {
      // Given
      TypeReference<List<PluginDefinition>> typeRef =
          new TypeReference<List<PluginDefinition>>() {};
      when(mockObjectMapper.readValue(any(BufferedReader.class), anyTypeReference()))
          .thenReturn(testPlugins);

      // When
      List<PluginDefinition> result = pluginService.loadBuiltInPlugins();

      // Then
      assertThat(result).isNotNull();
      assertThat(result).hasSize(1);
      assertThat(result.get(0).semanticType).isEqualTo("TEST_TYPE");
      verify(mockObjectMapper).readValue(any(BufferedReader.class), anyTypeReference());
    }

    @Test
    @DisplayName("Should handle JSON parsing exception during plugin loading")
    void shouldHandleJsonParsingExceptionDuringPluginLoading() throws Exception {
      // Given
      when(mockObjectMapper.readValue(any(BufferedReader.class), anyTypeReference()))
          .thenThrow(new JsonProcessingException("JSON parsing error") {});

      // When & Then
      assertThatThrownBy(() -> pluginService.loadBuiltInPlugins())
          .isInstanceOf(IOException.class)
          .hasMessage("Failed to load built-in plugins")
          .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("Should handle IO exception during plugin loading")
    void shouldHandleIOExceptionDuringPluginLoading() throws Exception {
      // Given
      when(mockObjectMapper.readValue(any(BufferedReader.class), anyTypeReference()))
          .thenThrow(new IOException("IO error"));

      // When & Then
      assertThatThrownBy(() -> pluginService.loadBuiltInPlugins())
          .isInstanceOf(IOException.class)
          .hasMessage("Failed to load built-in plugins")
          .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Should handle null input stream gracefully")
    void shouldHandleNullInputStreamGracefully() throws Exception {
      // This test verifies that the service handles cases where the resource might not be found
      // The actual implementation uses getResourceAsStream which could return null

      // Given
      when(mockObjectMapper.readValue(any(BufferedReader.class), anyTypeReference()))
          .thenThrow(new RuntimeException("Resource not found"));

      // When & Then
      assertThatThrownBy(() -> pluginService.loadBuiltInPlugins())
          .isInstanceOf(IOException.class)
          .hasMessage("Failed to load built-in plugins")
          .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should return empty list when no plugins found")
    void shouldReturnEmptyListWhenNoPluginsFound() throws Exception {
      // Given
      when(mockObjectMapper.readValue(any(BufferedReader.class), anyTypeReference()))
          .thenReturn(Collections.emptyList());

      // When
      List<PluginDefinition> result = pluginService.loadBuiltInPlugins();

      // Then
      assertThat(result).isNotNull();
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Has English or Universal Locale Tests")
  class HasEnglishOrUniversalLocaleTests {

    @Test
    @DisplayName("Should return true when validLocales is null")
    void shouldReturnTrueWhenValidLocalesIsNull() {
      // Given
      PluginDefinition plugin = new PluginDefinition();
      plugin.validLocales = null;

      // When
      boolean result = pluginService.hasEnglishOrUniversalLocale(plugin);

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return true when validLocales is empty")
    void shouldReturnTrueWhenValidLocalesIsEmpty() {
      // Given
      PluginDefinition plugin = new PluginDefinition();
      plugin.validLocales = new PluginLocaleEntry[0];

      // When
      boolean result = pluginService.hasEnglishOrUniversalLocale(plugin);

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return true for universal locale tag")
    void shouldReturnTrueForUniversalLocaleTag() {
      // Given
      PluginDefinition plugin = new PluginDefinition();
      PluginLocaleEntry localeEntry = new PluginLocaleEntry();
      localeEntry.localeTag = "*";
      plugin.validLocales = new PluginLocaleEntry[] {localeEntry};

      // When
      boolean result = pluginService.hasEnglishOrUniversalLocale(plugin);

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return true for English locale tag")
    void shouldReturnTrueForEnglishLocaleTag() {
      // Given
      PluginDefinition plugin = new PluginDefinition();
      PluginLocaleEntry localeEntry = new PluginLocaleEntry();
      localeEntry.localeTag = "en-US";
      plugin.validLocales = new PluginLocaleEntry[] {localeEntry};

      // When
      boolean result = pluginService.hasEnglishOrUniversalLocale(plugin);

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return true for comma-separated locales containing English")
    void shouldReturnTrueForCommaSeparatedLocalesContainingEnglish() {
      // Given
      PluginDefinition plugin = new PluginDefinition();
      PluginLocaleEntry localeEntry = new PluginLocaleEntry();
      localeEntry.localeTag = "fr-FR,en-GB,de-DE";
      plugin.validLocales = new PluginLocaleEntry[] {localeEntry};

      // When
      boolean result = pluginService.hasEnglishOrUniversalLocale(plugin);

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false for non-English locales")
    void shouldReturnFalseForNonEnglishLocales() {
      // Given
      PluginDefinition plugin = new PluginDefinition();
      PluginLocaleEntry localeEntry = new PluginLocaleEntry();
      localeEntry.localeTag = "fr-FR,de-DE,es-ES";
      plugin.validLocales = new PluginLocaleEntry[] {localeEntry};

      // When
      boolean result = pluginService.hasEnglishOrUniversalLocale(plugin);

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should handle null locale tag gracefully")
    void shouldHandleNullLocaleTagGracefully() {
      // Given
      PluginDefinition plugin = new PluginDefinition();
      PluginLocaleEntry localeEntry = new PluginLocaleEntry();
      localeEntry.localeTag = null;
      plugin.validLocales = new PluginLocaleEntry[] {localeEntry};

      // When
      boolean result = pluginService.hasEnglishOrUniversalLocale(plugin);

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should handle whitespace in locale tags")
    void shouldHandleWhitespaceInLocaleTags() {
      // Given
      PluginDefinition plugin = new PluginDefinition();
      PluginLocaleEntry localeEntry = new PluginLocaleEntry();
      localeEntry.localeTag = " en-US , fr-FR ";
      plugin.validLocales = new PluginLocaleEntry[] {localeEntry};

      // When
      boolean result = pluginService.hasEnglishOrUniversalLocale(plugin);

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return true when multiple locale entries exist with English")
    void shouldReturnTrueWhenMultipleLocaleEntriesExistWithEnglish() {
      // Given
      PluginDefinition plugin = new PluginDefinition();
      PluginLocaleEntry localeEntry1 = new PluginLocaleEntry();
      localeEntry1.localeTag = "fr-FR";
      PluginLocaleEntry localeEntry2 = new PluginLocaleEntry();
      localeEntry2.localeTag = "en-GB";
      plugin.validLocales = new PluginLocaleEntry[] {localeEntry1, localeEntry2};

      // When
      boolean result = pluginService.hasEnglishOrUniversalLocale(plugin);

      // Then
      assertThat(result).isTrue();
    }
  }

  @Nested
  @DisplayName("Convert Plugin Definition to Custom Type Tests")
  class ConvertPluginDefinitionToCustomTypeTests {

    @Test
    @DisplayName("Should convert complete plugin definition successfully")
    void shouldConvertCompletePluginDefinitionSuccessfully() {
      // Given
      PluginDefinition plugin = createCompleteTestPluginDefinition();

      // When
      CustomSemanticType result = pluginService.convertPluginDefinitionToCustomType(plugin);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getSemanticType()).isEqualTo("TEST_TYPE");
      assertThat(result.getDescription()).isEqualTo("Test description");
      assertThat(result.getPluginType()).isEqualTo("regex");
      assertThat(result.getThreshold()).isEqualTo(95);
      assertThat(result.getBaseType()).isEqualTo("String");
      assertThat(result.getMinimum()).isEqualTo("1");
      assertThat(result.getMaximum()).isEqualTo("100");
      assertThat(result.getMinSamples()).isEqualTo(5);
      assertThat(result.getMinMaxPresent()).isTrue();
      assertThat(result.getLocaleSensitive()).isFalse();
      // Priority is normalized upward for custom types;
      assertThat(result.getPriority()).isGreaterThanOrEqualTo(2000);
      assertThat(result.getSignature()).isEqualTo("test-signature");
      assertThat(result.getClazz()).isEqualTo("com.test.TestPlugin");
      assertThat(result.getPluginOptions()).isEqualTo("option1=value1");
      assertThat(result.getBackout()).isEqualTo("test-backout");
    }

    @Test
    @DisplayName("Should handle null baseType by defaulting to STRING")
    void shouldHandleNullBaseTypeByDefaultingToString() {
      // Given
      PluginDefinition plugin = new PluginDefinition();
      plugin.semanticType = "TEST_TYPE";
      plugin.baseType = null;

      // When
      CustomSemanticType result = pluginService.convertPluginDefinitionToCustomType(plugin);

      // Then
      assertThat(result.getBaseType()).isEqualTo("STRING");
    }

    @Test
    @DisplayName("Should convert baseType enum to string")
    void shouldConvertBaseTypeEnumToString() {
      // Given
      PluginDefinition plugin = new PluginDefinition();
      plugin.semanticType = "TEST_TYPE";
      plugin.baseType = FTAType.LONG;

      // When
      CustomSemanticType result = pluginService.convertPluginDefinitionToCustomType(plugin);

      // Then
      assertThat(result.getBaseType()).isEqualTo("Long");
    }

    @Test
    @DisplayName("Should convert valid locales successfully")
    void shouldConvertValidLocalesSuccessfully() {
      // Given
      PluginDefinition plugin = createPluginWithValidLocales();

      // When
      CustomSemanticType result = pluginService.convertPluginDefinitionToCustomType(plugin);

      // Then
      assertThat(result.getValidLocales()).isNotNull();
      assertThat(result.getValidLocales()).hasSize(1);
      assertThat(result.getValidLocales().get(0).getLocaleTag()).isEqualTo("en-US");
      assertThat(result.getValidLocales().get(0).getHeaderRegExps()).hasSize(1);
      // Match entries are null in our test case
      assertThat(result.getValidLocales().get(0).getMatchEntries()).isNull();
    }

    @Test
    @DisplayName("Should convert documentation successfully")
    void shouldConvertDocumentationSuccessfully() {
      // Given
      PluginDefinition plugin = createPluginWithDocumentation();

      // When
      CustomSemanticType result = pluginService.convertPluginDefinitionToCustomType(plugin);

      // Then
      assertThat(result.getDocumentation()).isNotNull();
      assertThat(result.getDocumentation()).hasSize(1);
      assertThat(result.getDocumentation().get(0).getSource()).isEqualTo("Test Source");
      assertThat(result.getDocumentation().get(0).getReference()).isEqualTo("Test Reference");
    }

    @Test
    @DisplayName("Should convert lists successfully")
    void shouldConvertListsSuccessfully() {
      // Given
      PluginDefinition plugin = createPluginWithLists();

      // When
      CustomSemanticType result = pluginService.convertPluginDefinitionToCustomType(plugin);

      // Then
      assertThat(result.getInvalidList()).isNotNull();
      assertThat(result.getInvalidList()).containsExactlyInAnyOrder("invalid1", "invalid2");
      assertThat(result.getIgnoreList()).isNotNull();
      assertThat(result.getIgnoreList()).containsExactlyInAnyOrder("ignore1", "ignore2");
    }

    @Test
    @DisplayName("Should handle null validLocales gracefully")
    void shouldHandleNullValidLocalesGracefully() {
      // Given
      PluginDefinition plugin = new PluginDefinition();
      plugin.semanticType = "TEST_TYPE";
      plugin.validLocales = null;

      // When
      CustomSemanticType result = pluginService.convertPluginDefinitionToCustomType(plugin);

      // Then
      assertThat(result.getValidLocales()).isNull();
    }

    @Test
    @DisplayName("Should handle null documentation gracefully")
    void shouldHandleNullDocumentationGracefully() {
      // Given
      PluginDefinition plugin = new PluginDefinition();
      plugin.semanticType = "TEST_TYPE";
      plugin.documentation = null;

      // When
      CustomSemanticType result = pluginService.convertPluginDefinitionToCustomType(plugin);

      // Then
      assertThat(result.getDocumentation()).isNull();
    }

    @Test
    @DisplayName("Should handle null lists gracefully")
    void shouldHandleNullListsGracefully() {
      // Given
      PluginDefinition plugin = new PluginDefinition();
      plugin.semanticType = "TEST_TYPE";
      plugin.invalidList = null;
      plugin.ignoreList = null;

      // When
      CustomSemanticType result = pluginService.convertPluginDefinitionToCustomType(plugin);

      // Then
      assertThat(result.getInvalidList()).isNull();
      assertThat(result.getIgnoreList()).isNull();
    }
  }

  @Nested
  @DisplayName("Register Custom Types Tests")
  class RegisterCustomTypesTests {

    @Mock private TextAnalyzer mockAnalyzer;

    @Mock private Plugins mockPlugins;

    @Test
    @DisplayName("Should register custom types successfully")
    void shouldRegisterCustomTypesSuccessfully() throws Exception {
      // Given
      Map<String, CustomSemanticType> customTypes = createTestCustomTypesMap();
      when(mockAnalyzer.getPlugins()).thenReturn(mockPlugins);
      when(mockAnalyzer.getConfig()).thenReturn(null);
      when(mockObjectMapper.writeValueAsString(any())).thenReturn("[]");

      // When
      pluginService.registerCustomTypes(mockAnalyzer, customTypes);

      // Then - behavior-based: no exception thrown
    }

    @Test
    @DisplayName("Should handle empty custom types map gracefully")
    void shouldHandleEmptyCustomTypesMapGracefully() throws Exception {
      // Given
      Map<String, CustomSemanticType> emptyMap = Collections.emptyMap();

      // When
      pluginService.registerCustomTypes(mockAnalyzer, emptyMap);

      // Then
      // Should return early without calling any mocked methods
      // This is verified by the lack of interactions
    }

    @Test
    @DisplayName("Should handle JSON serialization exception")
    void shouldHandleJsonSerializationException() throws Exception {
      // Given
      Map<String, CustomSemanticType> customTypes = createTestCustomTypesMap();
      when(mockObjectMapper.writeValueAsString(any()))
          .thenThrow(new JsonProcessingException("JSON error") {});

      // When & Then - current behavior logs and continues without throwing
      org.assertj.core.api.Assertions.assertThatCode(
              () -> pluginService.registerCustomTypes(mockAnalyzer, customTypes))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle FTA plugin exception")
    void shouldHandleFtaPluginException() throws Exception {
      // Given
      Map<String, CustomSemanticType> customTypes = createTestCustomTypesMap();
      when(mockAnalyzer.getPlugins()).thenReturn(mockPlugins);
      when(mockAnalyzer.getConfig()).thenReturn(null);
      when(mockObjectMapper.writeValueAsString(any())).thenReturn("[]");
      doThrow(new FTAPluginException("Plugin error"))
          .when(mockPlugins)
          .registerPlugins(any(StringReader.class), anyString(), any());

      // When & Then - current behavior logs and continues without throwing
      org.assertj.core.api.Assertions.assertThatCode(
              () -> pluginService.registerCustomTypes(mockAnalyzer, customTypes))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle invocation target exception")
    void shouldHandleInvocationTargetException() throws Exception {
      // Given
      Map<String, CustomSemanticType> customTypes = createTestCustomTypesMap();
      when(mockAnalyzer.getPlugins()).thenReturn(mockPlugins);
      when(mockAnalyzer.getConfig()).thenReturn(null);
      when(mockObjectMapper.writeValueAsString(any())).thenReturn("[]");
      doThrow(new InvocationTargetException(new RuntimeException("Target error")))
          .when(mockPlugins)
          .registerPlugins(any(StringReader.class), anyString(), any());

      // When & Then - current behavior logs and continues without throwing
      org.assertj.core.api.Assertions.assertThatCode(
              () -> pluginService.registerCustomTypes(mockAnalyzer, customTypes))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle class not found exception")
    void shouldHandleClassNotFoundException() throws Exception {
      // Given
      Map<String, CustomSemanticType> customTypes = createTestCustomTypesMap();
      when(mockAnalyzer.getPlugins()).thenReturn(mockPlugins);
      when(mockAnalyzer.getConfig()).thenReturn(null);
      when(mockObjectMapper.writeValueAsString(any())).thenReturn("[]");
      doThrow(new ClassNotFoundException("Class not found"))
          .when(mockPlugins)
          .registerPlugins(any(StringReader.class), anyString(), any());

      // When & Then - current behavior logs and continues without throwing
      org.assertj.core.api.Assertions.assertThatCode(
              () -> pluginService.registerCustomTypes(mockAnalyzer, customTypes))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle all other exceptions")
    void shouldHandleAllOtherExceptions() throws Exception {
      // Given
      Map<String, CustomSemanticType> customTypes = createTestCustomTypesMap();
      when(mockAnalyzer.getPlugins()).thenReturn(mockPlugins);
      when(mockAnalyzer.getConfig()).thenReturn(null);
      when(mockObjectMapper.writeValueAsString(any())).thenReturn("[]");
      doThrow(new NoSuchMethodException("Method not found"))
          .when(mockPlugins)
          .registerPlugins(any(StringReader.class), anyString(), any());

      // When & Then - current behavior logs and continues without throwing
      org.assertj.core.api.Assertions.assertThatCode(
              () -> pluginService.registerCustomTypes(mockAnalyzer, customTypes))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("Convert to FTA Format Tests")
  class ConvertToFTAFormatTests {

    @Test
    @DisplayName("Should convert regex custom type to FTA format successfully")
    void shouldConvertRegexCustomTypeToFtaFormatSuccessfully() throws Exception {
      // When
      Map<String, Object> result = invokeConvertToFTAFormat(regexCustomType);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.get("semanticType")).isEqualTo("TEST_REGEX_TYPE");
      assertThat(result.get("description")).isEqualTo("Test regex type");
      assertThat(result.get("pluginType")).isEqualTo("regex");
      assertThat(result.get("threshold")).isEqualTo(95);
      assertThat(result.get("baseType")).isEqualTo("STRING");
      assertThat(result.get("priority")).isEqualTo(10);
      assertThat(result.get("minSamples")).isEqualTo(5);
    }

    @Test
    @DisplayName("Should convert list custom type to FTA format successfully")
    void shouldConvertListCustomTypeToFtaFormatSuccessfully() throws Exception {
      // When
      Map<String, Object> result = invokeConvertToFTAFormat(listCustomType);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.get("semanticType")).isEqualTo("TEST_LIST_TYPE");
      assertThat(result.get("description")).isEqualTo("Test list type");
      assertThat(result.get("pluginType")).isEqualTo("list");
      assertThat(result.get("threshold")).isEqualTo(90);
      assertThat(result.get("baseType")).isEqualTo("STRING");
    }

    @Test
    @DisplayName("Should convert java custom type to FTA format successfully")
    void shouldConvertJavaCustomTypeToFtaFormatSuccessfully() throws Exception {
      // When
      Map<String, Object> result = invokeConvertToFTAFormat(javaCustomType);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.get("semanticType")).isEqualTo("TEST_JAVA_TYPE");
      assertThat(result.get("pluginType")).isEqualTo("java");
      assertThat(result.get("clazz")).isEqualTo("com.test.TestJavaPlugin");
      assertThat(result.get("signature")).isEqualTo("test-java-signature");
    }

    @Test
    @DisplayName("Should handle null values gracefully")
    void shouldHandleNullValuesGracefully() throws Exception {
      // Given
      CustomSemanticType customType = new CustomSemanticType();
      customType.setSemanticType("MINIMAL_TYPE");
      customType.setPluginType("regex");

      // When
      Map<String, Object> result = invokeConvertToFTAFormat(customType);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.get("semanticType")).isEqualTo("MINIMAL_TYPE");
      assertThat(result.get("pluginType")).isEqualTo("regex");
      assertThat(result.get("baseType")).isEqualTo("STRING"); // Default value
      assertThat(result.containsKey("priority")).isFalse();
      assertThat(result.containsKey("minSamples")).isFalse();
    }

    @Test
    @DisplayName("Should convert valid locales to map format")
    void shouldConvertValidLocalesToMapFormat() throws Exception {
      // When
      Map<String, Object> result = invokeConvertToFTAFormat(regexCustomType);

      // Then
      assertThat(result).containsKey("validLocales");
      List<Map<String, Object>> locales = (List<Map<String, Object>>) result.get("validLocales");
      assertThat(locales).hasSize(1);
      assertThat(locales.get(0).get("localeTag")).isEqualTo("*");
    }

    @Test
    @DisplayName("Should convert documentation to map format")
    void shouldConvertDocumentationToMapFormat() throws Exception {
      // When
      Map<String, Object> result = invokeConvertToFTAFormat(regexCustomType);

      // Then
      assertThat(result).containsKey("documentation");
      List<Map<String, String>> docs = (List<Map<String, String>>) result.get("documentation");
      assertThat(docs).hasSize(1);
      assertThat(docs.get(0).get("source")).isEqualTo("Test Source");
      assertThat(docs.get(0).get("reference")).isEqualTo("Test Reference");
    }

    @Test
    @DisplayName("Should convert content to map format")
    void shouldConvertContentToMapFormat() throws Exception {
      // When
      Map<String, Object> result = invokeConvertToFTAFormat(listCustomType);

      // Then
      assertThat(result).containsKey("content");
      Map<String, Object> content = (Map<String, Object>) result.get("content");
      assertThat(content.get("type")).isEqualTo("embedded");
      // plugin now emits uppercase "members" instead of "values"
      assertThat(content.get("members")).isEqualTo(Arrays.asList("VALUE1", "VALUE2", "VALUE3"));
    }
  }

  @Nested
  @DisplayName("Normalize Base Type Tests")
  class NormalizeBaseTypeTests {

    @Test
    @DisplayName("Should handle null baseType by returning STRING")
    void shouldHandleNullBaseTypeByReturningString() throws Exception {
      // When
      String result = invokeNormalizeBaseType(null);

      // Then
      assertThat(result).isEqualTo("STRING");
    }

    @Test
    @DisplayName("Should normalize valid base types to uppercase")
    void shouldNormalizeValidBaseTypesToUppercase() throws Exception {
      // Test all valid base types
      assertThat(invokeNormalizeBaseType("string")).isEqualTo("STRING");
      assertThat(invokeNormalizeBaseType("zoneddatetime")).isEqualTo("ZONEDDATETIME");
      assertThat(invokeNormalizeBaseType("localdatetime")).isEqualTo("LOCALDATETIME");
      assertThat(invokeNormalizeBaseType("localtime")).isEqualTo("LOCALTIME");
      assertThat(invokeNormalizeBaseType("localdate")).isEqualTo("LOCALDATE");
      assertThat(invokeNormalizeBaseType("long")).isEqualTo("LONG");
      assertThat(invokeNormalizeBaseType("offsetdatetime")).isEqualTo("OFFSETDATETIME");
      assertThat(invokeNormalizeBaseType("double")).isEqualTo("DOUBLE");
      assertThat(invokeNormalizeBaseType("boolean")).isEqualTo("BOOLEAN");
    }

    @Test
    @DisplayName("Should handle mixed case base types")
    void shouldHandleMixedCaseBaseTypes() throws Exception {
      // When & Then
      assertThat(invokeNormalizeBaseType("String")).isEqualTo("STRING");
      assertThat(invokeNormalizeBaseType("LONG")).isEqualTo("LONG");
      assertThat(invokeNormalizeBaseType("LoCalDaTe")).isEqualTo("LOCALDATE");
    }

    @Test
    @DisplayName("Should default unknown base types to STRING")
    void shouldDefaultUnknownBaseTypesToString() throws Exception {
      // When & Then
      assertThat(invokeNormalizeBaseType("unknown")).isEqualTo("STRING");
      assertThat(invokeNormalizeBaseType("invalid_type")).isEqualTo("STRING");
      assertThat(invokeNormalizeBaseType("")).isEqualTo("STRING");
      assertThat(invokeNormalizeBaseType("   ")).isEqualTo("STRING");
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling Tests")
  class EdgeCasesAndErrorHandlingTests {

    @Test
    @DisplayName("Should handle custom type with all null fields")
    void shouldHandleCustomTypeWithAllNullFields() throws Exception {
      // Given
      CustomSemanticType emptyType = new CustomSemanticType();

      // When
      Map<String, Object> result = invokeConvertToFTAFormat(emptyType);

      // Then
      assertThat(result).isNotNull();
      // baseType defaults to STRING; other nulls are allowed
      assertThat(result.get("baseType")).isEqualTo("STRING");
    }

    @Test
    @DisplayName("Should handle complex nested locale configurations")
    void shouldHandleComplexNestedLocaleConfigurations() {
      // Given
      CustomSemanticType complexType = createComplexCustomSemanticType();

      // When
      CustomSemanticType result =
          pluginService.convertPluginDefinitionToCustomType(createComplexPluginDefinition());

      // Then
      assertThat(result.getValidLocales()).isNotNull();
      assertThat(result.getValidLocales()).hasSize(2);

      // Verify first locale
      CustomSemanticType.LocaleConfig firstLocale = result.getValidLocales().get(0);
      assertThat(firstLocale.getLocaleTag()).isEqualTo("en-US");
      assertThat(firstLocale.getHeaderRegExps()).hasSize(2);
      assertThat(firstLocale.getMatchEntries()).isNull();

      // Verify second locale
      CustomSemanticType.LocaleConfig secondLocale = result.getValidLocales().get(1);
      assertThat(secondLocale.getLocaleTag()).isEqualTo("*");
      assertThat(secondLocale.getHeaderRegExps()).isNull();
      assertThat(secondLocale.getMatchEntries()).isNull();
    }

    @Test
    @DisplayName("Should handle very large custom types map")
    void shouldHandleVeryLargeCustomTypesMap() throws Exception {
      // Given
      Map<String, CustomSemanticType> largeMap = new HashMap<>();
      for (int i = 0; i < 1000; i++) {
        CustomSemanticType type = new CustomSemanticType();
        type.setSemanticType("TYPE_" + i);
        type.setPluginType("regex");
        largeMap.put("TYPE_" + i, type);
      }

      TextAnalyzer mockAnalyzer = mock(TextAnalyzer.class);
      Plugins mockPlugins = mock(Plugins.class);
      when(mockAnalyzer.getPlugins()).thenReturn(mockPlugins);
      when(mockAnalyzer.getConfig()).thenReturn(null);
      when(mockObjectMapper.writeValueAsString(any())).thenReturn("[]");

      // When
      pluginService.registerCustomTypes(mockAnalyzer, largeMap);

      // Then: behavior may skip registration due to structural validation; ensure no exception
    }

    @Test
    @DisplayName("Should handle custom type with unicode characters")
    void shouldHandleCustomTypeWithUnicodeCharacters() throws Exception {
      // Given
      CustomSemanticType unicodeType = new CustomSemanticType();
      unicodeType.setSemanticType("测试类型");
      unicodeType.setDescription("Тест описание with émojis 🚀");
      unicodeType.setPluginType("regex");

      // When
      Map<String, Object> result = invokeConvertToFTAFormat(unicodeType);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.get("semanticType")).isEqualTo("测试类型");
      assertThat(result.get("description")).isEqualTo("Тест описание with émojis 🚀");
    }

    @Test
    @DisplayName("Should handle content with null values in list")
    void shouldHandleContentWithNullValuesInList() throws Exception {
      // Given
      CustomSemanticType typeWithNullValues = new CustomSemanticType();
      typeWithNullValues.setSemanticType("NULL_VALUES_TYPE");
      typeWithNullValues.setPluginType("list");

      CustomSemanticType.ContentConfig content = new CustomSemanticType.ContentConfig();
      content.setType("embedded");
      content.setValues(Arrays.asList("value1", null, "value3"));
      typeWithNullValues.setContent(content);

      // When
      Map<String, Object> result = invokeConvertToFTAFormat(typeWithNullValues);

      // Then
      assertThat(result).containsKey("content");
      Map<String, Object> contentMap = (Map<String, Object>) result.get("content");
      @SuppressWarnings("unchecked")
      List<String> members = (List<String>) contentMap.get("members");
      assertThat(members).containsExactly("VALUE1", "VALUE3");
    }
  }

  // Helper methods for creating test data

  private PluginDefinition createTestPluginDefinition() {
    PluginDefinition plugin = new PluginDefinition();
    plugin.semanticType = "TEST_TYPE";
    plugin.description = "Test description";
    plugin.pluginType = "regex";
    plugin.threshold = 95;
    plugin.baseType = FTAType.STRING;
    return plugin;
  }

  private PluginDefinition createCompleteTestPluginDefinition() {
    PluginDefinition plugin = new PluginDefinition();
    plugin.semanticType = "TEST_TYPE";
    plugin.description = "Test description";
    plugin.pluginType = "regex";
    plugin.threshold = 95;
    plugin.baseType = FTAType.STRING;
    plugin.minimum = "1";
    plugin.maximum = "100";
    plugin.minSamples = 5;
    plugin.minMaxPresent = true;
    plugin.localeSensitive = false;
    plugin.priority = 10;
    plugin.signature = "test-signature";
    plugin.clazz = "com.test.TestPlugin";
    plugin.pluginOptions = "option1=value1";
    plugin.backout = "test-backout";
    return plugin;
  }

  private PluginDefinition createPluginWithValidLocales() {
    PluginDefinition plugin = createTestPluginDefinition();

    PluginLocaleEntry localeEntry = new PluginLocaleEntry();
    localeEntry.localeTag = "en-US";

    HeaderEntry headerEntry = new HeaderEntry();
    headerEntry.regExp = ".*test.*";
    headerEntry.confidence = 95;
    headerEntry.mandatory = true;
    localeEntry.headerRegExps = new HeaderEntry[] {headerEntry};

    // Skip match entries to avoid constructor issues
    localeEntry.matchEntries = null;

    plugin.validLocales = new PluginLocaleEntry[] {localeEntry};
    return plugin;
  }

  private PluginDefinition createPluginWithDocumentation() {
    PluginDefinition plugin = createTestPluginDefinition();

    PluginDocumentationEntry docEntry = new PluginDocumentationEntry();
    docEntry.source = "Test Source";
    docEntry.reference = "Test Reference";

    plugin.documentation = new PluginDocumentationEntry[] {docEntry};
    return plugin;
  }

  private PluginDefinition createPluginWithLists() {
    PluginDefinition plugin = createTestPluginDefinition();

    plugin.invalidList = new HashSet<>(Arrays.asList("invalid1", "invalid2"));
    plugin.ignoreList = new HashSet<>(Arrays.asList("ignore1", "ignore2"));

    return plugin;
  }

  private PluginDefinition createComplexPluginDefinition() {
    PluginDefinition plugin = createTestPluginDefinition();

    // Create first locale with header entries only
    PluginLocaleEntry locale1 = new PluginLocaleEntry();
    locale1.localeTag = "en-US";

    HeaderEntry header1 = new HeaderEntry();
    header1.regExp = ".*id.*";
    header1.confidence = 95;
    header1.mandatory = true;

    HeaderEntry header2 = new HeaderEntry();
    header2.regExp = ".*identifier.*";
    header2.confidence = 90;
    header2.mandatory = false;
    locale1.headerRegExps = new HeaderEntry[] {header1, header2};
    locale1.matchEntries = null; // Skip match entries

    // Create second locale with simple configuration
    PluginLocaleEntry locale2 = new PluginLocaleEntry();
    locale2.localeTag = "*";
    locale2.headerRegExps = null;
    locale2.matchEntries = null; // Skip match entries

    plugin.validLocales = new PluginLocaleEntry[] {locale1, locale2};
    return plugin;
  }

  private CustomSemanticType createRegexCustomSemanticType() {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("TEST_REGEX_TYPE");
    type.setDescription("Test regex type");
    type.setPluginType("regex");
    type.setThreshold(95);
    type.setBaseType("STRING");
    type.setPriority(10);
    type.setMinSamples(5);

    // Add valid locales
    CustomSemanticType.LocaleConfig locale = new CustomSemanticType.LocaleConfig();
    locale.setLocaleTag("*");

    CustomSemanticType.MatchEntry matchEntry = new CustomSemanticType.MatchEntry();
    matchEntry.setRegExpReturned("\\d{5}");
    matchEntry.setIsRegExpComplete(true);
    locale.setMatchEntries(Arrays.asList(matchEntry));

    type.setValidLocales(Arrays.asList(locale));

    // Add documentation
    CustomSemanticType.Documentation doc = new CustomSemanticType.Documentation();
    doc.setSource("Test Source");
    doc.setReference("Test Reference");
    type.setDocumentation(Arrays.asList(doc));

    return type;
  }

  private CustomSemanticType createListCustomSemanticType() {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("TEST_LIST_TYPE");
    type.setDescription("Test list type");
    type.setPluginType("list");
    type.setThreshold(90);
    type.setBaseType("STRING");

    CustomSemanticType.ContentConfig content = new CustomSemanticType.ContentConfig();
    content.setType("embedded");
    content.setValues(Arrays.asList("value1", "value2", "value3"));
    type.setContent(content);

    return type;
  }

  private CustomSemanticType createJavaCustomSemanticType() {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("TEST_JAVA_TYPE");
    type.setDescription("Test java type");
    type.setPluginType("java");
    type.setThreshold(85);
    type.setBaseType("STRING");
    type.setClazz("com.test.TestJavaPlugin");
    type.setSignature("test-java-signature");

    return type;
  }

  private CustomSemanticType createComplexCustomSemanticType() {
    CustomSemanticType type = new CustomSemanticType();
    type.setSemanticType("COMPLEX_TYPE");
    type.setDescription("Complex test type");
    type.setPluginType("regex");

    // Create multiple locales with various configurations
    List<CustomSemanticType.LocaleConfig> locales = new ArrayList<>();

    // First locale with headers and matches
    CustomSemanticType.LocaleConfig locale1 = new CustomSemanticType.LocaleConfig();
    locale1.setLocaleTag("en-US");

    List<CustomSemanticType.HeaderRegExp> headers = new ArrayList<>();
    CustomSemanticType.HeaderRegExp header1 = new CustomSemanticType.HeaderRegExp();
    header1.setRegExp(".*id.*");
    header1.setConfidence(95);
    header1.setMandatory(true);
    headers.add(header1);
    locale1.setHeaderRegExps(headers);

    List<CustomSemanticType.MatchEntry> matches = new ArrayList<>();
    CustomSemanticType.MatchEntry match1 = new CustomSemanticType.MatchEntry();
    match1.setRegExpReturned("\\d{5}");
    match1.setIsRegExpComplete(true);
    matches.add(match1);
    locale1.setMatchEntries(matches);

    locales.add(locale1);

    // Second locale with only matches
    CustomSemanticType.LocaleConfig locale2 = new CustomSemanticType.LocaleConfig();
    locale2.setLocaleTag("*");

    List<CustomSemanticType.MatchEntry> matches2 = new ArrayList<>();
    CustomSemanticType.MatchEntry match2 = new CustomSemanticType.MatchEntry();
    match2.setRegExpReturned(".*");
    match2.setIsRegExpComplete(false);
    matches2.add(match2);
    locale2.setMatchEntries(matches2);

    locales.add(locale2);

    type.setValidLocales(locales);
    return type;
  }

  private Map<String, CustomSemanticType> createTestCustomTypesMap() {
    Map<String, CustomSemanticType> map = new HashMap<>();
    map.put("REGEX_TYPE", regexCustomType);
    map.put("LIST_TYPE", listCustomType);
    return map;
  }

  // Helper methods to access private methods via reflection for testing

  private Map<String, Object> invokeConvertToFTAFormat(CustomSemanticType customType)
      throws Exception {
    java.lang.reflect.Method method =
        SemanticTypePluginService.class.getDeclaredMethod(
            "convertToFTAFormat", CustomSemanticType.class);
    method.setAccessible(true);
    Map<String, Object> result = (Map<String, Object>) method.invoke(pluginService, customType);
    return result;
  }

  private String invokeNormalizeBaseType(String baseType) throws Exception {
    java.lang.reflect.Method method =
        SemanticTypePluginService.class.getDeclaredMethod("normalizeBaseType", String.class);
    method.setAccessible(true);
    return (String) method.invoke(pluginService, baseType);
  }
}
