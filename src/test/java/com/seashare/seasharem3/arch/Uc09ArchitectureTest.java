package com.seashare.seasharem3.arch;

import com.seashare.seasharem3.application.service.ReconcileRefundIntentsService;
import com.seashare.seasharem3.application.service.RefundWebhookHandler;
import com.seashare.seasharem3.application.service.RegisterRefundResultService;
import com.seashare.seasharem3.application.service.RequestRefundService;
import com.seashare.seasharem3.application.service.SendRefundToGatewayService;
import com.seashare.seasharem3.domain.model.RefundIntent;
import com.seashare.seasharem3.domain.model.RefundRecord;
import com.seashare.seasharem3.domain.service.RefundCalculator;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.RefundCommandListener;
import com.seashare.seasharem3.infrastructure.adapter.out.gateway.MercadoPagoRefundAdapter;
import com.seashare.seasharem3.infrastructure.adapter.out.messaging.RefundCommandOutboxAdapter;
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

/** Reglas de arquitectura específicas de UC09 (general-plan §3.4, D-32 y D-34). */
class Uc09ArchitectureTest {

    private static final Set<String> UC09_CLASSES = Set.of(
            RefundIntent.class.getName(),
            RefundRecord.class.getName(),
            RefundCalculator.class.getName(),
            RequestRefundService.class.getName(),
            SendRefundToGatewayService.class.getName(),
            RegisterRefundResultService.class.getName(),
            ReconcileRefundIntentsService.class.getName(),
            RefundWebhookHandler.class.getName(),
            RefundCommandListener.class.getName(),
            RefundCommandOutboxAdapter.class.getName(),
            MercadoPagoRefundAdapter.class.getName());

    private static final String UC09_PATTERN = UC09_CLASSES.stream()
            .map(name -> name.replace(".", "\\."))
            .collect(Collectors.joining("|", "(", ")"));

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.seashare.seasharem3");

    @Test
    void importedClassesIncludeTheUc09Classes() {
        Set<String> importedNames = new HashSet<>();
        for (JavaClass imported : MAIN_CLASSES) {
            importedNames.add(imported.getName());
        }
        assertThat(importedNames).containsAll(UC09_CLASSES);
    }

    @Test
    void uc09DoesNotDependOnSettlementOrCommissionRecords() {
        noClasses().that().haveNameMatching(UC09_PATTERN)
                .should().dependOnClassesThat().haveNameMatching(
                        "com\\.seashare\\.seasharem3\\.domain\\.model\\.(SettlementIntent|SettlementRecord|"
                                + "CommissionRecord)")
                .because("UC09 solo ejecuta reembolsos; la liquidacion pertenece a UC10")
                .check(MAIN_CLASSES);
    }

    @Test
    void uc09DoesNotTalkToTheGatewayExceptThroughItsPort() {
        noClasses().that().haveNameMatching(UC09_PATTERN)
                .should().dependOnClassesThat().areAssignableTo(MercadoPagoRefundAdapter.class)
                .because("general-plan §3.4: la pasarela se consume por el puerto de salida")
                .check(MAIN_CLASSES);
    }

    @Test
    void refundRecordIsImmutableWithoutSetters() {
        assertThat(Arrays.stream(RefundRecord.class.getDeclaredFields()))
                .allMatch(field -> Modifier.isFinal(field.getModifiers()));
        assertThat(Arrays.stream(RefundRecord.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.matches("set[A-Z].*"));
    }
}
