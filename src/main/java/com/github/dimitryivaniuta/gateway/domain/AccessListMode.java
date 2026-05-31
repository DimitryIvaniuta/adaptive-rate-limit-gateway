package com.github.dimitryivaniuta.gateway.domain;

/**
 * Access-list decision mode.
 */
public enum AccessListMode {
    /** Bypass rate limiting for the subject. */
    ALLOW,
    /** Reject the subject before proxying. */
    BLOCK
}
