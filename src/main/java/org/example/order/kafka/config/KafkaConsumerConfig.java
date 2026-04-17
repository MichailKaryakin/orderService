package org.example.order.kafka.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    log.error("Sending to DLT: topic={}, key={}, error={}",
                            record.topic(), record.key(), ex.getMessage());
                    return new TopicPartition(record.topic() + ".DLT", record.partition());
                }
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(2000L, 3L)
        );

        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        return errorHandler;
    }
}
