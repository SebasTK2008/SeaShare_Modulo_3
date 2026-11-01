package com.seashare.seasharem3.infrastructure.adapter.in.web;

import com.seashare.seasharem3.application.dto.FinancialRecordResult;
import com.seashare.seasharem3.application.dto.FinancialRecordsPageResult;
import com.seashare.seasharem3.application.dto.QueryFinancialRecordsCommand;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.QueryFinancialRecordsUseCase;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.FinancialRecordType;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReportScope;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.FinancialRecordsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/financial-records")
public class FinancialRecordsController {

    private final QueryFinancialRecordsUseCase useCase;

    public FinancialRecordsController(QueryFinancialRecordsUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public ResponseEntity<FinancialRecordsResponse> get(
            @RequestParam String scope,
            @RequestParam int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(name = "transaction_type", required = false) String transactionType,
            @RequestParam(name = "owner_id", required = false) String ownerId,
            @RequestParam(name = "boat_id", required = false) String boatId,
            @RequestParam(name = "reservation_id", required = false) String reservationId,
            @RequestParam(name = "date_from", required = false) String dateFrom,
            @RequestParam(name = "date_to", required = false) String dateTo) {
        QueryFinancialRecordsCommand command = new QueryFinancialRecordsCommand(
                parseEnum(scope, ReportScope.class, "scope"),
                page,
                size,
                parseEnum(transactionType, FinancialRecordType.class, "transaction_type"),
                parseId(ownerId, "owner_id", OwnerId.class),
                parseId(boatId, "boat_id", BoatId.class),
                parseId(reservationId, "reservation_id", ReservationId.class),
                parseDate(dateFrom, "date_from"),
                parseDate(dateTo, "date_to"));
        return ResponseEntity.ok(toResponse(useCase.query(command)));
    }

    private static FinancialRecordsResponse toResponse(FinancialRecordsPageResult result) {
        List<FinancialRecordsResponse.FinancialRecordResponse> content = result.content().stream()
                .map(FinancialRecordsController::toResponse)
                .toList();
        return new FinancialRecordsResponse(content, result.pageNumber(), result.pageSize(),
                result.totalElements(), result.totalPages());
    }

    private static FinancialRecordsResponse.FinancialRecordResponse toResponse(FinancialRecordResult result) {
        return new FinancialRecordsResponse.FinancialRecordResponse(
                result.recordType().name(),
                result.reservationId().value().toString(),
                result.ownerId().value().toString(),
                result.boatId().value().toString(),
                result.amount().toOutputAmount().toPlainString(),
                result.createdAt().toString(),
                result.externalReference());
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type, String field) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw validation(field, "Unsupported value.");
        }
    }

    private static LocalDate parseDate(String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw validation(field, "Date must use the yyyy-MM-dd format.");
        }
    }

    private static <T> T parseId(String value, String field, Class<T> type) {
        if (value == null) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(value);
            if (type == OwnerId.class) return type.cast(new OwnerId(uuid));
            if (type == BoatId.class) return type.cast(new BoatId(uuid));
            return type.cast(new ReservationId(uuid));
        } catch (IllegalArgumentException exception) {
            throw validation(field, "Value must be a UUID.");
        }
    }

    private static ContractException validation(String field, String detail) {
        return new ContractException(ErrorCode.VALIDATION_ERROR, detail,
                List.of(new com.seashare.seasharem3.application.exception.ApiFieldError(field, detail)));
    }
}
