package com.nl2fta.classifier.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class CoreConfigTest {

  private CoreConfig coreConfig;

  @BeforeEach
  public void setUp() {
    coreConfig = new CoreConfig();
  }

  @Test
  public void testObjectMapperConfiguration() {
    ObjectMapper mapper = coreConfig.objectMapper();

    assertNotNull(mapper);
    assertTrue(mapper.isEnabled(SerializationFeature.INDENT_OUTPUT));
    assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    assertFalse(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
  }

  @Test
  public void testObjectMapperSerialization() throws Exception {
    ObjectMapper mapper = coreConfig.objectMapper();

    // Test date serialization
    LocalDateTime now = LocalDateTime.now();
    String json = mapper.writeValueAsString(now);
    assertNotNull(json);
    assertFalse(json.contains("timestamp"));

    // Test unknown properties deserialization
    String jsonWithExtra = "{\"known\":\"value\",\"unknown\":\"value\"}";
    TestClass result = mapper.readValue(jsonWithExtra, TestClass.class);
    assertEquals("value", result.known);
  }

  @Test
  public void testTaskExecutorConfiguration() {
    Executor executor = coreConfig.taskExecutor();

    assertNotNull(executor);
    assertTrue(executor instanceof ThreadPoolTaskExecutor);

    ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
    assertEquals(4, taskExecutor.getCorePoolSize());
    assertEquals(8, taskExecutor.getMaxPoolSize());
    assertEquals(100, taskExecutor.getQueueCapacity());
    assertEquals("async-", taskExecutor.getThreadNamePrefix());
  }

  @Test
  public void testTaskExecutorExecution() throws Exception {
    Executor executor = coreConfig.taskExecutor();

    // Test that tasks can be executed
    final boolean[] executed = {false};
    executor.execute(() -> executed[0] = true);

    // Give some time for async execution
    Thread.sleep(100);
    assertTrue(executed[0]);
  }

  @Test
  public void testMultipleTaskExecution() throws Exception {
    Executor executor = coreConfig.taskExecutor();

    int taskCount = 10;
    final int[] counter = {0};

    for (int i = 0; i < taskCount; i++) {
      executor.execute(
          () -> {
            synchronized (counter) {
              counter[0]++;
            }
          });
    }

    // Give time for all tasks to complete
    Thread.sleep(200);
    assertEquals(taskCount, counter[0]);
  }

  // Helper class for testing
  private static final class TestClass {
    public String known;
  }
}
