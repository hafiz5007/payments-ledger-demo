plugins {
    `java-library`
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    // Domain deliberately has no framework dependencies. Not Spring, not JPA,
    // not Jackson. If you feel like adding one, don't — write an adapter in
    // the infrastructure module that maps a framework type to the domain instead.
    //
    // The only allowed dep is SLF4J API (no binding) so services can log
    // without pulling in Logback. The Spring Boot host provides the binding.
    implementation("org.slf4j:slf4j-api:2.0.16")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
