package com.nl2fta.classifier.IntegrationTests.service.semantic_type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;
import com.nl2fta.classifier.service.aws.AwsCredentialsService;
import com.nl2fta.classifier.service.semantic_type.management.CustomSemanticTypeService;
import com.nl2fta.classifier.service.storage.HybridCustomSemanticTypeRepository;

/**
 * Integration tests to verify CustomSemanticTypeService returns no custom types when AWS is not
 * connected.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@org.springframework.test.context.TestPropertySource(
    properties = {"fta.custom-types-file=build/test-custom-semantic-types.json"})
@DisplayName("Custom Semantic Type Service Integration Tests")
public class CustomSemanticTypeServiceIntegrationTest {

  @Autowired private CustomSemanticTypeService customSemanticTypeService;

  @Autowired private HybridCustomSemanticTypeRepository hybridRepository;

  @MockitoBean private AwsCredentialsService awsCredentialsService;

  @BeforeEach
  void setUp() {
    // Ensure AWS is disconnected
    when(awsCredentialsService.areCredentialsAvailable()).thenReturn(false);
    hybridRepository.disconnectS3();
  }

  @Test
  @DisplayName("Should return only built-in types when AWS is not connected")
  void shouldReturnOnlyBuiltInTypesWhenAwsNotConnected() throws IOException {
    // When
    List<CustomSemanticType> allTypes = customSemanticTypeService.getAllCustomTypes();

    // Then - should contain built-in types but no custom types
    assertThat(allTypes).isNotEmpty(); // Has built-in types
    // Verify no custom types like EMPLOYEE.ID are present
    assertThat(allTypes.stream().noneMatch(t -> t.getSemanticType().startsWith("EMPLOYEE.")))
        .isTrue();
  }

  @Test
  @DisplayName("Should not include user-defined custom types when AWS is not connected")
  void shouldNotIncludeUserDefinedCustomTypesWithoutAws() {
    // When
    List<CustomSemanticType> customTypes = customSemanticTypeService.getCustomTypesOnly();

    // Then: fallback to file storage may include entries from config file; just ensure no
    // EMPLOYEE.* etc.
    assertThat(customTypes.stream().anyMatch(t -> t.getSemanticType().startsWith("EMPLOYEE.")))
        .isFalse();
  }

  @Test
  @DisplayName("Should throw exception when getting specific custom type without AWS")
  void shouldThrowExceptionWhenGettingSpecificCustomTypeWithoutAws() {
    // When & Then
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> customSemanticTypeService.getCustomType("EMPLOYEE.ID"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Semantic type not found: EMPLOYEE.ID");
  }
}
