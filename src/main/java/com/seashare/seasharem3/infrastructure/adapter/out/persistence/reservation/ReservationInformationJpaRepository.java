package com.seashare.seasharem3.infrastructure.adapter.out.persistence.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Repositorio Spring Data de {@code reservation_information}. */
public interface ReservationInformationJpaRepository
        extends JpaRepository<ReservationInformationJpaEntity, UUID> {
}
