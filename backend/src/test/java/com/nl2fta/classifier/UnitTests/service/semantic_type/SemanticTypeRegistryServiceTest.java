package com.nl2fta.classifier.service.semantic_type.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cobber.fta.PluginDefinition;
import com.cobber.fta.PluginDocumentationEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;

@ExtendWith(MockitoExtension.class)
@DisplayName("Semantic Type Registry Service Unit Tests")
@SuppressWarnings({"unchecked", "rawtypes"})
class SemanticTypeRegistryServiceTest {

  @Mock private ObjectMapper objectMapper;

  private SemanticTypeRegistryService registryService;

  @BeforeEach
  void setUp() {
    registryService = new SemanticTypeRegistryService(objectMapper);
    // Set default values
    ReflectionTestUtils.setField(registryService, "cacheEnabled", true);
    ReflectionTestUtils.setField(registryService, "cacheMaxSize", 1000L);
    ReflectionTestUtils.setField(registryService, "cacheExpireMinutes", 60L);
    ReflectionTestUtils.setField(registryService, "pluginResource", "/reference/plugins.json");
  }

  @Test
  @DisplayName("Should initialize cache when enabled")
  void shouldInitializeCacheWhenEnabled() throws IOException {
    // Given
    List<PluginDefinition> mockPlugins = createMockPlugins();
    when(objectMapper.readValue(any(java.io.BufferedReader.class), any(TypeReference.class)))
        .thenReturn(mockPlugins);

    // When
    registryService.init();

    // Then
    Map<String, SemanticTypeRegistryService.SemanticTypeInfo> builtInTypes =
        registryService.getAllBuiltInTypes();
    assertEquals(2, builtInTypes.size());
    assertTrue(builtInTypes.containsKey("EMAIL.ADDRESS"));
    assertTrue(builtInTypes.containsKey("NAME.FIRST"));
  }

  @Test
  @DisplayName("Should not initialize cache when disabled")
  void shouldNotInitializeCacheWhenDisabled() throws IOException {
    // Given
    ReflectionTestUtils.setField(registryService, "cacheEnabled", false);
    List<PluginDefinition> mockPlugins = createMockPlugins();
    when(objectMapper.readValue(any(java.io.BufferedReader.class), any(TypeReference.class)))
        .thenReturn(mockPlugins);

    // When
    registryService.init();

    // Then
    Cache<String, String> cache =
        (Cache<String, String>) ReflectionTestUtils.getField(registryService, "descriptionCache");
    assertNull(cache);
  }

  @Test
  @DisplayName("Should get description from cache when available")
  void shouldGetDescriptionFromCacheWhenAvailable() throws IOException {
    // Given
    List<PluginDefinition> mockPlugins = createMockPlugins();
    when(objectMapper.readValue(any(java.io.BufferedReader.class), any(TypeReference.class)))
        .thenReturn(mockPlugins);
    registryService.init();

    // When - first call should load from built-in types
    String description1 = registryService.getDescription("EMAIL.ADDRESS");
    // Second call should use cache
    String description2 = registryService.getDescription("EMAIL.ADDRESS");

    // Then
    assertEquals("Email address", description1);
    assertEquals("Email address", description2);
    assertEquals(description1, description2);
  }

  @Test
  @DisplayName("Should return semantic type when description not found")
  void shouldReturnSemanticTypeWhenDescriptionNotFound() throws IOException {
    // Given
    List<PluginDefinition> mockPlugins = createMockPlugins();
    when(objectMapper.readValue(any(java.io.BufferedReader.class), any(TypeReference.class)))
        .thenReturn(mockPlugins);
    registryService.init();

    // When
    String description = registryService.getDescription("UNKNOWN.TYPE");

    // Then
    assertEquals("UNKNOWN.TYPE", description);
  }

  @Test
  @DisplayName("Should handle null semantic type")
  void shouldHandleNullSemanticType() throws IOException {
    // Given
    List<PluginDefinition> mockPlugins = createMockPlugins();
    when(objectMapper.readValue(any(java.io.BufferedReader.class), any(TypeReference.class)))
        .thenReturn(mockPlugins);
    registryService.init();

    // When
    String description = registryService.getDescription(null);

    // Then
    assertNull(description);
  }

  @Test
  @DisplayName("Should get semantic type info")
  void shouldGetSemanticTypeInfo() throws IOException {
    // Given
    List<PluginDefinition> mockPlugins = createMockPlugins();
    when(objectMapper.readValue(any(java.io.BufferedReader.class), any(TypeReference.class)))
        .thenReturn(mockPlugins);
    registryService.init();

    // When
    SemanticTypeRegistryService.SemanticTypeInfo info =
        registryService.getSemanticTypeInfo("EMAIL.ADDRESS");

    // Then
    assertNotNull(info);
    assertEquals("EMAIL.ADDRESS", info.getSemanticType());
    assertEquals("Email address", info.getDescription());
    assertEquals("regex", info.getPluginType());
    assertEquals("String", info.getBaseType());
    assertEquals(95, info.getThreshold());
    assertEquals(10, info.getPriority());
    assertEquals("https://en.wikipedia.org/wiki/Email_address", info.getDocumentationUrl());
  }

  @Test
  @DisplayName("Should check if type is built-in")
  void shouldCheckIfTypeIsBuiltIn() throws IOException {
    // Given
    List<PluginDefinition> mockPlugins = createMockPlugins();
    when(objectMapper.readValue(any(java.io.BufferedReader.class), any(TypeReference.class)))
        .thenReturn(mockPlugins);
    registryService.init();

    // When & Then
    assertTrue(registryService.isBuiltInType("EMAIL.ADDRESS"));
    assertTrue(registryService.isBuiltInType("NAME.FIRST"));
    assertFalse(registryService.isBuiltInType("CUSTOM.TYPE"));
  }

