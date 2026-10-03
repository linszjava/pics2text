package com.pics2text.layout.infrastructure.persistence;

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
public interface LayoutEditorMapper {
    @Insert("""
            INSERT INTO editable_layout (id, project_id, source_auto_layout_id, mask_id, name,
              state, canvas_width, canvas_height, allow_photo_reuse, max_photo_reuse,
              current_revision, lock_version, current_version_number, created_by, created_at, updated_at)
            VALUES (#{id}, #{projectId}, #{sourceAutoLayoutId}, #{maskId}, #{name}, #{state},
              #{canvasWidth}, #{canvasHeight}, #{allowPhotoReuse}, #{maxPhotoReuse},
              #{currentRevision}, #{lockVersion}, #{currentVersionNumber}, #{createdBy}, #{createdAt}, #{updatedAt})
            """)
    int insertLayout(EditableLayoutEntity layout);

    @Select("SELECT * FROM editable_layout WHERE id = #{layoutId}")
    EditableLayoutEntity findLayout(@Param("layoutId") UUID layoutId);

    @Select("SELECT * FROM editable_layout WHERE source_auto_layout_id = #{sourceAutoLayoutId}")
    EditableLayoutEntity findBySource(@Param("sourceAutoLayoutId") UUID sourceAutoLayoutId);

