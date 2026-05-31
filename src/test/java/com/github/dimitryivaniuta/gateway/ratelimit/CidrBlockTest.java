package com.github.dimitryivaniuta.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for trusted proxy CIDR matching.
 */
class CidrBlockTest {

    /**
     * Private IPv4 ranges match their CIDR block.
     */
    @Test
    void matchesIpv4Cidr() throws Exception {
        CidrBlock block = CidrBlock.parse("10.0.0.0/8");

        assertThat(block.contains(InetAddress.getByName("10.12.1.5"))).isTrue();
        assertThat(block.contains(InetAddress.getByName("192.168.1.5"))).isFalse();
    }

    /**
     * Wildcard CIDR trusts every peer and is reserved for tests/local-only usage.
     */
    @Test
    void wildcardMatchesAnyIpv4() throws Exception {
        CidrBlock block = CidrBlock.parse("0.0.0.0/0");

        assertThat(block.wildcard()).isTrue();
        assertThat(block.contains(InetAddress.getByName("203.0.113.10"))).isTrue();
    }
}
