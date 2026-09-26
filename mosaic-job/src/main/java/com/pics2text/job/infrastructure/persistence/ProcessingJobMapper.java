package com.pics2text.job.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ProcessingJobMapper extends BaseMapper<ProcessingJobEntity> {
    @Insert("""
            INSERT INTO processing_job(id, project_id, resource_type, resource_id, type, status,
              progress, current_attempt, max_attempts, cancellable, payload_json, version,
              created_by, created_at, updated_at)
            VALUES(#{id}, #{projectId}, #{resourceType}, #{resourceId}, #{type}, 'PENDING',
              0, 0, #{maxAttempts}, #{cancellable}, CAST(#{payloadJson} AS jsonb), 0,
              #{createdBy}, #{now}, #{now})
            """)
    int insertJob(@Param("id") UUID id, @Param("projectId") UUID projectId,
            @Param("resourceType") String resourceType, @Param("resourceId") UUID resourceId,
            @Param("type") String type, @Param("maxAttempts") int maxAttempts,
            @Param("cancellable") boolean cancellable, @Param("payloadJson") String payloadJson,
            @Param("createdBy") UUID createdBy, @Param("now") Instant now);

    @Select("""
            SELECT * FROM processing_job WHERE project_id = #{projectId}
            ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<ProcessingJobEntity> listByProject(@Param("projectId") UUID projectId,
            @Param("limit") int limit, @Param("offset") long offset);

    @Select("""
            SELECT * FROM processing_job
            WHERE project_id = #{projectId} AND updated_at > #{after}
            ORDER BY updated_at, id LIMIT #{limit}
            """)
    List<ProcessingJobEntity> listUpdated(@Param("projectId") UUID projectId,
            @Param("after") Instant after, @Param("limit") int limit);

    @Update("""
            UPDATE processing_job SET status = 'RUNNING', current_attempt = #{attempt},
              worker_id = #{workerId}, started_at = COALESCE(started_at, #{now}),
              heartbeat_at = #{now}, next_attempt_at = NULL, error_code = NULL,
              error_message = NULL, version = version + 1, updated_at = #{now}
            WHERE id = #{jobId} AND status IN ('PENDING', 'RETRY_WAIT')
              AND current_attempt < #{attempt} AND cancellation_requested_at IS NULL
            """)
    int claim(@Param("jobId") UUID jobId, @Param("attempt") int attempt,
            @Param("workerId") String workerId, @Param("now") Instant now);

    @Update("""
            UPDATE processing_job SET heartbeat_at = #{now}, progress = GREATEST(progress, #{progress}),
              updated_at = #{now}, version = version + 1
            WHERE id = #{jobId} AND status IN ('RUNNING', 'CANCELLATION_REQUESTED')
              AND worker_id = #{workerId}
            """)
    int heartbeat(@Param("jobId") UUID jobId, @Param("workerId") String workerId,
            @Param("progress") int progress, @Param("now") Instant now);

    @Update("""
            UPDATE processing_job SET cancellable = FALSE, version = version + 1, updated_at = #{now}
            WHERE id = #{jobId} AND status = 'RUNNING' AND worker_id = #{workerId}
            """)
    int enterNonCancellablePhase(@Param("jobId") UUID jobId, @Param("workerId") String workerId,
            @Param("now") Instant now);

    @Update("""
            UPDATE processing_job SET status = 'SUCCEEDED', progress = 100, result_json = CAST(#{resultJson} AS jsonb),
              completed_at = #{now}, heartbeat_at = #{now}, version = version + 1, updated_at = #{now}
            WHERE id = #{jobId} AND status IN ('RUNNING', 'CANCELLATION_REQUESTED')
              AND worker_id = #{workerId}
            """)
    int succeed(@Param("jobId") UUID jobId, @Param("workerId") String workerId,
            @Param("resultJson") String resultJson, @Param("now") Instant now);

    @Update("""
            UPDATE processing_job SET status = #{status}, error_code = #{errorCode},
              error_message = #{errorMessage}, next_attempt_at = #{nextAttemptAt},
              completed_at = CASE WHEN #{terminal} THEN CAST(#{now} AS TIMESTAMPTZ) ELSE NULL::TIMESTAMPTZ END,
              heartbeat_at = #{now}, version = version + 1, updated_at = #{now}
            WHERE id = #{jobId} AND status IN ('RUNNING', 'CANCELLATION_REQUESTED')
              AND worker_id = #{workerId}
            """)
    int fail(@Param("jobId") UUID jobId, @Param("workerId") String workerId,
            @Param("status") String status, @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage, @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("terminal") boolean terminal, @Param("now") Instant now);

    @Update("""
            UPDATE processing_job SET status = CASE WHEN status IN ('PENDING', 'RETRY_WAIT') THEN 'CANCELLED'
                                                    ELSE 'CANCELLATION_REQUESTED' END,
              cancellation_requested_at = #{now},
              completed_at = CASE WHEN status IN ('PENDING', 'RETRY_WAIT') THEN #{now} ELSE completed_at END,
              version = version + 1, updated_at = #{now}
            WHERE id = #{jobId} AND cancellable = TRUE
              AND status IN ('PENDING', 'RETRY_WAIT', 'RUNNING')
            """)
    int requestCancellation(@Param("jobId") UUID jobId, @Param("now") Instant now);

    @Update("""
            UPDATE processing_job SET status = 'CANCELLED', completed_at = #{now}, heartbeat_at = #{now},
              version = version + 1, updated_at = #{now}
            WHERE id = #{jobId} AND status = 'CANCELLATION_REQUESTED' AND worker_id = #{workerId}
            """)
    int completeCancellation(@Param("jobId") UUID jobId, @Param("workerId") String workerId,
            @Param("now") Instant now);

    @Update("""
            UPDATE processing_job SET status = 'PENDING', progress = 0, max_attempts = #{maxAttempts},
              next_attempt_at = NULL, heartbeat_at = NULL, started_at = NULL, completed_at = NULL,
              cancellation_requested_at = NULL, worker_id = NULL, error_code = NULL, error_message = NULL,
              version = version + 1, updated_at = #{now}
            WHERE id = #{jobId} AND status IN ('FAILED', 'DEAD_LETTERED', 'CANCELLED')
            """)
    int resetForManualRetry(@Param("jobId") UUID jobId, @Param("maxAttempts") int maxAttempts,
            @Param("now") Instant now);

    @Select("""
            SELECT * FROM processing_job WHERE status IN ('RUNNING', 'CANCELLATION_REQUESTED')
              AND heartbeat_at < #{deadline} ORDER BY heartbeat_at LIMIT #{limit}
            """)
    List<ProcessingJobEntity> findStale(@Param("deadline") Instant deadline, @Param("limit") int limit);

    @Update("""
            UPDATE processing_job SET status = #{status}, error_code = 'WORKER_HEARTBEAT_TIMEOUT',
              error_message = '工作节点心跳超时，系统已接管该作业', next_attempt_at = #{nextAttemptAt},
              completed_at = CASE WHEN #{terminal} THEN CAST(#{now} AS TIMESTAMPTZ) ELSE NULL::TIMESTAMPTZ END,
              version = version + 1, updated_at = #{now}
            WHERE id = #{jobId} AND status = 'RUNNING' AND heartbeat_at < #{deadline}
            """)
    int recoverStale(@Param("jobId") UUID jobId, @Param("deadline") Instant deadline,
            @Param("status") String status, @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("terminal") boolean terminal, @Param("now") Instant now);

    @Update("""
            UPDATE processing_job SET status = 'CANCELLED', completed_at = #{now},
              error_code = 'CANCELLED_AFTER_WORKER_TIMEOUT',
              error_message = '取消中的作业因工作节点心跳超时而终止',
              version = version + 1, updated_at = #{now}
            WHERE id = #{jobId} AND status = 'CANCELLATION_REQUESTED' AND heartbeat_at < #{deadline}
            """)
    int recoverStaleCancellation(@Param("jobId") UUID jobId, @Param("deadline") Instant deadline,
            @Param("now") Instant now);
}
