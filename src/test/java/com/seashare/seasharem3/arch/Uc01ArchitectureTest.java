package com.seashare.seasharem3.arch;

import com.seashare.seasharem3.application.service.EstimateBatchService;
import com.seashare.seasharem3.application.service.EstimateSingleService;
import com.seashare.seasharem3.domain.service.DynamicRatePolicy;
import com.seashare.seasharem3.domain.service.HighSeasonCalendar;
import com.seashare.seasharem3.infrastructure.adapter.in.web.EstimateController;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas de arquitectura específicas de UC01 (CE-002).
 *
 * <p>UC01 no hace ninguna operación de precio: la tarifa dinámica pertenece a UC02
 * ({@link DynamicRatePolicy} / {@link HighSeasonCalendar}) y en UC01 solo existe
 * {@code PricingCalculator} para la aritmética de dominio. El controller habla únicamente con los
 * puertos de entrada, nunca con servicios concretos ni con adaptadores de salida.</p>
 */
class Uc01ArchitectureTest {

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.seashare.seasharem3");

    /**
     * Guarda contra reglas vacuas: si las clases objetivo no se importan, las reglas de abajo
     * pasarían sin comprobar nada (el fallo original de esta clase).
     */
    @Test
    void importedClassesIncludeTheUc01Classes() {
        assertThat(MAIN_CLASSES.contain(EstimateController.class))
                .as("EstimateController debe estar en el conjunto analizado")
                .isTrue();
        assertThat(MAIN_CLASSES.contain(EstimateBatchService.class)).isTrue();
        assertThat(MAIN_CLASSES.contain(EstimateSingleService.class)).isTrue();
    }

    @Test
    void controllerDependsOnlyOnInboundPortsAndWebDtos() {
        noClasses().that().haveSimpleName("EstimateController")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application.service..",
                        "..application.port.out..",
                        "..domain.service..",
                        "..infrastructure.adapter.out..")
                .because("general-plan §3.4-3/-5: el controller solo invoca application.port.in")
                .check(MAIN_CLASSES);
    }

    @Test
    void uc01DoesNotUseTheDynamicRatePolicy() {
        noClasses().that().haveSimpleNameStartingWith("Estimate")
                .should().dependOnClassesThat().areAssignableTo(DynamicRatePolicy.class)
                .because("CE-002: la tarifa dinámica solo se aplica en UC02")
                .check(MAIN_CLASSES);
    }

    @Test
    void uc01DoesNotUseTheHighSeasonCalendar() {
        noClasses().that().haveSimpleNameStartingWith("Estimate")
                .should().dependOnClassesThat().areAssignableTo(HighSeasonCalendar.class)
                .because("CE-002: el calendario de temporada alta es de UC02")
                .check(MAIN_CLASSES);
    }

    @Test
    void uc01ApplicationServicesDelegateMoneyArithmeticToTheDomain() {
        noClasses().that().resideInAPackage("..application.service..")
                .and().haveSimpleNameStartingWith("Estimate")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.math.BigDecimal")
                .because("la aritmética monetaria vive en Money/PricingCalculator, no en la aplicación")
                .check(MAIN_CLASSES);
    }
}