    @Select("""
            SELECT * FROM editable_layout WHERE project_id = #{projectId}
            ORDER BY updated_at DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<EditableLayoutEntity> listLayouts(@Param("projectId") UUID projectId,
            @Param("limit") int limit, @Param("offset") long offset);

    @Update("""
            UPDATE editable_layout SET name = #{name}, lock_version = lock_version + 1,
              updated_at = #{now}
            WHERE id = #{layoutId} AND lock_version = #{expectedLockVersion}
            """)
    int renameLayout(@Param("layoutId") UUID layoutId, @Param("name") String name,
            @Param("expectedLockVersion") long expectedLockVersion, @Param("now") Instant now);

    @Select("SELECT COUNT(*) FROM surface_layout_binding WHERE layout_id = #{layoutId}")
    int countSurfaceBindings(@Param("layoutId") UUID layoutId);

    @Update("UPDATE editable_layout SET current_version_id = NULL, published_version_id = NULL WHERE id = #{layoutId}")
    int clearVersionReferences(@Param("layoutId") UUID layoutId);

    @Delete("DELETE FROM editable_layout WHERE id = #{layoutId}")
    int deleteLayout(@Param("layoutId") UUID layoutId);

    @Update("UPDATE mosaic_project SET active_layout_id = #{layoutId}, updated_at = #{now} WHERE id = #{projectId}")
    int activateLayout(@Param("projectId") UUID projectId, @Param("layoutId") UUID layoutId,
            @Param("now") Instant now);

    @Insert("""
            INSERT INTO layout_draft_assignment
              (layout_id, slot_index, photo_id, x, y, width, height, crop_x, crop_y, crop_width, crop_height)
            SELECT #{layoutId}, a.slot_index, a.photo_id, s.x, s.y, s.width, s.height,
              a.crop_x, a.crop_y, a.crop_width, a.crop_height
            FROM auto_layout_assignment a
            JOIN text_mask_slot s ON s.mask_id = #{maskId} AND s.slot_index = a.slot_index
            WHERE a.layout_id = #{sourceAutoLayoutId}
            ORDER BY a.slot_index
            """)
    int copyAutoAssignments(@Param("layoutId") UUID layoutId,
            @Param("sourceAutoLayoutId") UUID sourceAutoLayoutId, @Param("maskId") UUID maskId);

    @Select("""
            SELECT layout_id AS owner_id, slot_index, photo_id, x, y, width, height,
              crop_x, crop_y, crop_width, crop_height
            FROM layout_draft_assignment WHERE layout_id = #{layoutId} ORDER BY slot_index
            """)
    List<LayoutEditorAssignmentEntity> listAllDraft(@Param("layoutId") UUID layoutId);

    @Select("""
            SELECT layout_id AS owner_id, slot_index, photo_id, x, y, width, height,
              crop_x, crop_y, crop_width, crop_height
            FROM layout_draft_assignment WHERE layout_id = #{layoutId} AND slot_index = #{slotIndex}
            FOR UPDATE
            """)
    LayoutEditorAssignmentEntity findDraftForUpdate(@Param("layoutId") UUID layoutId,
            @Param("slotIndex") int slotIndex);

    @Select({"<script>",
            "SELECT layout_id AS owner_id, slot_index, photo_id, x, y, width, height,",
            " crop_x, crop_y, crop_width, crop_height FROM layout_draft_assignment",
            " WHERE layout_id = #{layoutId}",
            "<if test='photoId != null'> AND photo_id = #{photoId}</if>",
            "<if test='slotIndex != null'> AND slot_index = #{slotIndex}</if>",
            "<if test='minX != null'> AND x + width &gt; #{minX}</if>",
            "<if test='minY != null'> AND y + height &gt; #{minY}</if>",
            "<if test='maxX != null'> AND x &lt; #{maxX}</if>",
            "<if test='maxY != null'> AND y &lt; #{maxY}</if>",
            " ORDER BY slot_index LIMIT #{limit}",
            "</script>"})
    List<LayoutEditorAssignmentEntity> queryDraft(@Param("layoutId") UUID layoutId,
            @Param("photoId") UUID photoId, @Param("slotIndex") Integer slotIndex,
            @Param("minX") Integer minX, @Param("minY") Integer minY,
            @Param("maxX") Integer maxX, @Param("maxY") Integer maxY,
            @Param("limit") int limit);

    @Select({"<script>",
            "SELECT DISTINCT p.id AS photo_id, COALESCE(p.preview_key, p.source_key, p.original_key) AS preview_key FROM layout_draft_assignment a",
            " JOIN photo_asset p ON p.id = a.photo_id WHERE a.layout_id = #{layoutId}",
            " AND p.id IN",
            "<foreach collection='photoIds' item='photoId' open='(' separator=',' close=')'>#{photoId}</foreach>",
            " ORDER BY p.id",
            "</script>"})
    List<LayoutPhotoPreviewEntity> listPhotoPreviews(@Param("layoutId") UUID layoutId,
            @Param("photoIds") List<UUID> photoIds);

    @Update("""
            UPDATE layout_draft_assignment SET photo_id = #{photoId}, crop_x = #{cropX},
              crop_y = #{cropY}, crop_width = #{cropWidth}, crop_height = #{cropHeight}
            WHERE layout_id = #{layoutId} AND slot_index = #{slotIndex}
            """)
    int updateDraft(@Param("layoutId") UUID layoutId, @Param("slotIndex") int slotIndex,
            @Param("photoId") UUID photoId, @Param("cropX") double cropX,
            @Param("cropY") double cropY, @Param("cropWidth") double cropWidth,
            @Param("cropHeight") double cropHeight);

    @Select("SELECT COUNT(*) FROM layout_draft_assignment WHERE layout_id = #{layoutId} AND photo_id = #{photoId}")
    int countPhotoUsage(@Param("layoutId") UUID layoutId, @Param("photoId") UUID photoId);

    @Select("""
            SELECT pa.photo_id, pa.crop_x, pa.crop_y, pa.crop_width, pa.crop_height
            FROM photo_analysis pa JOIN photo_asset p ON p.id = pa.photo_id
            WHERE pa.photo_id = #{photoId} AND p.project_id = #{projectId}
              AND p.status = 'AVAILABLE' AND p.analysis_status = 'SUCCEEDED'
              AND p.review_status = 'APPROVED' AND p.authorization_status = 'GRANTED'
            """)
    LayoutReplacementPhotoEntity findEligibleReplacement(@Param("projectId") UUID projectId,
            @Param("photoId") UUID photoId);

    @Update("""
            UPDATE editable_layout SET current_revision = current_revision + #{commandCount},
              lock_version = lock_version + 1, updated_at = #{now}
            WHERE id = #{layoutId} AND state = 'DRAFT' AND lock_version = #{expectedLockVersion}
            """)
    int beginEdit(@Param("layoutId") UUID layoutId, @Param("expectedLockVersion") long expectedLockVersion,
            @Param("commandCount") int commandCount, @Param("now") Instant now);

    @Insert("""
            INSERT INTO layout_edit_operation (id, layout_id, revision, client_batch_id, command_index,
              command_type, slot_index, secondary_slot_index, before_json, after_json,
              base_lock_version, resulting_lock_version, actor_id, created_at)
            VALUES (#{id}, #{layoutId}, #{revision}, #{clientBatchId}, #{commandIndex},
              #{commandType}, #{slotIndex}, #{secondarySlotIndex}, CAST(#{beforeJson} AS jsonb),
              CAST(#{afterJson} AS jsonb), #{baseLockVersion}, #{resultingLockVersion},
              #{actorId}, #{createdAt})
            """)
    int insertOperation(LayoutEditOperationEntity operation);

    @Insert("""
            INSERT INTO layout_version (id, layout_id, version_number, parent_version_id, reason,
              client_batch_id, source_revision, content_hash, assignment_count, note, created_by, created_at)
            VALUES (#{id}, #{layoutId}, #{versionNumber}, #{parentVersionId}, #{reason},
              #{clientBatchId}, #{sourceRevision}, #{contentHash}, #{assignmentCount},
              #{note}, #{createdBy}, #{createdAt})
            """)
    int insertVersion(LayoutVersionEntity version);

    @Insert("""
            INSERT INTO layout_version_assignment
              (version_id, slot_index, photo_id, x, y, width, height, crop_x, crop_y, crop_width, crop_height)
            SELECT #{versionId}, slot_index, photo_id, x, y, width, height,
              crop_x, crop_y, crop_width, crop_height
            FROM layout_draft_assignment WHERE layout_id = #{layoutId} ORDER BY slot_index
            """)
    int snapshotVersion(@Param("layoutId") UUID layoutId, @Param("versionId") UUID versionId);

    @Update("""
            UPDATE editable_layout SET current_version_id = #{versionId},
              current_version_number = #{versionNumber}, updated_at = #{now} WHERE id = #{layoutId}
            """)
    int setCurrentVersion(@Param("layoutId") UUID layoutId, @Param("versionId") UUID versionId,
            @Param("versionNumber") int versionNumber, @Param("now") Instant now);

    @Select("SELECT * FROM layout_version WHERE layout_id = #{layoutId} AND client_batch_id = #{clientBatchId}")
    LayoutVersionEntity findVersionByBatch(@Param("layoutId") UUID layoutId,
            @Param("clientBatchId") UUID clientBatchId);

    @Select("SELECT * FROM layout_version WHERE id = #{versionId}")
    LayoutVersionEntity findVersion(@Param("versionId") UUID versionId);

    @Select("""
            SELECT * FROM layout_version WHERE layout_id = #{layoutId}
            ORDER BY version_number DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<LayoutVersionEntity> listVersions(@Param("layoutId") UUID layoutId,
            @Param("limit") int limit, @Param("offset") long offset);

    @Select("""
            SELECT id, layout_id, revision, client_batch_id, command_index, command_type,
              slot_index, secondary_slot_index, before_json::text AS before_json,
              after_json::text AS after_json, base_lock_version, resulting_lock_version,
              actor_id, created_at FROM layout_edit_operation WHERE layout_id = #{layoutId}
            ORDER BY revision DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<LayoutEditOperationEntity> listOperations(@Param("layoutId") UUID layoutId,
            @Param("limit") int limit, @Param("offset") long offset);

    @Delete("DELETE FROM layout_draft_assignment WHERE layout_id = #{layoutId}")
    int deleteDraft(@Param("layoutId") UUID layoutId);

    @Insert("""
            INSERT INTO layout_draft_assignment
              (layout_id, slot_index, photo_id, x, y, width, height, crop_x, crop_y, crop_width, crop_height)
            SELECT #{layoutId}, slot_index, photo_id, x, y, width, height,
              crop_x, crop_y, crop_width, crop_height
            FROM layout_version_assignment WHERE version_id = #{versionId} ORDER BY slot_index
            """)
    int restoreDraft(@Param("layoutId") UUID layoutId, @Param("versionId") UUID versionId);

    @Update("""
            UPDATE editable_layout SET state = 'LOCKED', lock_version = lock_version + 1,
              locked_by = #{actorId}, locked_at = #{now}, updated_at = #{now}
            WHERE id = #{layoutId} AND state = 'DRAFT' AND lock_version = #{expectedLockVersion}
            """)
    int lock(@Param("layoutId") UUID layoutId, @Param("expectedLockVersion") long expectedLockVersion,
            @Param("actorId") UUID actorId, @Param("now") Instant now);

    @Update("""
            UPDATE editable_layout SET state = 'DRAFT', lock_version = lock_version + 1,
              locked_by = NULL, locked_at = NULL, updated_at = #{now}
            WHERE id = #{layoutId} AND state = 'LOCKED' AND lock_version = #{expectedLockVersion}
            """)
    int unlock(@Param("layoutId") UUID layoutId, @Param("expectedLockVersion") long expectedLockVersion,
            @Param("now") Instant now);

    @Update("""
            UPDATE editable_layout SET state = 'PUBLISHED', lock_version = lock_version + 1,
              published_by = #{actorId}, published_at = #{now}, updated_at = #{now}
            WHERE id = #{layoutId} AND state = 'LOCKED' AND lock_version = #{expectedLockVersion}
            """)
    int publish(@Param("layoutId") UUID layoutId, @Param("expectedLockVersion") long expectedLockVersion,
            @Param("actorId") UUID actorId, @Param("now") Instant now);

    @Update("UPDATE editable_layout SET published_version_id = #{versionId} WHERE id = #{layoutId}")
    int setPublishedVersion(@Param("layoutId") UUID layoutId, @Param("versionId") UUID versionId);

    @Insert("""
            INSERT INTO layout_preview (id, layout_id, version_id, status, assignment_count,
              created_by, created_at, updated_at) VALUES (#{id}, #{layoutId}, #{versionId},
              #{status}, #{assignmentCount}, #{createdBy}, #{createdAt}, #{updatedAt})
            ON CONFLICT (version_id) DO NOTHING
            """)
    int insertPreview(LayoutPreviewEntity preview);

    @Select("SELECT * FROM layout_preview WHERE id = #{previewId}")
    LayoutPreviewEntity findPreview(@Param("previewId") UUID previewId);

    @Select("SELECT * FROM layout_preview WHERE version_id = #{versionId}")
    LayoutPreviewEntity findPreviewByVersion(@Param("versionId") UUID versionId);

    @Select("""
            SELECT p.id AS preview_id, p.layout_id, p.version_id, l.project_id,
              l.canvas_width, l.canvas_height, p.status
            FROM layout_preview p JOIN editable_layout l ON l.id = p.layout_id
            WHERE p.id = #{previewId}
            """)
    LayoutPreviewContextEntity findPreviewContext(@Param("previewId") UUID previewId);

    @Select("""
            SELECT a.slot_index, a.x, a.y, a.width, a.height, p.lab_l, p.lab_a, p.lab_b
            FROM layout_version_assignment a JOIN photo_analysis p ON p.photo_id = a.photo_id
            WHERE a.version_id = #{versionId} ORDER BY a.slot_index
            """)
    List<LayoutPreviewPixelEntity> listPreviewPixels(@Param("versionId") UUID versionId);

    @Update("""
            UPDATE layout_preview SET status = 'PROCESSING', error_code = NULL,
              error_message = NULL, updated_at = #{now}
            WHERE id = #{previewId} AND status IN ('PENDING', 'PROCESSING', 'FAILED')
            """)
    int markPreviewProcessing(@Param("previewId") UUID previewId, @Param("now") Instant now);

    @Update("""
            UPDATE layout_preview SET status = 'READY', object_key = #{objectKey}, width = #{width},
              height = #{height}, assignment_count = #{assignmentCount}, error_code = NULL,
              error_message = NULL, completed_at = #{now}, updated_at = #{now} WHERE id = #{previewId}
            """)
    int markPreviewReady(@Param("previewId") UUID previewId, @Param("objectKey") String objectKey,
            @Param("width") int width, @Param("height") int height,
            @Param("assignmentCount") int assignmentCount, @Param("now") Instant now);

    @Update("""
            UPDATE layout_preview SET status = 'FAILED', error_code = #{code},
              error_message = #{message}, updated_at = #{now} WHERE id = #{previewId}
            """)
    int markPreviewFailed(@Param("previewId") UUID previewId, @Param("code") String code,
            @Param("message") String message, @Param("now") Instant now);
}
