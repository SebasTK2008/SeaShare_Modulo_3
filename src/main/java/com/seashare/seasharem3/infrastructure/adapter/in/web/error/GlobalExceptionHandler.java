package com.seashare.seasharem3.infrastructure.adapter.in.web.error;

import com.seashare.seasharem3.application.exception.ApiFieldError;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.exception.ChargeIntentNotFoundException;
import com.seashare.seasharem3.domain.exception.FinancialParametersNotConfiguredException;
import com.seashare.seasharem3.domain.exception.FinancialParametersValidationException;
import com.seashare.seasharem3.domain.exception.InvalidPageException;
import com.seashare.seasharem3.domain.exception.InvalidPaymentTokenException;
import com.seashare.seasharem3.domain.exception.ReservationInfoIncompleteException;
import com.seashare.seasharem3.domain.exception.ReservationInfoNotFoundException;
import com.seashare.seasharem3.infrastructure.adapter.in.web.CorrelationIdFilter;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.time.temporal.TemporalAccessor;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Traduce las excepciones de la aplicación y del framework a Problem Details del catálogo
 * (contracts/README §3.3–§3.4). Punto único de traducción [CONV]: los UC no declaran consejos
 * propios; cuando necesitan un error nuevo, agregan su excepción de dominio aquí.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApiProblemDetailsFactory problems;

    public GlobalExceptionHandler(ApiProblemDetailsFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(ContractException.class)
    public ProblemDetail handleContract(ContractException exception) {
        if (exception.code().httpStatus() >= 500) {
            log.error("Contract error {} (correlationId={})", exception.code(),
                    MDC.get(CorrelationIdFilter.MDC_KEY), exception);
        }
        return problems.create(exception.code(), exception.getMessage(), exception.errors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException exception) {
        List<ApiFieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(
                        FinancialParametersFieldNames.toJson(error.getField()), error.getDefaultMessage()))
                .toList();
        return problems.create(ErrorCode.VALIDATION_ERROR, "The request body does not comply with the contract.", errors);
    }

    @ExceptionHandler(BindException.class)
    public ProblemDetail handleBinding(BindException exception) {
        List<ApiFieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return problems.create(ErrorCode.VALIDATION_ERROR, "The request does not comply with the contract.", errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleParameterValidation(HandlerMethodValidationException exception) {
        List<ApiFieldError> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ApiFieldError(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return problems.create(ErrorCode.VALIDATION_ERROR, "The request does not comply with the contract.", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        List<ApiFieldError> errors = exception.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return problems.create(ErrorCode.VALIDATION_ERROR, "The request does not comply with the contract.", errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String expectedType = exception.getRequiredType() != null
                ? exception.getRequiredType().getSimpleName()
                : "the expected type";
        return problems.create(ErrorCode.VALIDATION_ERROR,
                "The value of '%s' is not valid for %s.".formatted(exception.getName(), expectedType));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException exception) {
        return problems.create(ErrorCode.VALIDATION_ERROR,
                "The required parameter '%s' is missing.".formatted(exception.getParameterName()));
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ProblemDetail handleRequestBinding(ServletRequestBindingException exception) {
        return problems.create(ErrorCode.VALIDATION_ERROR, "The request is missing a required value.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        if (isTemporalParseFailure(exception)) {
            return problems.create(ErrorCode.INVALID_DATE_RANGE,
                    "start_date and end_date must be ISO-8601 dates (yyyy-MM-dd).");
        }
        Optional<ApiFieldError> fieldError = fieldError(exception);
        if (fieldError.isPresent()) {
            ApiFieldError error = fieldError.get();
            return problems.create(ErrorCode.VALIDATION_ERROR, error.message(), List.of(error));
        }
        return problems.create(ErrorCode.VALIDATION_ERROR,
                "The request body is not valid JSON or does not match the contract.");
    }

    /**
     * Extrae el campo y el mensaje accionable de un fallo de deserialización de Jackson 3.
     * El path vive en {@link JacksonException#getPath()}; el mensaje se toma de la causa raíz
     * (por ejemplo, el {@code DomainException} del VO o un {@code NumberFormatException}).
     * Si no hay path (JSON mal formado), no se inventan campos.
     */
    private static Optional<ApiFieldError> fieldError(HttpMessageNotReadableException exception) {
        JacksonException jackson = findCause(exception, JacksonException.class);
        if (jackson == null) {
            return Optional.empty();
        }
        String field = jackson.getPath().stream()
                .map(reference -> reference.getPropertyName())
                .filter(Objects::nonNull)
                .collect(Collectors.joining("."));
        if (field.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ApiFieldError(field, messageFor(exception.getMostSpecificCause())));
    }

    private static String messageFor(Throwable cause) {
        if (cause instanceof NumberFormatException) {
            return "must be a decimal string";
        }
        if (cause instanceof MismatchedInputException mismatchedInput) {
            return mismatchedInput.getOriginalMessage();
        }
        if (cause instanceof DomainException domainException) {
            return domainException.getMessage();
        }
        return "is not valid";
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return null;
    }

    /**
     * Un formato de fecha invalido en el cuerpo es {@code INVALID_DATE_RANGE}, no un error de
     * validacion generico (contrato individual §5). Se distingue del resto de fallos de lectura
     * inspeccionando el tipo destino del error de Jackson o un {@link DateTimeParseException} anidado.
     */
    private static boolean isTemporalParseFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof DateTimeParseException) {
                return true;
            }
            if (current instanceof InvalidFormatException invalidFormat) {
                Class<?> targetType = invalidFormat.getTargetType();
                if (targetType != null && TemporalAccessor.class.isAssignableFrom(targetType)) {
                    return true;
                }
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return problems.create(ErrorCode.METHOD_NOT_ALLOWED,
                "The HTTP method is not supported on this route.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return problems.create(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "The Content-Type is not supported; use application/json.");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ProblemDetail handleNotAcceptable(HttpMediaTypeNotAcceptableException exception) {
        return problems.create(ErrorCode.NOT_ACCEPTABLE,
                "The requested Accept is not supported by this operation.");
    }

    @ExceptionHandler(BaseRateException.class)
    public ProblemDetail handleBaseRate(BaseRateException exception) {
        ErrorCode code = switch (exception.reason()) {
            case FLEET_UNAVAILABLE -> ErrorCode.FLEET_UNAVAILABLE;
            case DYNAMIC_RATE_NOT_CONFIGURED -> ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED;
            case PARAMETERS_UNAVAILABLE -> ErrorCode.INTERNAL_ERROR;
            case BASE_RATE_NOT_AVAILABLE -> ErrorCode.BASE_RATE_NOT_AVAILABLE;
        };
        return problems.create(code, exception.getMessage());
    }

    @ExceptionHandler(FinancialParametersNotConfiguredException.class)
    public ProblemDetail handleParametersNotConfigured(FinancialParametersNotConfiguredException exception) {
        return problems.create(ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED, exception.getMessage());
    }

    @ExceptionHandler(ReservationInfoNotFoundException.class)
    public ProblemDetail handleReservationInfoNotFound(ReservationInfoNotFoundException exception) {
        return problems.create(ErrorCode.RESERVATION_INFO_NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ReservationInfoIncompleteException.class)
    public ProblemDetail handleReservationInfoIncomplete(ReservationInfoIncompleteException exception) {
        return problems.create(ErrorCode.RESERVATION_INFO_INCOMPLETE, exception.getMessage());
    }

    @ExceptionHandler(ChargeIntentNotFoundException.class)
    public ProblemDetail handleChargeIntentNotFound(ChargeIntentNotFoundException exception) {
        return problems.create(ErrorCode.CHARGE_INTENT_NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidPageException.class)
    public ProblemDetail handleInvalidPage(InvalidPageException exception) {
        return problems.create(ErrorCode.PAGE_OUT_OF_RANGE, exception.getMessage());
    }

    @ExceptionHandler(InvalidPaymentTokenException.class)
    public ProblemDetail handleInvalidPaymentToken(InvalidPaymentTokenException exception) {
        return problems.create(ErrorCode.VALIDATION_ERROR, exception.getMessage());
    }

    @ExceptionHandler(FinancialParametersValidationException.class)
    public ProblemDetail handleFinancialParametersValidation(FinancialParametersValidationException exception) {
        String field = FinancialParametersFieldNames.toJson(exception.field());
        return problems.create(ErrorCode.VALIDATION_ERROR, exception.getMessage(),
                List.of(new ApiFieldError(field, exception.getMessage())));
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException exception) {
        return problems.create(ErrorCode.VALIDATION_ERROR, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            return enrichWithCatalogCode(errorResponse.getBody());
        }
        log.error("Unhandled error (correlationId={})", MDC.get("correlationId"), exception);
        return problems.create(ErrorCode.INTERNAL_ERROR, "An unexpected internal error occurred.");
    }

    /**
     * Completa {@code code}/{@code retryable} en los Problem Details generados por el framework cuando
     * no traen ya un código del catálogo (nunca se inventan códigos para status sin entrada).
     */
    private ProblemDetail enrichWithCatalogCode(ProblemDetail problem) {
        if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
            ErrorCode code = catalogCodeFor(problem.getStatus());
            if (code != null) {
                problem.setProperty("code", code.name());
                problem.setProperty("retryable", code.retryable());
            }
        }
        return problem;
    }

    private static ErrorCode catalogCodeFor(int status) {
        return switch (status) {
            case 400 -> ErrorCode.VALIDATION_ERROR;
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> ErrorCode.NOT_ACCEPTABLE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> status >= 500 && status <= 599 ? ErrorCode.INTERNAL_ERROR : null;
        };
    }
}
