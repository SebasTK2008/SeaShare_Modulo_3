package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.FinancialRecordsPageResult;
import com.seashare.seasharem3.application.dto.QueryFinancialRecordsCommand;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.QueryFinancialRecordsUseCase;
import com.seashare.seasharem3.application.port.out.FinancialRecordQueryPort;
import com.seashare.seasharem3.domain.exception.InvalidPageException;
import com.seashare.seasharem3.domain.valueobject.ReportScope;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Application service for UC12. The declared scope is treated as request data; no
 * authentication or ownership lookup is performed.
 */
public class FinancialRecordsQueryService implements QueryFinancialRecordsUseCase {

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    private final FinancialRecordQueryPort queryPort;

    public FinancialRecordsQueryService(FinancialRecordQueryPort queryPort) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort");
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialRecordsPageResult query(QueryFinancialRecordsCommand command) {
        validate(command);
        int pageSize = command.size() == null ? DEFAULT_PAGE_SIZE : command.size();
        QueryFinancialRecordsCommand normalized = new QueryFinancialRecordsCommand(
                command.scope(), command.page(), pageSize, command.transactionType(),
                command.ownerId(), command.boatId(), command.reservationId(),
                command.dateFrom(), command.dateTo());

        FinancialRecordsPageResult result = queryPort.query(normalized);
        int maximumPage = Math.max(result.totalPages(), 1);
        if (command.page() > maximumPage) {
            throw new InvalidPageException("The requested page is outside the available range.");
        }
        return result;
    }

    private static void validate(QueryFinancialRecordsCommand command) {
        if (command == null || command.scope() == null) {
            throw validation("scope", "scope is required.");
        }
        if (command.page() < 1) {
            throw validation("page", "page must be greater than or equal to 1.");
        }
        if (command.size() != null && (command.size() < 1 || command.size() > MAX_PAGE_SIZE)) {
            throw validation("size", "size must be between 1 and 100.");
        }
        if (command.scope() == ReportScope.OWNER && command.ownerId() == null) {
            throw validation("owner_id", "owner_id is required when scope is OWNER.");
        }
        if (command.dateFrom() != null && command.dateTo() != null
                && command.dateFrom().isAfter(command.dateTo())) {
            throw validation("date_from", "date_from must not be after date_to.");
        }
    }

    private static ContractException validation(String field, String message) {
        return new ContractException(ErrorCode.VALIDATION_ERROR, message,
                java.util.List.of(new com.seashare.seasharem3.application.exception.ApiFieldError(field, message)));
    }
}
