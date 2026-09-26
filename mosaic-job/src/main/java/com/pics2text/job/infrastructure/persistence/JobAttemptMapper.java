package com.pics2text.job.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.UUID;

@Mapper
public interface JobAttemptMapper {
    @Insert("""
            INSERT INTO job_attempt(job_id, attempt, event_id, worker_id, status, started_at, heartbeat_at)
            VALUES(#{jobId}, #{attempt}, #{eventId}, #{workerId}, 'RUNNING', #{now}, #{now})
            ON CONFLICT (job_id, attempt) DO NOTHING
            """)
    int insert(@Param("jobId") UUID jobId, @Param("attempt") int attempt,
            @Param("eventId") UUID eventId, @Param("workerId") String workerId,
            @Param("now") Instant now);

    @Update("""
            UPDATE job_attempt SET status = #{status}, heartbeat_at = #{now}, completed_at = #{now},
              error_message = #{errorMessage}
            WHERE job_id = #{jobId} AND attempt = #{attempt} AND worker_id = #{workerId}
            """)
    int complete(@Param("jobId") UUID jobId, @Param("attempt") int attempt,
            @Param("workerId") String workerId, @Param("status") String status,
            @Param("errorMessage") String errorMessage, @Param("now") Instant now);

    @Update("""
            UPDATE job_attempt SET heartbeat_at = #{now}
            WHERE job_id = #{jobId} AND attempt = #{attempt} AND worker_id = #{workerId}
            """)
    int heartbeat(@Param("jobId") UUID jobId, @Param("attempt") int attempt,
            @Param("workerId") String workerId, @Param("now") Instant now);
}
