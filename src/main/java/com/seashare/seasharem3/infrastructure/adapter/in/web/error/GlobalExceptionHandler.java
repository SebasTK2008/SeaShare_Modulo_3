package com.seashare.seasharem3.infrastructure.adapter.in.web.error;

import com.seashare.seasharem3.application.exception.ApiFieldError;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.exception.FinancialParametersNotConfiguredException;
import com.seashare.seasharem3.domain.exception.InvalidPaymentTokenException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.List;

/**
 * Traduce las excepciones de la aplicación y del framework a Problem Details del catálogo
 * (contracts/README §3.3–§3.4). Última prioridad: los consejos de cada UC pueden especializar
 * sin cambiarlo. [CONV] núcleo compartido.
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
        return problems.create(exception.code(), exception.getMessage(), exception.errors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException exception) {
        List<ApiFieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return problems.create(ErrorCode.VALIDATION_ERROR, "El cuerpo de la petición no cumple el contrato.", errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleParameterValidation(HandlerMethodValidationException exception) {
        List<ApiFieldError> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ApiFieldError(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return problems.create(ErrorCode.VALIDATION_ERROR, "La petición no cumple el contrato.", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        List<ApiFieldError> errors = exception.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return problems.create(ErrorCode.VALIDATION_ERROR, "La petición no cumple el contrato.", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        return problems.create(ErrorCode.VALIDATION_ERROR,
                "El cuerpo de la petición no es JSON válido o no coincide con el contrato.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return problems.create(ErrorCode.METHOD_NOT_ALLOWED,
                "El método HTTP no está soportado en esta ruta.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return problems.create(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "El Content-Type no está soportado; use application/json.");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ProblemDetail handleNotAcceptable(HttpMediaTypeNotAcceptableException exception) {
        return problems.create(ErrorCode.NOT_ACCEPTABLE,
                "El Accept solicitado no está soportado por esta operación.");
    }

    @ExceptionHandler(BaseRateException.class)
    public ProblemDetail handleBaseRate(BaseRateException exception) {
        ErrorCode code = switch (exception.reason()) {
            case FLEET_UNAVAILABLE -> ErrorCode.FLEET_UNAVAILABLE;
            case DYNAMIC_RATE_NOT_CONFIGURED -> ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED;
            case BASE_RATE_NOT_AVAILABLE -> ErrorCode.BASE_RATE_NOT_AVAILABLE;
        };
        return problems.create(code, exception.getMessage());
    }

    @ExceptionHandler(FinancialParametersNotConfiguredException.class)
    public ProblemDetail handleParametersNotConfigured(FinancialParametersNotConfiguredException exception) {
        return problems.create(ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED, exception.getMessage());
    }

    @ExceptionHandler(InvalidPaymentTokenException.class)
    public ProblemDetail handleInvalidPaymentToken(InvalidPaymentTokenException exception) {
        return problems.create(ErrorCode.VALIDATION_ERROR, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            return errorResponse.getBody();
        }
        log.error("Error no controlado (correlationId={})", MDC.get("correlationId"), exception);
        return problems.create(ErrorCode.INTERNAL_ERROR, "Ocurrió un error interno inesperado.");
    }
}
