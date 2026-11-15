package com.seashare.seasharem3.arch;

import com.seashare.seasharem3.application.dto.PaymentConfirmationQuery;
import com.seashare.seasharem3.application.dto.PaymentConfirmationResult;
import com.seashare.seasharem3.application.port.in.GetPaymentConfirmationUseCase;
import com.seashare.seasharem3.application.port.out.ChargeGatewayPort;
import com.seashare.seasharem3.application.service.GetPaymentConfirmationService;
import com.seashare.seasharem3.domain.valueobject.PaymentConfirmationStatus;
import com.seashare.seasharem3.infrastructure.adapter.in.web.PaymentConfirmationController;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.PaymentConfirmationResponse;
import com.seashare.seasharem3.infrastructure.config.PaymentConfirmationConfiguration;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas de arquitectura específicas de UC06: la consulta no depende de la pasarela ni de los
 * adaptadores de persistencia, y el controller solo usa el puerto de entrada. Fuente: UC06 plan
 * T014; SPEC 06 CE-002 y general-plan §3.4.
 */
class Uc06ArchitectureTest {

    private static final Set<String> UC06_CLASSES = Set.of(
            PaymentConfirmationStatus.class.getName(),
            GetPaymentConfirmationService.class.getName(),
            GetPaymentConfirmationUseCase.class.getName(),
            PaymentConfirmationQuery.class.getName(),
            PaymentConfirmationResult.class.getName(),
            PaymentConfirmationController.class.getName(),
            PaymentConfirmationResponse.class.getName(),
            PaymentConfirmationConfiguration.class.getName());

    /** Expresión regular de selección: clases exactas de UC06 (los nombres son FQN escapados). */
    private static final String UC06_PATTERN = UC06_CLASSES.stream()
            .map(name -> name.replace(".", "\\."))
            .collect(Collectors.joining("|", "(", ")"));

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.seashare.seasharem3");

    @Test
    void importedClassesIncludeTheUc06Classes() {
        Set<String> importedNames = new HashSet<>();
        for (JavaClass imported : MAIN_CLASSES) {
            importedNames.add(imported.getName());
        }
        assertThat(importedNames).containsAll(UC06_CLASSES);
    }

    @Test
    void uc06DoesNotTalkToTheGateway() {
        noClasses().that().haveNameMatching(UC06_PATTERN)
                .should().dependOnClassesThat().areAssignableTo(ChargeGatewayPort.class)
                .because("UC06 resuelve la confirmación solo con la intención persistida (RF-006, CE-002)")
                .check(MAIN_CLASSES);
    }

    @Test
    void uc06DoesNotDependOnPersistenceAdapters() {
        noClasses().that().haveNameMatching(UC06_PATTERN)
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure.adapter.out..")
                .because("el servicio consume el puerto de salida, no su adaptador (general-plan §3.4)")
                .check(MAIN_CLASSES);
    }

    @Test
    void paymentConfirmationControllerOnlyInvokesTheInboundPort() {
        noClasses().that().haveSimpleName(PaymentConfirmationController.class.getSimpleName())
                .should().dependOnClassesThat().resideInAnyPackage("..application.port.out..")
                .because("el adaptador de entrada no accede a repositorios (general-plan §3.4-5)")
                .check(MAIN_CLASSES);
    }

    @Test
    void jpaEntitiesStayInTheirPersistenceAdapter() {
        noClasses().that().resideOutsideOfPackage("..infrastructure.adapter.out.persistence..")
                .should().beAnnotatedWith(Entity.class)
                .because("las entidades JPA no salen de su adaptador de persistencia (general-plan §3.4-6)")
                .check(MAIN_CLASSES);
    }
}
