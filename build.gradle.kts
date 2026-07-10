// Root build: no source, no dependencies. Sub-modules own their own build files.
//
// Later phases add Spring Boot at the ledger-app module level, not here — keeping
// the root free of Spring means Gradle can build ledger-domain in isolation
// without pulling any framework onto the classpath.
allprojects {
    group = "com.hafiz5007"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
