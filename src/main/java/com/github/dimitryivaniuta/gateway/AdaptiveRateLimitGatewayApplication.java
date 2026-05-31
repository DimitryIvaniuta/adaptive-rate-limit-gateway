package com.github.dimitryivaniuta.gateway;

import com.github.dimitryivaniuta.gateway.config.AdaptiveRateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Application entry point for the adaptive API Gateway.
 *
 * <p>The gateway runs on Spring WebFlux/Netty and applies a Redis-backed
 * adaptive rate-limit filter before forwarding requests through Spring Cloud
 * Gateway routes.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(AdaptiveRateLimitProperties.class)
public class AdaptiveRateLimitGatewayApplication {

    /**
     * Starts the gateway process.
     *
     * @param args standard command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(AdaptiveRateLimitGatewayApplication.class, args);
    }
}
