package com.nl2fta.classifier.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

public class OpenApiConfigTest {

  private OpenApiConfig openApiConfig;

  @BeforeEach
  public void setUp() {
    openApiConfig = new OpenApiConfig();

    // Set default values
    ReflectionTestUtils.setField(openApiConfig, "title", "FTA Table Classifier API");
    ReflectionTestUtils.setField(openApiConfig, "version", "1.0.0");
    ReflectionTestUtils.setField(
        openApiConfig,
        "description",
        "REST API for classifying table columns using FTA (Fast Text Analysis) library. This service analyzes table data to identify base types and semantic types for each column.");
    ReflectionTestUtils.setField(openApiConfig, "licenseName", "Apache 2.0");
    ReflectionTestUtils.setField(
        openApiConfig, "licenseUrl", "https://www.apache.org/licenses/LICENSE-2.0");
    ReflectionTestUtils.setField(openApiConfig, "serverPort", "8081");
  }

  @Test
  public void testCustomOpenAPICreation() {
    OpenAPI openAPI = openApiConfig.customOpenAPI();

    assertNotNull(openAPI);
    assertNotNull(openAPI.getInfo());
    assertNotNull(openAPI.getServers());
  }

  @Test
  public void testInfoConfiguration() {
    OpenAPI openAPI = openApiConfig.customOpenAPI();
    Info info = openAPI.getInfo();

    assertEquals("FTA Table Classifier API", info.getTitle());
    assertEquals("1.0.0", info.getVersion());
    assertEquals(
        "REST API for classifying table columns using FTA (Fast Text Analysis) library. This service analyzes table data to identify base types and semantic types for each column.",
        info.getDescription());
  }

  @Test
  public void testLicenseConfiguration() {
    OpenAPI openAPI = openApiConfig.customOpenAPI();
    License license = openAPI.getInfo().getLicense();

    assertNotNull(license);
    assertEquals("Apache 2.0", license.getName());
    assertEquals("https://www.apache.org/licenses/LICENSE-2.0", license.getUrl());
  }

  @Test
  public void testServersConfiguration() {
    OpenAPI openAPI = openApiConfig.customOpenAPI();
    List<Server> servers = openAPI.getServers();

    assertEquals(2, servers.size());

    Server localServer = servers.get(0);
    assertEquals("http://localhost:8081", localServer.getUrl());
    assertEquals("Local development server", localServer.getDescription());

    Server prodServer = servers.get(1);
    assertEquals("https://api.nl2fta.com", prodServer.getUrl());
    assertEquals("Production server", prodServer.getDescription());
  }

  @Test
  public void testCustomValues() {
    OpenApiConfig config = new OpenApiConfig();

    // Set custom values
    ReflectionTestUtils.setField(config, "title", "Custom API");
    ReflectionTestUtils.setField(config, "version", "2.0.0");
    ReflectionTestUtils.setField(config, "description", "Custom Description");
    ReflectionTestUtils.setField(config, "licenseName", "MIT");
    ReflectionTestUtils.setField(config, "licenseUrl", "https://opensource.org/licenses/MIT");
    ReflectionTestUtils.setField(config, "serverPort", "9090");

    OpenAPI openAPI = config.customOpenAPI();

    assertEquals("Custom API", openAPI.getInfo().getTitle());
    assertEquals("2.0.0", openAPI.getInfo().getVersion());
    assertEquals("Custom Description", openAPI.getInfo().getDescription());
    assertEquals("MIT", openAPI.getInfo().getLicense().getName());
    assertEquals("https://opensource.org/licenses/MIT", openAPI.getInfo().getLicense().getUrl());
    assertEquals("http://localhost:9090", openAPI.getServers().get(0).getUrl());
    assertEquals("Local development server", openAPI.getServers().get(0).getDescription());
    assertEquals("https://api.nl2fta.com", openAPI.getServers().get(1).getUrl());
    assertEquals("Production server", openAPI.getServers().get(1).getDescription());
  }

  @Test
  public void testEmptyValues() {
    OpenApiConfig config = new OpenApiConfig();

    // Set empty values
    ReflectionTestUtils.setField(config, "title", "");
    ReflectionTestUtils.setField(config, "version", "");
    ReflectionTestUtils.setField(config, "description", "");
    ReflectionTestUtils.setField(config, "licenseName", "");
    ReflectionTestUtils.setField(config, "licenseUrl", "");
    ReflectionTestUtils.setField(config, "serverPort", "8081");

    OpenAPI openAPI = config.customOpenAPI();

    assertEquals("", openAPI.getInfo().getTitle());
    assertEquals("", openAPI.getInfo().getVersion());
    assertEquals("", openAPI.getInfo().getDescription());
    assertEquals("", openAPI.getInfo().getLicense().getName());
    assertEquals("", openAPI.getInfo().getLicense().getUrl());
    assertEquals("http://localhost:8081", openAPI.getServers().get(0).getUrl());
    assertEquals("Local development server", openAPI.getServers().get(0).getDescription());
    assertEquals("https://api.nl2fta.com", openAPI.getServers().get(1).getUrl());
    assertEquals("Production server", openAPI.getServers().get(1).getDescription());
  }
}
