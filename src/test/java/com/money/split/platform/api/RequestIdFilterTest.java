package com.money.split.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Verifies server-owned request ID creation and lookup.
 */
class RequestIdFilterTest {

    private static final UUID REQUEST_ID = UUID.fromString("6ca42164-0baf-4e27-bdad-bf66c5fcde17");

    /**
     * Replaces an untrusted client header and exposes one server-generated ID to the request chain.
     *
     * @throws Exception when servlet filtering fails
     */
    @Test
    void generatesAndPropagatesAServerOwnedRequestId() throws Exception {
        RequestIdFilter filter = new RequestIdFilter(() -> REQUEST_ID);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_NAME, "client-controlled");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) -> {
            invoked.set(true);
            assertThat(filteredRequest.getAttribute(RequestIdFilter.ATTRIBUTE_NAME))
                    .isEqualTo(REQUEST_ID);
            assertThat(filteredResponse).isSameAs(response);
        });

        assertThat(invoked).isTrue();
        assertThat(response.getHeader(RequestIdFilter.HEADER_NAME))
                .isEqualTo(REQUEST_ID.toString());
    }

    /**
     * Resolves the request ID initialized by the filter.
     */
    @Test
    void resolvesTheCurrentRequestId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.ATTRIBUTE_NAME, REQUEST_ID);

        assertThat(new ServletRequestIdProvider().currentRequestId(request)).isEqualTo(REQUEST_ID);
    }

    /**
     * Fails explicitly when a request bypasses request-ID initialization.
     */
    @Test
    void rejectsARequestWithoutAnInitializedId() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> new ServletRequestIdProvider().currentRequestId(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not initialize");
    }
}
