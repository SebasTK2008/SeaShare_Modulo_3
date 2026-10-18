package com.seashare.seasharem3.infrastructure.adapter.in.web.error;

import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemDetailsFactory()))
                .build();
    }

    @Test
    void contractExceptionProduceElProblemDetailsDelCatalogo() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"contract\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Período inválido"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void laValidacionDelCuerpoDevuelveValidationErrorConCampos() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("value"));
    }

    @Test
    void unCuerpoNoParseableDevuelveValidationError() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void unMetodoNoSoportadoDevuelveMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/probe"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unContentTypeNoSoportadoDevuelveUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unErrorNoControladoDevuelveInternalErrorSinFiltrarDetalleTecnico() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"boom\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error interno inesperado."));
    }

    @RestController
    static class ProbeController {

        @PostMapping(path = "/probe", consumes = MediaType.APPLICATION_JSON_VALUE)
        void probe(@Valid @RequestBody ProbeBody body) {
            if ("contract".equals(body.value())) {
                throw new ContractException(ErrorCode.INVALID_PERIOD, "El período es inválido.");
            }
            if ("boom".equals(body.value())) {
                throw new IllegalStateException("traza secreta que no debe salir");
            }
        }
    }

    record ProbeBody(@NotBlank String value) {
    }
}
