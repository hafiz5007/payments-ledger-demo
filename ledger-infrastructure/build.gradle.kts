plugins {
    `java-library`
    id("io.spring.dependency-management") version "1.1.6"
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencyManagement {
    imports {
        // Aligns Spring Boot dependency versions across the module without pulling
        // in the Spring Boot Gradle plugin here — that plugin belongs on the
        // executable app module (Phase 5), not on the library.
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.4")
    }
}

dependencies {
    api(project(":ledger-domain"))
    api(project(":ledger-application"))

    // Spring Data JPA + JDBC — the adapter surface. Managed by the BOM.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Postgres driver + Flyway migrations.
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // JSON serialisation for outbox event payloads. Kept off the domain
    // module — Jackson lives at this layer where it belongs.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    implementation("org.slf4j:slf4j-api")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
