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

package uk.ekwong.mailcommon.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import uk.ekwong.mailcommon.trace.TraceIds;

/**
 * Adds a {@code X-Request-Id} to every HTTP request and exposes it through the {@code traceId} MDC
 * key for the duration of the request, so log lines belonging to one call (including MCP {@code
 * tools/call} handling and REST endpoints) can be correlated. Clients may supply their own id in
 * the {@code X-Request-Id} header; otherwise a random UUID is generated. The id is echoed back in
 * the response header.
 *
 * <p>The SMTP and RocketMQ paths use the same {@code traceId} MDC key, so an HTTP-triggered
 * pipeline keeps one end-to-end trace id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements Filter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final String REQUEST_ID_ATTRIBUTE = RequestIdFilter.class.getName() + ".id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestId = (String) httpRequest.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (requestId == null) {
            requestId = resolveRequestId(httpRequest.getHeader(REQUEST_ID_HEADER));
            httpRequest.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
            if (response instanceof HttpServletResponse httpResponse) {
                httpResponse.setHeader(REQUEST_ID_HEADER, requestId);
            }
        }
        MDC.put(TraceIds.MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }
    }

    private String resolveRequestId(String provided) {
        if (provided != null && SAFE_REQUEST_ID.matcher(provided.trim()).matches()) {
            return provided.trim();
        }
        return UUID.randomUUID().toString();
    }
}
