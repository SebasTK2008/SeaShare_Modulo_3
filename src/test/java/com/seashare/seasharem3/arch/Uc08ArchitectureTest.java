package com.seashare.seasharem3.arch;

import com.seashare.seasharem3.application.port.in.HandleDisputeNotificationUseCase;
import com.seashare.seasharem3.application.service.DisputeInformationService;
import com.seashare.seasharem3.domain.model.DisputeEvent;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.DisputeNotificationListener;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.DisputeNotificationMessage;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.DisputeNotificationMessageMapper;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.dispute.DisputeEventLogJpaEntity;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.dispute.DisputeEventLogJpaRepository;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.dispute.DisputeEventLogPersistenceAdapter;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/** Reglas de arquitectura especificas de UC08 (general-plan §3.4 y D-34). */
class Uc08ArchitectureTest {

    private static final Set<String> UC08_CLASSES = Set.of(
            DisputeEvent.class.getName(),
            DisputeInformationService.class.getName(),
            DisputeNotificationListener.class.getName(),
            DisputeNotificationMessage.class.getName(),
            DisputeNotificationMessageMapper.class.getName(),
            DisputeEventLogJpaEntity.class.getName(),
            DisputeEventLogJpaRepository.class.getName(),
            DisputeEventLogPersistenceAdapter.class.getName());

    private static final String UC08_PATTERN = UC08_CLASSES.stream()
            .map(name -> name.replace(".", "\\."))
            .collect(Collectors.joining("|", "(", ")"));

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.seashare.seasharem3");

    @Test
    void importedClassesIncludeTheUc08Classes() {
        Set<String> importedNames = new HashSet<>();
        for (JavaClass imported : MAIN_CLASSES) {
            importedNames.add(imported.getName());
        }
        assertThat(importedNames).containsAll(UC08_CLASSES);
    }

    @Test
    void disputeInformationServiceImplementsTheInboundPort() {
        assertThat(HandleDisputeNotificationUseCase.class.isAssignableFrom(DisputeInformationService.class)).isTrue();
    }

    @Test
    void uc08DoesNotDependOnRefundOrSettlementModels() {
        noClasses().that().haveNameMatching(UC08_PATTERN)
                .should().dependOnClassesThat().haveNameMatching(
                        "com\\.seashare\\.seasharem3\\.domain\\.model\\.(RefundIntent|RefundRecord|"
                                + "SettlementIntent|SettlementRecord|CommissionRecord)")
                .because("UC08 delega reembolso y liquidacion en UC09/UC10")
                .check(MAIN_CLASSES);
    }

    @Test
    void listenerOnlyCallsInboundPortsAndNeverRepositories() {
        noClasses().that().areAssignableTo(DisputeNotificationListener.class)
                .should().dependOnClassesThat().resideInAPackage("..application.port.out..")
                .because("general-plan §3.4-5: los listeners no acceden a repositorios")
                .check(MAIN_CLASSES);
    }

    @Test
    void uc08DoesNotIntroduceJobsOrEndpoints() {
        noClasses().that().haveNameMatching(UC08_PATTERN)
                .should().beAnnotatedWith(Scheduled.class)
                .because("UC08 solo reacciona a la cola; no ejecuta temporizadores")
                .check(MAIN_CLASSES);
        noClasses().that().haveNameMatching(UC08_PATTERN)
                .should().beAnnotatedWith(RestController.class)
                .because("UC08 es un consumidor AMQP sin endpoint REST")
                .check(MAIN_CLASSES);
    }

    @Test
    void disputeEventIsImmutable() {
        assertThat(Arrays.stream(DisputeEvent.class.getDeclaredFields()))
                .allMatch(field -> Modifier.isFinal(field.getModifiers()));
    }
}
