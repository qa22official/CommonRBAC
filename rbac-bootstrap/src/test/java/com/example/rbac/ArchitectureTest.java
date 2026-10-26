package com.example.rbac;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Executable module-boundary rules R-1..R-9 (02 §5). Maven already prevents most
 * cross-module dependencies; these rules also catch package-level leaks such as
 * a core class importing servlet or web types.
 */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.example.rbac");
    }

    @Test
    void apiHasNoFrameworkImplementation() {
        ArchRule rule = noClasses().that().resideInAPackage("..rbac.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "org.apache.ibatis..", "jakarta.servlet..");
        rule.check(classes);
    }

    @Test
    void coreHasNoWebTypes() {
        ArchRule rule = noClasses().that().resideInAPackage("..rbac.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.servlet..", "org.springframework.web..");
        rule.check(classes);
    }

    @Test
    void coreDoesNotDependOnOtherModules() {
        ArchRule rule = noClasses().that().resideInAPackage("..rbac.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..rbac.auth..", "..rbac.cache..", "..rbac.audit..",
                        "..rbac.admin..", "..rbac.guard..");
        rule.check(classes);
    }

    @Test
    void cacheDoesNotDependOnOtherModules() {
        ArchRule rule = noClasses().that().resideInAPackage("..rbac.cache..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..rbac.core..", "..rbac.auth..", "..rbac.audit..",
                        "..rbac.admin..", "..rbac.guard..");
        rule.check(classes);
    }

    @Test
    void auditDoesNotDependOnOtherModules() {
        ArchRule rule = noClasses().that().resideInAPackage("..rbac.audit..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..rbac.core..", "..rbac.auth..", "..rbac.cache..",
                        "..rbac.admin..", "..rbac.guard..");
        rule.check(classes);
    }

    @Test
    void authDoesNotDependOnOtherModules() {
        ArchRule rule = noClasses().that().resideInAPackage("..rbac.auth..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..rbac.core..", "..rbac.cache..", "..rbac.audit..",
                        "..rbac.admin..", "..rbac.guard..");
        rule.check(classes);
    }

    @Test
    void guardDoesNotDependOnOtherModules() {
        ArchRule rule = noClasses().that().resideInAPackage("..rbac.guard..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..rbac.core..", "..rbac.auth..", "..rbac.cache..",
                        "..rbac.audit..", "..rbac.admin..");
        rule.check(classes);
    }

    @Test
    void adminDependsOnlyOnApi() {
        ArchRule rule = noClasses().that().resideInAPackage("..rbac.admin..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..rbac.core..", "..rbac.auth..", "..rbac.cache..",
                        "..rbac.audit..", "..rbac.guard..");
        rule.check(classes);
    }

    /**
     * R-5: SQL stays behind mapper interfaces; only the module's domain
     * services, snapshot loaders, validators and support classes may use them.
     */
    @Test
    void mappersAreOnlyUsedByOwnDomainServices() {
        ArchRule rule = classes().that().haveSimpleNameEndingWith("Mapper")
                .should().onlyBeAccessed().byClassesThat().resideInAnyPackage(
                        "..rbac.core.internal.mapper..",
                        "..rbac.core.internal.service..",
                        "..rbac.core.internal.snapshot..",
                        "..rbac.core.internal.support..",
                        "..rbac.core.internal.validator..",
                        "..rbac.audit.internal.mapper..",
                        "..rbac.audit.internal.service..");
        rule.check(classes);
    }

    @Test
    void controllersLiveInWebPackages() {
        ArchRule rule = classes().that()
                .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should().resideInAnyPackage("..web..");
        rule.check(classes);
    }
}
