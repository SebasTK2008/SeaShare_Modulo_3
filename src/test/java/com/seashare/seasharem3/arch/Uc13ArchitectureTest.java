package com.seashare.seasharem3.arch;

import com.seashare.seasharem3.application.port.in.ExportFinancialReportUseCase;
import com.seashare.seasharem3.application.port.in.GetFinancialReportUseCase;
import com.seashare.seasharem3.application.service.FinancialReportService;
import com.seashare.seasharem3.domain.model.FinancialRecord;
import com.seashare.seasharem3.domain.service.FinancialAggregator;
import com.seashare.seasharem3.domain.service.ReportPeriodResolver;
import com.seashare.seasharem3.domain.service.VariationCalculator;
import com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport.CsvReportExporter;
import com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport.FinancialReportController;
import com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport.FinancialReportResponseMapper;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.financialreport.FinancialReportQueryAdapter;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class Uc13ArchitectureTest {

    private static final Set<String> UC13_CLASSES = Set.of(
            FinancialReportService.class.getName(),
            FinancialRecord.class.getName(),
            FinancialAggregator.class.getName(),
            ReportPeriodResolver.class.getName(),
            VariationCalculator.class.getName(),
            FinancialReportController.class.getName(),
            FinancialReportResponseMapper.class.getName(),
            CsvReportExporter.class.getName(),
            FinancialReportQueryAdapter.class.getName());

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.seashare.seasharem3");

    @Test
    void includesTheUc13ComponentsAndInboundPorts() {
        Set<String> importedNames = MAIN_CLASSES.stream()
                .map(javaClass -> javaClass.getName())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(importedNames).containsAll(UC13_CLASSES);
        assertThat(GetFinancialReportUseCase.class.isAssignableFrom(FinancialReportService.class)).isTrue();
        assertThat(ExportFinancialReportUseCase.class.isAssignableFrom(FinancialReportService.class)).isTrue();
    }

    @Test
    void doesNotDependOnFleetOrMutationAdapters() {
        noClasses().that().haveNameMatching(".*(FinancialReport|ReportPeriod|FinancialAggregator|VariationCalculator).*")
                .should().dependOnClassesThat().resideInAPackage("..fleet..")
                .because("UC13 is read-only and determines scope from declared report data")
                .check(MAIN_CLASSES);
        noClasses().that().haveNameMatching(".*(FinancialReport|ReportPeriod|FinancialAggregator|VariationCalculator).*")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..persistence.charge..", "..persistence.refund..",
                        "..persistence.settlement..", "..persistence.parameters..")
                .because("UC13 reads the published financial-record view instead of mutating source records")
                .check(MAIN_CLASSES);
    }
}
