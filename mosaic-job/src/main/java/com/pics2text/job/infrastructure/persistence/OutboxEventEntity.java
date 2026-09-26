package com.pics2text.job.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public class OutboxEventEntity {
    private UUID eventId;
    private UUID aggregateId;
    private String exchangeName;
    private String routingKey;
    private String payloadJson;
    private Long delayMillis;
    private Integer publishAttempts;

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public UUID getAggregateId() { return aggregateId; }
    public void setAggregateId(UUID aggregateId) { this.aggregateId = aggregateId; }
    public String getExchangeName() { return exchangeName; }
    public void setExchangeName(String exchangeName) { this.exchangeName = exchangeName; }
    public String getRoutingKey() { return routingKey; }
    public void setRoutingKey(String routingKey) { this.routingKey = routingKey; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public Long getDelayMillis() { return delayMillis; }
    public void setDelayMillis(Long delayMillis) { this.delayMillis = delayMillis; }
    public Integer getPublishAttempts() { return publishAttempts; }
    public void setPublishAttempts(Integer publishAttempts) { this.publishAttempts = publishAttempts; }
}
