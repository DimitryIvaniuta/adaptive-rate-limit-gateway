package com.github.dimitryivaniuta.gateway.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Request body for creating an allowlist or blocklist entry.
 */
public record AccessListCreateRequest(
        @NotNull SubjectType subjectType,
        @NotBlank @Size(max = 256) String subjectValue,
        @NotNull AccessListMode mode,
        @Size(max = 512) String reason,
        OffsetDateTime expiresAt
) {
}
