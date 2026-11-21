package com.bluewhale.pm.overview;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmSprint;
import com.bluewhale.pm.infrastructure.entity.PmStatus;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.mapper.PmProjectMapper;
import com.bluewhale.pm.infrastructure.mapper.PmSprintMapper;
import com.bluewhale.pm.infrastructure.mapper.PmStatusMapper;
import com.bluewhale.pm.infrastructure.mapper.PmWorkItemMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PmOverviewStatsService} 单元测试：三本账的计数透传、
 * "未完成"按状态分类归并（不是按自定义状态码）、类型分布的顺序。
 *
 * <p>这里不验 SQL 长什么样——SQL 由 MyBatis-Plus 生成、租户条件由插件追加，
 * 断言它的字符串形态只会把测试绑死在框架实现上。真正会漂的是**口径**
 * （DONE 分类怎么取、类型分布含不含已完成件），所以只钉口径。</p>
 *
 * @author BlueWhale
 */
class PmOverviewStatsServiceTest {

    /**
     * 初始化 MyBatis-Plus 表元数据。
     *
     * <p>{@code in(...)} / {@code notIn(...)} 需要**立刻**把 lambda 解析成列名
     * （不像 {@code eq} 是渲染期才解析），而单测里没有 MyBatis 扫描 Mapper 的过程，
     * lambda 缓存是空的。不初始化就会抛 "can not find lambda cache for this entity"，
     * 且只有带 {@code in} / {@code notIn} 的那条路径会炸。</p>
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, PmProject.class);
        TableInfoHelper.initTableInfo(assistant, PmSprint.class);
        TableInfoHelper.initTableInfo(assistant, PmStatus.class);
        TableInfoHelper.initTableInfo(assistant, PmWorkItem.class);
    }

    private final PmProjectMapper projectMapper = mock(PmProjectMapper.class);

    private final PmSprintMapper sprintMapper = mock(PmSprintMapper.class);

    private final PmStatusMapper statusMapper = mock(PmStatusMapper.class);

    private final PmWorkItemMapper workItemMapper = mock(PmWorkItemMapper.class);

    private final PmOverviewStatsService service =
            new PmOverviewStatsService(projectMapper, sprintMapper, statusMapper, workItemMapper);

    /**
     * 构造一个带 id 与分类的状态行。
     *
     * @param id       状态 id
     * @param category 分类（TODO / IN_PROGRESS / DONE）
     * @return 状态实体
     */
    private static PmStatus status(long id, String category) {
        PmStatus entity = new PmStatus();
        entity.setId(id);
        entity.setCategory(category);
        return entity;
    }

    /**
     * 按调用顺序打桩。
     *
     * <p>状态 Mapper 依次查三类：DONE、TODO、IN_PROGRESS（每次各查一次分类）。</p>
     *
     * @param done   DONE 分类返回的状态 id
     * @param todo   TODO 分类返回的状态 id（可空表 = 该分类没配状态）
     * @param inProg IN_PROGRESS 分类返回的状态 id
     * @param items  工作项 Mapper 依次返回的计数（最长 12 个：总数、已完成、
     *               待办、进行中、逾期、逾期高优、无主、五类分布）
     */
    private void stub(List<Long> done, List<Long> todo, List<Long> inProg, long[] items) {
        when(statusMapper.selectList(any())).thenReturn(
                done.stream().map(id -> status(id, "DONE")).toList(),
                todo.stream().map(id -> status(id, "TODO")).toList(),
                inProg.stream().map(id -> status(id, "IN_PROGRESS")).toList());
        Long[] counts = new Long[items.length];
        for (int i = 0; i < items.length; i++) {
            counts[i] = items[i];
        }
        when(workItemMapper.selectCount(any())).thenReturn(counts[0],
                java.util.Arrays.copyOfRange(counts, 1, counts.length));
    }

    @Test
    @DisplayName("三本账：项目、迭代、工作项各按调用顺序透传，不做二次加工")
    void shouldCountThreeLedgers() {
        stub(List.of(9L, 10L), List.of(11L), List.of(12L),
                new long[] {100L, 40L, 35L, 25L, 6L, 2L, 3L, 5L, 20L, 30L, 8L, 2L});
        when(projectMapper.selectCount(any())).thenReturn(12L, 4L);
        when(sprintMapper.selectCount(any())).thenReturn(2L, 5L, 30L);

        PmOverviewStats stats = service.snapshot();

        assertThat(stats.projectActive()).isEqualTo(12L);
        assertThat(stats.projectArchived()).isEqualTo(4L);
        assertThat(stats.sprintActive()).isEqualTo(2L);
        assertThat(stats.sprintFuture()).isEqualTo(5L);
        assertThat(stats.sprintClosed()).isEqualTo(30L);
        assertThat(stats.itemTotal()).isEqualTo(100L);
        assertThat(stats.itemDone()).isEqualTo(40L);
        assertThat(stats.itemTodo()).isEqualTo(35L);
        assertThat(stats.itemInProgress()).isEqualTo(25L);
        assertThat(stats.itemOverdue()).isEqualTo(6L);
        assertThat(stats.itemOverdueHigh()).isEqualTo(2L);
        assertThat(stats.itemUnassigned()).isEqualTo(3L);
    }

