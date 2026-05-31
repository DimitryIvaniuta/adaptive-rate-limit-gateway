package com.github.dimitryivaniuta.gateway.ratelimit;

import com.github.dimitryivaniuta.gateway.config.AdaptiveRateLimitProperties;
import java.net.InetSocketAddress;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Allows forwarded headers only when the direct remote peer is a trusted proxy.
 */
@Component
public class TrustedProxyMatcher {

    private final List<CidrBlock> trustedCidrs;

    /**
     * Parses trusted proxy CIDRs once at startup.
     *
     * @param properties gateway configuration
     */
    public TrustedProxyMatcher(AdaptiveRateLimitProperties properties) {
        this.trustedCidrs = properties.trustedProxyCidrs().stream()
                .map(CidrBlock::parse)
                .toList();
    }

    /**
     * Checks whether forwarded headers should be trusted for the request.
     *
     * @param remoteAddress direct TCP peer
     * @return true when the peer matches trusted CIDRs
     */
    public boolean isTrusted(InetSocketAddress remoteAddress) {
        if (trustedCidrs.stream().anyMatch(CidrBlock::wildcard)) {
            return true;
        }
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return false;
        }
        return trustedCidrs.stream().anyMatch(block -> block.contains(remoteAddress.getAddress()));
    }
}
