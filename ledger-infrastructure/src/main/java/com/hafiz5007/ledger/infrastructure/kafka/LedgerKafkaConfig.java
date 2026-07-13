package com.hafiz5007.ledger.infrastructure.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka wiring — producer factory used by the outbox relay, consumer
 * container used by {@link PaymentSubmittedConsumer}, and a default error
 * handler that automatically routes irrecoverable failures to a dead-letter
 * topic after exponential retry.
 * <p>
 * String key + String value serialisation. Payloads are JSON — schema
 * evolution is handled application-side rather than via Avro/Protobuf.
 * For a real fintech deployment I'd add a schema registry, but for a
 * portfolio demo JSON keeps the moving parts down.
 */
@Configuration
@EnableKafka
public class LedgerKafkaConfig {

    @Bean
    public ProducerFactory<String, String> producerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer",   StringSerializer.class);
        props.put("value.serializer", StringSerializer.class);
        // Deliberately at-least-once. Idempotent producer + acks=all so a
        // resend after network flakes doesn't duplicate on the topic.
        props.put("acks", "all");
        props.put("enable.idempotence", true);
        props.put("retries", 10);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> pf) {
        return new KafkaTemplate<>(pf);
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:ledger}") String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("group.id",          groupId);
        props.put("key.deserializer",   StringDeserializer.class);
        props.put("value.deserializer", StringDeserializer.class);
        // Read-committed so consumers only see messages from committed
        // transactions — irrelevant for our JSON producer today but
        // future-proofs against turning on Kafka transactions.
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Manual ack — the listener's @Transactional boundary decides when
        // to commit the offset, tying it to the DB write.
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler errorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.setConcurrency(1);  // one thread per partition; increase in prod
        return factory;
    }

    /**
     * Retry three times with exponential backoff (200ms → 400ms → 800ms),
     * then publish to the {@link LedgerTopics#PAYMENTS_SUBMITTED_DLT}
     * dead-letter topic. The default recoverer preserves the original
     * headers and adds diagnostic ones.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        var backoff = new ExponentialBackOff(200L, 2.0);
        backoff.setMaxInterval(2_000L);
        backoff.setMaxElapsedTime(6_000L);
        var recoverer = new DeadLetterPublishingRecoverer(template);
        return new DefaultErrorHandler(recoverer, backoff);
    }
}
