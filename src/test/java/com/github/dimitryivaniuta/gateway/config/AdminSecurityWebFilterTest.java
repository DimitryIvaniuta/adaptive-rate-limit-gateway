package com.github.dimitryivaniuta.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dimitryivaniuta.gateway.TestProperties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

/**
 * Unit tests for admin endpoint token protection.
 */
class AdminSecurityWebFilterTest {

    private final AdminSecurityWebFilter filter = new AdminSecurityWebFilter(TestProperties.baseline());

    /**
     * Missing token blocks admin endpoints.
     */
    @Test
    void blocksAdminWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/admin/policy"));

        StepVerifier.create(filter.filter(exchange, chain -> reactor.core.publisher.Mono.empty()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Valid token allows the admin request to continue.
     */
    @Test
    void allowsAdminWithValidToken() {
        AtomicBoolean called = new AtomicBoolean(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/admin/policy")
                .header("X-Admin-Token", "test-token"));

        StepVerifier.create(filter.filter(exchange, chain -> {
                    called.set(true);
                    return reactor.core.publisher.Mono.empty();
                }))
                .verifyComplete();

        assertThat(called).isTrue();
    }
}
