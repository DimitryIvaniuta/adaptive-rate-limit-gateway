package com.github.dimitryivaniuta.gateway.domain;

/**
 * Result of consuming one token from a Redis fixed window.
 *
 * @param allowed whether consumption stays inside the configured limit
 * @param currentCount current request count in the window
 * @param remaining remaining allowance, never below zero
 * @param resetSeconds approximate seconds before window key expires
 */
public record WindowResult(boolean allowed, long currentCount, int remaining, long resetSeconds) {
}
