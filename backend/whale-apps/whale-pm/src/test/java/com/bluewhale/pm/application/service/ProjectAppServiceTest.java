package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmProjectMember;
import com.bluewhale.pm.infrastructure.mapper.PmProjectMapper;
import com.bluewhale.pm.infrastructure.mapper.PmProjectMemberMapper;
import com.bluewhale.pm.infrastructure.mapper.PmStatusMapper;
import com.bluewhale.pm.infrastructure.mapper.PmTransitionMapper;
import com.bluewhale.pm.interfaces.dto.ProjectQueryRequest;
import com.bluewhale.pm.interfaces.dto.ProjectSaveRequest;
import com.bluewhale.pm.interfaces.dto.ProjectStatusChangeRequest;
import com.bluewhale.pm.interfaces.vo.ProjectVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectAppService} 项目空间用例单元测试。
 *
 * @author BlueWhale
 */
class ProjectAppServiceTest {

    private final PmProjectMapper projectMapper = mock(PmProjectMapper.class);

    private final PmProjectMemberMapper memberMapper = mock(PmProjectMemberMapper.class);

    private final PmStatusMapper statusMapper = mock(PmStatusMapper.class);

    private final PmTransitionMapper transitionMapper = mock(PmTransitionMapper.class);

    private final ProjectGuard projectGuard = mock(ProjectGuard.class);

    private final ProjectWorkflowAppService workflowAppService = mock(ProjectWorkflowAppService.class);

    private final ProjectAppService projectAppService = new ProjectAppService(projectMapper, memberMapper,
            statusMapper, transitionMapper, projectGuard, workflowAppService);

    @Test
    @DisplayName("create：键归一化、初始化默认配置并登记负责人为管理员")
    void shouldCreateProjectWithDefaults() {
        when(projectMapper.selectCount(ArgumentMatchers.<Wrapper<PmProject>>any())).thenReturn(0L);
        when(projectMapper.insert(any(PmProject.class))).thenAnswer(invocation -> {
            PmProject saved = invocation.getArgument(0);
            saved.setId(100L);
            return 1;
        });
        when(memberMapper.selectOne(ArgumentMatchers.<Wrapper<PmProjectMember>>any())).thenReturn(null);

        ProjectVO vo = projectAppService.create(request("bw", "研发项目", 7L));

        assertEquals("BW", vo.getKey());
        assertEquals("ACTIVE", vo.getStatus());
        verify(workflowAppService).initializeDefaults(100L);
        ArgumentCaptor<PmProjectMember> captor = ArgumentCaptor.forClass(PmProjectMember.class);
        verify(memberMapper).insert(captor.capture());
        assertEquals("ADMIN", captor.getValue().getProjectRole());
        assertEquals(7L, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("create：项目键重复时拒绝且不落库")
    void shouldRejectDuplicatedKey() {
        when(projectMapper.selectCount(ArgumentMatchers.<Wrapper<PmProject>>any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> projectAppService.create(request("BW", "研发项目", null)));

        assertEquals(400, exception.getCode());
        verify(projectMapper, never()).insert(any(PmProject.class));
    }

    @Test
    @DisplayName("create：非法项目键在落库前拒绝")
    void shouldRejectIllegalKeyBeforeInsert() {
        assertThrows(BizException.class, () -> projectAppService.create(request("b", "研发项目", null)));

        verify(projectMapper, never()).insert(any(PmProject.class));
    }

    @Test
    @DisplayName("page：返回分页记录并保留总数")
    void shouldPageProjects() {
        PmProject project = project(1L, "BW", "研发项目");
        Page<PmProject> page = new Page<>(1, 10);
        page.setRecords(List.of(project));
        page.setTotal(1L);
        when(projectMapper.selectPage(ArgumentMatchers.<Page<PmProject>>any(),
                ArgumentMatchers.<Wrapper<PmProject>>any())).thenReturn(page);

        PageResult<ProjectVO> result = projectAppService.page(new ProjectQueryRequest());

        assertEquals(1L, result.getTotal());
        assertEquals("BW", result.getRecords().get(0).getKey());
    }

    @Test
    @DisplayName("getDetail：返回项目详情且 ID 为字符串")
    void shouldReturnDetail() {
        when(projectGuard.requireProject(1L)).thenReturn(project(1L, "BW", "研发项目"));

        ProjectVO vo = projectAppService.getDetail(1L);

        assertEquals("1", vo.getId());
        assertEquals("研发项目", vo.getName());
    }

    @Test
    @DisplayName("getDetail：项目不存在时透传 404")
    void shouldPropagateNotFound() {
        when(projectGuard.requireProject(9L))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND, "项目不存在"));

        BizException exception = assertThrows(BizException.class, () -> projectAppService.getDetail(9L));

        assertEquals(404, exception.getCode());
    }

    @Test
    @DisplayName("update：归档项目拒绝更新")
    void shouldRejectUpdateOnArchivedProject() {
        when(projectGuard.requireActiveProject(1L))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR, "项目已归档"));

