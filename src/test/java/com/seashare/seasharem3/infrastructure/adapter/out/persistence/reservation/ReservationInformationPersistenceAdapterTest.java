package com.seashare.seasharem3.infrastructure.adapter.out.persistence.reservation;

import com.seashare.seasharem3.application.port.out.ReservationLockPort;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica que el adaptador adquiere el bloqueo asesor por reserva (D-15) dentro de las operaciones
 * transaccionales de UC03/UC04. La integración con PostgreSQL real se cubre en
 * {@link ReservationInformationPersistenceAdapterIntegrationTest}.
 */
class ReservationInformationPersistenceAdapterTest {

    private static final ReservationInformation INFORMATION = ReservationInformation.create(
            new ReservationId(UUID.fromString("b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10")),
            new BoatId(UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01")),
            new BaseRate(new Money(new BigDecimal("437500.0000"))),
            null,
            LocalDate.of(2026, 12, 20),
            LocalDate.of(2026, 12, 22),
            4,
            new OwnerId(UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d")),
            6);

    private ReservationInformationJpaRepository repository;
    private ReservationLockPort reservationLockPort;
    private ReservationInformationPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = mock(ReservationInformationJpaRepository.class);
        reservationLockPort = mock(ReservationLockPort.class);
        adapter = new ReservationInformationPersistenceAdapter(repository, reservationLockPort);
    }

    @Test
    void saveAcquiresTheReservationLockBeforeTheUpsert() {
        ReservationInformationJpaEntity entity = ReservationInformationJpaEntity.from(INFORMATION);
        when(repository.findById(INFORMATION.reservationId().value())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenReturn(entity);

        adapter.save(INFORMATION);

        verify(reservationLockPort).lock(INFORMATION.reservationId());
        verify(repository).saveAndFlush(any());
    }

    @Test
    void saveCalculatedAmountsAcquiresTheReservationLockBeforeTheUpdate() {
        ReservationInformationJpaEntity entity = ReservationInformationJpaEntity.from(INFORMATION);
        when(repository.findById(INFORMATION.reservationId().value())).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(any())).thenReturn(entity);

        adapter.saveCalculatedAmounts(INFORMATION);

        verify(reservationLockPort).lock(INFORMATION.reservationId());
        verify(repository).saveAndFlush(any());
    }
}
