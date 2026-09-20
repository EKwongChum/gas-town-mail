/*
 * Copyright 2026 ekwongchum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ekwong.mailmcpserver.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Requires the {@code app.security.api-key} on the configured paths, so the outbound mail endpoints
 * cannot be used by anyone who can reach the server. The check is skipped while no key is
 * configured (local development), in which case a warning is logged at startup.
 */
@Component
@Order(10)
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final SecurityProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public ApiKeyFilter(SecurityProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void logConfiguration() {
        if (!configured()) {
            log.warn(
                    "app.security.api-key is not set: {} are served without authentication; set it "
                            + "or restrict network access before exposing this service",
                    properties.getProtectedPaths());
        } else {
            log.info("API key authentication enabled for {}", properties.getProtectedPaths());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!configured()) {
            return true;
        }
        return !protectedPath(RequestPath.of(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (matches(presentedKey(request), properties.getApiKey())) {
            filterChain.doFilter(request, response);
            return;
        }
        log.warn(
                "Rejected request without a valid API key: {} {}",
                request.getMethod(),
                RequestPath.of(request));
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"Missing or invalid API key\"}");
    }

    private boolean configured() {
        return StringUtils.hasText(properties.getApiKey());
    }

    private boolean protectedPath(String path) {
        List<String> patterns = properties.getProtectedPaths();
        return patterns != null
                && patterns.stream()
                        .filter(StringUtils::hasText)
                        .anyMatch(pattern -> pathMatcher.match(pattern.trim(), path));
    }

    private String presentedKey(HttpServletRequest request) {
        String key = request.getHeader(properties.getHeaderName());
        if (StringUtils.hasText(key)) {
            return key.trim();
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    /** Constant-time comparison of the presented key with the configured one. */
    private boolean matches(String presented, String expected) {
        if (!StringUtils.hasText(presented)) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
