package com.pics2text.layout.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AutoLayoutMapper extends BaseMapper<AutoLayoutEntity> {
    @Select("SELECT * FROM auto_layout WHERE project_id = #{projectId} ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<AutoLayoutEntity> listByProject(@Param("projectId") UUID projectId,
            @Param("limit") int limit, @Param("offset") long offset);

    @Select("SELECT * FROM auto_layout_assignment WHERE layout_id = #{layoutId} ORDER BY slot_index LIMIT #{limit} OFFSET #{offset}")
    List<AutoLayoutAssignmentEntity> listAssignments(@Param("layoutId") UUID layoutId,
            @Param("limit") int limit, @Param("offset") long offset);

    @Select("""
            SELECT pa.photo_id, pa.sharpness, pa.brightness, pa.contrast, pa.lab_l, pa.lab_a,
              pa.lab_b, pa.p_hash, pa.duplicate_group_id, pa.crop_x, pa.crop_y,
              pa.crop_width, pa.crop_height
            FROM photo_analysis pa JOIN photo_asset p ON p.id = pa.photo_id
            WHERE p.project_id = #{projectId} AND p.status = 'AVAILABLE'
              AND p.analysis_status = 'SUCCEEDED' AND p.review_status = 'APPROVED'
              AND p.authorization_status = 'GRANTED'
            ORDER BY pa.photo_id
            LIMIT 250001
            """)
    List<LayoutPhotoCandidateEntity> listEligiblePhotos(@Param("projectId") UUID projectId);

    @Select("SELECT * FROM text_mask_slot WHERE mask_id = #{maskId} ORDER BY slot_index")
    List<TextMaskSlotEntity> listMaskSlots(@Param("maskId") UUID maskId);

    @Update("""
            UPDATE auto_layout SET status = 'PROCESSING', error_code = NULL, error_message = NULL,
              updated_at = #{now} WHERE id = #{layoutId} AND status IN ('PENDING', 'PROCESSING', 'FAILED')
            """)
    int markProcessing(@Param("layoutId") UUID layoutId, @Param("now") Instant now);

    @Update("""
            UPDATE auto_layout SET status = 'FAILED', error_code = #{code}, error_message = #{message},
              updated_at = #{now} WHERE id = #{layoutId}
            """)
    int markFailed(@Param("layoutId") UUID layoutId, @Param("code") String code,
            @Param("message") String message, @Param("now") Instant now);

    @Delete("DELETE FROM auto_layout_assignment WHERE layout_id = #{layoutId}")
    int deleteAssignments(@Param("layoutId") UUID layoutId);

    @Insert({"<script>",
            "INSERT INTO auto_layout_assignment (layout_id, slot_index, photo_id, total_score, quality_score,",
            " brightness_score, color_score, crop_score, duplicate_score, adjacent_score, crop_x, crop_y, crop_width, crop_height) VALUES",
            "<foreach collection='items' item='item' separator=','>",
            "(#{item.layoutId}, #{item.slotIndex}, #{item.photoId}, #{item.totalScore}, #{item.qualityScore},",
            " #{item.brightnessScore}, #{item.colorScore}, #{item.cropScore}, #{item.duplicateScore},",
            " #{item.adjacentScore}, #{item.cropX}, #{item.cropY}, #{item.cropWidth}, #{item.cropHeight})",
            "</foreach>", "</script>"})
    int insertAssignments(@Param("items") List<AutoLayoutAssignmentEntity> items);

    @Update("""
            UPDATE auto_layout SET status = 'READY', algorithm_version = #{algorithmVersion},
              photo_set_hash = #{photoSetHash}, candidate_count = #{candidateCount},
              assigned_count = #{assignedCount}, initial_average_score = #{initialAverageScore},
              final_average_score = #{finalAverageScore}, average_brightness_score = #{averageBrightnessScore},
              average_color_score = #{averageColorScore}, average_crop_score = #{averageCropScore},
              average_adjacent_score = #{averageAdjacentScore}, unique_photo_count = #{uniquePhotoCount},
              duplicate_group_reuse_count = #{duplicateGroupReuseCount},
              candidate_evaluations = #{candidateEvaluations}, estimated_memory_bytes = #{estimatedMemoryBytes},
              runtime_millis = #{runtimeMillis}, error_code = NULL, error_message = NULL,
              completed_at = #{now}, updated_at = #{now} WHERE id = #{layoutId}
            """)
    int markReady(@Param("layoutId") UUID layoutId, @Param("algorithmVersion") String algorithmVersion,
            @Param("photoSetHash") String photoSetHash, @Param("candidateCount") int candidateCount,
            @Param("assignedCount") int assignedCount, @Param("initialAverageScore") double initialAverageScore,
            @Param("finalAverageScore") double finalAverageScore,
            @Param("averageBrightnessScore") double averageBrightnessScore,
            @Param("averageColorScore") double averageColorScore,
            @Param("averageCropScore") double averageCropScore,
            @Param("averageAdjacentScore") double averageAdjacentScore,
            @Param("uniquePhotoCount") int uniquePhotoCount,
            @Param("duplicateGroupReuseCount") int duplicateGroupReuseCount,
            @Param("candidateEvaluations") long candidateEvaluations,
            @Param("estimatedMemoryBytes") long estimatedMemoryBytes,
            @Param("runtimeMillis") long runtimeMillis, @Param("now") Instant now);
}
