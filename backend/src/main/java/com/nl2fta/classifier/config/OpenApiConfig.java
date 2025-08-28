package com.nl2fta.classifier.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

  @Value("${springdoc.info.title:FTA Table Classifier API}")
  private String title;

  @Value("${springdoc.info.version:1.0.0}")
  private String version;

  @Value(
      "${springdoc.info.description:REST API for classifying table columns using FTA (Fast Text Analysis) library. This service analyzes table data to identify base types and semantic types for each column.}")
  private String description;

  @Value("${springdoc.info.license.name:Apache 2.0}")
  private String licenseName;

  @Value("${springdoc.info.license.url:https://www.apache.org/licenses/LICENSE-2.0}")
  private String licenseUrl;

  @Value("${server.port:8081}")
  private String serverPort;

  @Bean
  public OpenAPI customOpenAPI() {
    String localServerUrl = "http://localhost:" + serverPort;
    String localServerDescription = "Local development server";
    String prodServerUrl = "https://api.nl2fta.com";
    String prodServerDescription = "Production server";

    return new OpenAPI()
        .info(
            new Info()
                .title(title)
                .version(version)
                .description(description)
                .license(new License().name(licenseName).url(licenseUrl)))
        .servers(
            List.of(
                new Server().url(localServerUrl).description(localServerDescription),
                new Server().url(prodServerUrl).description(prodServerDescription)));
  }
}
