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
public interface PhotoAssetMapper extends BaseMapper<PhotoAssetEntity> {
    @Update("""
            UPDATE photo_asset SET status = 'VERIFYING', verification_token = #{verificationToken}, updated_at = #{now}
            WHERE id = #{photoId} AND project_id = #{projectId}
              AND ((status = 'UPLOADING' AND upload_expires_at >= #{now})
                OR (status = 'VERIFYING' AND updated_at < #{staleBefore}))
            """)
    int claimVerification(@Param("projectId") UUID projectId, @Param("photoId") UUID photoId,
            @Param("verificationToken") UUID verificationToken,
            @Param("staleBefore") Instant staleBefore, @Param("now") Instant now);

    @Update("""
            UPDATE photo_asset SET status = 'FAILED', verification_token = NULL,
              failure_code = #{failureCode}, failure_message = #{failureMessage}, updated_at = #{now}
            WHERE id = #{photoId} AND status = 'VERIFYING' AND verification_token = #{verificationToken}
            """)
    int failVerification(@Param("photoId") UUID photoId,
            @Param("verificationToken") UUID verificationToken,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage, @Param("now") Instant now);

    @Update("""
            UPDATE photo_asset SET status = 'AVAILABLE', detected_content_type = #{contentType},
              width = #{width}, height = #{height}, available_at = #{now},
              duplicate_of = NULL, failure_code = NULL, failure_message = NULL,
              verification_token = NULL, updated_at = #{now}
            WHERE id = #{photoId} AND status = 'VERIFYING' AND verification_token = #{verificationToken}
            """)
    int markAvailableFromVerification(@Param("photoId") UUID photoId,
            @Param("verificationToken") UUID verificationToken,
            @Param("contentType") String contentType, @Param("width") int width,
            @Param("height") int height, @Param("now") Instant now);

    @Update("""
            UPDATE photo_asset SET status = 'DUPLICATE', duplicate_of = #{duplicateOf},
              failure_code = 'PHOTO_DUPLICATE', failure_message = '项目中已存在相同照片',
              verification_token = NULL, updated_at = #{now}
            WHERE id = #{photoId} AND status = 'VERIFYING' AND verification_token = #{verificationToken}
            """)
    int markDuplicateFromVerification(@Param("photoId") UUID photoId,
            @Param("verificationToken") UUID verificationToken,
            @Param("duplicateOf") UUID duplicateOf, @Param("now") Instant now);

    @Update("""
            UPDATE photo_asset SET status = 'FAILED', failure_code = 'UPLOAD_CANCELLED',
              failure_message = '上传已取消', updated_at = #{now}
            WHERE id = #{photoId} AND project_id = #{projectId} AND status = 'UPLOADING'
            """)
    int cancelUploading(@Param("projectId") UUID projectId, @Param("photoId") UUID photoId,
            @Param("now") Instant now);

    @Update("""
            UPDATE photo_asset SET status = 'FAILED', failure_code = 'UPLOAD_EXPIRED',
              failure_message = '上传会话已过期', updated_at = #{now}
            WHERE id = #{photoId} AND status = 'UPLOADING' AND upload_expires_at < #{now}
            """)
    int expireUploading(@Param("photoId") UUID photoId, @Param("now") Instant now);

    @Select("""
            SELECT 1 AS acquired
            FROM (SELECT pg_advisory_xact_lock(hashtextextended(CAST(#{projectId} AS text), 0))) acquired_lock
            """)
    int lockProjectUploadCompletion(@Param("projectId") UUID projectId);

    @Select("""
            SELECT * FROM photo_asset
            WHERE project_id = #{projectId} AND sha256 = #{sha256} AND status = 'AVAILABLE'
            LIMIT 1
            """)
    PhotoAssetEntity findAvailableDuplicate(@Param("projectId") UUID projectId, @Param("sha256") String sha256);

    @Select("""
            SELECT * FROM photo_asset
            WHERE project_id = #{projectId} AND status <> 'DELETED'
            ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<PhotoAssetEntity> listByProject(@Param("projectId") UUID projectId,
            @Param("limit") int limit, @Param("offset") long offset);

    @Select("""
            SELECT * FROM photo_asset
            WHERE (status = 'UPLOADING' AND upload_expires_at < #{now})
               OR (status = 'VERIFYING' AND updated_at < #{verifyingBefore})
               OR (status IN ('FAILED', 'DUPLICATE') AND objects_deleted_at IS NULL)
            ORDER BY upload_expires_at LIMIT #{limit}
            """)
    List<PhotoAssetEntity> findExpiredUploads(@Param("now") Instant now,
            @Param("verifyingBefore") Instant verifyingBefore, @Param("limit") int limit);

    @Select("""
            SELECT * FROM photo_asset
            WHERE status = 'WITHDRAWN' AND retention_until < #{now}
            ORDER BY retention_until LIMIT #{limit}
            """)
    List<PhotoAssetEntity> findExpiredRetention(@Param("now") Instant now, @Param("limit") int limit);

    @Select("""
            SELECT p.* FROM photo_asset p
            WHERE p.status = 'AVAILABLE' AND p.analysis_status = 'PENDING'
              AND NOT EXISTS (
                SELECT 1 FROM processing_job j
                WHERE j.resource_type = 'PHOTO_ASSET' AND j.resource_id = p.id
                  AND j.type = 'PHOTO_ANALYSIS'
                  AND j.status IN ('PENDING', 'RUNNING', 'RETRY_WAIT', 'CANCELLATION_REQUESTED')
              )
            ORDER BY p.available_at LIMIT #{limit}
            FOR UPDATE OF p SKIP LOCKED
            """)
    List<PhotoAssetEntity> findAnalysisDispatchCandidates(@Param("limit") int limit);

    @Update("""
            UPDATE photo_asset SET status = #{status}, verification_token = NULL, failure_code = #{failureCode},
              failure_message = #{failureMessage}, updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateFailure(@Param("id") UUID id, @Param("status") String status,
            @Param("failureCode") String failureCode, @Param("failureMessage") String failureMessage,
            @Param("updatedAt") Instant updatedAt);

    @Update("UPDATE photo_asset SET objects_deleted_at = #{deletedAt}, updated_at = #{deletedAt} WHERE id = #{id}")
    int markObjectsDeleted(@Param("id") UUID id, @Param("deletedAt") Instant deletedAt);

    @Update("""
            UPDATE photo_asset SET status = 'DELETED', deleted_at = #{deletedAt},
              objects_deleted_at = #{deletedAt}, failure_code = #{reason},
              failure_message = #{message}, updated_at = #{deletedAt}
            WHERE id = #{id}
            """)
    int markDeleted(@Param("id") UUID id, @Param("deletedAt") Instant deletedAt,
            @Param("reason") String reason, @Param("message") String message);
}
