package com.bluewhale.pm.application.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.mapper.PmProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectGuard} 项目存在性与归档守卫单元测试。
 *
 * @author BlueWhale
 */
class ProjectGuardTest {

    private final PmProjectMapper projectMapper = mock(PmProjectMapper.class);

    private final ProjectGuard guard = new ProjectGuard(projectMapper);

    @Test
    @DisplayName("requireProject：不存在时抛 404")
    void shouldThrowNotFoundWhenProjectMissing() {
        when(projectMapper.selectById(9L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () -> guard.requireProject(9L));

        assertEquals(404, exception.getCode());
    }

    @Test
    @DisplayName("requireActiveProject：归档项目拒绝变更")
    void shouldRejectArchivedProject() {
        PmProject project = new PmProject();
        project.setId(1L);
        project.setStatus(ProjectGuard.STATUS_ARCHIVED);
        when(projectMapper.selectById(1L)).thenReturn(project);

        BizException exception = assertThrows(BizException.class, () -> guard.requireActiveProject(1L));

        assertEquals(400, exception.getCode());
    }
}
