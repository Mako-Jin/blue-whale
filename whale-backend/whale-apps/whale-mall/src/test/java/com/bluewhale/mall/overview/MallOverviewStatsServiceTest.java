package com.bluewhale.mall.overview;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.mall.aftersale.infrastructure.persistence.entity.AfterSaleEntity;
import com.bluewhale.mall.aftersale.infrastructure.persistence.mapper.AfterSaleMapper;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.SkuStockEntity;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.SpuEntity;
import com.bluewhale.mall.catalog.infrastructure.persistence.mapper.SkuStockMapper;
import com.bluewhale.mall.catalog.infrastructure.persistence.mapper.SpuMapper;
import com.bluewhale.mall.order.infrastructure.persistence.entity.OrderEntity;
import com.bluewhale.mall.order.infrastructure.persistence.mapper.OrderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MallOverviewStatsService} 单元测试：商品/订单/售后/库存四本账的
 * 计数透传、状态口径与零库存按 SKU 数。
 *
 * <p>这里不验 SQL 长什么样——SQL 由 MyBatis-Plus 生成、租户条件由插件追加，
 * 断言它的字符串形态只会把测试绑死在框架实现上。真正会漂的是**口径**
 * （售后只数未结账、零库存按 SKU 不按 SPU），所以只钉口径。</p>
 *
 * @author BlueWhale
 */
class MallOverviewStatsServiceTest {

    /**
     * 初始化 MyBatis-Plus 表元数据。
     *
     * <p>单测里没有 MyBatis 扫描 Mapper 的过程，lambda 缓存是空的；
     * 不初始化就会抛 "can not find lambda cache for this entity"。</p>
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SpuEntity.class);
        TableInfoHelper.initTableInfo(assistant, OrderEntity.class);
        TableInfoHelper.initTableInfo(assistant, AfterSaleEntity.class);
        TableInfoHelper.initTableInfo(assistant, SkuStockEntity.class);
    }

    private final SpuMapper spuMapper = mock(SpuMapper.class);

    private final OrderMapper orderMapper = mock(OrderMapper.class);

    private final AfterSaleMapper afterSaleMapper = mock(AfterSaleMapper.class);

    private final SkuStockMapper skuStockMapper = mock(SkuStockMapper.class);

    private final MallOverviewStatsService service =
            new MallOverviewStatsService(spuMapper, orderMapper, afterSaleMapper, skuStockMapper);

    /**
     * 让四个 Mapper 各按调用顺序返回计数。
     *
     * @param spu      商品 Mapper 依次返回：总数、在售
     * @param order    订单 Mapper 依次返回：总数、待支付、待发货、已发货、已完成、已取消、退款中、已退款
     * @param afterSale 售后 Mapper 依次返回：待审核、待退货
     * @param stockZero 零库存 SKU 数
     */
    private void stub(long[] spu, long[] order, long[] afterSale, long stockZero) {
        when(spuMapper.selectCount(any())).thenReturn(spu[0], spu[1]);
        when(orderMapper.selectCount(any())).thenReturn(order[0], order[1], order[2],
                order[3], order[4], order[5], order[6], order[7]);
        when(afterSaleMapper.selectCount(any())).thenReturn(afterSale[0], afterSale[1]);
        when(skuStockMapper.selectCount(any())).thenReturn(stockZero);
    }

