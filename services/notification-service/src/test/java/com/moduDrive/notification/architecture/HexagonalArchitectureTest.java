package com.moduDrive.notification.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class HexagonalArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.moduDrive.notification");

    @Test
    void domainDoesNotDependOnApplicationOrAdapter() {
        ArchRule rule = noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..");
        rule.check(classes);
    }

    @Test
    void applicationDoesNotDependOnAdapter() {
        ArchRule rule = noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..");
        rule.check(classes);
    }

    @Test
    void applicationDoesNotDependOnConfigOrCrossServiceDtos() {
        ArchRule rule = noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.moduDrive.notification.config..", "com.moduDrive.common.api..");
        rule.check(classes);
    }
}
