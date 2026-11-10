package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.pm.domain.model.ActivityAction;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmSprint;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.mapper.PmSprintMapper;
import com.bluewhale.pm.infrastructure.mapper.PmStatusMapper;
import com.bluewhale.pm.infrastructure.mapper.PmWorkItemMapper;
import com.bluewhale.pm.interfaces.dto.SprintItemRequest;
import com.bluewhale.pm.interfaces.dto.SprintSaveRequest;
import com.bluewhale.pm.interfaces.vo.SprintVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SprintAppService} 迭代生命周期用例单元测试。
 *
 * @author BlueWhale
 */
class SprintAppServiceTest {

    private static final Long PROJECT_ID = 1L;

    private static final Long SPRINT_ID = 4001L;

    private static final Long OPERATOR_ID = 9L;

    private final PmSprintMapper sprintMapper = mock(PmSprintMapper.class);

    private final PmWorkItemMapper workItemMapper = mock(PmWorkItemMapper.class);

    private final PmStatusMapper statusMapper = mock(PmStatusMapper.class);

    private final ProjectGuard projectGuard = mock(ProjectGuard.class);

    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);

    private final WorkItemGuard workItemGuard = mock(WorkItemGuard.class);

    private final WorkItemActivityAppService activityAppService = mock(WorkItemActivityAppService.class);

    private final SprintAppService service = new SprintAppService(sprintMapper, workItemMapper, statusMapper,
            projectGuard, projectAccessGuard, workItemGuard, activityAppService);

    /**
     * 初始化 MyBatis-Plus 实体元数据（lambda 列名缓存）
     */
    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PmSprint.class);
        TableInfoHelper.initTableInfo(assistant, PmWorkItem.class);
    }

    @Test
    @DisplayName("create：迭代名称在项目内重复拒绝")
    void shouldRejectDuplicatedName() {
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(sprintMapper.selectCount(ArgumentMatchers.<Wrapper<PmSprint>>any())).thenReturn(1L);

        assertThrows(BizException.class, () -> service.create(saveRequest("Sprint 1"), OPERATOR_ID));

        verify(sprintMapper, never()).insert(any(PmSprint.class));
    }

    @Test
    @DisplayName("create：初始状态为未启动")
    void shouldCreateFutureSprint() {
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(sprintMapper.selectCount(ArgumentMatchers.<Wrapper<PmSprint>>any())).thenReturn(0L);
        when(sprintMapper.insert(any(PmSprint.class))).thenAnswer(invocation -> {
            PmSprint sprint = invocation.getArgument(0);
            sprint.setId(SPRINT_ID);
            return 1;
        });

        SprintVO vo = service.create(saveRequest(" Sprint 2 "), OPERATOR_ID);

        ArgumentCaptor<PmSprint> captor = ArgumentCaptor.forClass(PmSprint.class);
        verify(sprintMapper).insert(captor.capture());
        assertEquals("Sprint 2", captor.getValue().getName());
        assertEquals("FUTURE", captor.getValue().getStatus());
        assertEquals("4001", vo.getId());
    }

    @Test
    @DisplayName("create：结束日期早于开始日期拒绝")
    void shouldRejectReversedDateRange() {
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        SprintSaveRequest request = saveRequest("Sprint 3");
        request.setStartDate(LocalDate.of(2026, 12, 1));
        request.setEndDate(LocalDate.of(2026, 11, 1));

        assertThrows(BizException.class, () -> service.create(request, OPERATOR_ID));
    }

    @Test
    @DisplayName("update：仅未启动迭代可编辑")
    void shouldRejectUpdateOnActiveSprint() {
        PmSprint sprint = sprint("ACTIVE");
        when(sprintMapper.selectById(SPRINT_ID)).thenReturn(sprint);
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());

        assertThrows(BizException.class, () -> service.update(SPRINT_ID, saveRequest("Sprint 9"), OPERATOR_ID));
    }

    @Test
    @DisplayName("delete：迭代下仍有工作项拒绝")
    void shouldRejectDeleteWithItems() {
        PmSprint sprint = sprint("FUTURE");
        when(sprintMapper.selectById(SPRINT_ID)).thenReturn(sprint);
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(workItemMapper.selectCount(ArgumentMatchers.<Wrapper<PmWorkItem>>any())).thenReturn(1L);

        assertThrows(BizException.class, () -> service.delete(SPRINT_ID, OPERATOR_ID));

        verify(sprintMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("start：缺少起止日期拒绝")
    void shouldRejectStartWithoutDates() {
        PmSprint sprint = sprint("FUTURE");
        sprint.setStartDate(null);
        sprint.setEndDate(null);
        when(sprintMapper.selectById(SPRINT_ID)).thenReturn(sprint);
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());

        assertThrows(BizException.class, () -> service.start(SPRINT_ID, OPERATOR_ID));
    }

    @Test
    @DisplayName("start：同项目已有进行中迭代拒绝")
    void shouldRejectSecondActiveSprint() {
        PmSprint sprint = sprint("FUTURE");
        when(sprintMapper.selectById(SPRINT_ID)).thenReturn(sprint);
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(sprintMapper.selectCount(ArgumentMatchers.<Wrapper<PmSprint>>any())).thenReturn(1L);

        assertThrows(BizException.class, () -> service.start(SPRINT_ID, OPERATOR_ID));
    }

    @Test
    @DisplayName("start：未启动迭代成功启动")
    void shouldStartSprint() {
        PmSprint sprint = sprint("FUTURE");
        when(sprintMapper.selectById(SPRINT_ID)).thenReturn(sprint);
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(sprintMapper.selectCount(ArgumentMatchers.<Wrapper<PmSprint>>any())).thenReturn(0L);

        SprintVO vo = service.start(SPRINT_ID, OPERATOR_ID);

        assertEquals("ACTIVE", vo.getStatus());
        verify(sprintMapper).updateById(sprint);
    }

    @Test
    @DisplayName("close：非进行中迭代拒绝关闭")
    void shouldRejectCloseOnFutureSprint() {
        PmSprint sprint = sprint("FUTURE");
        when(sprintMapper.selectById(SPRINT_ID)).thenReturn(sprint);
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());

        assertThrows(BizException.class, () -> service.close(SPRINT_ID, OPERATOR_ID));
    }

    @Test
    @DisplayName("addItems：已关闭迭代拒绝加入")
    void shouldRejectAddItemsIntoClosedSprint() {
        when(sprintMapper.selectById(SPRINT_ID)).thenReturn(sprint("CLOSED"));
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());

        SprintItemRequest request = new SprintItemRequest();
        request.setItemIds(List.of(3001L));

        assertThrows(BizException.class, () -> service.addItems(SPRINT_ID, request, OPERATOR_ID));
    }

    @Test
    @DisplayName("addItems：进行中迭代加入工作项并记录动态")
    void shouldAddItems() {
        when(sprintMapper.selectById(SPRINT_ID)).thenReturn(sprint("ACTIVE"));
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        PmWorkItem item = new PmWorkItem();
        item.setId(3001L);
        item.setProjectId(PROJECT_ID);
        item.setVersion(0);
        when(workItemGuard.requireInProject(3001L, PROJECT_ID)).thenReturn(item);
        when(workItemMapper.update(ArgumentMatchers.<PmWorkItem>isNull(), ArgumentMatchers.any()))
                .thenReturn(1);

        SprintItemRequest request = new SprintItemRequest();
        request.setItemIds(List.of(3001L));

        assertEquals(1, service.addItems(SPRINT_ID, request, OPERATOR_ID));
        verify(activityAppService).record(eq(PROJECT_ID), eq(3001L), eq(OPERATOR_ID),
                eq(ActivityAction.UPDATE), eq("sprint"), ArgumentMatchers.<String>isNull(), eq("Sprint 1"));
    }

    @Test
    @DisplayName("removeItem：工作项不属于当前迭代拒绝")
    void shouldRejectRemoveForeignItem() {
        when(sprintMapper.selectById(SPRINT_ID)).thenReturn(sprint("ACTIVE"));
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        PmWorkItem item = new PmWorkItem();
        item.setId(3001L);
        item.setProjectId(PROJECT_ID);
        item.setSprintId(9999L);
        when(workItemGuard.requireInProject(3001L, PROJECT_ID)).thenReturn(item);

        assertThrows(BizException.class, () -> service.removeItem(SPRINT_ID, 3001L, OPERATOR_ID));
    }

    /**
     * 构造项目实体
     *
     * @return 项目实体
     */
    private PmProject project() {
        PmProject project = new PmProject();
        project.setId(PROJECT_ID);
        project.setStatus(ProjectGuard.STATUS_ACTIVE);
        return project;
    }

    /**
     * 构造迭代实体
     *
     * @param status 状态
     * @return 迭代实体
     */
    private PmSprint sprint(String status) {
        PmSprint sprint = new PmSprint();
        sprint.setId(SPRINT_ID);
        sprint.setProjectId(PROJECT_ID);
        sprint.setName("Sprint 1");
        sprint.setStatus(status);
        sprint.setStartDate(LocalDate.of(2026, 11, 1));
        sprint.setEndDate(LocalDate.of(2026, 11, 15));
        return sprint;
    }

    /**
     * 构造迭代保存请求
     *
     * @param name 迭代名称
     * @return 保存请求
     */
    private SprintSaveRequest saveRequest(String name) {
        SprintSaveRequest request = new SprintSaveRequest();
        request.setProjectId(PROJECT_ID);
        request.setName(name);
        request.setStartDate(LocalDate.of(2026, 11, 1));
        request.setEndDate(LocalDate.of(2026, 11, 15));
        return request;
    }
}
