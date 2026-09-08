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

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import uk.ekwong.mailcommon.trace.TraceIds;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @BeforeEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void usesClientSuppliedRequestIdAndExposesItDuringTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "client-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain =
                (servletRequest, servletResponse) ->
                        assertThat(MDC.get(TraceIds.MDC_KEY)).isEqualTo("client-id-123");
        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
                .isEqualTo("client-id-123");
        assertThat(MDC.get(TraceIds.MDC_KEY)).isNull();
    }

    @Test
    void generatesRequestIdWhenHeaderIsMissingAndAlwaysCleansMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mail-originals");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {});

        String requestId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(requestId).matches("[0-9a-f-]{36}");
        assertThat(MDC.get(TraceIds.MDC_KEY)).isNull();
    }

    @Test
    void ignoresUnsafeClientSuppliedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "bad\nid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {});

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
                .isNotEqualTo("bad\nid")
                .matches("[0-9a-f-]{36}");
    }
}
