package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.FinancialRecordsPageResult;
import com.seashare.seasharem3.application.dto.QueryFinancialRecordsCommand;
import com.seashare.seasharem3.application.port.out.FinancialRecordQueryPort;
import com.seashare.seasharem3.domain.exception.InvalidPageException;
import com.seashare.seasharem3.domain.valueobject.FinancialRecordType;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReportScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class FinancialRecordsQueryServiceTest {

    @Test
    void appliesDefaultPageSizeAndPreservesDeclaredOwnerScope() {
        CapturingPort port = new CapturingPort(new FinancialRecordsPageResult(List.of(), 1, 10, 0, 0));
        FinancialRecordsQueryService service = new FinancialRecordsQueryService(port);
        OwnerId owner = new OwnerId(UUID.randomUUID());

        service.query(new QueryFinancialRecordsCommand(
                ReportScope.OWNER, 1, null, FinancialRecordType.COMMISSION, owner,
                null, null, null, null));

        assertThat(port.command.size()).isEqualTo(10);
        assertThat(port.command.ownerId()).isEqualTo(owner);
        assertThat(port.command.transactionType()).isEqualTo(FinancialRecordType.COMMISSION);
    }

    @Test
    void rejectsPageOutsideAvailableRange() {
        CapturingPort port = new CapturingPort(new FinancialRecordsPageResult(List.of(), 2, 10, 11, 2));
        FinancialRecordsQueryService service = new FinancialRecordsQueryService(port);

        assertThatExceptionOfType(InvalidPageException.class).isThrownBy(() ->
                service.query(new QueryFinancialRecordsCommand(
                        ReportScope.PLATFORM, 3, 10, null, null, null, null, null, null)));
    }

    @Test
    void rejectsAMissingScope() {
        FinancialRecordsQueryService service = new FinancialRecordsQueryService(
                new CapturingPort(new FinancialRecordsPageResult(List.of(), 1, 10, 0, 0)));

        assertThatExceptionOfType(com.seashare.seasharem3.application.exception.ContractException.class)
                .isThrownBy(() -> service.query(new QueryFinancialRecordsCommand(
                        null, 1, 10, null, null, null, null, null, null)));
    }

    @Test
    void rejectsAPageBelowOne() {
        FinancialRecordsQueryService service = new FinancialRecordsQueryService(
                new CapturingPort(new FinancialRecordsPageResult(List.of(), 1, 10, 0, 0)));

        assertThatExceptionOfType(com.seashare.seasharem3.application.exception.ContractException.class)
                .isThrownBy(() -> service.query(new QueryFinancialRecordsCommand(
                        ReportScope.PLATFORM, 0, 10, null, null, null, null, null, null)));
    }

    @Test
    void rejectsAPageSizeOutsideTheAllowedRange() {
        FinancialRecordsQueryService service = new FinancialRecordsQueryService(
                new CapturingPort(new FinancialRecordsPageResult(List.of(), 1, 10, 0, 0)));

        assertThatExceptionOfType(com.seashare.seasharem3.application.exception.ContractException.class)
                .isThrownBy(() -> service.query(new QueryFinancialRecordsCommand(
                        ReportScope.PLATFORM, 1, 101, null, null, null, null, null, null)));
        assertThatExceptionOfType(com.seashare.seasharem3.application.exception.ContractException.class)
                .isThrownBy(() -> service.query(new QueryFinancialRecordsCommand(
                        ReportScope.PLATFORM, 1, 0, null, null, null, null, null, null)));
    }

    @Test
    void rejectsAnOwnerScopeWithoutOwnerId() {
        FinancialRecordsQueryService service = new FinancialRecordsQueryService(
                new CapturingPort(new FinancialRecordsPageResult(List.of(), 1, 10, 0, 0)));

        assertThatExceptionOfType(com.seashare.seasharem3.application.exception.ContractException.class)
                .isThrownBy(() -> service.query(new QueryFinancialRecordsCommand(
                        ReportScope.OWNER, 1, 10, null, null, null, null, null, null)));
    }

    @Test
    void rejectsAnInvertedDateRange() {
        FinancialRecordsQueryService service = new FinancialRecordsQueryService(
                new CapturingPort(new FinancialRecordsPageResult(List.of(), 1, 10, 0, 0)));

        assertThatExceptionOfType(com.seashare.seasharem3.application.exception.ContractException.class)
                .isThrownBy(() -> service.query(new QueryFinancialRecordsCommand(
                        ReportScope.PLATFORM, 1, 10, null, null, null, null,
                        java.time.LocalDate.of(2026, 10, 31), java.time.LocalDate.of(2026, 10, 1))));
    }

    private static final class CapturingPort implements FinancialRecordQueryPort {
        private final FinancialRecordsPageResult result;
        private QueryFinancialRecordsCommand command;

        private CapturingPort(FinancialRecordsPageResult result) {
            this.result = result;
        }

        @Override
        public FinancialRecordsPageResult query(QueryFinancialRecordsCommand command) {
            this.command = command;
            return result;
        }
    }
}
