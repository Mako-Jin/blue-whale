package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmProjectMember;
import com.bluewhale.pm.infrastructure.mapper.PmProjectMemberMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectAccessGuard} 项目数据范围用例单元测试。
 *
 * @author BlueWhale
 */
class ProjectAccessGuardTest {

    private static final Long PROJECT_ID = 1L;

    private static final Long USER_ID = 9L;

    private final PmProjectMemberMapper memberMapper = mock(PmProjectMemberMapper.class);

    private final ProjectGuard projectGuard = mock(ProjectGuard.class);

    private final ProjectAccessGuard guard = new ProjectAccessGuard(memberMapper, projectGuard);

    @Test
    @DisplayName("requireReadable：缺少用户上下文返回未认证")
    void shouldRejectMissingUser() {
        BizException exception = assertThrows(BizException.class, () -> guard.requireReadable(PROJECT_ID, null));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("requireReadable：非项目成员返回无权限")
    void shouldRejectNonMember() {
        when(memberMapper.selectOne(ArgumentMatchers.<Wrapper<PmProjectMember>>any())).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () -> guard.requireReadable(PROJECT_ID, USER_ID));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("requireReadable：只读成员可读取")
    void shouldAllowViewerRead() {
        when(memberMapper.selectOne(ArgumentMatchers.<Wrapper<PmProjectMember>>any()))
                .thenReturn(member("VIEWER"));
        PmProject project = new PmProject();
        project.setId(PROJECT_ID);
        when(projectGuard.requireProject(PROJECT_ID)).thenReturn(project);

        assertEquals(PROJECT_ID, guard.requireReadable(PROJECT_ID, USER_ID).getId());
    }

    @Test
    @DisplayName("requireWritable：只读成员不可写入")
    void shouldRejectViewerWrite() {
        when(memberMapper.selectOne(ArgumentMatchers.<Wrapper<PmProjectMember>>any()))
                .thenReturn(member("VIEWER"));

        BizException exception = assertThrows(BizException.class, () -> guard.requireWritable(PROJECT_ID, USER_ID));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("requireAdmin：普通成员不可执行管理员操作")
    void shouldRejectMemberAdminOperation() {
        when(memberMapper.selectOne(ArgumentMatchers.<Wrapper<PmProjectMember>>any()))
                .thenReturn(member("MEMBER"));

        assertThrows(BizException.class, () -> guard.requireAdmin(PROJECT_ID, USER_ID));
    }

    /**
     * 构造项目成员实体
     *
     * @param role 项目角色
     * @return 项目成员实体
     */
    private PmProjectMember member(String role) {
        PmProjectMember member = new PmProjectMember();
        member.setId(101L);
        member.setProjectId(PROJECT_ID);
        member.setUserId(USER_ID);
        member.setProjectRole(role);
        return member;
    }
}
