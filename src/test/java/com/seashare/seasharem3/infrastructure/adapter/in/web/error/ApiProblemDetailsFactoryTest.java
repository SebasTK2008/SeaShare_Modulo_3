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
    void armaElProblemDetailsDelCatalogo() {
        ProblemDetail problem = factory.create(ErrorCode.CHARGE_INTENT_NOT_FOUND, "No hay intención para la reserva.");

        assertThat(problem.getType().toString()).isEqualTo("about:blank");
        assertThat(problem.getTitle()).isEqualTo("Intención de cobro no encontrada");
        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).isEqualTo("No hay intención para la reserva.");
        assertThat(problem.getProperties())
                .containsEntry("code", "CHARGE_INTENT_NOT_FOUND")
                .containsEntry("retryable", true)
                .doesNotContainKey("errors");
    }

    @Test
    void noMarcaRetryableLosErroresDeContrato() {
        ProblemDetail problem = factory.create(ErrorCode.INVALID_PERIOD, "El período es inválido.");

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties()).containsEntry("retryable", false);
    }

    @Test
    void incluyeLosErroresDeValidacionCuandoExisten() {
        ProblemDetail problem = factory.create(ErrorCode.VALIDATION_ERROR, "La petición no cumple el contrato.",
                List.of(new ApiFieldError("page", "debe ser mayor o igual a 1")));

        assertThat(problem.getProperties()).containsKey("errors");
        assertThat(problem.getProperties().get("errors"))
                .isEqualTo(List.of(new ApiFieldError("page", "debe ser mayor o igual a 1")));
    }
}
