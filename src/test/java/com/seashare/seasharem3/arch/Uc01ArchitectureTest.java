package com.seashare.seasharem3.arch;

import com.seashare.seasharem3.application.port.in.EstimateBatchUseCase;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class Uc01ArchitectureTest {

    private static final com.tngtech.archunit.core.domain.JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.seashare.seasharem3");

    @Test
    void uc01ControllerOnlyDependsOnUseCasePorts() {
        noClasses().that()
                .resideInAPackage("..infrastructure.adapter.in.web..")
                .and().haveNameMatching("EstimateController")
                .should().dependOnClassesThat()
                .areAssignableTo(EstimateBatchUseCase.class)
                .allowEmptyShould(true)
                .check(MAIN_CLASSES);
    }
}
