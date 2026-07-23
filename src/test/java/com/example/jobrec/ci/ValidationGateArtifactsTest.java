package com.example.jobrec.ci;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the CI/CD validation artifacts that back the "automated regression gate"
 * claim. Measured efficiency (manual smoke vs {@code mvn test}) is produced by
 * {@code scripts/measure-validation-gate.sh} and recorded in
 * {@code docs/METRICS.md} — this test only asserts the gate inputs exist.
 */
class ValidationGateArtifactsTest {
    @Test
    void missionCriticalValidationArtifactsArePresentForCiGate() throws Exception {
        Path collection = Paths.get("src/test/resources/postman/innova-product-api.postman_collection.json");
        assertTrue(Files.exists(collection), "Postman collection required for API validation gate");

        String body = new String(Files.readAllBytes(collection), StandardCharsets.UTF_8);
        assertTrue(body.contains("favorite") || body.contains("search") || body.contains("item"),
                "Postman collection should cover mission-critical product flows");

        String pomXml = new String(Files.readAllBytes(Paths.get("pom.xml")), StandardCharsets.UTF_8);
        assertTrue(pomXml.contains("jacoco-maven-plugin"), "JaCoCo required for coverage gate");
        assertTrue(pomXml.contains("maven-surefire-plugin"), "Surefire required for unit-test gate");

        assertTrue(Files.exists(Paths.get("Jenkinsfile")), "Jenkinsfile required for Jenkins CI/CD");
        assertTrue(Files.exists(Paths.get(".gitlab-ci.yml")), "GitLab CI config required");
        assertTrue(Files.exists(Paths.get("scripts/measure-validation-gate.sh")),
                "Validation gate measurement script required");
    }
}
