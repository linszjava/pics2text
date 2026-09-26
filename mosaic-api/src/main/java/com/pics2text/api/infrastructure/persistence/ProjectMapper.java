package com.pics2text.api.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectMapper extends BaseMapper<ProjectEntity> {
    @Select("""
            SELECT p.* FROM mosaic_project p
            WHERE #{administrator} OR EXISTS (
              SELECT 1 FROM project_member pm WHERE pm.project_id = p.id AND pm.user_id = #{userId}
            )
            ORDER BY p.created_at DESC
            """)
    List<ProjectEntity> listVisible(@Param("userId") UUID userId, @Param("administrator") boolean administrator);
}
