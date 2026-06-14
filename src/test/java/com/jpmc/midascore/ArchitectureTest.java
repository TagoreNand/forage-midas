package com.jpmc.midascore;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Executable architecture guardrail (Phase 1): the domain must stay a pure, framework-free
 * hexagon. If anyone makes a domain class depend on Spring, JPA, Kafka, Hibernate, or
 * Resilience4j, this test fails the build — the dependency-inversion rule made enforceable.
 */
@AnalyzeClasses(packages = "com.jpmc.midascore")
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_depends_on_no_framework =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "org.apache.kafka..",
                            "org.hibernate..",
                            "io.github.resilience4j..");
}
