package com.nl2fta.classifier.config;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.context.annotation.Configuration;

import com.nl2fta.classifier.service.CloudWatchLoggingService;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;

@Configuration
public class CloudWatchLoggingConfig {
  private static final Logger LOGGER = LoggerFactory.getLogger(CloudWatchLoggingConfig.class);

  @Autowired private CloudWatchLoggingService cloudWatchLoggingService;

  @Autowired private LoggingSystem loggingSystem;

  @Value("${aws.cloudwatch.admin-access-key-id:}")
  private String adminAccessKeyId;

  @Value("${aws.cloudwatch.admin-secret-access-key:}")
  private String adminSecretAccessKey;

  @PostConstruct
  public void configureCloudWatchLogging() {
    try {
      // Enable CloudWatch only when admin credentials are present
      if (adminAccessKeyId == null
          || adminAccessKeyId.isEmpty()
          || adminSecretAccessKey == null
          || adminSecretAccessKey.isEmpty()) {
        LOGGER.debug("CloudWatch admin credentials not present; skipping appender configuration");
        return;
      }
      if (loggingSystem instanceof LogbackLoggingSystem) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        CloudWatchAppender cloudWatchAppender = new CloudWatchAppender();
        cloudWatchAppender.setCloudWatchLoggingService(cloudWatchLoggingService);
        cloudWatchAppender.setContext(loggerContext);
        cloudWatchAppender.start();

        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(cloudWatchAppender);

        LOGGER.debug("CloudWatch logging appender configured successfully");
      }
    } catch (Exception e) {
      LOGGER.error("Failed to configure CloudWatch logging appender", e);
    }
  }

  private static final class CloudWatchAppender extends AppenderBase<ILoggingEvent> {
    private CloudWatchLoggingService cloudWatchLoggingService;

    public void setCloudWatchLoggingService(CloudWatchLoggingService service) {
      this.cloudWatchLoggingService = service;
    }

    @Override
    protected void append(ILoggingEvent event) {
      if (cloudWatchLoggingService == null) {
        return;
      }

      // Skip logging CloudWatch service logs to prevent recursion
      if (event.getLoggerName().contains("CloudWatchLoggingService")
          || event.getLoggerName().contains("CloudWatchLogsClient")) {
        return;
      }

      try {
        Map<String, Object> data = new HashMap<>();
        data.put("logger", event.getLoggerName());
        data.put("thread", event.getThreadName());

        // Add exception info if present
        if (event.getThrowableProxy() != null) {
          data.put("exception", event.getThrowableProxy().getClassName());
          data.put("exceptionMessage", event.getThrowableProxy().getMessage());
        }

        // Extract username from MDC and add to data for CloudWatchLoggingService
        String username = event.getMDCPropertyMap().get("username");
        if (username != null && !username.isEmpty()) {
          data.put("username", username);
        }

        cloudWatchLoggingService.log(
            event.getLevel().toString(), event.getFormattedMessage(), data);
      } catch (Exception e) {
        // Don't let logging errors break the application
        addError("Failed to send log to CloudWatch", e);
      }
    }
  }
}
