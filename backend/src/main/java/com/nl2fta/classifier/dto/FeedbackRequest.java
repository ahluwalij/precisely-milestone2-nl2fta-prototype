package com.nl2fta.classifier.dto;

public class FeedbackRequest {
  private String type;
  private String feedback;
  private String semanticTypeName;
  private String description;
  private String pluginType;
  private String regexPattern;
  private String headerPatterns;
  private String username;
  private String timestamp;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getFeedback() {
    return feedback;
  }

  public void setFeedback(String feedback) {
    this.feedback = feedback;
  }

  public String getSemanticTypeName() {
    return semanticTypeName;
  }

  public void setSemanticTypeName(String semanticTypeName) {
    this.semanticTypeName = semanticTypeName;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getPluginType() {
    return pluginType;
  }

  public void setPluginType(String pluginType) {
    this.pluginType = pluginType;
  }

  public String getRegexPattern() {
    return regexPattern;
  }

  public void setRegexPattern(String regexPattern) {
    this.regexPattern = regexPattern;
  }

  public String getHeaderPatterns() {
    return headerPatterns;
  }

  public void setHeaderPatterns(String headerPatterns) {
    this.headerPatterns = headerPatterns;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }
}
