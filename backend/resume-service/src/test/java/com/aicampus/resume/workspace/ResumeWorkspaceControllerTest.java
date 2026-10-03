package com.aicampus.resume.workspace;

import com.aicampus.resume.controller.ResumeWorkspaceController;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ResumeWorkspaceControllerTest {
    @Test void allWorkspaceReadsRequireStudentIdentity() {
        ResumeWorkspaceController controller = new ResumeWorkspaceController(mock(WorkspaceService.class));
        WorkspaceException missing = assertThrows(WorkspaceException.class, () -> controller.profile(null, null));
        assertEquals(401, missing.status().value());
        WorkspaceException wrongRole = assertThrows(WorkspaceException.class, () -> controller.profile("student-1", "ADMIN"));
        assertEquals(401, wrongRole.status().value());
    }
}
