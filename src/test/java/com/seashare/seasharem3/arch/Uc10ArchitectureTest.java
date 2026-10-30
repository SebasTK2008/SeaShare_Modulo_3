package com.seashare.seasharem3.arch;

import com.seashare.seasharem3.application.service.ReconcileSettlementIntentsService;
import com.seashare.seasharem3.application.service.RegisterSettlementResultService;
import com.seashare.seasharem3.application.service.RequestSettlementService;
import com.seashare.seasharem3.application.service.SendSettlementToGatewayService;
import com.seashare.seasharem3.application.service.SettlementWebhookHandler;
import com.seashare.seasharem3.domain.model.CommissionRecord;
import com.seashare.seasharem3.domain.model.SettlementIntent;
import com.seashare.seasharem3.domain.model.SettlementRecord;
import com.seashare.seasharem3.domain.service.SettlementCalculator;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.SettlementCommandListener;
import com.seashare.seasharem3.infrastructure.adapter.out.gateway.MercadoPagoSettlementAdapter;
import com.seashare.seasharem3.infrastructure.adapter.out.messaging.SettlementCommandOutboxAdapter;
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

/** Reglas de arquitectura específicas de UC10 (general-plan §3.4, D-34 y D-35). */
class Uc10ArchitectureTest {

    private static final Set<String> UC10_CLASSES = Set.of(
            SettlementIntent.class.getName(),
            SettlementRecord.class.getName(),
            CommissionRecord.class.getName(),
            SettlementCalculator.class.getName(),
            RequestSettlementService.class.getName(),
            SendSettlementToGatewayService.class.getName(),
            RegisterSettlementResultService.class.getName(),
            ReconcileSettlementIntentsService.class.getName(),
            SettlementWebhookHandler.class.getName(),
            SettlementCommandListener.class.getName(),
            SettlementCommandOutboxAdapter.class.getName(),
            MercadoPagoSettlementAdapter.class.getName());

    private static final String UC10_PATTERN = UC10_CLASSES.stream()
            .map(name -> name.replace(".", "\\."))
            .collect(Collectors.joining("|", "(", ")"));

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.seashare.seasharem3");

    @Test
    void importedClassesIncludeTheUc10Classes() {
        Set<String> importedNames = new HashSet<>();
        for (JavaClass imported : MAIN_CLASSES) {
            importedNames.add(imported.getName());
        }
        assertThat(importedNames).containsAll(UC10_CLASSES);
    }

    @Test
    void uc10DoesNotDependOnRefundRecords() {
        noClasses().that().haveNameMatching(UC10_PATTERN)
                .should().dependOnClassesThat().haveNameMatching(
                        "com\\.seashare\\.seasharem3\\.domain\\.model\\.(RefundIntent|RefundRecord)")
                .because("UC10 solo ejecuta liquidaciones; el reembolso pertenece a UC09")
                .check(MAIN_CLASSES);
    }

    @Test
    void uc10DoesNotTalkToTheGatewayExceptThroughItsPort() {
        noClasses().that().haveNameMatching(UC10_PATTERN)
                .should().dependOnClassesThat().areAssignableTo(MercadoPagoSettlementAdapter.class)
                .because("general-plan §3.4: la pasarela se consume por el puerto de salida")
                .check(MAIN_CLASSES);
    }

    @Test
    void settlementAndCommissionRecordsAreImmutableWithoutSetters() {
        for (Class<?> type : Set.of(SettlementRecord.class, CommissionRecord.class)) {
            assertThat(Arrays.stream(type.getDeclaredFields()))
                    .allMatch(field -> Modifier.isFinal(field.getModifiers()));
            assertThat(Arrays.stream(type.getMethods()).map(Method::getName))
                    .noneMatch(name -> name.matches("set[A-Z].*"));
        }
    }
}
