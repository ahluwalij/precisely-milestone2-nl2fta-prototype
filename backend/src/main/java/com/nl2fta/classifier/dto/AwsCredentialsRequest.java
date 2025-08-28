package com.nl2fta.classifier.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwsCredentialsRequest {

  @NotBlank(message = "AWS Access Key ID is required")
  private String accessKeyId;

  @NotBlank(message = "AWS Secret Access Key is required")
  private String secretAccessKey;

  private String region;
}