    @Test
    @DisplayName("缺租户 ID 直接抛：总控会显式传租户，缺租户说明调用方配错了，宁可降级不可给假 0")
    void shouldRejectMissingTenant() {
        assertThatThrownBy(() -> service.snapshot(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("四本账：商品、订单七态、售后两段、零库存各按调用顺序透传，不做二次加工")
    void shouldCountFourLedgers() {
        stub(new long[] {30L, 18L},
                new long[] {100L, 8L, 5L, 12L, 60L, 10L, 3L, 2L},
                new long[] {4L, 2L}, 6L);

        MallOverviewStats stats = service.snapshot(9001L);

        assertThat(stats.spuTotal()).isEqualTo(30L);
        assertThat(stats.spuOnSale()).isEqualTo(18L);
        assertThat(stats.orderTotal()).isEqualTo(100L);
        assertThat(stats.orderPendingPayment()).isEqualTo(8L);
        assertThat(stats.orderPaid()).isEqualTo(5L);
        assertThat(stats.orderShipped()).isEqualTo(12L);
        assertThat(stats.orderCompleted()).isEqualTo(60L);
        assertThat(stats.orderCancelled()).isEqualTo(10L);
        assertThat(stats.orderRefunding()).isEqualTo(3L);
        assertThat(stats.orderRefunded()).isEqualTo(2L);
        assertThat(stats.aftersaleCreated()).isEqualTo(4L);
        assertThat(stats.aftersaleWaitReturn()).isEqualTo(2L);
        assertThat(stats.stockZero()).isEqualTo(6L);
    }

    @Test
    @DisplayName("订单状态计数走独立 wrapper：七次查询每次一条 eq 条件，退款不混进取消")
    void shouldCountOrderStatusSeparately() {
        stub(new long[] {0L, 0L}, new long[] {0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L},
                new long[] {0L, 0L}, 0L);

        service.snapshot(9001L);

        /* 第 1 次是总数（selectCount(null)），第 2-8 次各是一个状态 wrapper */
        ArgumentCaptor<Wrapper<OrderEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper, times(8)).selectCount(captor.capture());
        assertThat(captor.getAllValues().get(0)).isNull();
        List<String> segments = captor.getAllValues().stream().skip(1)
                .map(wrapper -> ((LambdaQueryWrapper<OrderEntity>) wrapper).getSqlSegment())
                .toList();
        assertThat(segments).hasSize(7).allMatch(segment -> segment.contains("status = "));
    }

    @Test
    @DisplayName("售后只数未结账两段：待审核与待退货各一次查询，终态不进待办")
    void shouldCountOnlyOpenAfterSale() {
        stub(new long[] {0L, 0L}, new long[] {0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L},
                new long[] {0L, 0L}, 0L);

        service.snapshot(9001L);

        ArgumentCaptor<Wrapper<AfterSaleEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(afterSaleMapper, times(2)).selectCount(captor.capture());
        List<String> segments = captor.getAllValues().stream()
                .map(wrapper -> ((LambdaQueryWrapper<AfterSaleEntity>) wrapper).getSqlSegment())
                .toList();
        assertThat(segments).hasSize(2).allMatch(segment -> segment.contains("status = "));
    }

    @Test
    @DisplayName("零库存按 SKU 数：查的是库存表的可售数，不是商品表的缺货商品数")
    void shouldCountZeroStockBySku() {
        stub(new long[] {0L, 0L}, new long[] {0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L},
                new long[] {0L, 0L}, 0L);

        service.snapshot(9001L);

        ArgumentCaptor<Wrapper<SkuStockEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(skuStockMapper, times(1)).selectCount(captor.capture());
        String segment = ((LambdaQueryWrapper<SkuStockEntity>) captor.getValue()).getSqlSegment();
        assertThat(segment).contains("available_qty = ");
    }

    @Test
    @DisplayName("计数返回 null（无数据/驱动异常）：一律当 0，不能拆箱 NPE")
    void shouldTreatNullCountAsZero() {
        when(spuMapper.selectCount(any())).thenReturn(null);
        when(orderMapper.selectCount(any())).thenReturn(null);
        when(afterSaleMapper.selectCount(any())).thenReturn(null);
        when(skuStockMapper.selectCount(any())).thenReturn(null);

        MallOverviewStats stats = service.snapshot(9001L);

        assertThat(stats.spuOnSale()).isZero();
        assertThat(stats.orderTotal()).isZero();
        assertThat(stats.orderPaid()).isZero();
        assertThat(stats.aftersaleCreated()).isZero();
        assertThat(stats.stockZero()).isZero();
    }
}
