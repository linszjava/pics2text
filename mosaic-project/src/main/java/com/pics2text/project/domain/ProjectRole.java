package com.pics2text.project.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ProjectRole {
    PROJECT_ADMIN(EnumSet.allOf(ProjectPermission.class)),
    REVIEWER(EnumSet.of(ProjectPermission.VIEW, ProjectPermission.UPLOAD_PHOTOS,
            ProjectPermission.MANAGE_PHOTOS, ProjectPermission.REVIEW_PHOTOS)),
    DESIGNER(EnumSet.of(ProjectPermission.VIEW, ProjectPermission.UPLOAD_PHOTOS,
            ProjectPermission.MANAGE_PHOTOS, ProjectPermission.EDIT_LAYOUT)),
    VIEWER(EnumSet.of(ProjectPermission.VIEW));

    private final Set<ProjectPermission> permissions;

    ProjectRole(Set<ProjectPermission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public boolean allows(ProjectPermission permission) {
        return permissions.contains(permission);
    }

    public Set<ProjectPermission> permissions() {
        return permissions;
    }
}
