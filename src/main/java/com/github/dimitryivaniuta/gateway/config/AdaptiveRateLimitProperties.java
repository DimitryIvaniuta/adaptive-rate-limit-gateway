package com.github.dimitryivaniuta.gateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * External configuration for gateway protection, audit, and abuse event publishing.
 *
 * <p>The record keeps all runtime-tunable settings in one namespace so that
 * production deployments can tune limits per environment without rebuilding
 * the gateway image.</p>
 */
@Validated
@ConfigurationProperties(prefix = "gateway")
public record AdaptiveRateLimitProperties(
        @NotBlank String adminToken,
        boolean trustedForwardedHeaders,
        @NotNull List<@NotBlank String> trustedProxyCidrs,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Kafka kafka,
        @Valid @NotNull Audit audit
) {
    /**
     * Rate-limit and adaptive throttling settings.
     */
    public record RateLimit(
            boolean enabled,
            boolean failOpen,
            @NotNull Duration window,
            @Min(1) int baseLimitPerMinute,
            @Min(1) int tenantBaseLimitPerMinute,
            @Min(1) int minimumLimitPerMinute,
            boolean allowlistBypass,
            @DecimalMin("0.0") @DecimalMax("1.0") double errorRateThreshold,
            @Min(1) int abuseScoreHardBlock,
            @Min(1) int statisticsBuckets,
            @NotNull Duration accessListCacheTtl,
            @NotNull Duration statisticsTtl,
            @NotNull Duration abuseScoreTtl,
            @NotNull List<Integer> responseErrorStatuses,
            @NotNull Map<@NotBlank String, @Valid RoutePolicy> routePolicies
    ) {
    }

    /**
     * Optional per-route overrides keyed by Spring Cloud Gateway route id.
     *
     * <p>Use this for business-critical or high-cost APIs. Null values inherit
     * from the global {@link RateLimit} defaults.</p>
     */
    public record RoutePolicy(
            @Min(1) Integer baseLimitPerMinute,
            @Min(1) Integer tenantBaseLimitPerMinute,
            @Min(1) Integer minimumLimitPerMinute,
            Duration window,
            @DecimalMin("0.0") @DecimalMax("1.0") Double errorRateThreshold,
            @Min(1) Integer abuseScoreHardBlock
    ) {
    }

    /**
     * Kafka publishing settings for abuse/rate-limit events.
     */
    public record Kafka(boolean enabled, @NotBlank String abuseTopic) {
    }

    /**
     * Audit persistence settings.
     */
    public record Audit(
            boolean enabled,
            @DecimalMin("0.0") @DecimalMax("1.0") double persistAllowedSampleRate
    ) {
    }
}
