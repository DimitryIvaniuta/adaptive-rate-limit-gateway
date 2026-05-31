package com.github.dimitryivaniuta.gateway.access;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive PostgreSQL repository for access-list entries.
 */
public interface AccessListRepository extends ReactiveCrudRepository<AccessListEntity, UUID> {

    /**
     * Finds active, non-expired entries for a subject.
     */
    @Query("""
            SELECT * FROM access_list
            WHERE subject_type = :subjectType
              AND subject_value = :subjectValue
              AND active = TRUE
              AND (expires_at IS NULL OR expires_at > :now)
            ORDER BY created_at DESC
            """)
    Flux<AccessListEntity> findActive(String subjectType, String subjectValue, OffsetDateTime now);

    /**
     * Lists active entries for admin inspection.
     */
    @Query("""
            SELECT * FROM access_list
            WHERE active = TRUE
              AND (expires_at IS NULL OR expires_at > :now)
            ORDER BY created_at DESC
            """)
    Flux<AccessListEntity> findActiveEntries(OffsetDateTime now);

    /**
     * Disables an entry without deleting historical metadata.
     */
    @Modifying
    @Query("""
            UPDATE access_list
               SET active = FALSE, updated_at = :updatedAt
             WHERE id = :id
            """)
    Mono<Integer> disable(UUID id, OffsetDateTime updatedAt);
}
