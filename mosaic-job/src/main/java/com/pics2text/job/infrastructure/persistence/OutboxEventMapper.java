package com.pics2text.job.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface OutboxEventMapper {
    @Insert("""
            INSERT INTO outbox_event(event_id, aggregate_type, aggregate_id, event_type,
              exchange_name, routing_key, payload_json, delay_millis, status, next_attempt_at,
              created_at, updated_at)
            VALUES(#{eventId}, 'PROCESSING_JOB', #{jobId}, #{eventType}, #{exchangeName},
              #{routingKey}, CAST(#{payloadJson} AS jsonb), #{delayMillis}, 'PENDING', #{now}, #{now}, #{now})
            """)
    int insert(@Param("eventId") UUID eventId, @Param("jobId") UUID jobId,
            @Param("eventType") String eventType, @Param("exchangeName") String exchangeName,
            @Param("routingKey") String routingKey, @Param("payloadJson") String payloadJson,
            @Param("delayMillis") long delayMillis, @Param("now") Instant now);

    @Select("""
            WITH candidates AS (
              SELECT event_id FROM outbox_event
              WHERE ((status = 'PENDING' AND next_attempt_at <= #{now})
                     OR (status = 'PUBLISHING' AND locked_until < #{now}))
              ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT #{limit}
            )
            UPDATE outbox_event o SET status = 'PUBLISHING', locked_until = #{lockedUntil},
              publish_attempts = publish_attempts + 1, updated_at = #{now}
            FROM candidates c WHERE o.event_id = c.event_id
            RETURNING o.event_id, o.aggregate_id, o.exchange_name, o.routing_key,
              o.payload_json::text AS payload_json, o.delay_millis, o.publish_attempts
            """)
    List<OutboxEventEntity> claimBatch(@Param("now") Instant now,
            @Param("lockedUntil") Instant lockedUntil, @Param("limit") int limit);

    @Update("""
            UPDATE outbox_event SET status = 'PUBLISHED', published_at = #{now}, locked_until = NULL,
              last_error = NULL, updated_at = #{now} WHERE event_id = #{eventId} AND status = 'PUBLISHING'
            """)
    int markPublished(@Param("eventId") UUID eventId, @Param("now") Instant now);

    @Update("""
            UPDATE outbox_event SET status = #{status}, next_attempt_at = #{nextAttemptAt},
              locked_until = NULL, last_error = #{error}, updated_at = #{now}
            WHERE event_id = #{eventId} AND status = 'PUBLISHING'
            """)
    int markFailure(@Param("eventId") UUID eventId, @Param("status") String status,
            @Param("nextAttemptAt") Instant nextAttemptAt, @Param("error") String error,
            @Param("now") Instant now);
}
