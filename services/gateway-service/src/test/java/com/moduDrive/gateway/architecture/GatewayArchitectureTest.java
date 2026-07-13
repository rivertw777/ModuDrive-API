package com.moduDrive.gateway.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class GatewayArchitectureTest {

    private static final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.moduDrive.gateway");

    @Test
    void adapterOutDoesNotDependOnAdapterIn() {
        ArchRule rule = noClasses().that().resideInAPackage("..adapter.out..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.in..");
        rule.check(classes);
    }

    @Test
    void exceptionDoesNotDependOnAdapter() {
        ArchRule rule = noClasses().that().resideInAPackage("..exception..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..");
        rule.check(classes);
    }

    @Test
    void configDoesNotDependOnAdapterOut() {
        ArchRule rule = noClasses().that().resideInAPackage("..gateway.config..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.out..");
        rule.check(classes);
    }

    @Test
    void configDoesNotDependOnAdapterIn() {
        ArchRule rule = noClasses().that().resideInAPackage("..gateway.config..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.in..");
        rule.check(classes);
    }
}
