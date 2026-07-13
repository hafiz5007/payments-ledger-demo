package com.hafiz5007.ledger.app;

import com.hafiz5007.ledger.infrastructure.config.InfrastructureConfig;
import com.hafiz5007.ledger.infrastructure.kafka.LedgerKafkaConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * The runnable host. Everything else (Domain, Application, Infrastructure)
 * is a library; this module owns the composition + wire-up.
 * <p>
 * {@code @SpringBootApplication} at this specific package
 * ({@code com.hafiz5007.ledger.app}) scopes component scanning to the API
 * controllers only. The Infrastructure config is pulled in explicitly with
 * {@code @Import} rather than scanned — that means bean wiring is auditable
 * in exactly two files ({@link InfrastructureConfig} and this one).
 */
@SpringBootApplication
@Import({ InfrastructureConfig.class, LedgerKafkaConfig.class })
public class LedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerApplication.class, args);
    }
}
