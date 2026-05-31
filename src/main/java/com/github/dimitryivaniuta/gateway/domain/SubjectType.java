package com.github.dimitryivaniuta.gateway.domain;

/**
 * Supported identity dimensions for access-list and dashboard operations.
 */
public enum SubjectType {
    /** Client IP address. */
    IP,
    /** Tenant or customer identifier. */
    TENANT,
    /** SHA-256 hash of an API key. */
    API_KEY
}
