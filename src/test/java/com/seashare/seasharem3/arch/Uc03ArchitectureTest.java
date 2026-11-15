package com.seashare.seasharem3.arch;

import com.seashare.seasharem3.application.dto.RegisterReservationInformationCommand;
import com.seashare.seasharem3.application.port.in.RegisterReservationInformationUseCase;
import com.seashare.seasharem3.application.port.out.FleetRatePort;
import com.seashare.seasharem3.application.port.out.MessageBrokerPort;
import com.seashare.seasharem3.application.port.out.OutboxPort;
import com.seashare.seasharem3.application.service.RegisterReservationInformationService;
import com.seashare.seasharem3.domain.service.DynamicRatePolicy;
import com.seashare.seasharem3.domain.service.PricingCalculator;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.ReservationInfoListener;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.reservation.ReservationInformationPersistenceAdapter;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.lang.reflect.Method;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas de arquitectura específicas de UC03 (CE-002 y RF-007): el caso de uso es unidireccional
 * (sin controller ni publicaciones), no calcula montos y no consulta a Flota directamente.
 */
class Uc03ArchitectureTest {

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.seashare.seasharem3");

    @Test
    void importedClassesIncludeTheUc03Classes() {
        assertThat(MAIN_CLASSES.contain(RegisterReservationInformationService.class)).isTrue();
        assertThat(MAIN_CLASSES.contain(ReservationInfoListener.class)).isTrue();
        assertThat(MAIN_CLASSES.contain(ReservationInformationPersistenceAdapter.class)).isTrue();
    }

    @Test
    void ce002_uc03HasNoWebAdapter() {
        noClasses().that().resideInAnyPackage(
                        "..infrastructure.adapter.in.web..",
                        "..infrastructure.adapter.in.scheduler..")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        RegisterReservationInformationUseCase.class.getName())
                .because("CE-002: UC03 es unidireccional y no expone una respuesta HTTP")
                .check(MAIN_CLASSES);
    }

    @Test
    void ce002_listenerDoesNotPublishMessages() {
        noClasses().that().resideInAPackage("..infrastructure.adapter.in.messaging..")
                .should().dependOnClassesThat().areAssignableTo(RabbitTemplate.class)
                .because("CE-002: el listener de UC03 no publica mensajes de salida")
                .check(MAIN_CLASSES);
        noClasses().that().resideInAPackage("..infrastructure.adapter.in.messaging..")
                .should().dependOnClassesThat().areAssignableTo(OutboxPort.class)
                .because("CE-002: UC03 no usa outbox ni responde al productor")
                .check(MAIN_CLASSES);
        noClasses().that().resideInAPackage("..infrastructure.adapter.in.messaging..")
                .should().dependOnClassesThat().areAssignableTo(MessageBrokerPort.class)
                .because("CE-002: el listener de UC03 no publica mensajes de salida")
                .check(MAIN_CLASSES);
    }

    @Test
    void ce002_serviceDoesNotPublishMessages() {
        noClasses().that().haveFullyQualifiedName(RegisterReservationInformationService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(RabbitTemplate.class)
                .because("CE-002: UC03 no publica mensajes de salida")
                .check(MAIN_CLASSES);
        noClasses().that().haveFullyQualifiedName(RegisterReservationInformationService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(OutboxPort.class)
                .because("CE-002: UC03 no usa outbox ni responde al productor")
                .check(MAIN_CLASSES);
        noClasses().that().haveFullyQualifiedName(RegisterReservationInformationService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(MessageBrokerPort.class)
                .because("CE-002: UC03 no publica mensajes de salida")
                .check(MAIN_CLASSES);
    }

    @Test
    void ce002_listenerOnlyCallsInboundPorts() {
        noClasses().that().haveFullyQualifiedName(ReservationInfoListener.class.getName())
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application.service..",
                        "..application.port.out..",
                        "..infrastructure.adapter.out..")
                .because("general-plan §3.4-3/-5: el listener solo invoca application.port.in")
                .check(MAIN_CLASSES);
    }

    @Test
    void ce002_useCaseHasNoResponse() throws NoSuchMethodException {
        Method method = RegisterReservationInformationUseCase.class.getDeclaredMethod(
                "register", RegisterReservationInformationCommand.class);

        assertThat(method.getReturnType()).isEqualTo(void.class);
    }

    @Test
    void rf007_serviceDoesNotCalculateAmounts() {
        noClasses().that().haveFullyQualifiedName(RegisterReservationInformationService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(PricingCalculator.class)
                .because("RF-007: UC03 no calcula alquiler, seguro ni depósito")
                .check(MAIN_CLASSES);
    }

    @Test
    void rf003_serviceDoesNotConsultFlotaDirectly() {
        noClasses().that().haveFullyQualifiedName(RegisterReservationInformationService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(FleetRatePort.class)
                .because("RF-003/CE-004: UC03 solo consulta Flota a través de UC02")
                .check(MAIN_CLASSES);
        noClasses().that().haveFullyQualifiedName(RegisterReservationInformationService.class.getName())
                .should().dependOnClassesThat().areAssignableTo(DynamicRatePolicy.class)
                .because("UC03 no aplica la tarifa dinámica; eso pertenece a UC02")
                .check(MAIN_CLASSES);
    }
}
