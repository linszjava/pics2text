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
public interface TextMaskMapper extends BaseMapper<TextMaskDesignEntity> {
    @Select("""
            SELECT * FROM text_mask_design WHERE project_id = #{projectId}
            ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<TextMaskDesignEntity> listByProject(@Param("projectId") UUID projectId,
            @Param("limit") int limit, @Param("offset") long offset);

    @Update("""
            UPDATE text_mask_design SET status = 'PROCESSING', error_code = NULL,
              error_message = NULL, updated_at = #{now}
            WHERE id = #{maskId} AND status IN ('PENDING', 'PROCESSING', 'FAILED')
            """)
    int markProcessing(@Param("maskId") UUID maskId, @Param("now") Instant now);

    @Update("""
            UPDATE text_mask_design SET status = 'FAILED', error_code = #{code},
              error_message = #{message}, updated_at = #{now}
            WHERE id = #{maskId}
            """)
    int markFailed(@Param("maskId") UUID maskId, @Param("code") String code,
            @Param("message") String message, @Param("now") Instant now);

    @Update("""
            UPDATE text_mask_design SET status = 'READY', algorithm_version = #{algorithmVersion},
              mask_key = #{maskKey}, debug_key = #{debugKey},
              bounds_x = #{boundsX}, bounds_y = #{boundsY}, bounds_width = #{boundsWidth},
              bounds_height = #{boundsHeight}, estimated_stroke_width = #{estimatedStrokeWidth},
              average_coverage = #{averageCoverage}, slot_count = #{slotCount},
              warnings_json = CAST(#{warningsJson} AS jsonb), error_code = NULL,
              error_message = NULL, completed_at = #{now}, updated_at = #{now}
            WHERE id = #{maskId}
            """)
    int markReady(@Param("maskId") UUID maskId, @Param("algorithmVersion") String algorithmVersion,
            @Param("maskKey") String maskKey, @Param("debugKey") String debugKey,
            @Param("boundsX") int boundsX, @Param("boundsY") int boundsY,
            @Param("boundsWidth") int boundsWidth, @Param("boundsHeight") int boundsHeight,
            @Param("estimatedStrokeWidth") double estimatedStrokeWidth,
            @Param("averageCoverage") double averageCoverage, @Param("slotCount") int slotCount,
            @Param("warningsJson") String warningsJson, @Param("now") Instant now);

    @Delete("DELETE FROM text_mask_slot WHERE mask_id = #{maskId}")
    int deleteSlots(@Param("maskId") UUID maskId);

    @Insert({"<script>",
            "INSERT INTO text_mask_slot (mask_id, slot_index, row_index, column_index, x, y, width, height, coverage, edge) VALUES",
            "<foreach collection='slots' item='slot' separator=','>",
            "(#{slot.maskId}, #{slot.slotIndex}, #{slot.rowIndex}, #{slot.columnIndex}, #{slot.x}, #{slot.y},",
            " #{slot.width}, #{slot.height}, #{slot.coverage}, #{slot.edge})",
            "</foreach>",
            "</script>"})
    int insertSlots(@Param("slots") List<TextMaskSlotEntity> slots);

    @Select("""
            SELECT * FROM text_mask_slot WHERE mask_id = #{maskId}
            ORDER BY slot_index LIMIT #{limit} OFFSET #{offset}
            """)
    List<TextMaskSlotEntity> listSlots(@Param("maskId") UUID maskId,
            @Param("limit") int limit, @Param("offset") long offset);
}
