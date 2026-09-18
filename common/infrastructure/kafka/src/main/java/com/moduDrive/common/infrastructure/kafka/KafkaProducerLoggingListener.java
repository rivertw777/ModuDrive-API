package com.moduDrive.common.infrastructure.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.ProducerListener;

class KafkaProducerLoggingListener implements ProducerListener<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerLoggingListener.class);

    @Override
    public void onError(ProducerRecord<Object, Object> producerRecord, RecordMetadata recordMetadata,
                         Exception exception) {
        log.error("Kafka send failed: topic={}, key={}", producerRecord.topic(), producerRecord.key(), exception);
    }
}
