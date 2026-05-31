package com.github.dimitryivaniuta.gateway.access;

import com.github.dimitryivaniuta.gateway.domain.AccessListEntry;
import com.github.dimitryivaniuta.gateway.domain.AccessListMode;
import com.github.dimitryivaniuta.gateway.domain.SubjectType;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC entity storing allowlist and blocklist entries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("access_list")
public class AccessListEntity {
    @Id
    private UUID id;
    private String subjectType;
    private String subjectValue;
    private String mode;
    private String reason;
    private OffsetDateTime expiresAt;
    private boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /**
     * Converts this entity to an API DTO.
     *
     * @return access-list entry DTO
     */
    public AccessListEntry toEntry() {
        return new AccessListEntry(
                id,
                SubjectType.valueOf(subjectType),
                subjectValue,
                AccessListMode.valueOf(mode),
                reason,
                expiresAt,
                active,
                createdAt,
                updatedAt
        );
    }
}
