package com.github.dimitryivaniuta.gateway.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Public access-list view returned by the admin API.
 */
public record AccessListEntry(
        UUID id,
        SubjectType subjectType,
        String subjectValue,
        AccessListMode mode,
        String reason,
        OffsetDateTime expiresAt,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
