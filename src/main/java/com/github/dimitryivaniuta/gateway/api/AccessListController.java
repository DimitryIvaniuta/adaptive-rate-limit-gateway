package com.github.dimitryivaniuta.gateway.api;

import com.github.dimitryivaniuta.gateway.access.AccessListService;
import com.github.dimitryivaniuta.gateway.domain.AccessListCreateRequest;
import com.github.dimitryivaniuta.gateway.domain.AccessListEntry;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Admin API for managing blocklist and allowlist entries.
 */
@RestController
@RequestMapping("/admin/access-list")
@RequiredArgsConstructor
public class AccessListController {

    private final AccessListService service;

    /**
     * Creates a new active access-list entry.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AccessListEntry> create(@Valid @RequestBody AccessListCreateRequest request) {
        return service.create(request);
    }

    /**
     * Lists currently active entries.
     */
    @GetMapping
    public Flux<AccessListEntry> list() {
        return service.listActive();
    }

    /**
     * Disables an entry by id.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> disable(@PathVariable UUID id) {
        return service.disable(id);
    }
}
