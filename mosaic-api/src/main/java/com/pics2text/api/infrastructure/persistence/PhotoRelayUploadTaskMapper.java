package com.pics2text.api.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PhotoRelayUploadTaskMapper extends BaseMapper<PhotoRelayUploadTaskEntity> {
    @Select("""
      SELECT * FROM photo_relay_upload_task
      WHERE storage_node=#{node} AND status IN ('QUEUED','RETRY_WAIT') AND next_attempt_at<=#{now}
      ORDER BY created_at LIMIT #{limit} FOR UPDATE SKIP LOCKED
      """)
    List<PhotoRelayUploadTaskEntity> findClaimCandidates(
            @Param("node") String node, @Param("now") Instant now, @Param("limit") int limit);

    @Update("""
      UPDATE photo_relay_upload_task SET status='BACKEND_UPLOADING', claim_token=#{token}, claimed_at=#{now},
        attempt_count=attempt_count+1, updated_at=#{now}
      WHERE id=#{id} AND status IN ('QUEUED','RETRY_WAIT')
      """)
    int claim(@Param("id") UUID id, @Param("token") UUID token, @Param("now") Instant now);

    @Update("UPDATE photo_relay_upload_task SET claimed_at=#{now}, updated_at=#{now} WHERE id=#{id} AND status='BACKEND_UPLOADING' AND claim_token=#{token}")
    int heartbeat(@Param("id") UUID id, @Param("token") UUID token, @Param("now") Instant now);

    @Update("""
      UPDATE photo_relay_upload_task SET status='QUEUED', claim_token=NULL, claimed_at=NULL, updated_at=#{now}
      WHERE id=#{id} AND status='BACKEND_UPLOADING' AND claim_token=#{token}
      """)
    int releaseClaim(@Param("id") UUID id, @Param("token") UUID token, @Param("now") Instant now);

    @Update("""
      UPDATE photo_relay_upload_task SET status='SUCCEEDED', claim_token=NULL, claimed_at=NULL, updated_at=#{now}
      WHERE id=#{id} AND status='BACKEND_UPLOADING' AND claim_token=#{token}
      """)
    int succeed(@Param("id") UUID id, @Param("token") UUID token, @Param("now") Instant now);

    @Update("""
      UPDATE photo_relay_upload_task SET status=#{status}, next_attempt_at=#{next}, claim_token=NULL,
        claimed_at=NULL, failure_code=#{code}, failure_message=#{message}, updated_at=#{now}
      WHERE id=#{id} AND status='BACKEND_UPLOADING' AND claim_token=#{token}
      """)
    int failAttempt(@Param("id") UUID id, @Param("token") UUID token,
            @Param("status") String status, @Param("next") Instant next,
            @Param("code") String code, @Param("message") String message,
            @Param("now") Instant now);

    @Update("""
      UPDATE photo_relay_upload_task SET status='RETRY_WAIT', claim_token=NULL, claimed_at=NULL,
        next_attempt_at=#{now}, failure_code='RELAY_RECOVERED', failure_message='服务重启后恢复', updated_at=#{now}
      WHERE storage_node=#{node} AND status='BACKEND_UPLOADING' AND claimed_at<#{staleBefore}
      """)
    int recoverStale(@Param("node") String node, @Param("staleBefore") Instant staleBefore, @Param("now") Instant now);

    @Select("SELECT count(*) FROM photo_relay_upload_task WHERE storage_node=#{node} AND status IN ('RECEIVED','QUEUED','BACKEND_UPLOADING','RETRY_WAIT')")
    long countPending(@Param("node") String node);

    @Select("SELECT COALESCE(sum(byte_size),0) FROM photo_relay_upload_task WHERE storage_node=#{node} AND status IN ('RECEIVED','QUEUED','BACKEND_UPLOADING','RETRY_WAIT')")
    long sumPendingBytes(@Param("node") String node);

    @Update("UPDATE photo_relay_upload_task SET status='QUEUED', updated_at=#{now} WHERE id=#{id} AND status='RECEIVED'")
    int queueReceived(@Param("id") UUID id, @Param("now") Instant now);

    @Update("UPDATE photo_relay_upload_task SET status='QUEUED', updated_at=#{now} WHERE storage_node=#{node} AND status='RECEIVED'")
    int recoverReceived(@Param("node") String node, @Param("now") Instant now);

    @Select("SELECT * FROM photo_relay_upload_task WHERE storage_node=#{node} AND status IN ('SUCCEEDED','FAILED') AND temp_deleted_at IS NULL LIMIT #{limit}")
    List<PhotoRelayUploadTaskEntity> findTerminalCleanup(@Param("node") String node, @Param("limit") int limit);

    @Update("UPDATE photo_relay_upload_task SET temp_deleted_at=#{now}, updated_at=#{now} WHERE id=#{id} AND status IN ('SUCCEEDED','FAILED')")
    int markTempDeleted(@Param("id") UUID id, @Param("now") Instant now);
}
