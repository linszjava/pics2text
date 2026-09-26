package com.pics2text.api.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProjectMemberMapper {

    @Select("""
            SELECT project_id, user_id, role, created_by, created_at
            FROM project_member
            WHERE project_id = #{projectId} AND user_id = #{userId}
            """)
    ProjectMemberEntity find(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

    @Select("""
            SELECT project_id, user_id, role, created_by, created_at
            FROM project_member
            WHERE project_id = #{projectId}
            ORDER BY created_at
            """)
    List<ProjectMemberEntity> listByProject(@Param("projectId") UUID projectId);

    @Select("""
            SELECT COUNT(*) FROM project_member
            WHERE project_id = #{projectId} AND role = 'PROJECT_ADMIN'
            """)
    long countAdministrators(@Param("projectId") UUID projectId);

    @Insert("""
            INSERT INTO project_member (project_id, user_id, role, created_by, created_at)
            VALUES (#{projectId}, #{userId}, #{role}, #{createdBy}, #{createdAt})
            """)
    int insert(ProjectMemberEntity entity);

    @Update("""
            UPDATE project_member SET role = #{role}
            WHERE project_id = #{projectId} AND user_id = #{userId}
            """)
    int updateRole(
            @Param("projectId") UUID projectId,
            @Param("userId") UUID userId,
            @Param("role") String role);

    @Delete("""
            DELETE FROM project_member
            WHERE project_id = #{projectId} AND user_id = #{userId}
            """)
    int deleteMembership(@Param("projectId") UUID projectId, @Param("userId") UUID userId);
}
