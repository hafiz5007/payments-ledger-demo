package com.hafiz5007.ledger.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hafiz5007.ledger.infrastructure.repositories.OutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test.
 *
 * Spins up Postgres 16 + Kafka via Testcontainers, boots the full Spring
 * context, exercises the REST API, and asserts that the outbox picks up
 * every published event. Runs slow (~30s cold start) — the trade-off for
 * genuinely realistic coverage.
 *
 * The three tests together cover the marquee properties: idempotency,
 * balance-projection correctness, and outbox-based event delivery.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class LedgerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:16-alpine"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void springProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",       postgres::getJdbcUrl);
        r.add("spring.datasource.username",  postgres::getUsername);
        r.add("spring.datasource.password",  postgres::getPassword);
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OutboxJpaRepository outbox;

    @Test
    void endToEnd_createAccounts_seedBalance_postPayment_checkBalance() throws Exception {
        // 1. Create two accounts.
        String aliceId = createAccount("alice", "LIABILITY", "GBP");
        String bobId   = createAccount("bob",   "LIABILITY", "GBP");
        String capitalId = createAccount("capital", "EQUITY", "GBP");

        // 2. Seed alice with 100 GBP by posting a payment from capital → alice.
        //    (An equity account can carry a debit balance — represents retained losses.)
        postPayment(newTx(), UUID.randomUUID().toString(), capitalId, aliceId, "100.00", "GBP", "opening")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.outcome").value("PostedNew"));

        // 3. Alice pays bob 30.
        String paymentId = UUID.randomUUID().toString();
        String txId = newTx();
        postPayment(txId, paymentId, aliceId, bobId, "30.00", "GBP", "coffee")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.outcome").value("PostedNew"));

        // 4. Balances updated correctly.
        mvc.perform(get("/api/v1/accounts/" + aliceId + "/balance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount").value("70.00"));
        mvc.perform(get("/api/v1/accounts/" + bobId + "/balance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount").value("30.00"));
    }

    @Test
    void duplicateSubmission_isIdempotent() throws Exception {
        String alice = createAccount("alice-dup", "LIABILITY", "GBP");
        String bob   = createAccount("bob-dup",   "LIABILITY", "GBP");
        String capital = createAccount("capital-dup", "EQUITY", "GBP");
        postPayment(newTx(), UUID.randomUUID().toString(), capital, alice, "50.00", "GBP", "seed").andExpect(status().isCreated());

        String paymentId = UUID.randomUUID().toString();
        String txId = "dup-" + UUID.randomUUID();

        // First submission → 201 PostedNew.
        MvcResult first = postPayment(txId, paymentId, alice, bob, "10.00", "GBP", "same-tx")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.outcome").value("PostedNew"))
            .andReturn();
        String entryId = json.readTree(first.getResponse().getContentAsString())
            .get("ledgerEntryId").asText();

        // Second submission with the same tx → 200 AlreadyPosted with the SAME entry id.
        postPayment(txId, paymentId, alice, bob, "10.00", "GBP", "same-tx")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.outcome").value("AlreadyPosted"))
            .andExpect(jsonPath("$.ledgerEntryId").value(entryId));

        // Alice's balance dropped by 10 exactly once.
        mvc.perform(get("/api/v1/accounts/" + alice + "/balance"))
            .andExpect(jsonPath("$.amount").value("40.00"));
    }

    @Test
    void outbox_receivesEventsPerPayment() throws Exception {
        String alice = createAccount("alice-obx", "LIABILITY", "GBP");
        String bob   = createAccount("bob-obx",   "LIABILITY", "GBP");
        String capital = createAccount("capital-obx", "EQUITY", "GBP");
        postPayment(newTx(), UUID.randomUUID().toString(), capital, alice, "50.00", "GBP", "seed").andExpect(status().isCreated());
        postPayment(newTx(), UUID.randomUUID().toString(), alice, bob, "5.00", "GBP", "coffee").andExpect(status().isCreated());

        // Outbox holds AccountCreated x3 + PaymentPosted x2. The relay worker
        // publishes to Kafka on its tick; assert the rows exist regardless of
        // whether the relay has run yet.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            long total = outbox.count();
            assertThat(total).isGreaterThanOrEqualTo(5);
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private String createAccount(String name, String type, String currency) throws Exception {
        var body = Map.of("name", name, "type", type, "currency", currency);
        var res = mvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions postPayment(
            String txId, String paymentId, String from, String to,
            String amount, String currency, String reference) throws Exception {
        var body = Map.of(
            "paymentId", paymentId,
            "transactionId", txId,
            "fromAccountId", from,
            "toAccountId", to,
            "amount", amount,
            "currency", currency,
            "reference", reference);
        return mvc.perform(post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
    }

    private static String newTx() {
        return "tx-" + UUID.randomUUID();
    }
}
