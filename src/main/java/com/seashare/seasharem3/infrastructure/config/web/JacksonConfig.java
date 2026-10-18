package com.seashare.seasharem3.infrastructure.config.web;

import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.DisputeId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;

/**
 * JSON de la API: los escalares del dominio viajan con la forma de los contratos (contracts/README §3.1):
 * {@link BigDecimal} y {@link Money} como string decimal de 2 decimales HALF_UP, {@link Percentage} de 2,
 * {@link BaseRate} como su monto y los identificadores como UUID plano. Las fechas e instantes usan ISO-8601:
 * en Jackson 3 {@code WRITE_DATES_AS_TIMESTAMPS} vive en {@link DateTimeFeature} y Boot 4 ya no la expone
 * como propiedad, por lo que se desactiva aquí. Aplica al ObjectMapper web y al de AMQP por compartir el
 * JsonMapper de Spring Boot, pero NO a clientes de pasarela construidos aparte: [CONV] los adaptadores
 * externos (Mercado Pago) deben registrar su propia configuración si el proveedor exige números.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer domainScalarsCustomizer() {
        SimpleModule module = new SimpleModule("seashare-scalars");
        module.addSerializer(BigDecimal.class, new BigDecimalPlainStringSerializer());
        module.addSerializer(Money.class, new MoneySerializer());
        module.addDeserializer(Money.class, new MoneyDeserializer());
        module.addSerializer(Percentage.class, new PercentageSerializer());
        module.addDeserializer(Percentage.class, new PercentageDeserializer());
        module.addSerializer(BaseRate.class, new BaseRateSerializer());
        module.addDeserializer(BaseRate.class, new BaseRateDeserializer());
        module.addSerializer(ReservationId.class, new UuidScalarSerializer<>(ReservationId::value));
        module.addDeserializer(ReservationId.class, new UuidScalarDeserializer<>(ReservationId::new));
        module.addSerializer(OwnerId.class, new UuidScalarSerializer<>(OwnerId::value));
        module.addDeserializer(OwnerId.class, new UuidScalarDeserializer<>(OwnerId::new));
        module.addSerializer(BoatId.class, new UuidScalarSerializer<>(BoatId::value));
        module.addDeserializer(BoatId.class, new UuidScalarDeserializer<>(BoatId::new));
        module.addSerializer(DisputeId.class, new UuidScalarSerializer<>(DisputeId::value));
        module.addDeserializer(DisputeId.class, new UuidScalarDeserializer<>(DisputeId::new));
        return builder -> builder
                .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .addModule(module);
    }
}
