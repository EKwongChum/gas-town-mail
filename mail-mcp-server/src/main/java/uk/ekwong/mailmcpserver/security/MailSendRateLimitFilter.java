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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.ekwong.mailmcpserver.send.MailSendProperties;
import uk.ekwong.mailmcpserver.send.MailSendRateLimiter;

/**
 * Applies {@code app.send.rate-limit.requests-per-minute} per client to the outbound mail endpoints
 * and answers {@code 429} with a {@code Retry-After} header when the limit is exceeded. It runs
 * after {@link ApiKeyFilter}, so unauthenticated traffic is rejected first.
 */
@Component
@Order(20)
public class MailSendRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MailSendRateLimitFilter.class);

    /** Only the delivery endpoints are limited; polling a delivery task must not consume quota. */
    private static final List<String> LIMITED_PATHS =
            List.of("/api/mails/send", "/api/mails/reply", "/api/mails/forward");

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final MailSendProperties properties;
    private final MailSendRateLimiter rateLimiter;

    public MailSendRateLimitFilter(MailSendProperties properties, MailSendRateLimiter rateLimiter) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !rateLimiter.enabled() || !LIMITED_PATHS.contains(RequestPath.of(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String client = client(request);
        MailSendRateLimiter.Decision decision = rateLimiter.acquire(client);
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }
        log.warn(
                "Rate limit reached for client={} on {} {}",
                client,
                request.getMethod(),
                RequestPath.of(request));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter()
                .write(
                        "{\"error\":\"Too many mail requests, retry in "
                                + decision.retryAfterSeconds()
                                + " seconds\"}");
    }

    private String client(HttpServletRequest request) {
        if (properties.getRateLimit().isTrustForwardedFor()) {
            String forwarded = request.getHeader(FORWARDED_FOR);
            if (StringUtils.hasText(forwarded)) {
                return forwarded.split(",")[0].trim();
            }
        }
        String remoteAddress = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddress) ? remoteAddress : "unknown";
    }
}
