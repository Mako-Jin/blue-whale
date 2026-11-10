package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmProjectMember;
import com.bluewhale.pm.infrastructure.mapper.PmProjectMemberMapper;
import com.bluewhale.pm.interfaces.dto.ProjectMemberSaveRequest;
import com.bluewhale.pm.interfaces.dto.ProjectMemberUpdateRequest;
import com.bluewhale.pm.interfaces.vo.ProjectMemberVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectMemberAppService} 项目成员用例单元测试。
 *
 * @author BlueWhale
 */
class ProjectMemberAppServiceTest {

    private final PmProjectMemberMapper memberMapper = mock(PmProjectMemberMapper.class);

    private final ProjectGuard projectGuard = mock(ProjectGuard.class);

    private final ProjectMemberAppService memberAppService = new ProjectMemberAppService(memberMapper, projectGuard);

    @Test
    @DisplayName("add：新增成员默认角色来自请求")
    void shouldAddMember() {
        when(projectGuard.requireActiveProject(1L)).thenReturn(project(1L, 5L));
        when(memberMapper.selectCount(ArgumentMatchers.<Wrapper<PmProjectMember>>any())).thenReturn(0L);

        ProjectMemberVO vo = memberAppService.add(1L, saveRequest(6L, "MEMBER"));

        assertEquals("MEMBER", vo.getProjectRole());
        ArgumentCaptor<PmProjectMember> captor = ArgumentCaptor.forClass(PmProjectMember.class);
        verify(memberMapper).insert(captor.capture());
        assertEquals(6L, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("add：重复成员拒绝")
    void shouldRejectDuplicatedMember() {
        when(projectGuard.requireActiveProject(1L)).thenReturn(project(1L, 5L));
        when(memberMapper.selectCount(ArgumentMatchers.<Wrapper<PmProjectMember>>any())).thenReturn(1L);

        assertThrows(BizException.class, () -> memberAppService.add(1L, saveRequest(6L, "MEMBER")));

        verify(memberMapper, never()).insert(any(PmProjectMember.class));
    }

    @Test
    @DisplayName("add：归档项目拒绝新增成员")
    void shouldRejectAddOnArchivedProject() {
        when(projectGuard.requireActiveProject(1L))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR, "项目已归档"));

        assertThrows(BizException.class, () -> memberAppService.add(1L, saveRequest(6L, "MEMBER")));
    }

    @Test
    @DisplayName("updateRole：项目负责人不可降级")
    void shouldRejectLeaderDemotion() {
        when(memberMapper.selectById(11L)).thenReturn(member(11L, 1L, 5L, "ADMIN"));
        when(projectGuard.requireActiveProject(1L)).thenReturn(project(1L, 5L));

        assertThrows(BizException.class, () -> memberAppService.updateRole(11L, updateRequest("MEMBER")));

        verify(memberMapper, never()).updateById(any(PmProjectMember.class));
    }

    @Test
    @DisplayName("updateRole：最后一名管理员不可降级")
    void shouldRejectLastAdminDemotion() {
        when(memberMapper.selectById(12L)).thenReturn(member(12L, 1L, 6L, "ADMIN"));
        when(projectGuard.requireActiveProject(1L)).thenReturn(project(1L, 5L));
        when(memberMapper.selectCount(ArgumentMatchers.<Wrapper<PmProjectMember>>any())).thenReturn(1L);

        assertThrows(BizException.class, () -> memberAppService.updateRole(12L, updateRequest("VIEWER")));
    }

    @Test
    @DisplayName("updateRole：普通成员可降级为只读")
    void shouldUpdateRole() {
        PmProjectMember member = member(13L, 1L, 7L, "MEMBER");
        when(memberMapper.selectById(13L)).thenReturn(member);
        when(projectGuard.requireActiveProject(1L)).thenReturn(project(1L, 5L));

        ProjectMemberVO vo = memberAppService.updateRole(13L, updateRequest("VIEWER"));

        assertEquals("VIEWER", vo.getProjectRole());
        verify(memberMapper).updateById(member);
    }

    @Test
    @DisplayName("remove：项目负责人不可移除")
    void shouldRejectRemoveLeader() {
        when(memberMapper.selectById(14L)).thenReturn(member(14L, 1L, 5L, "ADMIN"));
        when(projectGuard.requireActiveProject(1L)).thenReturn(project(1L, 5L));

        assertThrows(BizException.class, () -> memberAppService.remove(14L));
    }

    @Test
    @DisplayName("remove：最后一名管理员不可移除")
    void shouldRejectRemoveLastAdmin() {
        when(memberMapper.selectById(15L)).thenReturn(member(15L, 1L, 6L, "ADMIN"));
        when(projectGuard.requireActiveProject(1L)).thenReturn(project(1L, 5L));
        when(memberMapper.selectCount(ArgumentMatchers.<Wrapper<PmProjectMember>>any())).thenReturn(1L);

        assertThrows(BizException.class, () -> memberAppService.remove(15L));
    }

    @Test
    @DisplayName("remove：普通成员可移除")
    void shouldRemoveMember() {
        when(memberMapper.selectById(16L)).thenReturn(member(16L, 1L, 7L, "VIEWER"));
        when(projectGuard.requireActiveProject(1L)).thenReturn(project(1L, 5L));

        memberAppService.remove(16L);

        verify(memberMapper).deleteById(16L);
    }

    /**
     * 构造项目实体
     *
     * @param id         项目ID
     * @param leadUserId 负责人ID
     * @return 项目实体
     */
    private PmProject project(Long id, Long leadUserId) {
        PmProject project = new PmProject();
        project.setId(id);
        project.setLeadUserId(leadUserId);
        project.setStatus(ProjectGuard.STATUS_ACTIVE);
        return project;
    }

    /**
     * 构造成员实体
     *
     * @param id        成员记录ID
     * @param projectId 项目ID
     * @param userId    用户ID
     * @param role      项目角色
     * @return 成员实体
     */
    private PmProjectMember member(Long id, Long projectId, Long userId, String role) {
        PmProjectMember member = new PmProjectMember();
        member.setId(id);
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setProjectRole(role);
        return member;
    }

    /**
     * 构造成员新增请求
     *
     * @param userId 用户ID
     * @param role   项目角色
     * @return 新增请求
     */
    private ProjectMemberSaveRequest saveRequest(Long userId, String role) {
        ProjectMemberSaveRequest request = new ProjectMemberSaveRequest();
        request.setUserId(userId);
        request.setProjectRole(role);
        return request;
    }

    /**
     * 构造角色变更请求
     *
     * @param role 项目角色
     * @return 变更请求
     */
    private ProjectMemberUpdateRequest updateRequest(String role) {
        ProjectMemberUpdateRequest request = new ProjectMemberUpdateRequest();
        request.setProjectRole(role);
        return request;
    }
}
