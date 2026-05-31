package com.github.dimitryivaniuta.gateway.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

/**
 * Small JSON error handler for admin API validation failures.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Converts validation errors to a compact JSON response.
     */
    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<Map<String, Object>> validation(WebExchangeBindException ex) {
        Map<String, String> fields = ex.getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage(),
                        (left, right) -> left
                ));
        return Mono.just(Map.of(
                "timestamp", Instant.now().toString(),
                "error", "validation_failed",
                "fields", fields
        ));
    }
}
