package com.pics2text.api.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectMapper extends BaseMapper<ProjectEntity> {
    @Select("SELECT * FROM mosaic_project WHERE created_by = #{creatorId} ORDER BY created_at DESC")
    List<ProjectEntity> listByCreator(@Param("creatorId") UUID creatorId);
}
