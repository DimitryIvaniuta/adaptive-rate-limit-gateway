package com.github.dimitryivaniuta.gateway.api;

import com.github.dimitryivaniuta.gateway.config.AdaptiveRateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only policy endpoint that exposes the active adaptive rate-limit settings.
 */
@RestController
@RequestMapping("/admin/policy")
@RequiredArgsConstructor
public class PolicyController {

    private final AdaptiveRateLimitProperties properties;

    /**
     * Returns active rate-limit configuration.
     */
    @GetMapping
    public AdaptiveRateLimitProperties.RateLimit currentPolicy() {
        return properties.rateLimit();
    }
}
