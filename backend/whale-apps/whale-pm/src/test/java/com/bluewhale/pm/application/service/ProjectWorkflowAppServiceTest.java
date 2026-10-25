package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmStatus;
import com.bluewhale.pm.infrastructure.entity.PmTransition;
import com.bluewhale.pm.infrastructure.mapper.PmStatusMapper;
import com.bluewhale.pm.infrastructure.mapper.PmTransitionMapper;
import com.bluewhale.pm.interfaces.dto.StatusSaveRequest;
import com.bluewhale.pm.interfaces.dto.TransitionSaveRequest;
import com.bluewhale.pm.interfaces.vo.StatusVO;
import com.bluewhale.pm.interfaces.vo.TransitionVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectWorkflowAppService} 状态与流转配置用例单元测试。
 *
 * @author BlueWhale
 */
class ProjectWorkflowAppServiceTest {

    private final ProjectGuard projectGuard = mock(ProjectGuard.class);

    private final PmStatusMapper statusMapper = mock(PmStatusMapper.class);

    private final PmTransitionMapper transitionMapper = mock(PmTransitionMapper.class);

    private final ProjectWorkflowAppService workflowAppService = new ProjectWorkflowAppService(projectGuard,
            statusMapper, transitionMapper);

    @Test
    @DisplayName("initializeDefaults：复制 3 个默认状态与 4 条流转边")
    void shouldInitializeDefaultWorkflow() {
        when(statusMapper.selectCount(ArgumentMatchers.<Wrapper<PmStatus>>any())).thenReturn(0L);
        AtomicLong sequence = new AtomicLong(100);
        when(statusMapper.insert(any(PmStatus.class))).thenAnswer(invocation -> {
            PmStatus status = invocation.getArgument(0);
            status.setId(sequence.incrementAndGet());
            return 1;
        });

        workflowAppService.initializeDefaults(1L);

        verify(statusMapper, times(3)).insert(any(PmStatus.class));
        ArgumentCaptor<PmTransition> captor = ArgumentCaptor.forClass(PmTransition.class);
        verify(transitionMapper, times(4)).insert(captor.capture());
        List<PmTransition> transitions = captor.getAllValues();
        assertEquals(101L, transitions.get(0).getFromStatusId());
        assertEquals(102L, transitions.get(0).getToStatusId());
        assertEquals(103L, transitions.get(1).getToStatusId());
    }

    @Test
    @DisplayName("initializeDefaults：已存在配置时跳过")
    void shouldSkipInitializationWhenConfigured() {
        when(statusMapper.selectCount(ArgumentMatchers.<Wrapper<PmStatus>>any())).thenReturn(3L);

        workflowAppService.initializeDefaults(1L);

        verify(statusMapper, never()).insert(any(PmStatus.class));
    }

    @Test
    @DisplayName("listStatuses：按排序号返回")
    void shouldListStatuses() {
        when(projectGuard.requireProject(1L)).thenReturn(project(1L));
        when(statusMapper.selectList(ArgumentMatchers.<Wrapper<PmStatus>>any()))
                .thenReturn(List.of(status(101L, 1L, "TODO", 1, 1), status(102L, 1L, "IN_PROGRESS", 2, 0)));

        List<StatusVO> statuses = workflowAppService.listStatuses(1L);

        assertEquals(2, statuses.size());
        assertEquals("TODO", statuses.get(0).getCode());
        assertEquals(true, statuses.get(0).getIsDefault());
    }

    @Test
    @DisplayName("addStatus：指定默认状态时清除其他默认标记")
    void shouldTransferDefaultFlag() {
        when(projectGuard.requireActiveProject(1L)).thenReturn(project(1L));
        when(statusMapper.selectCount(ArgumentMatchers.<Wrapper<PmStatus>>any())).thenReturn(0L);
        PmStatus existing = status(101L, 1L, "TODO", 1, 1);
        when(statusMapper.selectList(ArgumentMatchers.<Wrapper<PmStatus>>any())).thenReturn(List.of(existing));

        StatusVO vo = workflowAppService.addStatus(1L, statusRequest("REVIEW", "TODO", true));

        assertEquals("REVIEW", vo.getCode());
        verify(statusMapper).updateById(existing);
        assertEquals(0, existing.getIsDefault());
    }

    @Test
    @DisplayName("addStatus：状态编码重复拒绝")
    void shouldRejectDuplicatedStatusCode() {
        when(projectGuard.requireActiveProject(1L)).thenReturn(project(1L));
        when(statusMapper.selectCount(ArgumentMatchers.<Wrapper<PmStatus>>any())).thenReturn(1L);

        assertThrows(BizException.class, () -> workflowAppService.addStatus(1L, statusRequest("TODO", "TODO", false)));

        verify(statusMapper, never()).insert(any(PmStatus.class));
    }

    @Test
    @DisplayName("deleteStatus：默认状态拒绝删除")
    void shouldRejectDeleteDefaultStatus() {
        when(statusMapper.selectById(101L)).thenReturn(status(101L, 1L, "TODO", 1, 1));
        when(projectGuard.requireActiveProject(1L)).thenReturn(project(1L));

        assertThrows(BizException.class, () -> workflowAppService.deleteStatus(101L));

        verify(statusMapper, never()).deleteById(any(PmStatus.class));
    }