  @Test
  @DisplayName("Should clear cache")
  void shouldClearCache() throws IOException {
    // Given
    List<PluginDefinition> mockPlugins = createMockPlugins();
    when(objectMapper.readValue(any(java.io.BufferedReader.class), any(TypeReference.class)))
        .thenReturn(mockPlugins);
    registryService.init();

    // Prime the cache
    registryService.getDescription("EMAIL.ADDRESS");

    // When
    registryService.clearCache();

    // Then
    // Cache should be cleared, but built-in types should still be available
    Map<String, SemanticTypeRegistryService.SemanticTypeInfo> builtInTypes =
        registryService.getAllBuiltInTypes();
    assertEquals(2, builtInTypes.size());
  }

  @Test
  @DisplayName("Should reload registry")
  void shouldReloadRegistry() throws IOException {
    // Given
    List<PluginDefinition> initialPlugins = createMockPlugins();
    List<PluginDefinition> reloadedPlugins = createReloadedPlugins();

    when(objectMapper.readValue(any(java.io.BufferedReader.class), any(TypeReference.class)))
        .thenReturn(initialPlugins)
        .thenReturn(reloadedPlugins);

    registryService.init();

    // When
    registryService.reload();

    // Then
    Map<String, SemanticTypeRegistryService.SemanticTypeInfo> builtInTypes =
        registryService.getAllBuiltInTypes();
    assertEquals(3, builtInTypes.size());
    assertTrue(builtInTypes.containsKey("EMAIL.ADDRESS"));
    assertTrue(builtInTypes.containsKey("NAME.FIRST"));
    assertTrue(builtInTypes.containsKey("PHONE.NUMBER"));
  }

  @Test
  @DisplayName("Should handle IOException during initialization")
  void shouldHandleIOExceptionDuringInitialization() throws IOException {
    // Given
    when(objectMapper.readValue(any(java.io.BufferedReader.class), any(TypeReference.class)))
        .thenThrow(new IOException("Test error"));

    // When & Then
    assertThrows(RuntimeException.class, () -> registryService.init());
  }

  @Test
  @DisplayName("Should handle plugin without documentation")
  void shouldHandlePluginWithoutDocumentation() throws IOException {
    // Given
    List<PluginDefinition> plugins = new ArrayList<>();
    PluginDefinition plugin = new PluginDefinition();
    plugin.semanticType = "SIMPLE.TYPE";
    plugin.description = "Simple type";
    plugin.pluginType = "java";
    plugin.threshold = 90;
    plugin.priority = 5;
    plugins.add(plugin);

    when(objectMapper.readValue(any(java.io.BufferedReader.class), any(TypeReference.class)))
        .thenReturn(plugins);

    // When
    registryService.init();

    // Then
    SemanticTypeRegistryService.SemanticTypeInfo info =
        registryService.getSemanticTypeInfo("SIMPLE.TYPE");
    assertNotNull(info);
    assertNull(info.getDocumentationUrl());
  }

  @Test
  @DisplayName("Should work without cache when disabled")
  void shouldWorkWithoutCacheWhenDisabled() throws IOException {
    // Given
    ReflectionTestUtils.setField(registryService, "cacheEnabled", false);
    List<PluginDefinition> mockPlugins = createMockPlugins();
    when(objectMapper.readValue(any(java.io.BufferedReader.class), any(TypeReference.class)))
        .thenReturn(mockPlugins);
    registryService.init();

    // When
    String description = registryService.getDescription("EMAIL.ADDRESS");

    // Then
    assertEquals("Email address", description);
  }

  private List<PluginDefinition> createMockPlugins() {
    List<PluginDefinition> plugins = new ArrayList<>();

    // Email plugin
    PluginDefinition emailPlugin = new PluginDefinition();
    emailPlugin.semanticType = "EMAIL.ADDRESS";
    emailPlugin.description = "Email address";
    emailPlugin.pluginType = "regex";
    emailPlugin.threshold = 95;
    emailPlugin.priority = 10;

    PluginDocumentationEntry emailDoc = new PluginDocumentationEntry();
    emailDoc.source = "wikipedia";
    emailDoc.reference = "https://en.wikipedia.org/wiki/Email_address";
    emailPlugin.documentation = new PluginDocumentationEntry[] {emailDoc};

    plugins.add(emailPlugin);

    // Name plugin
    PluginDefinition namePlugin = new PluginDefinition();
    namePlugin.semanticType = "NAME.FIRST";
    namePlugin.description = "First name";
    namePlugin.pluginType = "list";
    namePlugin.threshold = 85;
    namePlugin.priority = 8;

    PluginDocumentationEntry nameDoc = new PluginDocumentationEntry();
    nameDoc.source = "wikidata";
    nameDoc.reference = "https://www.wikidata.org/wiki/Q202444";
    namePlugin.documentation = new PluginDocumentationEntry[] {nameDoc};

    plugins.add(namePlugin);

    return plugins;
  }

  private List<PluginDefinition> createReloadedPlugins() {
    List<PluginDefinition> plugins = createMockPlugins();

    // Add phone plugin
    PluginDefinition phonePlugin = new PluginDefinition();
    phonePlugin.semanticType = "PHONE.NUMBER";
    phonePlugin.description = "Phone number";
    phonePlugin.pluginType = "regex";
    phonePlugin.threshold = 90;
    phonePlugin.priority = 9;

    plugins.add(phonePlugin);

    return plugins;
  }
}
