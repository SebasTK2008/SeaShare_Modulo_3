package com.seashare.seasharem3.infrastructure.adapter.out.persistence.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Repositorio Spring Data de {@code outbox_message}. */
public interface OutboxMessageJpaRepository extends JpaRepository<OutboxMessageJpaEntity, UUID> {

    List<OutboxMessageJpaEntity> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);
}
