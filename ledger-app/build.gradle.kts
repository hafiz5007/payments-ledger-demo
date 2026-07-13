plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    // The composed application: bring in Domain (via Application transitively)
    // + Application + Infrastructure. Everything the app needs at runtime.
    implementation(project(":ledger-domain"))
    implementation(project(":ledger-application"))
    implementation(project(":ledger-infrastructure"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework:spring-tx")

    // Test
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.awaitility:awaitility:4.2.2")
}

tasks.withType<Test> {
    useJUnitPlatform()

    val currentDockerHost = System.getenv("DOCKER_HOST")
    if (currentDockerHost.isNullOrBlank() || currentDockerHost.contains("dockerDesktopLinuxEngine", ignoreCase = true)) {
        environment("DOCKER_HOST", "npipe:////./pipe/docker_engine")
    }
}

springBoot {
    mainClass.set("com.hafiz5007.ledger.app.LedgerApplication")
}
