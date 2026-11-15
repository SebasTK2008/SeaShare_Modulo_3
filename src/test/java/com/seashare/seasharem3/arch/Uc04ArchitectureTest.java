package com.seashare.seasharem3.arch;

import com.seashare.seasharem3.application.dto.CalculateReservationValueCommand;
import com.seashare.seasharem3.application.dto.ReservationValueResult;
import com.seashare.seasharem3.application.port.in.CalculateReservationValueUseCase;
import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.application.port.out.FleetRatePort;
import com.seashare.seasharem3.application.service.CalculateReservationValueService;
import com.seashare.seasharem3.domain.service.DynamicRatePolicy;
import com.seashare.seasharem3.domain.service.PricingCalculator;
import com.seashare.seasharem3.domain.service.ReservationValueCalculator;
import com.seashare.seasharem3.domain.valueobject.ReservationValue;
import com.seashare.seasharem3.infrastructure.adapter.in.web.ReservationValueController;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.ReservationValueResponse;
import com.seashare.seasharem3.infrastructure.config.CalculateReservationValueConfiguration;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas de arquitectura específicas de UC04 (CE-002 y general-plan §3.4): el cálculo usa solo la
 * información registrada, sin depender de Flota, de UC02 ni de la tarifa dinámica, y el adaptador web
 * solo invoca el puerto de entrada.
 */
class Uc04ArchitectureTest {

    private static final Set<String> UC04_CLASSES = Set.of(
            ReservationValue.class.getName(),
            ReservationValueCalculator.class.getName(),
            CalculateReservationValueService.class.getName(),
            ReservationValueController.class.getName(),
            CalculateReservationValueUseCase.class.getName(),
            CalculateReservationValueCommand.class.getName(),
            ReservationValueResult.class.getName(),
            ReservationValueResponse.class.getName(),
            CalculateReservationValueConfiguration.class.getName());

    /** Expresión regular de selección: clases exactas de UC04 (los nombres son FQN escapados). */
    private static final String UC04_PATTERN = UC04_CLASSES.stream()
            .map(name -> name.replace(".", "\\."))
            .collect(Collectors.joining("|", "(", ")"));

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.seashare.seasharem3");

    @Test
    void importedClassesIncludeTheUc04Classes() {
        // Guarda contra reglas vacías: si la selección no encuentra clases, todas "pasan" sin comprobar nada.
        Set<String> importedNames = new HashSet<>();
        for (JavaClass imported : MAIN_CLASSES) {
            importedNames.add(imported.getName());
        }
        assertThat(importedNames).containsAll(UC04_CLASSES);
    }

    @Test
    void ce002_uc04DoesNotDependOnFlotaOrBaseRateRecalculation() {
        noClasses().that().haveNameMatching(UC04_PATTERN)
                .should().dependOnClassesThat().areAssignableTo(FleetRatePort.class)
                .because("CE-002: UC04 usa la base_rate registrada y nunca consulta a Flota")
                .check(MAIN_CLASSES);
        noClasses().that().haveNameMatching(UC04_PATTERN)
                .should().dependOnClassesThat().areAssignableTo(ProvideBaseRateUseCase.class)
                .because("CE-002: UC04 no incluye a UC02")
                .check(MAIN_CLASSES);
        noClasses().that().haveNameMatching(UC04_PATTERN)
                .should().dependOnClassesThat().areAssignableTo(DynamicRatePolicy.class)
                .because("CE-002: la tarifa dinámica vive solo en UC02")
                .check(MAIN_CLASSES);
        noClasses().that().haveNameMatching(UC04_PATTERN)
                .should().dependOnClassesThat().areAssignableTo(PricingCalculator.class)
                .because("CE-002: UC04 no vuelve a calcular la tarifa")
                .check(MAIN_CLASSES);
    }

    @Test
    void controllerOnlyInvokesTheInboundPort() {
        noClasses().that().haveFullyQualifiedName(ReservationValueController.class.getName())
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application.service..", "..application.port.out..", "..infrastructure.adapter.out..")
                .because("general-plan §3.4-3/-5: el adaptador de entrada solo invoca application.port.in")
                .check(MAIN_CLASSES);
    }

    @Test
    void uc04DoesNotTouchJpaEntitiesDirectly() {
        noClasses().that().haveNameMatching(UC04_PATTERN)
                .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
                .because("general-plan §3.4-6: la entidad JPA no sale de adapter.out.persistence")
                .check(MAIN_CLASSES);
    }
}
