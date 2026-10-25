package com.seashare.seasharem3.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Verifica las 7 reglas de dependencia de general-plan §3.4 sobre el código de producción
 * (las clases de prueba quedan excluidas). [CONV] núcleo compartido.
 */
class ArchitectureTest {

    private static final com.tngtech.archunit.core.domain.JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.seashare.seasharem3");

    @Test
    void rule1DomainDoesNotDependOnFrameworksOrOtherLayers() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta..", "tools.jackson..", "com.fasterxml.jackson..",
                        "org.hibernate..", "com.seashare.seasharem3.application..",
                        "com.seashare.seasharem3.infrastructure..")
                .because("general-plan §3.4-1: domain is plain Java")
                .check(MAIN_CLASSES);
    }

    @Test
    void rule2ApplicationDependsOnlyOnDomain() {
        classes().that().resideInAPackage("..application..")
                .should(onlyApplicationDependencies())
                .because("general-plan §3.4-2: application depends only on domain (exception: @Transactional)")
                .check(MAIN_CLASSES);
    }

    @Test
    void rule3InboundAdapterOnlyCallsInboundPortsAndOutboundAdapterOnlyImplementsOutboundPorts() {
        noClasses().that().resideInAPackage("..infrastructure.adapter.in..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application.service..", "..infrastructure.adapter.out..")
                .because("general-plan §3.4-3: inbound adapters only call port.in")
                .check(MAIN_CLASSES);
        noClasses().that().resideInAPackage("..infrastructure.adapter.out..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application.service..", "..application.port.in..", "..infrastructure.adapter.in..")
                .because("general-plan §3.4-3: outbound adapters only implement port.out")
                .check(MAIN_CLASSES);
    }

    @Test
    void rule4UseCasesCollaborateOnlyThroughTheirInboundPort() {
        noClasses().that().resideInAPackage("..application.service..")
                .should().dependOnClassesThat().resideInAPackage("..application.service..")
                .because("general-plan §3.4-4: UC07→UC05/UC09/UC10 only through port.in")
                .check(MAIN_CLASSES);
    }

    @Test
    void rule5NoInboundAdapterAccessesRepositories() {
        noClasses().that().resideInAPackage("..infrastructure.adapter.in..")
                .should().dependOnClassesThat().resideInAPackage("..application.port.out..")
                .because("general-plan §3.4-5: controllers, listeners, webhooks and jobs do not touch repositories")
                .check(MAIN_CLASSES);
    }

    @Test
    void rule6JpaEntitiesDoNotLeaveThePersistenceAdapter() {
        noClasses().that().resideOutsideOfPackage("..infrastructure.adapter.out.persistence..")
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .because("general-plan §3.4-6: JPA entities are an adapter detail")
                .check(MAIN_CLASSES);
    }

    @Test
    void rule7ApplicationDoesNotDependOnInfrastructure() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("general-plan §3.4-7: HTTP/AMQP DTOs live in adapter.in; application uses command/result")
                .check(MAIN_CLASSES);
    }

    private static ArchCondition<JavaClass> onlyApplicationDependencies() {
        return new ArchCondition<>("depend only on domain, java and org.springframework.transaction.annotation") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!allowedForApplication(target)) {
                        events.add(SimpleConditionEvent.violated(dependency,
                                item.getName() + " cannot depend on " + target.getName()));
                    }
                }
            }
        };
    }

    private static boolean allowedForApplication(JavaClass target) {
        String packageName = target.getPackageName();
        return target.isPrimitive()
                || packageName.startsWith("java.")
                || packageName.startsWith("com.seashare.seasharem3.domain")
                || packageName.startsWith("com.seashare.seasharem3.application")
                || packageName.startsWith("org.springframework.transaction.annotation");
    }
}
