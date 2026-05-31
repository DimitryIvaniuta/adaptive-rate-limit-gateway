package com.github.dimitryivaniuta.gateway.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Minimal admin API protection used for this standalone gateway.
 *
 * <p>In production, replace this token guard with OAuth2/OIDC, mTLS, or an
 * internal service mesh identity policy. A constant-time comparison is used to
 * avoid trivial timing leaks.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AdminSecurityWebFilter implements WebFilter {

    private static final String ADMIN_HEADER = "X-Admin-Token";
    private final AdaptiveRateLimitProperties properties;

    /**
     * Requires a valid admin token for every /admin endpoint.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith("/admin")) {
            return chain.filter(exchange);
        }
        String provided = exchange.getRequest().getHeaders().getFirst(ADMIN_HEADER);
        if (provided != null && secureEquals(provided, properties.adminToken())) {
            return chain.filter(exchange);
        }
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "ApiKey realm=admin");
        return response.setComplete();
    }

    private boolean secureEquals(String provided, String expected) {
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(providedBytes, expectedBytes);
    }
}
