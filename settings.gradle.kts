rootProject.name = "payments-ledger-demo"

// Phase 3 — ledger-domain + ledger-application + ledger-infrastructure.
// Phase 5 adds ledger-app (Spring Boot host).
include(":ledger-domain")
include(":ledger-application")
include(":ledger-infrastructure")
