package com.pics2text.photoasset.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PhotoAnalysisMapper {
    @Select("SELECT * FROM photo_asset WHERE id = #{photoId}")
    PhotoAnalysisAssetEntity findAsset(@Param("photoId") UUID photoId);

    @Update("""
            UPDATE photo_asset SET analysis_status = 'PROCESSING', analysis_error_code = NULL,
              analysis_error_message = NULL, updated_at = #{now}
            WHERE id = #{photoId} AND status = 'AVAILABLE'
            """)
    int markProcessing(@Param("photoId") UUID photoId, @Param("now") Instant now);

    @Update("""
            UPDATE photo_asset SET analysis_status = 'FAILED', analysis_error_code = #{code},
              analysis_error_message = #{message}, updated_at = #{now}
            WHERE id = #{photoId}
            """)
    int markFailed(@Param("photoId") UUID photoId, @Param("code") String code,
            @Param("message") String message, @Param("now") Instant now);

    @Insert("""
            INSERT INTO photo_analysis (
              photo_id, project_id, algorithm_version, sharpness, brightness, contrast,
              lab_l, lab_a, lab_b, d_hash, p_hash, face_count, faces_json,
              subject_x, subject_y, subject_width, subject_height,
              crop_x, crop_y, crop_width, crop_height, duplicate_group_id,
              auto_review_decision, review_reasons_json, orientation_applied, analyzed_at, updated_at
            ) VALUES (
              #{photoId}, #{projectId}, #{algorithmVersion}, #{sharpness}, #{brightness}, #{contrast},
              #{labL}, #{labA}, #{labB}, #{dHash}, #{pHash}, #{faceCount}, CAST(#{facesJson} AS jsonb),
              #{subjectX}, #{subjectY}, #{subjectWidth}, #{subjectHeight},
              #{cropX}, #{cropY}, #{cropWidth}, #{cropHeight}, #{duplicateGroupId},
              #{autoReviewDecision}, CAST(#{reviewReasonsJson} AS jsonb), #{orientationApplied},
              #{analyzedAt}, #{updatedAt}
            )
            ON CONFLICT (photo_id) DO UPDATE SET
              algorithm_version = EXCLUDED.algorithm_version, sharpness = EXCLUDED.sharpness,
              brightness = EXCLUDED.brightness, contrast = EXCLUDED.contrast,
              lab_l = EXCLUDED.lab_l, lab_a = EXCLUDED.lab_a, lab_b = EXCLUDED.lab_b,
              d_hash = EXCLUDED.d_hash, p_hash = EXCLUDED.p_hash,
              face_count = EXCLUDED.face_count, faces_json = EXCLUDED.faces_json,
              subject_x = EXCLUDED.subject_x, subject_y = EXCLUDED.subject_y,
              subject_width = EXCLUDED.subject_width, subject_height = EXCLUDED.subject_height,
              crop_x = EXCLUDED.crop_x, crop_y = EXCLUDED.crop_y,
              crop_width = EXCLUDED.crop_width, crop_height = EXCLUDED.crop_height,
              duplicate_group_id = EXCLUDED.duplicate_group_id,
              auto_review_decision = EXCLUDED.auto_review_decision,
              review_reasons_json = EXCLUDED.review_reasons_json,
              orientation_applied = EXCLUDED.orientation_applied,
              analyzed_at = EXCLUDED.analyzed_at, updated_at = EXCLUDED.updated_at
            """)
    int upsertAnalysis(PhotoAnalysisEntity analysis);

    @Insert("""
            INSERT INTO photo_variant
              (photo_id, variant_type, object_key, width, height, byte_size, content_type, created_at)
            VALUES (#{photoId}, #{variantType}, #{objectKey}, #{width}, #{height},
              #{byteSize}, #{contentType}, #{createdAt})
            ON CONFLICT (photo_id, variant_type) DO UPDATE SET
              object_key = EXCLUDED.object_key, width = EXCLUDED.width, height = EXCLUDED.height,
              byte_size = EXCLUDED.byte_size, content_type = EXCLUDED.content_type,
              created_at = EXCLUDED.created_at
            """)
    int upsertVariant(PhotoVariantEntity variant);

    @Update("""
            UPDATE photo_asset SET analysis_status = 'SUCCEEDED', review_status = #{reviewStatus},
              source_key = COALESCE(source_key, original_key), original_key = #{sanitizedKey},
              thumbnail_key = #{thumbnailKey}, preview_key = #{previewKey},
              width = #{width}, height = #{height}, analysis_error_code = NULL,
              analysis_error_message = NULL, updated_at = #{now}
            WHERE id = #{photoId} AND status = 'AVAILABLE'
            """)
    int markSucceeded(@Param("photoId") UUID photoId, @Param("reviewStatus") String reviewStatus,
            @Param("sanitizedKey") String sanitizedKey, @Param("thumbnailKey") String thumbnailKey,
            @Param("previewKey") String previewKey, @Param("width") int width,
            @Param("height") int height, @Param("now") Instant now);

    @Select("""
            SELECT * FROM photo_analysis
            WHERE project_id = #{projectId} AND photo_id <> #{photoId}
            ORDER BY analyzed_at DESC LIMIT #{limit}
            """)
    List<PhotoAnalysisEntity> findHashCandidates(@Param("projectId") UUID projectId,
            @Param("photoId") UUID photoId, @Param("limit") int limit);

    @Update("UPDATE photo_analysis SET duplicate_group_id = #{groupId}, updated_at = #{now} WHERE photo_id = #{photoId}")
    int assignDuplicateGroup(@Param("photoId") UUID photoId, @Param("groupId") UUID groupId,
            @Param("now") Instant now);

    @Select("SELECT * FROM photo_analysis WHERE photo_id = #{photoId}")
    PhotoAnalysisEntity findAnalysis(@Param("photoId") UUID photoId);

    @Select("SELECT * FROM photo_variant WHERE photo_id = #{photoId} ORDER BY width")
    List<PhotoVariantEntity> listVariants(@Param("photoId") UUID photoId);

}
