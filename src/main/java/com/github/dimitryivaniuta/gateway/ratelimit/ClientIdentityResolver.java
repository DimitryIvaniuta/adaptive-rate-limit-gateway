package com.github.dimitryivaniuta.gateway.ratelimit;

import com.github.dimitryivaniuta.gateway.config.AdaptiveRateLimitProperties;
import com.github.dimitryivaniuta.gateway.domain.ClientIdentity;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * Resolves a stable client identity from request headers and transport metadata.
 */
@Component
@RequiredArgsConstructor
public class ClientIdentityResolver {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final String REAL_IP_HEADER = "X-Real-IP";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final AdaptiveRateLimitProperties properties;
    private final TrustedProxyMatcher trustedProxyMatcher;

    /**
     * Builds the identity used by access lists, counters, audit rows, and Kafka events.
     *
     * @param exchange current request exchange
     * @return resolved identity
     */
    public ClientIdentity resolve(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        HttpHeaders headers = request.getHeaders();
        String tenant = normalize(headers.getFirst(TENANT_HEADER));
        String apiKeyHash = apiKeyHash(headers.getFirst(API_KEY_HEADER));
        String clientIp = resolveClientIp(request);
        String subjectKey = subjectKey(tenant, apiKeyHash, clientIp);
        return new ClientIdentity(subjectKey, tenant, clientIp, apiKeyHash);
    }

    private String subjectKey(String tenant, String apiKeyHash, String clientIp) {
        if (StringUtils.hasText(tenant)) {
            return "tenant:" + tenant;
        }
        if (StringUtils.hasText(apiKeyHash)) {
            return "api-key:" + apiKeyHash;
        }
        return "ip:" + clientIp;
    }

    private String resolveClientIp(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (properties.trustedForwardedHeaders() && trustedProxyMatcher.isTrusted(remoteAddress)) {
            String forwardedFor = request.getHeaders().getFirst(FORWARDED_FOR_HEADER);
            if (StringUtils.hasText(forwardedFor)) {
                return normalize(forwardedFor.split(",")[0]);
            }
            String realIp = request.getHeaders().getFirst(REAL_IP_HEADER);
            if (StringUtils.hasText(realIp)) {
                return normalize(realIp);
            }
        }
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String apiKeyHash(String apiKey) {
        String normalized = normalize(apiKey);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is required by the JDK", ex);
        }
    }
}
