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
        // notification-service has no domain/model package yet — it's a pure event-consuming
        // mailer with no business invariants of its own. allowEmptyShould keeps the rule from
        // failing on "checked zero classes" until a real domain model lands here.
        ArchRule rule = noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void applicationDoesNotDependOnAdapter() {
        ArchRule rule = noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..");
        rule.check(classes);
    }
}
