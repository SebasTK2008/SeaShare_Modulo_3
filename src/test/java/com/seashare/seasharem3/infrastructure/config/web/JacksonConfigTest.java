package com.seashare.seasharem3.infrastructure.config.web;

import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonConfigTest {

    private static final UUID BOAT_UUID = UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01");

    private final JsonMapper mapper = buildMapper();

    @Test
    void serializesBigDecimalAsTwoDecimalStringWithoutScientificNotation() {
        assertThat(mapper.writeValueAsString(new Amount(new BigDecimal("350000.00"))))
                .isEqualTo("{\"amount\":\"350000.00\"}");
        assertThat(mapper.writeValueAsString(new Amount(new BigDecimal("1E+3"))))
                .isEqualTo("{\"amount\":\"1000.00\"}");
        assertThat(mapper.writeValueAsString(new Amount(new BigDecimal("0.105"))))
                .isEqualTo("{\"amount\":\"0.11\"}");
    }

    @Test
    void serializesDomainScalarsWithContractShape() {
        assertThat(mapper.writeValueAsString(new Money(new BigDecimal("350000"))))
                .isEqualTo("\"350000.00\"");
        assertThat(mapper.writeValueAsString(new Percentage(new BigDecimal("15"))))
                .isEqualTo("\"15.00\"");
        assertThat(mapper.writeValueAsString(new BoatId(BOAT_UUID)))
                .isEqualTo("\"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01\"");
    }

    @Test
    void serializesJavaTimeAsIso8601Strings() {
        assertThat(mapper.writeValueAsString(new InstantValue(Instant.parse("2026-10-01T15:04:05Z"))))
                .isEqualTo("{\"value\":\"2026-10-01T15:04:05Z\"}");
        assertThat(mapper.writeValueAsString(new DayValue(LocalDate.of(2026, 10, 3))))
                .isEqualTo("{\"value\":\"2026-10-03\"}");
    }

    @Test
    void deserializesDomainScalarsFromContractStrings() {
        assertThat(mapper.readValue("\"350000.00\"", Money.class))
                .isEqualTo(new Money(new BigDecimal("350000")));
        assertThat(mapper.readValue("\"15.00\"", Percentage.class))
                .isEqualTo(new Percentage(new BigDecimal("15")));
        assertThat(mapper.readValue("\"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01\"", BoatId.class))
                .isEqualTo(new BoatId(BOAT_UUID));
    }

    @Test
    void rejectsJsonNumbersForMoneyAndPercentage() {
        assertThatThrownBy(() -> mapper.readValue("350000.00", Money.class))
                .hasMessageContaining("must be a decimal string");
        assertThatThrownBy(() -> mapper.readValue("15.00", Percentage.class))
                .hasMessageContaining("must be a decimal string");
    }

    private static JsonMapper buildMapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().domainScalarsCustomizer().customize(builder);
        return builder.build();
    }

    record Amount(BigDecimal amount) {
    }

    record InstantValue(Instant value) {
    }

    record DayValue(LocalDate value) {
    }
}
