package com.github.dimitryivaniuta.gateway.ratelimit;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Minimal IPv4 CIDR matcher used to decide whether forwarded headers can be trusted.
 */
final class CidrBlock {

    private final long network;
    private final long mask;
    private final boolean wildcard;

    private CidrBlock(long network, long mask, boolean wildcard) {
        this.network = network;
        this.mask = mask;
        this.wildcard = wildcard;
    }

    /**
     * Parses a CIDR block such as {@code 10.0.0.0/8} or a single IPv4 address.
     *
     * @param value CIDR value
     * @return parsed block
     */
    static CidrBlock parse(String value) {
        String trimmed = value == null ? "" : value.trim();
        if ("0.0.0.0/0".equals(trimmed)) {
            return new CidrBlock(0, 0, true);
        }
        String[] parts = trimmed.split("/", -1);
        String address = parts[0];
        int prefix = parts.length == 1 ? 32 : Integer.parseInt(parts[1]);
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Invalid IPv4 CIDR prefix: " + value);
        }
        long mask = prefix == 0 ? 0 : 0xFFFF_FFFFL << (32 - prefix) & 0xFFFF_FFFFL;
        long network = ipv4ToLong(address) & mask;
        return new CidrBlock(network, mask, prefix == 0);
    }

    /**
     * Returns true when this block is the wildcard block.
     */
    boolean wildcard() {
        return wildcard;
    }

    /**
     * Checks if an IP address is inside this CIDR block.
     */
    boolean contains(InetAddress address) {
        if (wildcard) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            return false;
        }
        long candidate = bytesToLong(bytes);
        return (candidate & mask) == network;
    }

    private static long ipv4ToLong(String address) {
        try {
            return bytesToLong(InetAddress.getByName(address).getAddress());
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("Invalid IPv4 CIDR address: " + address, ex);
        }
    }

    private static long bytesToLong(byte[] bytes) {
        if (bytes.length != 4) {
            throw new IllegalArgumentException("Only IPv4 CIDR blocks are supported for trusted proxies");
        }
        return ((long) bytes[0] & 0xFF) << 24
                | ((long) bytes[1] & 0xFF) << 16
                | ((long) bytes[2] & 0xFF) << 8
                | ((long) bytes[3] & 0xFF);
    }
}
