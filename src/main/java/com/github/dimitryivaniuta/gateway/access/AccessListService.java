package com.github.dimitryivaniuta.gateway.access;

import com.github.dimitryivaniuta.gateway.config.AdaptiveRateLimitProperties;
import com.github.dimitryivaniuta.gateway.domain.AccessListCreateRequest;
import com.github.dimitryivaniuta.gateway.domain.AccessListEntry;
import com.github.dimitryivaniuta.gateway.domain.AccessListMode;
import com.github.dimitryivaniuta.gateway.domain.ClientIdentity;
import com.github.dimitryivaniuta.gateway.domain.SubjectType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Access-list service using Redis as the hot-path cache and PostgreSQL as source of truth.
 */
@Service
@RequiredArgsConstructor
public class AccessListService {

    private static final String CACHE_PREFIX = "arl:access:";
    private static final String NONE = "NONE";

    private final AccessListRepository repository;
    private final ReactiveStringRedisTemplate redis;
    private final AdaptiveRateLimitProperties properties;

    /**
     * Resolves the highest-priority access-list mode for the current identity.
     *
     * <p>Explicit BLOCK wins over ALLOW to avoid accidental bypass of emergency blocks.</p>
     *
     * @param identity client identity
     * @return optional mode
     */
    public Mono<Optional<AccessListMode>> resolve(ClientIdentity identity) {
        return resolveSubject(SubjectType.IP, identity.clientIp())
                .zipWith(resolveSubject(SubjectType.TENANT, identity.tenantId() == null ? "" : identity.tenantId()))
                .zipWith(resolveSubject(SubjectType.API_KEY, identity.apiKeyHash() == null ? "" : identity.apiKeyHash()))
                .map(tuple -> highest(tuple.getT1().getT1(), tuple.getT1().getT2(), tuple.getT2()));
    }

    /**
     * Creates an access-list entry and refreshes the hot cache.
     *
     * @param request admin request
     * @return created entry
     */
    public Mono<AccessListEntry> create(AccessListCreateRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AccessListEntity entity = AccessListEntity.builder()
                .subjectType(request.subjectType().name())
                .subjectValue(request.subjectValue())
                .mode(request.mode().name())
                .reason(request.reason())
                .expiresAt(request.expiresAt())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return repository.save(entity)
                .flatMap(saved -> cache(request.subjectType(), request.subjectValue(), request.mode().name())
                        .thenReturn(saved.toEntry()));
    }

    /**
     * Lists active entries.
     *
     * @return access-list entries
     */
    public Flux<AccessListEntry> listActive() {
        return repository.findActiveEntries(OffsetDateTime.now(ZoneOffset.UTC)).map(AccessListEntity::toEntry);
    }

    /**
     * Disables an access-list entry.
     *
     * @param id entry id
     * @return completion signal
     */
    public Mono<Void> disable(UUID id) {
        return repository.findById(id)
                .flatMap(entry -> repository.disable(id, OffsetDateTime.now(ZoneOffset.UTC))
                        .then(evict(SubjectType.valueOf(entry.getSubjectType()), entry.getSubjectValue())))
                .then();
    }

    private Mono<Optional<AccessListMode>> resolveSubject(SubjectType type, String value) {
        if (value == null || value.isBlank()) {
            return Mono.just(Optional.empty());
        }
        String key = cacheKey(type, value);
        return redis.opsForValue().get(key)
                .flatMap(cached -> cached.equals(NONE)
                        ? Mono.just(Optional.<AccessListMode>empty())
                        : Mono.just(Optional.of(AccessListMode.valueOf(cached))))
                .switchIfEmpty(loadAndCache(type, value));
    }

    private Mono<Optional<AccessListMode>> loadAndCache(SubjectType type, String value) {
        return repository.findActive(type.name(), value, OffsetDateTime.now(ZoneOffset.UTC))
                .map(AccessListEntity::getMode)
                .collectList()
                .flatMap(modes -> {
                    Optional<AccessListMode> mode = highest(modes.stream()
                            .map(AccessListMode::valueOf)
                            .map(Optional::of)
                            .toArray(Optional[]::new));
                    return cache(type, value, mode.map(Enum::name).orElse(NONE)).thenReturn(mode);
                });
    }

    private Optional<AccessListMode> highest(Optional<AccessListMode>... values) {
        for (Optional<AccessListMode> value : values) {
            if (value.isPresent() && value.get() == AccessListMode.BLOCK) {
                return value;
            }
        }
        for (Optional<AccessListMode> value : values) {
            if (value.isPresent() && value.get() == AccessListMode.ALLOW) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Mono<Boolean> cache(SubjectType type, String value, String mode) {
        return redis.opsForValue()
                .set(cacheKey(type, value), mode, properties.rateLimit().accessListCacheTtl())
                .onErrorReturn(Boolean.FALSE);
    }

    private Mono<Boolean> evict(SubjectType type, String value) {
        return redis.delete(cacheKey(type, value))
                .map(deleted -> deleted > 0)
                .onErrorReturn(Boolean.FALSE);
    }

    private String cacheKey(SubjectType type, String value) {
        return CACHE_PREFIX + type.name().toLowerCase() + ':' + value;
    }
}
