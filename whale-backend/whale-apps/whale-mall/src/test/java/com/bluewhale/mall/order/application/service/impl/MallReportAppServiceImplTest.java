package com.bluewhale.mall.order.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.order.application.view.DailyReportView;
import com.bluewhale.mall.order.domain.model.DailyReportStat;
import com.bluewhale.mall.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 运营日报表应用服务。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MallReportAppServiceImplTest {

    private static final MallActor OPERATOR =
            new MallActor(9L, "op", Set.of("mall_operator"), Set.of());

    private static final MallActor BUYER = new MallActor(7L, "buyer", Set.of(), Set.of());

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private MallReportAppServiceImpl reportAppService;

    /** 运营查看日报表：日期区间与统计行原样透出为视图 */
    @Test
    @DisplayName("运营可查看日报表并返回视图")
    void dailyReturnsViews() {
        DailyReportStat stat = new DailyReportStat();
        stat.setStatDate(LocalDate.of(2026, 11, 8));
        stat.setOrderCount(12L);
        stat.setGoodsAmount(new BigDecimal("1200.00"));
        stat.setFreightAmount(new BigDecimal("60.00"));
        stat.setCouponDiscount(new BigDecimal("80.00"));
        stat.setPaidAmount(new BigDecimal("1180.00"));
        stat.setRefundAmount(new BigDecimal("20.00"));
        when(orderRepository.dailyReport(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8)))
                .thenReturn(List.of(stat));

        List<DailyReportView> views = reportAppService.daily(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8), OPERATOR);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).getStatDate()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(views.get(0).getOrderCount()).isEqualTo(12L);
        assertThat(views.get(0).getPaidAmount()).isEqualByComparingTo("1180.00");
        assertThat(views.get(0).getRefundAmount()).isEqualByComparingTo("20.00");
    }

    /** 买家看报表越权拒绝，且不触达仓储 */
    @Test
    @DisplayName("非运营角色查看报表越权")
    void dailyRejectsNonOperator() {
        assertThatThrownBy(() -> reportAppService.daily(LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 8), BUYER))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.ORDER_FORBIDDEN));

        verifyNoInteractions(orderRepository);
    }

    /** 匿名请求直接按缺身份拒绝 */
    @Test
    @DisplayName("匿名请求查看报表拒绝")
    void dailyRejectsAnonymous() {
        assertThatThrownBy(() -> reportAppService.daily(LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 8), MallActor.anonymous()))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(orderRepository);
    }

    /** 空区间（from 晚于 to）返回空列表而不是报错：前端选错日期看到空表即知 */
    @Test
    @DisplayName("乱序日期区间返回空列表")
    void dailyWithReversedRange() {
        when(orderRepository.dailyReport(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

        List<DailyReportView> views = reportAppService.daily(
                LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 1), OPERATOR);

        assertThat(views).isEmpty();
    }
}