        assertThrows(BizException.class, () -> projectAppService.update(1L, request("BW", "研发项目", null)));

        verify(projectMapper, never()).updateById(any(PmProject.class));
    }

    @Test
    @DisplayName("update：更新名称并校验键唯一性（排除自身）")
    void shouldUpdateProject() {
        PmProject project = project(1L, "BW", "旧名称");
        when(projectGuard.requireActiveProject(1L)).thenReturn(project);
        when(projectMapper.selectCount(ArgumentMatchers.<Wrapper<PmProject>>any())).thenReturn(0L);

        ProjectVO vo = projectAppService.update(1L, request("bw2", "新名称", null));

        assertEquals("BW2", vo.getKey());
        assertEquals("新名称", vo.getName());
        verify(projectMapper).updateById(project);
    }

    @Test
    @DisplayName("changeStatus：归档项目")
    void shouldArchiveProject() {
        PmProject project = project(1L, "BW", "研发项目");
        when(projectGuard.requireProject(1L)).thenReturn(project);
        ProjectStatusChangeRequest request = new ProjectStatusChangeRequest();
        request.setStatus("ARCHIVED");

        ProjectVO vo = projectAppService.changeStatus(1L, request);

        assertEquals("ARCHIVED", vo.getStatus());
        verify(projectMapper).updateById(project);
    }

    @Test
    @DisplayName("delete：级联逻辑删除成员、流转、状态与项目")
    void shouldDeleteProjectCascade() {
        when(projectGuard.requireProject(1L)).thenReturn(project(1L, "BW", "研发项目"));

        projectAppService.delete(1L);

        verify(memberMapper).delete(ArgumentMatchers.<Wrapper<PmProjectMember>>any());
        verify(transitionMapper).delete(ArgumentMatchers.any());
        verify(statusMapper).delete(ArgumentMatchers.any());
        verify(projectMapper).deleteById(1L);
    }

    /**
     * 构造项目实体
     *
     * @param id   ID
     * @param key  项目键
     * @param name 项目名称
     * @return 项目实体
     */
    private PmProject project(Long id, String key, String name) {
        PmProject project = new PmProject();
        project.setId(id);
        project.setProjectKey(key);
        project.setName(name);
        project.setProjectType("SCRUM");
        project.setVisibility("PRIVATE");
        project.setStatus(ProjectGuard.STATUS_ACTIVE);
        return project;
    }

    /**
     * 构造项目请求
     *
     * @param key        项目键
     * @param name       项目名称
     * @param leadUserId 负责人ID
     * @return 项目请求
     */
    private ProjectSaveRequest request(String key, String name, Long leadUserId) {
        ProjectSaveRequest request = new ProjectSaveRequest();
        request.setKey(key);
        request.setName(name);
        request.setProjectType("SCRUM");
        request.setVisibility("PRIVATE");
        request.setLeadUserId(leadUserId);
        return request;
    }
}
