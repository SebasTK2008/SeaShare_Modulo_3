package com.seashare.seasharem3.arch;

import com.seashare.seasharem3.application.port.out.FleetRatePort;
import com.seashare.seasharem3.application.service.ProvideBaseRateService;
import com.seashare.seasharem3.domain.service.DynamicRatePolicy;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas de arquitectura específicas de UC02 (CE-002).
 *
 * <p>La política dinámica sólo puede ser usada por el dominio de pricing de UC02, por su servicio de
 * aplicación y por la configuración de wiring. Ningún adaptador de entrada puede llamar directamente
 * a Flota: UC02 es interno y sólo se consume a través de su puerto de entrada.</p>
 */
class Uc02ArchitectureTest {

    private static final com.tngtech.archunit.core.domain.JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.seashare.seasharem3");

    @Test
    void ce002DynamicRatePolicyOnlyUsedByUc02PricingServiceAndWiring() {
        noClasses().that()
                .resideOutsideOfPackages("..domain.service..", "..infrastructure.config..")
                .and().doNotHaveFullyQualifiedName(ProvideBaseRateService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(DynamicRatePolicy.class)
                .because("UC02 is the only owner of the dynamic rate policy; other use cases must not duplicate it")
                .check(MAIN_CLASSES);
    }

    @Test
    void ce002InboundAdaptersDoNotCallFleetDirectly() {
        noClasses().that().resideInAPackage("..infrastructure.adapter.in..")
                .should().dependOnClassesThat().areAssignableTo(FleetRatePort.class)
                .because("UC02 has no REST endpoint; Fleet is only reachable through ProvideBaseRateService")
                .check(MAIN_CLASSES);

        noClasses().that().resideInAPackage("..infrastructure.adapter.in..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure.adapter.out.fleet..")
                .because("inbound adapters must not bypass the UC02 application service")
                .check(MAIN_CLASSES);
    }
}
