package com.moduDrive.mail.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.INTERFACES;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class HexagonalArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.moduDrive.mail");

    @Test
    void domainDoesNotDependOnApplicationOrAdapter() {
        // mail-service has no domain package; allowEmptyShould keeps the rule from failing on zero classes.
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

    @Test
    void applicationDoesNotDependOnConfigOrCrossServiceDtos() {
        ArchRule rule = noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.moduDrive.mail.config..", "com.moduDrive.common.api..");
        rule.check(classes);
    }

    @Test
    void domainDoesNotLog() {
        ArchRule rule = noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("org.slf4j..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void loggersComeFromSlf4jAnnotation() {
        // Lombok's field is "log" on a class. Catches a hand-written "logger"/"LOG" and a logger in an
        // interface (a port); a hand-written field that happens to be named "log" still passes.
        ArchRule rule = fields().that().haveRawType(Logger.class)
                .should().haveName("log")
                .andShould().beDeclaredInClassesThat(not(INTERFACES))
                .allowEmptyShould(true);
        rule.check(classes);
    }
}
