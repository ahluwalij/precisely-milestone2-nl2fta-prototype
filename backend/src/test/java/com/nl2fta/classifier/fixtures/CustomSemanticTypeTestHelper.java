package com.nl2fta.classifier.fixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2fta.classifier.dto.semantic_type.CustomSemanticType;

public final class CustomSemanticTypeTestHelper {

  private CustomSemanticTypeTestHelper() {}

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static Path createTempCustomTypesFile(List<CustomSemanticType> types) throws IOException {
    Path tempFile = Files.createTempFile("custom-types", ".json");
    Files.writeString(
        tempFile, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(types));
    return tempFile;
  }

  public static Path createTempCustomTypesFile(CustomSemanticType type) throws IOException {
    return createTempCustomTypesFile(java.util.List.of(type));
  }

  public static Path createEmptyTempCustomTypesFile() throws IOException {
    Path tempFile = Files.createTempFile("custom-types-empty", ".json");
    Files.writeString(tempFile, "[]");
    return tempFile;
  }

  public static CustomSemanticType createEmployeeIdType() {
    CustomSemanticType custom = new CustomSemanticType();
    custom.setSemanticType("IDENTITY.EMPLOYEE_ID");
    custom.setDescription("Employee ID");
    custom.setPluginType("regex");
    return custom;
  }

  public static Path createTempDirectory() throws IOException {
    return Files.createTempDirectory("custom-types-dir");
  }
}