    @Test
    @DisplayName("deleteStatus：仍被流转引用时拒绝删除")
    void shouldRejectDeleteReferencedStatus() {
        when(statusMapper.selectById(102L)).thenReturn(status(102L, 1L, "IN_PROGRESS", 2, 0));
        when(projectGuard.requireActiveProject(1L)).thenReturn(project(1L));
        when(transitionMapper.selectCount(ArgumentMatchers.<Wrapper<PmTransition>>any())).thenReturn(1L);

        assertThrows(BizException.class, () -> workflowAppService.deleteStatus(102L));
    }

    @Test
    @DisplayName("deleteStatus：无引用时逻辑删除")
    void shouldDeleteStatus() {
        when(statusMapper.selectById(102L)).thenReturn(status(102L, 1L, "IN_PROGRESS", 2, 0));
        when(projectGuard.requireActiveProject(1L)).thenReturn(project(1L));
        when(transitionMapper.selectCount(ArgumentMatchers.<Wrapper<PmTransition>>any())).thenReturn(0L);

        workflowAppService.deleteStatus(102L);

        verify(statusMapper).deleteById(102L);
    }

    @Test
    @DisplayName("addTransition：源与目标相同拒绝")
    void shouldRejectSelfLoopTransition() {
        when(projectGuard.requireActiveProject(1L)).thenReturn(project(1L));
        when(statusMapper.selectById(101L)).thenReturn(status(101L, 1L, "TODO", 1, 1));

        assertThrows(BizException.class, () -> workflowAppService.addTransition(1L,
                transitionRequest(101L, 101L)));

        verify(transitionMapper, never()).insert(any(PmTransition.class));
    }

    @Test
    @DisplayName("addTransition：状态不属于当前项目拒绝")
    void shouldRejectCrossProjectTransition() {
        when(projectGuard.requireActiveProject(1L)).thenReturn(project(1L));
        when(statusMapper.selectById(101L)).thenReturn(status(101L, 1L, "TODO", 1, 1));
        when(statusMapper.selectById(201L)).thenReturn(status(201L, 2L, "TODO", 1, 1));

        assertThrows(BizException.class, () -> workflowAppService.addTransition(1L,
                transitionRequest(101L, 201L)));
    }

    @Test
    @DisplayName("addTransition：合法流转落库")
    void shouldAddTransition() {
        when(projectGuard.requireActiveProject(1L)).thenReturn(project(1L));
        when(statusMapper.selectById(101L)).thenReturn(status(101L, 1L, "TODO", 1, 1));
        when(statusMapper.selectById(102L)).thenReturn(status(102L, 1L, "IN_PROGRESS", 2, 0));
        when(transitionMapper.selectCount(ArgumentMatchers.<Wrapper<PmTransition>>any())).thenReturn(0L);

        TransitionVO vo = workflowAppService.addTransition(1L, transitionRequest(101L, 102L));

        assertEquals("101", vo.getFromStatusId());
        assertEquals("102", vo.getToStatusId());
        ArgumentCaptor<PmTransition> captor = ArgumentCaptor.forClass(PmTransition.class);
        verify(transitionMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getRequireComment());
    }

    /**
     * 构造项目实体
     *
     * @param id 项目ID
     * @return 项目实体
     */
    private PmProject project(Long id) {
        PmProject project = new PmProject();
        project.setId(id);
        project.setStatus(ProjectGuard.STATUS_ACTIVE);
        return project;
    }

    /**
     * 构造状态实体
     *
     * @param id        状态ID
     * @param projectId 项目ID
     * @param code      状态编码
     * @param sortNo    排序号
     * @param isDefault 是否默认
     * @return 状态实体
     */
    private PmStatus status(Long id, Long projectId, String code, int sortNo, int isDefault) {
        PmStatus status = new PmStatus();
        status.setId(id);
        status.setProjectId(projectId);
        status.setCode(code);
        status.setName(code);
        status.setCategory("TODO");
        status.setSortNo(sortNo);
        status.setIsDefault(isDefault);
        return status;
    }

    /**
     * 构造状态请求
     *
     * @param code      状态编码
     * @param category  状态分类
     * @param isDefault 是否默认
     * @return 状态请求
     */
    private StatusSaveRequest statusRequest(String code, String category, boolean isDefault) {
        StatusSaveRequest request = new StatusSaveRequest();
        request.setCode(code);
        request.setName(code);
        request.setCategory(category);
        request.setIsDefault(isDefault);
        return request;
    }

    /**
     * 构造流转请求
     *
     * @param fromStatusId 源状态ID
     * @param toStatusId   目标状态ID
     * @return 流转请求
     */
    private TransitionSaveRequest transitionRequest(Long fromStatusId, Long toStatusId) {
        TransitionSaveRequest request = new TransitionSaveRequest();
        request.setFromStatusId(fromStatusId);
        request.setToStatusId(toStatusId);
        request.setName("流转");
        return request;
    }
}
