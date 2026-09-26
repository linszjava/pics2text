package com.pics2text.job.infrastructure.messaging;

import com.pics2text.job.infrastructure.persistence.OutboxEventEntity;
import com.pics2text.job.infrastructure.persistence.OutboxEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "jobs.publisher.enabled", havingValue = "true")
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration claimTimeout;
    private final Duration confirmTimeout;

    public OutboxPublisher(OutboxEventMapper outboxMapper, RabbitTemplate rabbitTemplate,
            @Value("${jobs.publisher.batch-size:50}") int batchSize,
            @Value("${jobs.publisher.max-attempts:10}") int maxAttempts,
            @Value("${jobs.publisher.claim-timeout:1m}") Duration claimTimeout,
            @Value("${jobs.publisher.confirm-timeout:10s}") Duration confirmTimeout) {
        this.outboxMapper = outboxMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.claimTimeout = claimTimeout;
        this.confirmTimeout = confirmTimeout;
        this.rabbitTemplate.setMandatory(true);
    }

    @Scheduled(fixedDelayString = "${jobs.publisher.interval:1000}")
    public void publishPending() {
        Instant now = Instant.now();
        List<OutboxEventEntity> events = outboxMapper.claimBatch(now, now.plus(claimTimeout), batchSize);
        for (OutboxEventEntity event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEventEntity event) {
        Instant now = Instant.now();
        try {
            CorrelationData correlation = new CorrelationData(event.getEventId().toString());
            rabbitTemplate.convertAndSend(event.getExchangeName(), event.getRoutingKey(), event.getPayloadJson(),
                    message -> {
                        message.getMessageProperties().setMessageId(event.getEventId().toString());
                        message.getMessageProperties().setContentType("application/json");
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        if (event.getDelayMillis() != null && event.getDelayMillis() > 0) {
                            message.getMessageProperties().setExpiration(event.getDelayMillis().toString());
                        }
                        return message;
                    }, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack() || correlation.getReturned() != null) {
                String reason = correlation.getReturned() != null
                        ? "消息无法路由" : "Broker NACK: " + confirm.reason();
                throw new AmqpException(reason);
            }
            outboxMapper.markPublished(event.getEventId(), Instant.now());
        } catch (Exception exception) {
            int attempts = event.getPublishAttempts() == null ? 1 : event.getPublishAttempts();
            String status = attempts >= maxAttempts ? "DEAD" : "PENDING";
            long seconds = Math.min(1L << Math.min(attempts, 10), 300L);
            outboxMapper.markFailure(event.getEventId(), status, now.plusSeconds(seconds),
                    truncate(exception.getMessage()), now);
            log.warn("Outbox 事件发布失败 eventId={}, attempts={}", event.getEventId(), attempts, exception);
        }
    }

    private static String truncate(String value) {
        String normalized = value == null ? "未知发布错误" : value;
        return normalized.length() <= 2000 ? normalized : normalized.substring(0, 2000);
    }
}
