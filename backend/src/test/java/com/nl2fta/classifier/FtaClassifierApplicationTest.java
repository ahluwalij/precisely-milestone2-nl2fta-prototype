package com.nl2fta.classifier;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {"spring.profiles.active=test", "server.port=0"})
public class FtaClassifierApplicationTest {

  @Test
  public void contextLoads() {
    // Test that the Spring context loads successfully
  }
}
