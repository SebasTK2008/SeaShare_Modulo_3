package com.seashare.seasharem3.infrastructure.adapter.in.web.error;

import com.seashare.seasharem3.application.exception.ApiFieldError;
import com.seashare.seasharem3.application.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiProblemDetailsFactoryTest {

    private final ApiProblemDetailsFactory factory = new ApiProblemDetailsFactory();

    @Test
    void buildsCatalogProblemDetails() {
        ProblemDetail problem = factory.create(ErrorCode.CHARGE_INTENT_NOT_FOUND, "There is no intent for the reservation.");

        assertThat(problem.getType().toString()).isEqualTo("about:blank");
        assertThat(problem.getTitle()).isEqualTo("Charge intent not found");
        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).isEqualTo("There is no intent for the reservation.");
        assertThat(problem.getProperties())
                .containsEntry("code", "CHARGE_INTENT_NOT_FOUND")
                .containsEntry("retryable", true)
                .doesNotContainKey("errors");
    }

    @Test
    void doesNotMarkContractErrorsRetryable() {
        ProblemDetail problem = factory.create(ErrorCode.INVALID_PERIOD, "The period is invalid.");

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties()).containsEntry("retryable", false);
    }

    @Test
    void includesValidationErrorsWhenPresent() {
        ProblemDetail problem = factory.create(ErrorCode.VALIDATION_ERROR, "The request does not comply with the contract.",
                List.of(new ApiFieldError("page", "must be greater than or equal to 1")));

        assertThat(problem.getProperties()).containsKey("errors");
        assertThat(problem.getProperties().get("errors"))
                .isEqualTo(List.of(new ApiFieldError("page", "must be greater than or equal to 1")));
    }
}
