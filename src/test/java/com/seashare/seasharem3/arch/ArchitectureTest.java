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
    void regla1ElDominioNoDependeDeFrameworksNiDeOtrasCapas() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta..", "tools.jackson..", "com.fasterxml.jackson..",
                        "org.hibernate..", "com.seashare.seasharem3.application..",
                        "com.seashare.seasharem3.infrastructure..")
                .because("general-plan §3.4-1: domain es Java puro")
                .check(MAIN_CLASSES);
    }

    @Test
    void regla2LaAplicacionSoloDependeDelDominio() {
        classes().that().resideInAPackage("..application..")
                .should(onlyApplicationDependencies())
                .because("general-plan §3.4-2: application solo depende de domain (excepción: @Transactional)")
                .check(MAIN_CLASSES);
    }

    @Test
    void regla3AdapterInSoloInvocaPuertosDeEntradaYAdapterOutSoloImplementaPuertosDeSalida() {
        noClasses().that().resideInAPackage("..infrastructure.adapter.in..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application.service..", "..infrastructure.adapter.out..")
                .because("general-plan §3.4-3: los adaptadores de entrada solo invocan port.in")
                .check(MAIN_CLASSES);
        noClasses().that().resideInAPackage("..infrastructure.adapter.out..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application.service..", "..application.port.in..", "..infrastructure.adapter.in..")
                .because("general-plan §3.4-3: los adaptadores de salida solo implementan port.out")
                .check(MAIN_CLASSES);
    }

    @Test
    void regla4LosCasosDeUsoColaboranSoloPorSuPuertoDeEntrada() {
        noClasses().that().resideInAPackage("..application.service..")
                .should().dependOnClassesThat().resideInAPackage("..application.service..")
                .because("general-plan §3.4-4: UC07→UC05/UC09/UC10 solo por port.in")
                .check(MAIN_CLASSES);
    }

    @Test
    void regla5NingunAdaptadorDeEntradaAccedeARepositorios() {
        noClasses().that().resideInAPackage("..infrastructure.adapter.in..")
                .should().dependOnClassesThat().resideInAPackage("..application.port.out..")
                .because("general-plan §3.4-5: controllers, listeners, webhooks y jobs no tocan repositorios")
                .check(MAIN_CLASSES);
    }

    @Test
    void regla6LasEntidadesJpaNoSalenDelAdaptadorDePersistencia() {
        noClasses().that().resideOutsideOfPackage("..infrastructure.adapter.out.persistence..")
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .because("general-plan §3.4-6: las entidades JPA son un detalle del adaptador")
                .check(MAIN_CLASSES);
    }

    @Test
    void regla7LaAplicacionNoDependeDeInfrastructure() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("general-plan §3.4-7: los DTOs HTTP/AMQP viven en adapter.in; application usa command/result")
                .check(MAIN_CLASSES);
    }

    private static ArchCondition<JavaClass> onlyApplicationDependencies() {
        return new ArchCondition<>("solo depender de domain, java y org.springframework.transaction.annotation") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!allowedForApplication(target)) {
                        events.add(SimpleConditionEvent.violated(dependency,
                                item.getName() + " no puede depender de " + target.getName()));
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
