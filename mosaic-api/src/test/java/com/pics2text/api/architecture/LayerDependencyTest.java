package com.pics2text.api.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class LayerDependencyTest {

    @Test
    void domainMustNotDependOnFrameworksOrInfrastructure() {
        var classes = new ClassFileImporter().importPackages("com.pics2text");

        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application..",
                        "..infrastructure..",
                        "..interfaces..",
                        "org.springframework..",
                        "org.apache.ibatis..",
                        "com.baomidou..",
                        "cn.dev33..",
                        "jakarta.persistence..")
                .check(classes);
    }
}
