rootProject.name = "payments-ledger-demo"

// Phase 2 — ledger-domain + ledger-application.
// Later phases add: ledger-infrastructure, ledger-app (Spring Boot host).
include(":ledger-domain")
include(":ledger-application")
