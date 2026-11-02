package com.seashare.seasharem3.arch;

import com.seashare.seasharem3.application.port.in.QueryFinancialRecordsUseCase;
import com.seashare.seasharem3.application.service.FinancialRecordsQueryService;
import com.seashare.seasharem3.infrastructure.adapter.in.web.FinancialRecordsController;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.financialrecords.FinancialRecordQueryAdapter;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class Uc12ArchitectureTest {

    private static final Set<String> UC12_CLASSES = Set.of(
            FinancialRecordsQueryService.class.getName(),
            FinancialRecordsController.class.getName(),
            FinancialRecordQueryAdapter.class.getName());

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.seashare.seasharem3");

    @Test
    void includesTheUc12ComponentsAndInboundPort() {
        Set<String> importedNames = MAIN_CLASSES.stream()
                .map(javaClass -> javaClass.getName())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(importedNames).containsAll(UC12_CLASSES);
        assertThat(QueryFinancialRecordsUseCase.class.isAssignableFrom(FinancialRecordsQueryService.class)).isTrue();
    }

    @Test
    void doesNotDependOnMutationAdapters() {
        noClasses().that().haveNameMatching(".*(FinancialRecords|FinancialRecordQuery).*")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..persistence.charge..", "..persistence.refund..",
                        "..persistence.settlement..", "..persistence.parameters..")
                .check(MAIN_CLASSES);
    }
}
