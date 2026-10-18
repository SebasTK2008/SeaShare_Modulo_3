package com.seashare.seasharem3.infrastructure.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void propagatesReceivedHeaderAndPublishesItInMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/probe");
        request.addHeader(CorrelationIdFilter.HEADER, "corr-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringChain = new AtomicReference<>();

        filter.doFilter(request, response,
                (servletRequest, servletResponse) -> duringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("corr-123");
        assertThat(duringChain.get()).isEqualTo("corr-123");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesANewOneWhenAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/probe");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
    }
}
