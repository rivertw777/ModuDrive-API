package com.moduDrive.gateway.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.INTERFACES;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class GatewayArchitectureTest {

    private static final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.moduDrive.gateway");

    @Test
    void clientDoesNotDependOnInboundPackages() {
        ArchRule rule = noClasses().that().resideInAPackage("com.moduDrive.gateway.client..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.moduDrive.gateway.filter..", "com.moduDrive.gateway.security..", "com.moduDrive.gateway.fallback..");
        rule.check(classes);
    }

    @Test
    void exceptionDoesNotDependOnOtherPackages() {
        ArchRule rule = noClasses().that().resideInAPackage("com.moduDrive.gateway.exception..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.moduDrive.gateway.client..", "com.moduDrive.gateway.filter..", "com.moduDrive.gateway.security..",
                        "com.moduDrive.gateway.fallback..", "com.moduDrive.gateway.config..");
        rule.check(classes);
    }

    @Test
    void configDoesNotDependOnImplementations() {
        ArchRule rule = noClasses().that().resideInAPackage("com.moduDrive.gateway.config..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.moduDrive.gateway.client..", "com.moduDrive.gateway.filter..", "com.moduDrive.gateway.security..",
                        "com.moduDrive.gateway.fallback..");
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
