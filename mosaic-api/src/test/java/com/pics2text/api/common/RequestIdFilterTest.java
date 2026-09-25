package com.pics2text.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestIdFilterTest {

    @Test
    void preservesSafeRequestId() {
        assertThat(RequestIdFilter.normalize("request-123:test")).isEqualTo("request-123:test");
    }

    @Test
    void replacesUnsafeRequestId() {
        assertThat(RequestIdFilter.normalize("unsafe request\nvalue"))
                .matches("[0-9a-f-]{36}");
    }

    @Test
    void createsRequestIdWhenMissing() {
        assertThat(RequestIdFilter.normalize(null)).matches("[0-9a-f-]{36}");
    }
}

