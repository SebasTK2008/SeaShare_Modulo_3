package com.seashare.seasharem3.arch;

import com.seashare.seasharem3.application.dto.ReservationStatusCommand;
import com.seashare.seasharem3.application.port.in.HandleReservationStatusUseCase;
import com.seashare.seasharem3.application.port.out.ReservationStatusLogRepository;
import com.seashare.seasharem3.application.service.HandleReservationStatusService;
import com.seashare.seasharem3.domain.service.ReservationStatusPolicy;
import com.seashare.seasharem3.domain.valueobject.PlannedOperation;
import com.seashare.seasharem3.domain.valueobject.ReservationStatus;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.ReservationStatusListener;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.ReservationStatusMessage;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.ReservationStatusMessageMapper;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.status.ReservationStatusLogJpaEntity;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.status.ReservationStatusLogJpaRepository;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.status.ReservationStatusLogPersistenceAdapter;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/** Reglas de arquitectura específicas de UC07 (general-plan §3.4, §3.5 y D-33). */
class Uc07ArchitectureTest {

    private static final Set<String> UC07_CLASSES = Set.of(
            ReservationStatus.class.getName(),
            PlannedOperation.class.getName(),
            ReservationStatusPolicy.class.getName(),
            ReservationStatusCommand.class.getName(),
            HandleReservationStatusService.class.getName(),
            ReservationStatusListener.class.getName(),
            ReservationStatusMessage.class.getName(),
            ReservationStatusMessageMapper.class.getName(),
            ReservationStatusLogJpaEntity.class.getName(),
            ReservationStatusLogJpaRepository.class.getName(),
            ReservationStatusLogPersistenceAdapter.class.getName());

    private static final String UC07_PATTERN = UC07_CLASSES.stream()
            .map(name -> name.replace(".", "\\."))
            .collect(Collectors.joining("|", "(", ")"));

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.seashare.seasharem3");

    @Test
    void importedClassesIncludeTheUc07Classes() {
        Set<String> importedNames = new HashSet<>();
        for (JavaClass imported : MAIN_CLASSES) {
            importedNames.add(imported.getName());
        }
        assertThat(importedNames).containsAll(UC07_CLASSES);
    }

    @Test
    void handleReservationStatusServiceImplementsTheInboundPort() {
        assertThat(HandleReservationStatusUseCase.class.isAssignableFrom(HandleReservationStatusService.class))
                .isTrue();
    }

    @Test
    void listeningAdapterNeverReachesRepositories() {
        noClasses().that().areAssignableTo(ReservationStatusListener.class)
                .should().dependOnClassesThat().resideInAPackage("..application.port.out..")
                .because("general-plan §3.4-5: los listeners no acceden a repositorios")
                .check(MAIN_CLASSES);
    }

    @Test
    void serviceDelegatesOnlyThroughPublishedInboundPorts() {
        Set<String> delegateServices = Set.of(
                "com.seashare.seasharem3.application.service.ProcessChargeService",
                "com.seashare.seasharem3.application.service.RequestRefundService",
                "com.seashare.seasharem3.application.service.RequestSettlementService",
                "com.seashare.seasharem3.application.service.OpenDepositTrackingService");
        String delegatePattern = delegateServices.stream()
                .map(name -> name.replace(".", "\\."))
                .collect(Collectors.joining("|", "(", ")"));

        noClasses().that().areAssignableTo(HandleReservationStatusService.class)
                .should().dependOnClassesThat().haveNameMatching(delegatePattern)
                .because("UC07 delega en UC05/UC08/UC09/UC10 solo por port.in")
                .check(MAIN_CLASSES);
    }

    @Test
    void domainPolicyStaysFreeOfFrameworks() {
        noClasses().that().areAssignableTo(ReservationStatusPolicy.class)
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                        "tools.jackson..")
                .because("general-plan §3.4: el dominio no depende de frameworks")
                .check(MAIN_CLASSES);
    }

    @Test
    void uc07DoesNotIntroduceJobsOrEndpoints() {
        noClasses().that().haveNameMatching(UC07_PATTERN)
                .should().beAnnotatedWith(Scheduled.class)
                .because("UC07 solo reacciona a la cola; no ejecuta temporizadores")
                .check(MAIN_CLASSES);
        noClasses().that().haveNameMatching(UC07_PATTERN)
                .should().beAnnotatedWith(RestController.class)
                .because("UC07 es un consumidor AMQP sin endpoint REST")
                .check(MAIN_CLASSES);
    }

    @Test
    void uc07DoesNotDecideAboutRefundOrSettlementRecords() {
        noClasses().that().haveNameMatching(UC07_PATTERN)
                .should().dependOnClassesThat().haveNameMatching(
                        "com\\.seashare\\.seasharem3\\.domain\\.model\\.(RefundIntent|RefundRecord|"
                                + "SettlementIntent|SettlementRecord|CommissionRecord)")
                .because("UC07 no calcula montos: delega en UC09/UC10")
                .check(MAIN_CLASSES);
    }

    @Test
    void persistenceAdapterImplementsTheOutboundPort() {
        assertThat(ReservationStatusLogRepository.class.isAssignableFrom(ReservationStatusLogPersistenceAdapter.class))
                .isTrue();
    }
}
