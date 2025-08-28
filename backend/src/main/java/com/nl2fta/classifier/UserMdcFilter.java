package com.nl2fta.classifier;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(1)
public class UserMdcFilter extends OncePerRequestFilter {

  private static final String USERNAME_MDC_KEY = "username";
  private static final String USERNAME_HEADER = "X-Username";
  private static final String DEFAULT_USERNAME = "anonymous";
  private static final String CORRELATION_ID_MDC_KEY = "correlationId";
  private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      // Try to get username from header (sent by frontend)
      String username = request.getHeader(USERNAME_HEADER);

      // If no username header, check for session attribute
      if (username == null || username.isEmpty()) {
        Object sessionUsername = request.getSession().getAttribute("username");
        if (sessionUsername != null) {
          username = sessionUsername.toString();
        }
      }

      // If still no username, use default
      if (username == null || username.isEmpty()) {
        username = DEFAULT_USERNAME;
      }

      // Add username to MDC
      MDC.put(USERNAME_MDC_KEY, username);

      // Add correlation id to MDC if provided by client
      String correlationId = request.getHeader(CORRELATION_ID_HEADER);
      if (correlationId != null && !correlationId.isEmpty()) {
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
      }

      // Continue the filter chain
      filterChain.doFilter(request, response);
    } finally {
      // Clean up MDC
      MDC.remove(USERNAME_MDC_KEY);
      MDC.remove(CORRELATION_ID_MDC_KEY);
    }
  }
}
