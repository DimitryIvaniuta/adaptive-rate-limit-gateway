package com.github.dimitryivaniuta.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dimitryivaniuta.gateway.TestProperties;
import com.github.dimitryivaniuta.gateway.config.AdaptiveRateLimitProperties;
import com.github.dimitryivaniuta.gateway.domain.ClientIdentity;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/**
 * Unit tests for request identity resolution.
 */
class ClientIdentityResolverTest {

    private final AdaptiveRateLimitProperties properties = TestProperties.baseline();
    private final ClientIdentityResolver resolver = new ClientIdentityResolver(properties, new TrustedProxyMatcher(properties));

    /**
     * Tenant id wins as the main subject key.
     */
    @Test
    void resolvesTenantSubjectKey() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test")
                .remoteAddress(new InetSocketAddress("10.0.0.10", 12345))
                .header("X-Tenant-Id", "tenant-a")
                .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
                .build());

        ClientIdentity identity = resolver.resolve(exchange);

        assertThat(identity.subjectKey()).isEqualTo("tenant:tenant-a");
        assertThat(identity.clientIp()).isEqualTo("203.0.113.10");
    }

    /**
     * API key is hashed before being used in identifiers.
     */
    @Test
    void hashesApiKey() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test")
                .remoteAddress(new InetSocketAddress("10.0.0.10", 12345))
                .header("X-Api-Key", "secret")
                .header("X-Real-IP", "203.0.113.20")
                .build());

        ClientIdentity identity = resolver.resolve(exchange);

        assertThat(identity.subjectKey()).startsWith("api-key:");
        assertThat(identity.subjectKey()).doesNotContain("secret");
        assertThat(identity.apiKeyHash()).hasSize(64);
        assertThat(identity.clientIp()).isEqualTo("203.0.113.20");
    }

    /**
     * Untrusted peers cannot spoof X-Forwarded-For.
     */
    @Test
    void ignoresForwardedForFromUntrustedPeer() {
        AdaptiveRateLimitProperties secureProperties = new AdaptiveRateLimitProperties(
                properties.adminToken(),
                true,
                java.util.List.of("10.0.0.0/8"),
                properties.rateLimit(),
                properties.kafka(),
                properties.audit()
        );
        ClientIdentityResolver secureResolver = new ClientIdentityResolver(secureProperties, new TrustedProxyMatcher(secureProperties));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test")
                .remoteAddress(new InetSocketAddress("203.0.113.99", 12345))
                .header("X-Forwarded-For", "198.51.100.10")
                .build());

        ClientIdentity identity = secureResolver.resolve(exchange);

        assertThat(identity.clientIp()).isEqualTo("203.0.113.99");
    }
}
