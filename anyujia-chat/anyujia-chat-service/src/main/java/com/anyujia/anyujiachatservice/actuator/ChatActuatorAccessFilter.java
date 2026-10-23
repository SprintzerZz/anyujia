package com.anyujia.anyujiachatservice.actuator;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protects chat actuator endpoints without depending on the application's user-token filter.
 * Health remains probeable; metrics and operational endpoints require a dedicated management
 * token and fail closed when no token is configured.
 */
@Component
@RequiredArgsConstructor
public class ChatActuatorAccessFilter extends OncePerRequestFilter {
    private static final String TOKEN_HEADER = "X-Actuator-Token";

    @Value("${chat.actuator.access-token:}")
    private String accessToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith(request.getContextPath() + "/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String endpoint = path.substring((request.getContextPath() + "/actuator/").length());
        if ("health".equals(endpoint) || endpoint.startsWith("health/")
                || "info".equals(endpoint) || endpoint.startsWith("info/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String presented = request.getHeader(TOKEN_HEADER);
        if (!StringUtils.hasText(accessToken) || !constantTimeEquals(accessToken, presented)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Actuator authentication required");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String expected, String presented) {
        if (!StringUtils.hasText(presented)) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}
