package com.moduDrive.common.infrastructure.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real shared application-kafka.yml and error handler against an embedded broker:
 * a record the listener can't deserialize must reach {@code <topic>-dlt} byte-for-byte, so it
 * can be read and replayed as it was sent. */
class DeadLetterPublishingTest {

    private static final String TOPIC = "orders";
    private static final String FAILING_TOPIC = "payments";
    private static final EmbeddedKafkaKraftBroker BROKER = new EmbeddedKafkaKraftBroker(1, 1,
            TOPIC, TOPIC + "-dlt", FAILING_TOPIC, FAILING_TOPIC + "-dlt");

    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void start() {
        BROKER.afterPropertiesSet();
        context = new SpringApplicationBuilder(ListenerConfig.class)
                .sources(KafkaAutoConfiguration.class, KafkaConsumerRetryAutoConfiguration.class,
                        KafkaLoggingAutoConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.config.import=classpath:application-kafka.yml",
                        "KAFKA_BOOTSTRAP_SERVERS=" + BROKER.getBrokersAsString(),
                        "spring.kafka.consumer.group-id=dlt-test",
                        "spring.kafka.consumer.auto-offset-reset=earliest",
                        "spring.kafka.consumer.properties.spring.json.trusted.packages=" + ListenerConfig.class.getPackageName())
                .run();
    }

    @AfterAll
    static void stop() {
        context.close();
        BROKER.destroy();
    }

    @Test
    @DisplayName("역직렬화할 수 없는 메시지는 원본 바이트 그대로 DLT에 쌓인다")
    void deadLettersAnUndeserializableRecordByteForByte() {
        byte[] poison = "{not json".getBytes(StandardCharsets.UTF_8);
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BROKER.getBrokersAsString()),
                new StringSerializer(), new ByteArraySerializer())) {
            producer.send(new ProducerRecord<>(TOPIC, "k1", poison));
        }

        ConsumerRecord<String, byte[]> dead = readDeadLetter(TOPIC);

        assertThat(dead.key()).isEqualTo("k1");
        assertThat(dead.value()).isEqualTo(poison);
    }

    @Test
    @DisplayName("처리에 실패한 정상 메시지는 재시도 후 기존처럼 JSON으로 DLT에 쌓인다")
    void deadLettersAFailedEventAsJsonAfterRetries() {
        try (KafkaProducer<String, Object> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BROKER.getBrokersAsString()),
                new StringSerializer(), new JsonSerializer<>())) {
            producer.send(new ProducerRecord<>(FAILING_TOPIC, "k2", new Order("o-1")));
        }

        ConsumerRecord<String, byte[]> dead = readDeadLetter(FAILING_TOPIC);

        assertThat(dead.key()).isEqualTo("k2");
        assertThat(new String(dead.value(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":\"o-1\"}");
    }

    private ConsumerRecord<String, byte[]> readDeadLetter(String topic) {
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BROKER.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new ByteArrayDeserializer())) {
            consumer.subscribe(List.of(topic + "-dlt"));
            return KafkaTestUtils.getSingleRecord(consumer, topic + "-dlt", Duration.ofSeconds(30));
        }
    }

    record Order(String id) {
    }

    @Configuration
    static class ListenerConfig {

        @KafkaListener(topics = TOPIC)
        void onOrder(Order order) {
        }

        @KafkaListener(topics = FAILING_TOPIC)
        void onPayment(Order order) {
            throw new IllegalStateException("downstream down");
        }
    }
}
