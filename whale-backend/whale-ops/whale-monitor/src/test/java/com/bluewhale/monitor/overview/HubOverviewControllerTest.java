package com.bluewhale.monitor.overview;

import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.monitor.vo.HubHallDetailVO;
import com.bluewhale.monitor.vo.HubHallRowVO;
import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.model.OverviewSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link HubOverviewController} 屏风页接口测试：直接调用控制器方法，聚焦分页与筛选。
 *
 * <p>聚合器用 Mockito 替身，只验「同一份聚合结果怎么被切页/筛选」；
 * 真正的下游问询与降级判定由 {@link HubOverviewAggregatorTest} 覆盖。</p>
 *
 * @author BlueWhale
 */
class HubOverviewControllerTest {

    private HubOverviewAggregator aggregator;

    private HubOverviewController controller;

    @BeforeEach
    void setUp() {
        aggregator = mock(HubOverviewAggregator.class);
        controller = new HubOverviewController(aggregator);
        when(aggregator.aggregate(any(OverviewContext.class))).thenReturn(sample());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    private static HubProbe probe(String nodeKey, String name, HubHallState state) {
        return new HubProbe(nodeKey, name, state, "说明",
                OverviewSnapshot.empty("erp", nodeKey, name, "经营驾驶舱", null, null));
    }

    private static HubAggregate sample() {
        return new HubAggregate(
                OverviewSnapshot.empty("monitor", "shushu-hall", "数枢殿", "总控", null, null),
                List.of(probe("hubu-hall", "户部", HubHallState.LIVE),
                        probe("duzhi-hall", "度支殿", HubHallState.STALE),
                        probe("gongbu-hall", "工部", HubHallState.DEGRADED),
                        probe("libu-hall", "礼部", HubHallState.DENIED)));
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "11");
        request.addHeader("X-Tenant-Id", "7");
        request.addHeader("X-Permission-Codes", "sys:erp:bi:cockpit:list");
        request.addHeader("X-User-Roles", "crm_admin");
        return request;
    }

    @Test
    @DisplayName("halls：无筛选返回全部；非法页码与超大页长收敛到 [1, 50]")
    void shouldListAllHallsWithSanePaging() {
        Result<PageResult<HubHallRowVO>> result = controller.halls(null, null, 0L, 100L, request());

        assertThat(result.getCode()).isZero();
        PageResult<HubHallRowVO> page = result.getData();
        assertThat(page.getTotal()).isEqualTo(4);
        assertThat(page.getCurrent()).isEqualTo(1L);
        assertThat(page.getSize()).isEqualTo(50L);
        assertThat(page.getRecords()).extracting(HubHallRowVO::nodeKey)
                .containsExactly("hubu-hall", "duzhi-hall", "gongbu-hall", "libu-hall");
    }

    @Test
    @DisplayName("halls：按页切取，total 是筛选后的总数而不是本页条数")
    void shouldPageThroughHalls() {
        Result<PageResult<HubHallRowVO>> result = controller.halls(null, null, 2L, 2L, request());

        assertThat(result.getData().getTotal()).isEqualTo(4);
        assertThat(result.getData().getCurrent()).isEqualTo(2L);
        assertThat(result.getData().getSize()).isEqualTo(2L);
        assertThat(result.getData().getRecords()).extracting(HubHallRowVO::nodeKey)
                .containsExactly("gongbu-hall", "libu-hall");
    }

    @Test
    @DisplayName("halls：关键字命中节点 key（忽略大小写与首尾空白）")
    void shouldFilterByKeywordInNodeKeyIgnoringCase() {
        Result<PageResult<HubHallRowVO>> result = controller.halls("  DUZHI  ", null, 1L, 6L, request());

        assertThat(result.getData().getTotal()).isEqualTo(1);
        assertThat(result.getData().getRecords().get(0).name()).isEqualTo("度支殿");
    }

    @Test
    @DisplayName("halls：关键字命中殿名即可，无需再比较节点 key")
    void shouldFilterByKeywordInName() {
        Result<PageResult<HubHallRowVO>> result = controller.halls("户部", null, 1L, 6L, request());

        assertThat(result.getData().getRecords()).extracting(HubHallRowVO::nodeKey)
                .containsExactly("hubu-hall");
    }

    @Test
    @DisplayName("halls：状态筛选忽略大小写与首尾空白")
    void shouldFilterByStateIgnoringCase() {
        Result<PageResult<HubHallRowVO>> result = controller.halls(null, " STALE ", 1L, 6L, request());

        assertThat(result.getData().getRecords()).extracting(HubHallRowVO::nodeKey)
                .containsExactly("duzhi-hall");
    }

    @Test
    @DisplayName("halls：空白关键字与空白状态视为不筛选")
    void shouldIgnoreBlankFilters() {
        Result<PageResult<HubHallRowVO>> result = controller.halls("   ", "   ", 1L, 6L, request());

        assertThat(result.getData().getTotal()).isEqualTo(4);
    }

    @Test
    @DisplayName("hall：在册节点返回同一次聚合里的详情")
    void shouldReturnHallDetail() {
        Result<HubHallDetailVO> result = controller.hall("hubu-hall", request());

        assertThat(result.getCode()).isZero();
        assertThat(result.getData().nodeKey()).isEqualTo("hubu-hall");
        assertThat(result.getData().state()).isEqualTo("live");
    }

    @Test
    @DisplayName("hall：节点不在册抛 NOT_FOUND，不返回空壳详情")
    void shouldThrowWhenHallUnknown() {
        assertThatThrownBy(() -> controller.hall("nope", request()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未知殿");
    }
}
