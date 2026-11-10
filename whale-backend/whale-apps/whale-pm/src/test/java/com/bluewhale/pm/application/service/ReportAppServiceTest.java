package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.cache.CacheConsistencyTemplate;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.pm.infrastructure.entity.PmActivity;
import com.bluewhale.pm.infrastructure.entity.PmSprint;
import com.bluewhale.pm.infrastructure.entity.PmStatus;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.mapper.PmActivityMapper;
import com.bluewhale.pm.infrastructure.mapper.PmSprintMapper;
import com.bluewhale.pm.infrastructure.mapper.PmStatusMapper;
import com.bluewhale.pm.infrastructure.mapper.PmWorkItemMapper;
import com.bluewhale.pm.interfaces.vo.BurndownVO;
import com.bluewhale.pm.interfaces.vo.CumulativeFlowVO;
import com.bluewhale.pm.interfaces.vo.VelocityVO;
import com.bluewhale.pm.interfaces.vo.WorkloadMemberVO;
import com.bluewhale.pm.interfaces.vo.WorkloadVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReportAppService} 报表计算与区间护栏单元测试。
 *
 * @author BlueWhale
 */
class ReportAppServiceTest {

    private static final Long PROJECT_ID = 1L;

    private static final Long SPRINT_ID = 4001L;

    private static final Long TODO_STATUS_ID = 1001L;

    private static final Long DONE_STATUS_ID = 1003L;

    private static final Long OPERATOR_ID = 9L;

    private final PmWorkItemMapper workItemMapper = mock(PmWorkItemMapper.class);

    private final PmStatusMapper statusMapper = mock(PmStatusMapper.class);

    private final PmSprintMapper sprintMapper = mock(PmSprintMapper.class);

