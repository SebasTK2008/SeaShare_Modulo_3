package com.seashare.seasharem3.arch;

import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.FailureRecorderPort;
import com.seashare.seasharem3.application.port.out.FleetRatePort;
import com.seashare.seasharem3.application.port.out.OutboxPort;
import com.seashare.seasharem3.application.port.out.RefundIntentRepository;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.port.out.ReservationLockPort;
import com.seashare.seasharem3.application.port.out.SettlementIntentRepository;
import com.seashare.seasharem3.application.service.EstimateBatchService;
import com.seashare.seasharem3.application.service.EstimateSingleService;
import com.seashare.seasharem3.application.service.FinancialParametersService;
import com.seashare.seasharem3.application.service.ProvideBaseRateService;
import com.seashare.seasharem3.domain.service.DynamicRatePolicy;
import com.seashare.seasharem3.domain.service.PricingCalculator;
import com.seashare.seasharem3.infrastructure.adapter.in.web.FinancialParametersController;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters.FinancialParametersPersistenceAdapter;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas de arquitectura específicas de UC11: el controller solo habla con puertos de entrada y
 * UC11 no depende de repositorios, servicios ni políticas de otros casos de uso (RF-007, T042).
 */
class Uc11ArchitectureTest {

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.seashare.seasharem3");

    @Test
    void importedClassesIncludeTheUc11Classes() {
        assertThat(MAIN_CLASSES.contain(FinancialParametersController.class))
                .as("FinancialParametersController debe estar en el conjunto analizado")
                .isTrue();
        assertThat(MAIN_CLASSES.contain(FinancialParametersService.class)).isTrue();
        assertThat(MAIN_CLASSES.contain(FinancialParametersPersistenceAdapter.class)).isTrue();
    }

    @Test
    void controllerDependsOnlyOnInboundPortsAndWebDtos() {
        noClasses().that().haveSimpleName("FinancialParametersController")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application.service..",
                        "..application.port.out..",
                        "..domain.service..",
                        "..infrastructure.adapter.out..")
                .because("general-plan §3.4-3/-5: el controller solo invoca application.port.in")
                .check(MAIN_CLASSES);
    }

    @Test
    void uc11ServiceDoesNotDependOnOtherUseCaseServices() {
        noClasses().that().haveFullyQualifiedName(FinancialParametersService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(ProvideBaseRateService.class)
                .because("UC11 no orquesta UC02")
                .check(MAIN_CLASSES);
        noClasses().that().haveFullyQualifiedName(FinancialParametersService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(EstimateBatchService.class)
                .because("UC11 no orquesta UC01")
                .check(MAIN_CLASSES);
        noClasses().that().haveFullyQualifiedName(FinancialParametersService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(EstimateSingleService.class)
                .because("UC11 no orquesta UC01")
                .check(MAIN_CLASSES);
    }

    @Test
    void uc11ServiceDoesNotDependOnOtherUseCaseRepositoriesOrPorts() {
        noClasses().that().haveFullyQualifiedName(FinancialParametersService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(FleetRatePort.class)
                .because("RF-007: UC11 no lee tarifas de Flota (UC02)")
                .check(MAIN_CLASSES);
        noClasses().that().haveFullyQualifiedName(FinancialParametersService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(ReservationInformationRepository.class)
                .because("RF-007: UC11 no lee reservas (UC03/UC04)")
                .check(MAIN_CLASSES);
        noClasses().that().haveFullyQualifiedName(FinancialParametersService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(ChargeIntentRepository.class)
                .because("RF-007: UC11 no lee intenciones de cobro (UC05)")
                .check(MAIN_CLASSES);
        noClasses().that().haveFullyQualifiedName(FinancialParametersService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(RefundIntentRepository.class)
                .because("RF-007: UC11 no lee intenciones de reembolso (UC09)")
                .check(MAIN_CLASSES);
        noClasses().that().haveFullyQualifiedName(FinancialParametersService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(SettlementIntentRepository.class)
                .because("RF-007: UC11 no lee intenciones de liquidación (UC10)")
                .check(MAIN_CLASSES);
        noClasses().that().haveFullyQualifiedName(FinancialParametersService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(ReservationLockPort.class)
                .because("RF-007: UC11 no bloquea reservas")
                .check(MAIN_CLASSES);
        noClasses().that().haveFullyQualifiedName(FinancialParametersService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(OutboxPort.class)
                .because("RF-007: UC11 no publica eventos")
                .check(MAIN_CLASSES);
        noClasses().that().haveFullyQualifiedName(FinancialParametersService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(FailureRecorderPort.class)
                .because("RF-007: UC11 no registra fallos de otros UC")
                .check(MAIN_CLASSES);
    }

    @Test
    void uc11DoesNotUseOtherPricingPolicies() {
        noClasses().that().haveFullyQualifiedName(FinancialParametersService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(DynamicRatePolicy.class)
                .because("CE-002: la política dinámica es de UC02; UC11 solo muestra ventanas")
                .check(MAIN_CLASSES);
        noClasses().that().haveFullyQualifiedName(FinancialParametersService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(PricingCalculator.class)
                .because("CE-002: la aritmética de precios es de UC01")
                .check(MAIN_CLASSES);
    }
}
