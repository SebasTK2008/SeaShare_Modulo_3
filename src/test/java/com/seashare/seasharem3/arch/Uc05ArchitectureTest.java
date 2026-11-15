package com.seashare.seasharem3.arch;

import com.seashare.seasharem3.application.service.ChargePaymentWebhookHandler;
import com.seashare.seasharem3.application.service.GatewayWebhookDispatcherService;
import com.seashare.seasharem3.application.service.ProcessChargeService;
import com.seashare.seasharem3.application.service.ReconcileChargeIntentsService;
import com.seashare.seasharem3.application.service.RegisterChargeResultService;
import com.seashare.seasharem3.application.service.SendChargeToGatewayService;
import com.seashare.seasharem3.domain.model.ChargeRecord;
import com.seashare.seasharem3.domain.service.DynamicRatePolicy;
import com.seashare.seasharem3.domain.service.PricingCalculator;
import com.seashare.seasharem3.domain.service.ReservationValueCalculator;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.ChargeCommandListener;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.GatewayNotificationListener;
import com.seashare.seasharem3.infrastructure.adapter.in.scheduler.GatewayReconciliationJob;
import com.seashare.seasharem3.infrastructure.adapter.in.webhook.GatewayWebhookController;
import com.seashare.seasharem3.infrastructure.adapter.out.gateway.MercadoPagoChargeAdapter;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas de arquitectura específicas de UC05: el cobro no recalcula ni consulta las tarifas de
 * otros casos de uso, los adaptadores de entrada solo invocan puertos de entrada y el registro de
 * auditoría es inmutable (general-plan §3.4, D-28, D-32).
 */
class Uc05ArchitectureTest {

    private static final Set<String> UC05_CLASSES = Set.of(
            ChargeRecord.class.getName(),
            ProcessChargeService.class.getName(),
            RegisterChargeResultService.class.getName(),
            SendChargeToGatewayService.class.getName(),
            ReconcileChargeIntentsService.class.getName(),
            ChargePaymentWebhookHandler.class.getName(),
            GatewayWebhookDispatcherService.class.getName(),
            GatewayWebhookController.class.getName(),
            ChargeCommandListener.class.getName(),
            GatewayNotificationListener.class.getName(),
            GatewayReconciliationJob.class.getName());

    /** Expresión regular de selección: clases exactas de UC05 (los nombres son FQN escapados). */
    private static final String UC05_PATTERN = UC05_CLASSES.stream()
            .map(name -> name.replace(".", "\\."))
            .collect(Collectors.joining("|", "(", ")"));

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.seashare.seasharem3");

    @Test
    void importedClassesIncludeTheUc05Classes() {
        // Guarda contra reglas vacías: si la selección no encuentra clases, todas "pasan" sin comprobar nada.
        Set<String> importedNames = new HashSet<>();
        for (JavaClass imported : MAIN_CLASSES) {
            importedNames.add(imported.getName());
        }
        assertThat(importedNames).containsAll(UC05_CLASSES);
    }

    @Test
    void uc05DoesNotRecalculateRatesOrAmounts() {
        noClasses().that().haveNameMatching(UC05_PATTERN)
                .should().dependOnClassesThat().areAssignableTo(PricingCalculator.class)
                .because("UC05 cobra el valor ya calculado por UC04 y no vuelve a calcular la tarifa")
                .check(MAIN_CLASSES);
        noClasses().that().haveNameMatching(UC05_PATTERN)
                .should().dependOnClassesThat().areAssignableTo(DynamicRatePolicy.class)
                .because("la tarifa dinamica vive solo en UC02")
                .check(MAIN_CLASSES);
        noClasses().that().haveNameMatching(UC05_PATTERN)
                .should().dependOnClassesThat().areAssignableTo(ReservationValueCalculator.class)
                .because("UC05 no recalcula el valor de la reserva: lo lee registrado (RF-002, RF-015)")
                .check(MAIN_CLASSES);
    }

    @Test
    void uc05DoesNotTalkToTheGatewayExceptThroughItsPort() {
        noClasses().that().haveNameMatching(UC05_PATTERN)
                .should().dependOnClassesThat().areAssignableTo(MercadoPagoChargeAdapter.class)
                .because("general-plan §3.4: la pasarela se consume por el puerto de salida, no por su "
                        + "implementacion")
                .check(MAIN_CLASSES);
    }

    @Test
    void inboundAdaptersOnlyInvokeTheInboundPorts() {
        noClasses().that().resideInAPackage("..infrastructure.adapter.in..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application.service..", "..application.port.out..", "..infrastructure.adapter.out..")
                .because("general-plan §3.4-3/-5: los adaptadores de entrada solo invocan application.port.in")
                .check(MAIN_CLASSES);
    }

    @Test
    void chargeRecordIsImmutableWithoutSetters() {
        assertThat(Arrays.stream(ChargeRecord.class.getDeclaredFields()))
                .allMatch(field -> Modifier.isFinal(field.getModifiers()));
        assertThat(Arrays.stream(ChargeRecord.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("set"));
    }
}
