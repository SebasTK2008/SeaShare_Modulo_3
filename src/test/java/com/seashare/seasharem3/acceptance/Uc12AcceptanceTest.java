package com.seashare.seasharem3.acceptance;

import com.seashare.seasharem3.application.dto.FinancialRecordResult;
import com.seashare.seasharem3.application.dto.FinancialRecordsPageResult;
import com.seashare.seasharem3.application.dto.QueryFinancialRecordsCommand;
import com.seashare.seasharem3.application.port.out.FinancialRecordQueryPort;
import com.seashare.seasharem3.application.service.FinancialRecordsQueryService;
import com.seashare.seasharem3.domain.exception.InvalidPageException;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.FinancialRecordType;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReportScope;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de aceptación de UC12 (CE-001…CE-005): alcance declarado, recorrido completo sin
 * duplicados, paginación por defecto y metadatos, detalle de los cuatro tipos y consulta de solo
 * lectura.
 *
 * <p>Fuente: UC12 plan T013-T017 y T019; SPEC 12 Criterios de Éxito.</p>
 */
class Uc12AcceptanceTest {

    private static final OwnerId OWNER = new OwnerId(UUID.randomUUID());
    private static final OwnerId OTHER = new OwnerId(UUID.randomUUID());

    private static FinancialRecordResult record(OwnerId owner, FinancialRecordType type, String amount) {
        return new FinancialRecordResult(type, new ReservationId(UUID.randomUUID()), owner,
                new BoatId(UUID.randomUUID()), new Money(new BigDecimal(amount)),
                Instant.parse("2026-10-10T10:00:00Z"), null);
    }

    private static FinancialRecordsQueryService service() {
        List<FinancialRecordResult> records = new ArrayList<>();
        records.add(record(OWNER, FinancialRecordType.CHARGE, "1000.00"));
        records.add(record(OWNER, FinancialRecordType.REFUND, "100.00"));
        records.add(record(OWNER, FinancialRecordType.SETTLEMENT, "800.00"));
        records.add(record(OWNER, FinancialRecordType.COMMISSION, "10.00"));
        records.add(record(OTHER, FinancialRecordType.CHARGE, "9999.00"));
        return new FinancialRecordsQueryService(new PagingPort(records));
    }

    private static QueryFinancialRecordsCommand command(ReportScope scope, OwnerId owner, int page, Integer size) {
        return new QueryFinancialRecordsCommand(scope, page, size, null, owner, null, null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    }

    @Test
    void ce001_alcance_propietario() {
        FinancialRecordsPageResult page = service().query(command(ReportScope.OWNER, OWNER, 1, 10));

        assertThat(page.content()).isNotEmpty();
        assertThat(page.content()).allMatch(result -> result.ownerId().equals(OWNER));
        assertThat(page.totalElements()).isEqualTo(4);
    }

    @Test
    void ce002_cobertura_global_sin_duplicados() {
        FinancialRecordsQueryService service = service();
        List<FinancialRecordResult> visited = new ArrayList<>();
        for (int page = 1; page <= 2; page++) {
            visited.addAll(service.query(command(ReportScope.PLATFORM, null, page, 3)).content());
        }

        assertThat(visited).hasSize(5);
        assertThat(visited).doesNotHaveDuplicates();
    }

    @Test
    void ce003_paginacion_default_y_metadatos() {
        FinancialRecordsPageResult page = service().query(command(ReportScope.PLATFORM, null, 1, null));

        assertThat(page.pageSize()).isEqualTo(FinancialRecordsQueryService.DEFAULT_PAGE_SIZE);
        assertThat(page.pageNumber()).isEqualTo(1);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    void ce004_detalle_completo_y_comision() {
        FinancialRecordsPageResult page = service().query(command(ReportScope.OWNER, OWNER, 1, 10));

        assertThat(page.content()).extracting(FinancialRecordResult::recordType)
                .containsExactlyInAnyOrder(FinancialRecordType.CHARGE, FinancialRecordType.REFUND,
                        FinancialRecordType.SETTLEMENT, FinancialRecordType.COMMISSION);
        assertThat(page.content()).allMatch(result -> result.amount() != null && result.reservationId() != null);
    }

    @Test
    void ce005_solo_lectura_sin_exportacion() {
        FinancialRecordsQueryService service = service();
        FinancialRecordsPageResult first = service.query(command(ReportScope.PLATFORM, null, 1, 10));
        FinancialRecordsPageResult second = service.query(command(ReportScope.PLATFORM, null, 1, 10));

        assertThat(second).isEqualTo(first);
        assertThat(service.query(command(ReportScope.PLATFORM, null, 1, 10)).totalElements()).isEqualTo(5);
    }

    @Test
    void rejectsAPageOutsideTheAvailableRange() {
        assertThatThrownBy(() -> service().query(command(ReportScope.PLATFORM, null, 9, 2)))
                .isInstanceOf(InvalidPageException.class);
    }

    /** Puerto en memoria con la misma semántica de paginación del adaptador real (1-based). */
    private static final class PagingPort implements FinancialRecordQueryPort {

        private final List<FinancialRecordResult> records;

        private PagingPort(List<FinancialRecordResult> records) {
            this.records = records.stream()
                    .sorted(Comparator.comparing(FinancialRecordResult::createdAt).reversed())
                    .toList();
        }

        @Override
        public FinancialRecordsPageResult query(QueryFinancialRecordsCommand command) {
            List<FinancialRecordResult> scoped = records.stream()
                    .filter(result -> command.scope() != ReportScope.OWNER
                            || result.ownerId().equals(command.ownerId()))
                    .toList();
            int size = command.size();
            int totalPages = scoped.isEmpty() ? 0 : ((scoped.size() - 1) / size) + 1;
            int from = (command.page() - 1) * size;
            List<FinancialRecordResult> content = from >= scoped.size() ? List.of()
                    : scoped.subList(from, Math.min(from + size, scoped.size()));
            return new FinancialRecordsPageResult(content, command.page(), size, scoped.size(), totalPages);
        }
    }
}
