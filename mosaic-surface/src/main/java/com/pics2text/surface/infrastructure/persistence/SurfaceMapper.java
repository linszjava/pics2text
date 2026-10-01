package com.pics2text.surface.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SurfaceMapper {
    @Insert("""
            INSERT INTO surface_template (id, project_id, name, surface_type, unit, diameter_mm,
              circumference_mm, axial_length_mm, seam_x_mm, seam_safety_mm, safe_top_mm,
              safe_right_mm, safe_bottom_mm, safe_left_mm, template_version, status,
              geometry_hash, created_by, created_at)
            VALUES (#{id}, #{projectId}, #{name}, #{surfaceType}, #{unit}, #{diameterMm},
              #{circumferenceMm}, #{axialLengthMm}, #{seamXmm}, #{seamSafetyMm}, #{safeTopMm},
              #{safeRightMm}, #{safeBottomMm}, #{safeLeftMm}, #{templateVersion}, #{status},
              #{geometryHash}, #{createdBy}, #{createdAt})
            """)
    int insertTemplate(SurfaceTemplateEntity value);

    @Insert("""
            INSERT INTO surface_region (template_id, region_index, region_type, name, x_mm, y_mm,
              width_mm, height_mm, blocking, metadata_json)
            VALUES (#{templateId}, #{regionIndex}, #{regionType}, #{name}, #{xMm}, #{yMm},
              #{widthMm}, #{heightMm}, #{blocking}, CAST(#{metadataJson} AS jsonb))
            """)
    int insertRegion(SurfaceRegionEntity value);

    @Select("SELECT * FROM surface_template WHERE id = #{id}")
    SurfaceTemplateEntity findTemplate(@Param("id") UUID id);

    @Select("""
            SELECT * FROM surface_template WHERE project_id = #{projectId}
            ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<SurfaceTemplateEntity> listTemplates(@Param("projectId") UUID projectId,
            @Param("limit") int limit, @Param("offset") long offset);

    @Select("""
            SELECT template_id, region_index, region_type, name, x_mm, y_mm, width_mm,
              height_mm, blocking, metadata_json::text AS metadata_json
            FROM surface_region WHERE template_id = #{templateId} ORDER BY region_index
            """)
    List<SurfaceRegionEntity> listRegions(@Param("templateId") UUID templateId);

    @Select("""
            SELECT slot_index, photo_id, x, y, width, height, crop_x, crop_y, crop_width, crop_height
            FROM layout_version_assignment WHERE version_id = #{versionId} ORDER BY slot_index
            """)
    List<SurfaceVersionSlotEntity> listVersionSlots(@Param("versionId") UUID versionId);

    @Insert("""
            INSERT INTO surface_layout_binding (id, project_id, layout_id, layout_version_id,
              template_id, origin_x_mm, origin_y_mm, print_width_mm, print_height_mm,
              scale_x_mm_per_px, scale_y_mm_per_px, validation_status, violation_count,
              violations_json, coordinate_manifest_json, manifest_hash, validated_by,
              validated_at, created_at, updated_at)
            VALUES (#{id}, #{projectId}, #{layoutId}, #{layoutVersionId}, #{templateId},
              #{originXmm}, #{originYmm}, #{printWidthMm}, #{printHeightMm}, #{scaleXmmPerPx},
              #{scaleYmmPerPx}, #{validationStatus}, #{violationCount}, CAST(#{violationsJson} AS jsonb),
              CAST(#{coordinateManifestJson} AS jsonb), #{manifestHash}, #{validatedBy},
              #{validatedAt}, #{createdAt}, #{updatedAt})
            ON CONFLICT (layout_id) DO UPDATE SET layout_version_id = EXCLUDED.layout_version_id,
              template_id = EXCLUDED.template_id, origin_x_mm = EXCLUDED.origin_x_mm,
              origin_y_mm = EXCLUDED.origin_y_mm, print_width_mm = EXCLUDED.print_width_mm,
              print_height_mm = EXCLUDED.print_height_mm,
              scale_x_mm_per_px = EXCLUDED.scale_x_mm_per_px,
              scale_y_mm_per_px = EXCLUDED.scale_y_mm_per_px,
              validation_status = EXCLUDED.validation_status,
              violation_count = EXCLUDED.violation_count,
              violations_json = EXCLUDED.violations_json,
              coordinate_manifest_json = EXCLUDED.coordinate_manifest_json,
              manifest_hash = EXCLUDED.manifest_hash, validated_by = EXCLUDED.validated_by,
              validated_at = EXCLUDED.validated_at, updated_at = EXCLUDED.updated_at
            """)
    int upsertBinding(SurfaceBindingEntity value);

    @Select("""
            SELECT id, project_id, layout_id, layout_version_id, template_id, origin_x_mm,
              origin_y_mm, print_width_mm, print_height_mm, scale_x_mm_per_px,
              scale_y_mm_per_px, validation_status, violation_count,
              violations_json::text AS violations_json,
              coordinate_manifest_json::text AS coordinate_manifest_json, manifest_hash,
              validated_by, validated_at, created_at, updated_at
            FROM surface_layout_binding WHERE layout_id = #{layoutId}
            """)
    SurfaceBindingEntity findBinding(@Param("layoutId") UUID layoutId);

    @Select("""
            SELECT p.id AS preview_id, p.status, p.object_key, p.width, p.height
            FROM layout_version target
            JOIN layout_version candidate ON candidate.layout_id = target.layout_id
              AND candidate.content_hash = target.content_hash
            JOIN layout_preview p ON p.version_id = candidate.id
            WHERE target.id = #{versionId}
            ORDER BY (candidate.id = target.id) DESC, p.created_at DESC LIMIT 1
            """)
    SurfacePreviewAssetEntity findPreviewAsset(@Param("versionId") UUID versionId);
}