    private final PmActivityMapper activityMapper = mock(PmActivityMapper.class);

    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);

    private final CacheConsistencyTemplate cacheTemplate = mock(CacheConsistencyTemplate.class);

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final ReportAppService service = new ReportAppService(workItemMapper, statusMapper, sprintMapper,
            activityMapper, projectAccessGuard, cacheTemplate, objectMapper);

    /**
     * 初始化 MyBatis-Plus 实体元数据（lambda 列名缓存）
     */
    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PmWorkItem.class);
        TableInfoHelper.initTableInfo(assistant, PmStatus.class);
        TableInfoHelper.initTableInfo(assistant, PmSprint.class);
        TableInfoHelper.initTableInfo(assistant, PmActivity.class);
    }

    @Test
    @DisplayName("burndown：无进行中迭代返回空序列")
    void shouldReturnEmptyBurndownWithoutSprint() {
        when(sprintMapper.selectOne(ArgumentMatchers.<Wrapper<PmSprint>>any())).thenReturn(null);

        BurndownVO vo = service.burndown(PROJECT_ID, null, OPERATOR_ID);

        assertTrue(vo.getPoints().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getTotalPoints()));
        verify(workItemMapper, never()).selectList(ArgumentMatchers.<Wrapper<PmWorkItem>>any());
    }

    @Test
    @DisplayName("burndown：按天扣减已完成故事点并给出理想线")
    void shouldBuildBurndownPoints() {
        LocalDate start = LocalDate.now().minusDays(2);
        PmSprint sprint = sprint(start, LocalDate.now().plusDays(1));
        when(sprintMapper.selectById(SPRINT_ID)).thenReturn(sprint);
        when(statusMapper.selectList(ArgumentMatchers.<Wrapper<PmStatus>>any()))
                .thenReturn(List.of(status(TODO_STATUS_ID, "TODO", "TODO"),
                        status(DONE_STATUS_ID, "DONE", "DONE")));
        PmWorkItem doneItem = item(3001L, DONE_STATUS_ID, "3");
        doneItem.setCreateTime(start.atStartOfDay());
        PmWorkItem todoItem = item(3002L, TODO_STATUS_ID, "5");
        todoItem.setCreateTime(start.atTime(10, 0));
        when(workItemMapper.selectList(ArgumentMatchers.<Wrapper<PmWorkItem>>any()))
                .thenReturn(List.of(doneItem, todoItem));
        PmActivity transition = new PmActivity();
        transition.setId(1L);
        transition.setWorkItemId(3001L);
        transition.setAction("TRANSITION");
        transition.setOldValue("TODO");
        transition.setNewValue("DONE");
        transition.setCreateTime(start.plusDays(1).atTime(10, 0));
        when(activityMapper.selectList(ArgumentMatchers.<Wrapper<PmActivity>>any()))
                .thenReturn(List.of(transition));

        BurndownVO vo = service.burndown(PROJECT_ID, SPRINT_ID, OPERATOR_ID);

        assertEquals(3, vo.getPoints().size());
        assertEquals(0, new BigDecimal("8").compareTo(vo.getTotalPoints()));
        assertEquals(0, new BigDecimal("8").compareTo(vo.getPoints().get(0).getRemainingPoints()));
        assertEquals(0, new BigDecimal("5").compareTo(vo.getPoints().get(1).getRemainingPoints()));
        assertEquals(0, new BigDecimal("5").compareTo(vo.getPoints().get(2).getRemainingPoints()));
        assertEquals(0, new BigDecimal("8").compareTo(vo.getPoints().get(0).getIdealPoints()));
        assertEquals(0, new BigDecimal("5.33").compareTo(vo.getPoints().get(1).getIdealPoints()));
    }

    @Test
    @DisplayName("velocity：聚合已关闭迭代的承诺与完成故事点")
    void shouldAggregateVelocity() {
        PmSprint sprint = sprint(LocalDate.now().minusDays(10), LocalDate.now().minusDays(3));
        sprint.setStatus("CLOSED");
        when(sprintMapper.selectList(ArgumentMatchers.<Wrapper<PmSprint>>any())).thenReturn(List.of(sprint));
        when(statusMapper.selectList(ArgumentMatchers.<Wrapper<PmStatus>>any()))
                .thenReturn(List.of(status(TODO_STATUS_ID, "TODO", "TODO"),
                        status(DONE_STATUS_ID, "DONE", "DONE")));
        when(workItemMapper.selectList(ArgumentMatchers.<Wrapper<PmWorkItem>>any()))
                .thenReturn(List.of(item(3001L, DONE_STATUS_ID, "3"), item(3002L, TODO_STATUS_ID, "2")));

        VelocityVO vo = service.velocity(PROJECT_ID, null, OPERATOR_ID);

        assertEquals(1, vo.getSprints().size());
        assertEquals(0, new BigDecimal("5").compareTo(vo.getSprints().get(0).getCommittedPoints()));
        assertEquals(0, new BigDecimal("3").compareTo(vo.getSprints().get(0).getCompletedPoints()));
    }

    @Test
    @DisplayName("cumulativeFlow：缺省区间回看 30 天")
    void shouldDefaultFlowRangeTo30Days() {
        when(sprintMapper.selectOne(ArgumentMatchers.<Wrapper<PmSprint>>any())).thenReturn(null);
        when(statusMapper.selectList(ArgumentMatchers.<Wrapper<PmStatus>>any()))
                .thenReturn(List.of(status(TODO_STATUS_ID, "TODO", "TODO")));
        when(workItemMapper.selectList(ArgumentMatchers.<Wrapper<PmWorkItem>>any())).thenReturn(List.of());

        CumulativeFlowVO vo = service.cumulativeFlow(PROJECT_ID, null, null, null, OPERATOR_ID);

        assertEquals(30, vo.getDates().size());
        assertEquals(3, vo.getSeries().size());
        assertTrue(vo.getSeries().get(0).getValues().stream().allMatch(value -> value == 0));
    }

    @Test
    @DisplayName("cumulativeFlow：仅传开始日期拒绝")
    void shouldRejectSingleBoundary() {
        when(sprintMapper.selectOne(ArgumentMatchers.<Wrapper<PmSprint>>any())).thenReturn(null);

        assertThrows(BizException.class,
                () -> service.cumulativeFlow(PROJECT_ID, null, LocalDate.now(), null, OPERATOR_ID));
    }

    @Test
    @DisplayName("cumulativeFlow：区间超过 1 年拒绝")
    void shouldRejectRangeOverOneYear() {
        when(sprintMapper.selectOne(ArgumentMatchers.<Wrapper<PmSprint>>any())).thenReturn(null);

        assertThrows(BizException.class, () -> service.cumulativeFlow(PROJECT_ID, null,
                LocalDate.now().minusDays(400), LocalDate.now(), OPERATOR_ID));
    }

    @Test
    @DisplayName("cumulativeFlow：按天统计状态分类数量")
    void shouldCountByCategory() {
        when(sprintMapper.selectOne(ArgumentMatchers.<Wrapper<PmSprint>>any())).thenReturn(null);
        when(statusMapper.selectList(ArgumentMatchers.<Wrapper<PmStatus>>any()))
                .thenReturn(List.of(status(TODO_STATUS_ID, "TODO", "TODO"),
                        status(DONE_STATUS_ID, "DONE", "DONE")));
        PmWorkItem doneItem = item(3001L, DONE_STATUS_ID, "3");
        doneItem.setCreateTime(LocalDate.now().minusDays(2).atStartOfDay());
        PmWorkItem todoItem = item(3002L, TODO_STATUS_ID, "2");
        todoItem.setCreateTime(LocalDate.now().minusDays(1).atStartOfDay());
        when(workItemMapper.selectList(ArgumentMatchers.<Wrapper<PmWorkItem>>any()))
                .thenReturn(List.of(doneItem, todoItem));
        when(activityMapper.selectList(ArgumentMatchers.<Wrapper<PmActivity>>any())).thenReturn(List.of());

        CumulativeFlowVO vo = service.cumulativeFlow(PROJECT_ID, null, LocalDate.now().minusDays(2),
                LocalDate.now(), OPERATOR_ID);

        assertEquals(3, vo.getDates().size());
        assertEquals(0, vo.getSeries().get(0).getValues().get(0));
        assertEquals(1, vo.getSeries().get(0).getValues().get(2));
        assertEquals(1, vo.getSeries().get(2).getValues().get(2));
    }

    @Test
    @DisplayName("workload：按负责人聚合未完成与已完成")
    void shouldAggregateWorkload() {
        when(statusMapper.selectList(ArgumentMatchers.<Wrapper<PmStatus>>any()))
                .thenReturn(List.of(status(TODO_STATUS_ID, "TODO", "TODO"),
                        status(DONE_STATUS_ID, "DONE", "DONE")));
        PmWorkItem done = item(3001L, DONE_STATUS_ID, "2");
        done.setAssigneeId(9L);
        PmWorkItem todo = item(3002L, TODO_STATUS_ID, "3");
        todo.setAssigneeId(9L);
        PmWorkItem unassigned = item(3003L, TODO_STATUS_ID, "1");
        when(workItemMapper.selectList(ArgumentMatchers.<Wrapper<PmWorkItem>>any()))
                .thenReturn(List.of(done, todo, unassigned));

        WorkloadVO vo = service.workload(PROJECT_ID, OPERATOR_ID);

        assertEquals(2, vo.getMembers().size());
        WorkloadMemberVO first = vo.getMembers().get(0);
        assertEquals("9", first.getAssigneeId());
        assertEquals(2, first.getTotalCount());
        assertEquals(1, first.getOpenCount());
        assertEquals(1, first.getDoneCount());
        assertEquals(0, new BigDecimal("2").compareTo(first.getDonePoints()));
        WorkloadMemberVO second = vo.getMembers().get(1);
        assertNull(second.getAssigneeId());
        assertEquals(1, second.getOpenCount());
    }

    @Test
    @DisplayName("cached：命中缓存时反序列化并跳过回源")
    void shouldUseCachedJson() {
        WorkloadVO cached = new WorkloadVO();
        WorkloadMemberVO member = new WorkloadMemberVO();
        member.setAssigneeId("9");
        member.setOpenCount(1);
        cached.setMembers(List.of(member));
        when(cacheTemplate.getOrLoad(anyString(), anyString(), ArgumentMatchers.<Duration>any(),
                ArgumentMatchers.<Supplier<String>>any()))
                .thenReturn(objectMapper.writeValueAsString(cached));

        WorkloadVO vo = service.workload(PROJECT_ID, OPERATOR_ID);

        assertEquals(1, vo.getMembers().size());
        assertEquals("9", vo.getMembers().get(0).getAssigneeId());
        verify(workItemMapper, never()).selectList(ArgumentMatchers.<Wrapper<PmWorkItem>>any());
    }

    @Test
    @DisplayName("velocity：迭代数量收敛到上限 12")
    void shouldClampVelocityLimit() {
        when(sprintMapper.selectList(ArgumentMatchers.<Wrapper<PmSprint>>any())).thenReturn(List.of());

        VelocityVO vo = service.velocity(PROJECT_ID, 100, OPERATOR_ID);

        assertTrue(vo.getSprints().isEmpty());
    }

    /**
     * 构造迭代实体
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 迭代实体
     */
    private PmSprint sprint(LocalDate startDate, LocalDate endDate) {
        PmSprint sprint = new PmSprint();
        sprint.setId(SPRINT_ID);
        sprint.setProjectId(PROJECT_ID);
        sprint.setName("Sprint 1");
        sprint.setStartDate(startDate);
        sprint.setEndDate(endDate);
        sprint.setStatus("ACTIVE");
        return sprint;
    }

    /**
     * 构造状态实体
     *
     * @param id       状态ID
     * @param code     状态编码
     * @param category 状态分类
     * @return 状态实体
     */
    private PmStatus status(Long id, String code, String category) {
        PmStatus status = new PmStatus();
        status.setId(id);
        status.setProjectId(PROJECT_ID);
        status.setCode(code);
        status.setName(code);
        status.setCategory(category);
        return status;
    }

    /**
     * 构造工作项实体
     *
     * @param id          工作项ID
     * @param statusId    状态ID
     * @param storyPoints 故事点
     * @return 工作项实体
     */
    private PmWorkItem item(Long id, Long statusId, String storyPoints) {
        PmWorkItem item = new PmWorkItem();
        item.setId(id);
        item.setProjectId(PROJECT_ID);
        item.setSprintId(SPRINT_ID);
        item.setStatusId(statusId);
        item.setStoryPoints(new BigDecimal(storyPoints));
        item.setTitle("工作项");
        item.setRankValue("000000001000");
        item.setCreateTime(LocalDateTime.now().minusDays(30));
        return item;
    }
}
