package com.seashare.seasharem3.infrastructure.config.web;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    private final JsonMapper mapper = buildMapper();

    @Test
    void serializaBigDecimalComoStringDecimalSinNotacionCientifica() {
        assertThat(mapper.writeValueAsString(new Amount(new BigDecimal("350000.00"))))
                .isEqualTo("{\"amount\":\"350000.00\"}");
        assertThat(mapper.writeValueAsString(new Amount(new BigDecimal("1E+3"))))
                .isEqualTo("{\"amount\":\"1000\"}");
        assertThat(mapper.writeValueAsString(new Amount(new BigDecimal("0.10"))))
                .isEqualTo("{\"amount\":\"0.10\"}");
    }

    private static JsonMapper buildMapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().moneyAsPlainStringCustomizer().customize(builder);
        return builder.build();
    }

    record Amount(BigDecimal amount) {
    }
}