    @Test
    @DisplayName("未完成按状态分类归并：DONE 分类先查再排除，分类查询确实按 category 过滤")
    void shouldResolveCategoriesFromStatusTable() {
        stub(List.of(9L, 10L), List.of(11L), List.of(12L),
                new long[] {100L, 40L, 35L, 25L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L});

        service.snapshot();

        ArgumentCaptor<Wrapper<PmStatus>> statusCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(statusMapper, times(3)).selectList(statusCaptor.capture());
        /* 分类过滤条件以参数形式挂在 wrapper 上。参数在本版 MyBatis-Plus 里是
           渲染时才登记的：先调 getSqlSegment() 触发，再取参数表，否则拿到空表。 */
        List<String> categories = statusCaptor.getAllValues().stream().map(wrapper -> {
            wrapper.getSqlSegment();
            return String.valueOf(((LambdaQueryWrapper<PmStatus>) wrapper)
                    .getParamNameValuePairs().values().iterator().next());
        }).toList();
        assertThat(categories).containsExactly("DONE", "TODO", "IN_PROGRESS");
    }

    @Test
    @DisplayName("未完成限定：逾期/无主/类型分布都要排除 DONE 分类，不把修完的活算欠账")
    void shouldExcludeDoneItemsFromOpenCounts() {
        stub(List.of(9L, 10L), List.of(11L), List.of(12L),
                new long[] {100L, 40L, 35L, 25L, 6L, 2L, 3L, 5L, 20L, 30L, 8L, 2L});

        service.snapshot();

        ArgumentCaptor<Wrapper<PmWorkItem>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(workItemMapper, times(12)).selectCount(captor.capture());
        /* 第 5 个调用是逾期计数（countOpen 路径），必须带 NOT IN 排除已完成状态 */
        Wrapper<PmWorkItem> overdueWrapper = captor.getAllValues().get(4);
        String sqlSegment = overdueWrapper.getSqlSegment();
        assertThat(sqlSegment).contains("NOT IN");
        List<Object> params = List.copyOf(
                ((LambdaQueryWrapper<PmWorkItem>) overdueWrapper).getParamNameValuePairs().values());
        assertThat(params).contains(9L, 10L);
    }

    @Test
    @DisplayName("没有任何 DONE 分类状态：已完成与各未完成分类都是 0，不报错、不加 NOT IN")
    void shouldHandleEmptyDoneCategory() {
        stub(List.of(), List.of(), List.of(),
                new long[] {100L, 6L, 2L, 3L, 5L, 20L, 30L, 8L, 2L});

        PmOverviewStats stats = service.snapshot();

        assertThat(stats.itemDone()).isZero();
        assertThat(stats.itemTodo()).isZero();
        assertThat(stats.itemInProgress()).isZero();
        ArgumentCaptor<Wrapper<PmWorkItem>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(workItemMapper, times(9)).selectCount(captor.capture());
        /* 总数那次调用传的是 null wrapper（无条件全表 count），跳过 */
        captor.getAllValues().stream().filter(Objects::nonNull).forEach(wrapper ->
                assertThat(wrapper.getSqlSegment()).doesNotContain("NOT IN"));
    }

    @Test
    @DisplayName("类型分布：史诗/需求/任务/缺陷/子任务按固定顺序，且只数未完成件")
    void shouldBuildTypeDistributionInFixedOrder() {
        stub(List.of(9L, 10L), List.of(11L), List.of(12L),
                new long[] {100L, 40L, 35L, 25L, 6L, 2L, 3L, 5L, 20L, 30L, 8L, 2L});

        List<PmOverviewStats.TypeCount> types = service.snapshot().itemByType();

        assertThat(types).extracting(PmOverviewStats.TypeCount::label)
                .containsExactly("史诗", "需求", "任务", "缺陷", "子任务");
        assertThat(types).extracting(PmOverviewStats.TypeCount::count)
                .containsExactly(5L, 20L, 30L, 8L, 2L);
    }

    @Test
    @DisplayName("计数返回 null（无数据/驱动异常）：一律当 0，不能拆箱 NPE")
    void shouldTreatNullCountAsZero() {
        stub(List.of(9L), List.of(), List.of(), new long[12]);
        when(projectMapper.selectCount(any())).thenReturn(null);
        when(sprintMapper.selectCount(any())).thenReturn(null);
        when(workItemMapper.selectCount(any())).thenReturn(null);

        PmOverviewStats stats = service.snapshot();

        assertThat(stats.projectActive()).isZero();
        assertThat(stats.sprintClosed()).isZero();
        assertThat(stats.itemOverdue()).isZero();
        assertThat(stats.itemByType()).extracting(PmOverviewStats.TypeCount::count).containsOnly(0L);
    }
}
