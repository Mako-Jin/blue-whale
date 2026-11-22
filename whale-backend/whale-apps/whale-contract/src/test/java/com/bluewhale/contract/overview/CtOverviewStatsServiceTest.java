package com.bluewhale.contract.overview;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.contract.entity.CtContract;
import com.bluewhale.contract.mapper.CtContractMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CtOverviewStatsService} 单元测试：状态透传、到期双窗口
 * （30 天内到期 / 已过期仍生效）、金额只合计 CNY。
 *
 * <p>这里不验 SQL 长什么样——SQL 由 MyBatis-Plus 生成、租户条件由插件追加，
 * 断言它的字符串形态只会把测试绑死在框架实现上。真正会漂的是**口径**
 * （到期窗口是不是 30 天、过期判定是不是"到期日早于今天且仍生效"、
 * 外币要不要进金额图），所以只钉口径。</p>
 *
 * @author BlueWhale
 */
class CtOverviewStatsServiceTest {

    /**
     * 初始化 MyBatis-Plus 表元数据。
     *
     * <p>{@code between(...)} / {@code lt(...)} 需要**立刻**把 lambda 解析成列名，
     * 而单测里没有 MyBatis 扫描 Mapper 的过程，lambda 缓存是空的。
     * 不初始化就会抛 "can not find lambda cache for this entity"。</p>
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, CtContract.class);
    }

    private final CtContractMapper contractMapper = mock(CtContractMapper.class);

    private final CtOverviewStatsService service = new CtOverviewStatsService(contractMapper);

    /**
     * 构造一行生效中合同的金额信息。
     *
     * @param category 类别码值
     * @param amount   金额（可空）
     * @param currency 币种（可空）
     * @return 合同实体
     */
    private static CtContract money(String category, String amount, String currency) {
        CtContract entity = new CtContract();
        entity.setCategory(category);
        entity.setAmount(amount == null ? null : new BigDecimal(amount));
        entity.setCurrency(currency);
        return entity;
    }

    /**
     * 按调用顺序打桩：前 12 个计数（总数 + 十一状态逐个），后 2 个到期窗口计数。
     *
     * @param counts 依次返回的计数（长度 14：总数、生效、审批、签署、起草、
     *              驳回、撤回、归档、终止、作废、已批、已签、30 天到期、已过期）
     */
    private void stubCounts(long[] counts) {
        Long[] boxed = new Long[counts.length];
        for (int i = 0; i < counts.length; i++) {
            boxed[i] = counts[i];
        }
        when(contractMapper.selectCount(any())).thenReturn(boxed[0],
                java.util.Arrays.copyOfRange(boxed, 1, boxed.length));
    }

    @Test
    @DisplayName("状态账：十一状态逐个透传，在途 = 审批+已批+签署+已签，终止 = 终止+作废")
    void shouldCountLedgersByStatus() {
        stubCounts(new long[] {100L, 40L, 5L, 6L, 3L, 2L, 1L, 30L, 4L, 1L, 7L, 8L, 0L, 0L});

        CtOverviewStats stats = service.snapshot();

        assertThat(stats.total()).isEqualTo(100L);
        assertThat(stats.effective()).isEqualTo(40L);
        assertThat(stats.inFlight()).isEqualTo(5L + 7L + 6L + 8L);
        assertThat(stats.approving()).isEqualTo(5L);
        assertThat(stats.signing()).isEqualTo(6L);
        assertThat(stats.draft()).isEqualTo(3L);
        assertThat(stats.rejectWithdrawn()).isEqualTo(2L + 1L);
        assertThat(stats.archived()).isEqualTo(30L);
        assertThat(stats.terminated()).isEqualTo(4L + 1L);
    }

    @Test
    @DisplayName("到期双窗口：30 天窗口往后看、过期往前看，两者都限定 EFFECTIVE 状态")
    void shouldComputeExpiringAndExpiredWindows() {
        stubCounts(new long[] {100L, 40L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 6L, 2L});

        CtOverviewStats stats = service.snapshot();

        assertThat(stats.expiringSoon()).isEqualTo(6L);
        assertThat(stats.expiredActive()).isEqualTo(2L);

        ArgumentCaptor<Wrapper<CtContract>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(contractMapper, times(14)).selectCount(captor.capture());
        /* 第 13/14 个调用是两个到期窗口；状态限定必须挂在条件里，
           否则"30 天内到期"会把已终止、已归档的合同也数进来 */
        List<Wrapper<CtContract>> windows = captor.getAllValues().subList(12, 14);
        windows.forEach(wrapper -> {
            wrapper.getSqlSegment();
            List<Object> params = List.copyOf(
                    ((LambdaQueryWrapper<CtContract>) wrapper).getParamNameValuePairs().values());
            assertThat(params).contains("EFFECTIVE");
        });
    }

    @Test
    @DisplayName("金额图：五个类别固定顺序，只合计 CNY（外币单列提示，不混加）")
    void shouldSumMoneyByCategoryOnlyCny() {
        stubCounts(new long[] {100L, 40L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L});
        when(contractMapper.selectList(any())).thenReturn(List.of(
                money("PURCHASE", "1000", "CNY"),
                money("PURCHASE", "500", null),
                money("SALES", "2000", "CNY"),
                money("SERVICE", "300", "CNY"),
                money("LEASE", "999", "USD"),
                money("OTHER", null, "CNY")));

        CtOverviewStats stats = service.snapshot();

        assertThat(stats.effectiveMoney()).extracting(CtOverviewStats.CategoryAmount::label)
                .containsExactly("采购", "销售", "服务", "租赁", "其他");
        assertThat(stats.effectiveMoney()).extracting(CtOverviewStats.CategoryAmount::amount)
                .containsExactly(1500.0, 2000.0, 300.0, 0.0, 0.0);
        assertThat(stats.foreignCurrency()).isEqualTo(1L);
    }

    @Test
    @DisplayName("没有任何生效合同：金额图五项全 0，外币数 0，不报错")
    void shouldHandleEmptyEffectiveRows() {
        stubCounts(new long[] {0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L});
        when(contractMapper.selectList(any())).thenReturn(List.of());

        CtOverviewStats stats = service.snapshot();

        assertThat(stats.effectiveMoney()).extracting(CtOverviewStats.CategoryAmount::amount)
                .containsOnly(0.0);
        assertThat(stats.foreignCurrency()).isZero();
    }

    @Test
    @DisplayName("计数返回 null（无数据/驱动异常）：一律当 0，不能拆箱 NPE")
    void shouldTreatNullCountAsZero() {
        stubCounts(new long[14]);
        when(contractMapper.selectCount(any())).thenReturn(null);
        when(contractMapper.selectList(any())).thenReturn(List.of());

        CtOverviewStats stats = service.snapshot();

        assertThat(stats.total()).isZero();
        assertThat(stats.expiringSoon()).isZero();
        assertThat(stats.expiredActive()).isZero();
        assertThat(stats.effectiveMoney()).extracting(CtOverviewStats.CategoryAmount::amount)
                .containsOnly(0.0);
    }
}
