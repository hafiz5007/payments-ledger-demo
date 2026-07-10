rootProject.name = "payments-ledger-demo"

// Phase 1 — pure-Java 21 domain sub-module only.
// Later phases add: ledger-application, ledger-infrastructure, ledger-app (Spring Boot host).
include(":ledger-domain")
