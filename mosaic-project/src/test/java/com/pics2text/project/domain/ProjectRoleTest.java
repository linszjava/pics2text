package com.pics2text.project.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProjectRoleTest {

    @Test
    void projectAdministratorCanManageMembers() {
        assertTrue(ProjectRole.PROJECT_ADMIN.allows(ProjectPermission.MANAGE_MEMBERS));
    }

    @Test
    void reviewerCanReviewButCannotEditLayout() {
        assertTrue(ProjectRole.REVIEWER.allows(ProjectPermission.REVIEW_PHOTOS));
        assertFalse(ProjectRole.REVIEWER.allows(ProjectPermission.EDIT_LAYOUT));
    }

    @Test
    void viewerCanOnlyView() {
        assertTrue(ProjectRole.VIEWER.allows(ProjectPermission.VIEW));
        assertFalse(ProjectRole.VIEWER.allows(ProjectPermission.REVIEW_PHOTOS));
        assertFalse(ProjectRole.VIEWER.allows(ProjectPermission.EDIT_LAYOUT));
        assertFalse(ProjectRole.VIEWER.allows(ProjectPermission.MANAGE_MEMBERS));
    }

    @Test
    void reviewerCanUploadAndManagePhotosButCannotManageProject() {
        assertTrue(ProjectRole.REVIEWER.allows(ProjectPermission.UPLOAD_PHOTOS));
        assertTrue(ProjectRole.REVIEWER.allows(ProjectPermission.MANAGE_PHOTOS));
        assertFalse(ProjectRole.REVIEWER.allows(ProjectPermission.MANAGE_PROJECT));
    }
}
