package com.moduDrive.storage.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class HexagonalArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.moduDrive.storage");

    @Test
    void domainDoesNotDependOnApplicationOrAdapter() {
        ArchRule rule = noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void applicationDoesNotDependOnAdapter() {
        ArchRule rule = noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..")
                .allowEmptyShould(true);
        rule.check(classes);
    }
}
