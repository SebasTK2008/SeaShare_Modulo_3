package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.FinancialParametersCommand;
import com.seashare.seasharem3.application.dto.FinancialParametersResult;
import com.seashare.seasharem3.application.dto.SaveFinancialParametersResult;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.service.HighSeasonCalendar;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.support.FixedClockPort;
import com.seashare.seasharem3.support.InMemoryFinancialParametersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Pruebas de aceptación arquitectónica y consistencia de UC11:
 * CE-001 (consistencia de configuración), CE-002 (cumplimiento arquitectónico y no retroactividad)
 * y CE-004 (integridad de edición).
 * Fuente: {@code docs/features/011-configurar-parametros-financieros-globales/spec.md} y plan T034, T035, T036.
 */
class FinancialParametersConsistencyTest {

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 10, 24);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC);

    private InMemoryFinancialParametersRepository repository;
    private FinancialParametersService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryFinancialParametersRepository();
        service = new FinancialParametersService(
                repository,
                new HighSeasonCalendar(),
                new FixedClockPort(FIXED_DATE),
                FIXED_CLOCK);
    }

    @Test
    @DisplayName("CE-001: 100% de las configuraciones guardadas reflejan exactamente sus 4 parámetros en la lectura")
    void ce001_lectura_refleja_ultima_escritura() {
        // T034 (CE-001): PUT -> GET devuelve lo persistido; consistencia entre escritura y lectura.
        FinancialParametersCommand command = new FinancialParametersCommand(
                new Percentage(new BigDecimal("15.00")),
                new Money(new BigDecimal("15000.00")),
                new Percentage(new BigDecimal("10.00")),
                new Percentage(new BigDecimal("25.00")));

        SaveFinancialParametersResult saveResult = service.save(command);
        assertThat(saveResult.versionNumber()).isEqualTo(1);

        FinancialParametersResult loadResult = service.load();

        assertThat(loadResult.versionNumber()).isEqualTo(1);
        assertThat(loadResult.commissionPct()).isEqualTo(command.commissionPct());
        assertThat(loadResult.insuranceFeePerPassenger()).isEqualTo(command.insuranceFeePerPassenger());
        assertThat(loadResult.weekendIncreasePct()).isEqualTo(command.weekendIncreasePct());
        assertThat(loadResult.highSeasonIncreasePct()).isEqualTo(command.highSeasonIncreasePct());
        assertThat(loadResult.guaranteeDepositRule()).isEqualTo("10% de la tarifa base diaria");
        assertThat(loadResult.highSeasonWindows()).contains("semana-santa");
    }

    @Test
    @DisplayName("CE-002: Actualizaciones completas crean una nueva versión vigente conservando las versiones anteriores íntegras")
    void ce002_version_nueva_completa_sin_parciales() {
        // T035 (CE-002): Cada guardado válido con contenido distinto crea una versión nueva;
        // las anteriores se conservan como historial inmutable sin modificaciones.
        FinancialParametersCommand v1Command = new FinancialParametersCommand(
                new Percentage(new BigDecimal("10.00")),
                new Money(new BigDecimal("5000.00")),
                new Percentage(new BigDecimal("10.00")),
                new Percentage(new BigDecimal("15.00")));

        service.save(v1Command);
        FinancialParameters v1Entity = repository.findCurrent().orElseThrow();
        UUID v1Id = v1Entity.id();
        assertThat(v1Entity.versionNumber()).isEqualTo(1);

        // Guardar nueva versión con comisión y seguro modificados
        FinancialParametersCommand v2Command = new FinancialParametersCommand(
                new Percentage(new BigDecimal("20.00")),
                new Money(new BigDecimal("8500.00")),
                new Percentage(new BigDecimal("10.00")),
                new Percentage(new BigDecimal("15.00")));

        service.save(v2Command);
        FinancialParameters v2Entity = repository.findCurrent().orElseThrow();

        // La nueva versión vigente es la 2
        assertThat(v2Entity.versionNumber()).isEqualTo(2);
        assertThat(v2Entity.commissionPct().value()).isEqualByComparingTo(new BigDecimal("20.0000"));

        // La versión 1 sigue intacta e inmutable (historial append-only, RF-007)
        FinancialParameters historicalV1 = repository.findById(v1Id).orElseThrow();
        assertThat(historicalV1.versionNumber()).isEqualTo(1);
        assertThat(historicalV1.commissionPct().value()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(historicalV1.insuranceFeePerPassenger().amount()).isEqualByComparingTo(new BigDecimal("5000.0000"));

        // La versión vigente sigue siendo la 2 con sus valores íntegros.
        // El PUT inválido se prueba en UC11FinancialParametersWebIntegrationTest (M3).
        assertThat(service.load().versionNumber()).isEqualTo(2);
        assertThat(service.load().commissionPct().value()).isEqualByComparingTo(new BigDecimal("20.0000"));
    }

    @Test
    @DisplayName("CE-004: Integridad de edición - guardado fallido no persiste y conserva intacta la configuración anterior")
    void ce004_guardado_invalido_no_persiste() {
        // T036 (CE-004): Fallo durante guardado conserva íntegramente la configuración anterior
        FinancialParametersCommand v1Command = new FinancialParametersCommand(
                new Percentage(new BigDecimal("12.00")),
                new Money(new BigDecimal("6000.00")),
                new Percentage(new BigDecimal("8.00")),
                new Percentage(new BigDecimal("18.00")));

        service.save(v1Command);
        assertThat(service.load().versionNumber()).isEqualTo(1);

        // Simular fallo de persistencia en el repositorio
        InMemoryFinancialParametersRepository failingRepo = new InMemoryFinancialParametersRepository() {
            private int callCount = 0;

            @Override
            public FinancialParameters saveNewVersion(FinancialParameters params) {
                callCount++;
                if (callCount > 1) {
                    throw new IllegalStateException("Simulated database failure");
                }
                return super.saveNewVersion(params);
            }
        };

        // Copiamos la versión 1 al failing repo
        failingRepo.saveNewVersion(repository.findCurrent().orElseThrow());

        FinancialParametersService serviceWithFailingRepo = new FinancialParametersService(
                failingRepo,
                new HighSeasonCalendar(),
                new FixedClockPort(FIXED_DATE),
                FIXED_CLOCK);

        FinancialParametersCommand v2Candidate = new FinancialParametersCommand(
                new Percentage(new BigDecimal("22.00")),
                new Money(new BigDecimal("9000.00")),
                new Percentage(new BigDecimal("12.00")),
                new Percentage(new BigDecimal("22.00")));

        assertThatExceptionOfType(ContractException.class)
                .isThrownBy(() -> serviceWithFailingRepo.save(v2Candidate))
                .satisfies(ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.PARAMETERS_SAVE_FAILED);
                    assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
                });

        // La configuración previa se conserva intacta
        FinancialParameters current = failingRepo.findCurrent().orElseThrow();
        assertThat(current.versionNumber()).isEqualTo(1);
        assertThat(current.commissionPct().value()).isEqualByComparingTo(new BigDecimal("12.0000"));
    }
}
