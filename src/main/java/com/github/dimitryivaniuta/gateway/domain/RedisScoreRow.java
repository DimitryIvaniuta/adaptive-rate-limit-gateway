package com.github.dimitryivaniuta.gateway.domain;

/**
 * Current Redis abuse-score row.
 *
 * @param subject subject identifier
 * @param score current score
 */
public record RedisScoreRow(String subject, double score) {
}
