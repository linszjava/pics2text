package com.pics2text.rendering.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PrintRenderMapper {
    @Insert("""
            INSERT INTO print_render (id, project_id, layout_id, layout_version_id, surface_binding_id,
              status, format, dpi, bleed_mm, tile_size, compression, color_profile, crop_marks,
              registration_marks, content_width_px, content_height_px, output_width_px, output_height_px,
              input_manifest_json, input_manifest_hash, created_by, created_at, updated_at)
            VALUES (#{id}, #{projectId}, #{layoutId}, #{layoutVersionId}, #{surfaceBindingId}, #{status},
              #{format}, #{dpi}, #{bleedMm}, #{tileSize}, #{compression}, #{colorProfile}, #{cropMarks},
              #{registrationMarks}, #{contentWidthPx}, #{contentHeightPx}, #{outputWidthPx}, #{outputHeightPx},
              CAST(#{inputManifestJson} AS jsonb), #{inputManifestHash}, #{createdBy}, #{createdAt}, #{updatedAt})
            """)
    int insert(PrintRenderEntity value);

    @Select("SELECT * FROM print_render WHERE id = #{id}")
    PrintRenderEntity find(@Param("id") UUID id);

    @Select("SELECT * FROM print_render WHERE project_id = #{projectId} ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<PrintRenderEntity> list(@Param("projectId") UUID projectId, @Param("limit") int limit,
            @Param("offset") long offset);

    @Select("SELECT * FROM print_render WHERE project_id = #{projectId} AND input_manifest_hash = #{hash}")
    PrintRenderEntity findByInputHash(@Param("projectId") UUID projectId, @Param("hash") String hash);

    @Select("""
            SELECT a.slot_index, a.photo_id, a.x, a.y, a.width, a.height, a.crop_x, a.crop_y,
              a.crop_width, a.crop_height, p.source_key, p.sha256 AS original_sha256,
              p.width AS source_width, p.height AS source_height, p.review_status, p.authorization_status
            FROM layout_version_assignment a JOIN photo_asset p ON p.id = a.photo_id
            WHERE a.version_id = #{versionId} ORDER BY a.slot_index
            """)
    List<PrintRenderPhotoRow> listPhotoInputs(@Param("versionId") UUID versionId);

    @Update("UPDATE print_render SET job_id = #{jobId}, updated_at = #{now} WHERE id = #{id} AND job_id IS NULL")
    int attachJob(@Param("id") UUID id, @Param("jobId") UUID jobId, @Param("now") Instant now);

    @Update("""
            UPDATE print_render SET status = 'PROCESSING', error_code = NULL, error_message = NULL,
              updated_at = #{now} WHERE id = #{id} AND status IN ('PENDING', 'PROCESSING', 'FAILED')
            """)
    int markProcessing(@Param("id") UUID id, @Param("now") Instant now);

    @Update("""
            UPDATE print_render SET status = 'READY_FOR_APPROVAL', output_object_key = #{outputKey},
              output_sha256 = #{outputSha}, output_byte_size = #{outputSize},
              output_manifest_json = CAST(#{manifestJson} AS jsonb), output_manifest_hash = #{manifestHash},
              location_list_object_key = #{locationsKey}, location_list_sha256 = #{locationsSha},
              completed_at = #{now}, updated_at = #{now}, error_code = NULL, error_message = NULL
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int complete(@Param("id") UUID id, @Param("outputKey") String outputKey,
            @Param("outputSha") String outputSha, @Param("outputSize") long outputSize,
            @Param("manifestJson") String manifestJson, @Param("manifestHash") String manifestHash,
            @Param("locationsKey") String locationsKey, @Param("locationsSha") String locationsSha,
            @Param("now") Instant now);

    @Update("""
            UPDATE print_render SET status = 'FAILED', error_code = #{code}, error_message = #{message},
              updated_at = #{now} WHERE id = #{id} AND status IN ('PENDING', 'PROCESSING', 'FAILED')
            """)
    int fail(@Param("id") UUID id, @Param("code") String code, @Param("message") String message,
            @Param("now") Instant now);

    @Update("UPDATE print_render SET status = #{decision}, updated_at = #{now} WHERE id = #{id} AND status = 'READY_FOR_APPROVAL'")
    int decide(@Param("id") UUID id, @Param("decision") String decision, @Param("now") Instant now);

    @Insert("""
            INSERT INTO print_render_approval (id, render_id, decision, note, actor_id, created_at)
            VALUES (#{id}, #{renderId}, #{decision}, #{note}, #{actorId}, #{now})
            """)
    int insertApproval(@Param("id") UUID id, @Param("renderId") UUID renderId,
            @Param("decision") String decision, @Param("note") String note,
            @Param("actorId") UUID actorId, @Param("now") Instant now);
}
