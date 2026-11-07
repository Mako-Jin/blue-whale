package com.bluewhale.mall.aftersale.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.aftersale.application.command.AfterSaleApplyCommand;
import com.bluewhale.mall.aftersale.application.view.AfterSaleView;
import com.bluewhale.mall.aftersale.domain.model.AfterSaleItem;
import com.bluewhale.mall.aftersale.domain.model.AfterSaleStatus;
import com.bluewhale.mall.aftersale.domain.model.AfterSaleType;
import com.bluewhale.mall.aftersale.domain.model.MallAfterSale;
import com.bluewhale.mall.aftersale.domain.repository.AfterSaleRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.config.MallProperties;
import com.bluewhale.mall.coupon.application.service.CouponAppService;
import com.bluewhale.mall.order.domain.model.MallOrder;
import com.bluewhale.mall.order.domain.model.OrderItem;
import com.bluewhale.mall.order.domain.model.OrderLog;
import com.bluewhale.mall.order.domain.model.OrderStatus;
import com.bluewhale.mall.order.domain.repository.OrderRepository;
import com.bluewhale.mall.order.infrastructure.client.PayInternalClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AfterSaleAppServiceImpl} 单元测试。
 *
 * <p>覆盖申请（行归属、数量、重复行、额度占用失败）、审核（剩余金额校验、幂等键透传）、
 * 驳回/撤销（释放额度）、回调（结单 + 累计 + 幂等 + 无售后单兼容）与整单退款。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AfterSaleAppServiceImplTest {

    @Mock
    private AfterSaleRepository afterSaleRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PayInternalClient payInternalClient;

    @Mock
    private CouponAppService couponAppService;

    @Mock
    private MallProperties properties;

    @InjectMocks
    private AfterSaleAppServiceImpl afterSaleAppService;

    private MallActor operator;

    private MallActor buyer;

    @BeforeEach
    void setUp() {
        operator = new MallActor(99L, "operator", Set.of("mall_operator"), Set.of());
        buyer = new MallActor(7L, "buyer", Set.of(), Set.of());
        lenient().when(properties.getPageLimit()).thenReturn(100);
    }

    /**
     * 构造已支付订单（含一行 19.90 × 2，共 39.80）。
     *
     * @return 订单
     */
    private MallOrder paidOrder() {
        MallOrder order = new MallOrder();
        order.setId(5L);
        order.setOrderNo("MO20261106000001");
        order.setBuyerUserId(7L);
        order.setStatus(OrderStatus.PAID);
        order.setPayNo("MO20261106000001");
        order.setTotalAmount(new BigDecimal("39.80"));
        order.setRefundAmount(BigDecimal.ZERO);
        OrderItem item = new OrderItem();
        item.setId(11L);
        item.setSkuId(21L);
        item.setSpuId(31L);
        item.setSpuName("测试商品");
        item.setSkuCode("SKU-1");
        item.setUnitPrice(new BigDecimal("19.90"));
        item.setQuantity(2);
        item.setRefundedQuantity(0);
        item.setRefundedAmount(BigDecimal.ZERO);
        item.setAmount(new BigDecimal("39.80"));
        order.getItems().add(item);
        return order;
    }

    /**
     * 构造申请命令。
     *
     * @param orderItemId 订单明细 ID
     * @param quantity    数量
     * @return 命令
     */
    private AfterSaleApplyCommand command(long orderItemId, int quantity) {
        AfterSaleApplyCommand command = new AfterSaleApplyCommand();
        command.setOrderId(5L);
        command.setType("REFUND_ONLY");
        command.setReason("质量问题");
        AfterSaleApplyCommand.ApplyLine line = new AfterSaleApplyCommand.ApplyLine();
        line.setOrderItemId(orderItemId);
        line.setQuantity(quantity);
        command.getLines().add(line);
        return command;
    }

    @Test
    @DisplayName("申请售后：占用行额度、落单、返回 ID")
    void applyOccupiesQuotaAndInserts() {
        MallOrder order = paidOrder();
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(afterSaleRepository.occupyItemQuota(eq(11L), eq(1), any())).thenReturn(true);
        when(afterSaleRepository.insert(any(MallAfterSale.class))).thenAnswer(invocation -> {
            MallAfterSale afterSale = invocation.getArgument(0);
            afterSale.setId(88L);
            return 88L;
        });

        Long id = afterSaleAppService.apply(command(11L, 1), buyer);

        assertThat(id).isEqualTo(88L);
        ArgumentCaptor<MallAfterSale> captor = ArgumentCaptor.forClass(MallAfterSale.class);
        verify(afterSaleRepository).insert(captor.capture());
        assertThat(captor.getValue().getRefundAmount()).isEqualByComparingTo("19.90");
        assertThat(captor.getValue().getStatus()).isEqualTo(AfterSaleStatus.CREATED);
        assertThat(captor.getValue().getType()).isEqualTo(AfterSaleType.REFUND_ONLY);
        assertThat(captor.getValue().getAfterSaleNo()).startsWith("AS");
        verify(afterSaleRepository).occupyItemQuota(eq(11L), eq(1), any());
        verify(orderRepository).appendLog(eq(5L), any(OrderLog.class));
    }

    @Test
    @DisplayName("申请售后：非本人订单拒绝")
    void applyRejectsStranger() {
        MallActor stranger = new MallActor(8L, "stranger", Set.of(), Set.of());
        when(orderRepository.findById(5L)).thenReturn(Optional.of(paidOrder()));

        assertThatThrownBy(() -> afterSaleAppService.apply(command(11L, 1), stranger))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_NOT_OWNER));
        verify(afterSaleRepository, never()).insert(any(MallAfterSale.class));
    }

    @Test
    @DisplayName("申请售后：待支付订单不可退")
    void applyRejectsPendingPayment() {
        MallOrder order = paidOrder();
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> afterSaleAppService.apply(command(11L, 1), buyer))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.ORDER_REFUND_NOT_ALLOWED));
    }

    @Test
    @DisplayName("申请售后：退数量超过可退数量拒绝")
    void applyRejectsOverQuantity() {
        when(orderRepository.findById(5L)).thenReturn(Optional.of(paidOrder()));

        assertThatThrownBy(() -> afterSaleAppService.apply(command(11L, 3), buyer))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_QUANTITY_EXCEEDED));
        verify(afterSaleRepository, never()).insert(any(MallAfterSale.class));
    }

    @Test
    @DisplayName("申请售后：已退完的行不可再退")
    void applyRejectsFullyRefundedLine() {
        MallOrder order = paidOrder();
        order.getItems().get(0).setRefundedQuantity(2);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> afterSaleAppService.apply(command(11L, 1), buyer))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_QUANTITY_EXCEEDED));
    }

    @Test
    @DisplayName("申请售后：同一商品行重复提交拒绝（否则一次申请就能翻倍占额度）")
    void applyRejectsDuplicateLine() {
        when(orderRepository.findById(5L)).thenReturn(Optional.of(paidOrder()));
        AfterSaleApplyCommand command = command(11L, 1);
        AfterSaleApplyCommand.ApplyLine second = new AfterSaleApplyCommand.ApplyLine();
        second.setOrderItemId(11L);
        second.setQuantity(1);
        command.getLines().add(second);

        assertThatThrownBy(() -> afterSaleAppService.apply(command, buyer))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_INVALID));
    }

    @Test
    @DisplayName("申请售后：订单中不存在该商品行拒绝")
    void applyRejectsUnknownLine() {
        when(orderRepository.findById(5L)).thenReturn(Optional.of(paidOrder()));

        assertThatThrownBy(() -> afterSaleAppService.apply(command(999L, 1), buyer))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_NOT_FOUND));
    }

    @Test
    @DisplayName("申请售后：额度占用失败（并发抢同一行）直接拒绝")
    void applyRejectsWhenQuotaMissed() {
        when(orderRepository.findById(5L)).thenReturn(Optional.of(paidOrder()));
        when(afterSaleRepository.occupyItemQuota(anyLong(), anyInt(), any())).thenReturn(false);

        assertThatThrownBy(() -> afterSaleAppService.apply(command(11L, 1), buyer))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_QUANTITY_EXCEEDED));
        verify(afterSaleRepository, never()).insert(any(MallAfterSale.class));
    }

    @Test
    @DisplayName("审核通过：以售后单号作幂等键向支付侧发起退款")
    void approveRefundsWithIdempotencyKey() {
        MallAfterSale afterSale = approvedPending();
        when(afterSaleRepository.findById(88L)).thenReturn(Optional.of(afterSale));
        when(orderRepository.findById(5L)).thenReturn(Optional.of(paidOrder()));

        afterSaleAppService.approve(88L, "同意", operator);

        assertThat(afterSale.getStatus()).isEqualTo(AfterSaleStatus.APPROVED);
        verify(payInternalClient).refund("MO20261106000001", afterSale.getAfterSaleNo(),
                afterSale.getRefundAmount(), "质量问题");
        verify(afterSaleRepository).update(afterSale);
    }

    @Test
    @DisplayName("审核通过：订单剩余可退金额不足时拒绝")
    void approveRejectsWhenRemainingInsufficient() {
        MallAfterSale afterSale = approvedPending();
        MallOrder order = paidOrder();
        order.setRefundAmount(new BigDecimal("30.00"));
        when(afterSaleRepository.findById(88L)).thenReturn(Optional.of(afterSale));
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> afterSaleAppService.approve(88L, "同意", operator))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_AMOUNT_EXCEEDED));
        verify(payInternalClient, never()).refund(anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("审核通过：订单无支付单时拒绝")
    void approveRejectsWhenNoPayOrder() {
        MallAfterSale afterSale = approvedPending();
        MallOrder order = paidOrder();
        order.setPayNo(null);
        when(afterSaleRepository.findById(88L)).thenReturn(Optional.of(afterSale));
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> afterSaleAppService.approve(88L, "同意", operator))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.ORDER_REFUND_FAILED));
    }

    @Test
    @DisplayName("审核：非运营角色拒绝")
    void approveRejectsBuyer() {
        assertThatThrownBy(() -> afterSaleAppService.approve(88L, "同意", buyer))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.ORDER_FORBIDDEN));
        verify(payInternalClient, never()).refund(anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("驳回：释放行额度，商品可以重新申请售后")
    void rejectReleasesQuota() {
        MallAfterSale afterSale = approvedPending();
        when(afterSaleRepository.findById(88L)).thenReturn(Optional.of(afterSale));

        afterSaleAppService.reject(88L, "不符合条件", operator);

        assertThat(afterSale.getStatus()).isEqualTo(AfterSaleStatus.REJECTED);
        verify(afterSaleRepository).releaseItemQuota(eq(11L), eq(2), any());
        verify(payInternalClient, never()).refund(anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("撤销：买家可撤销本人待审核售后，并释放额度")
    void cancelReleasesQuota() {
        MallAfterSale afterSale = approvedPending();
        when(afterSaleRepository.findById(88L)).thenReturn(Optional.of(afterSale));

        afterSaleAppService.cancel(88L, buyer);

        assertThat(afterSale.getStatus()).isEqualTo(AfterSaleStatus.CANCELLED);
        verify(afterSaleRepository).releaseItemQuota(eq(11L), eq(2), any());
    }

    @Test
    @DisplayName("撤销：非本人且非运营拒绝")
    void cancelRejectsStranger() {
        MallAfterSale afterSale = approvedPending();
        when(afterSaleRepository.findById(88L)).thenReturn(Optional.of(afterSale));
        MallActor stranger = new MallActor(8L, "stranger", Set.of(), Set.of());

        assertThatThrownBy(() -> afterSaleAppService.cancel(88L, stranger))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_NOT_OWNER));
    }

    @Test
    @DisplayName("退款回调：结售后单并累计订单退款金额")
    void markRefundedClosesAfterSale() {
        MallAfterSale afterSale = approvedPending();
        afterSale.approve(99L, "operator", "同意");
        when(orderRepository.findByOrderNo("MO20261106000001")).thenReturn(Optional.of(paidOrder()));
        when(afterSaleRepository.findByAfterSaleNo("AS20261107000001")).thenReturn(Optional.of(afterSale));
        when(afterSaleRepository.applyOrderRefund(5L, new BigDecimal("39.80"))).thenReturn(true);

        afterSaleAppService.markRefunded("MO20261106000001", "AS20261107000001", new BigDecimal("39.80"));

        assertThat(afterSale.getStatus()).isEqualTo(AfterSaleStatus.REFUNDED);
        verify(afterSaleRepository).applyOrderRefund(5L, new BigDecimal("39.80"));
        verify(orderRepository).appendLog(eq(5L), any(OrderLog.class));
    }

    @Test
    @DisplayName("退款回调：已退款重复到达幂等返回，不重复累计")
    void markRefundedIsIdempotent() {
        MallAfterSale afterSale = approvedPending();
        afterSale.approve(99L, "operator", "同意");
        afterSale.markRefunded("AS20261107000001", null);
        when(orderRepository.findByOrderNo("MO20261106000001")).thenReturn(Optional.of(paidOrder()));
        when(afterSaleRepository.findByAfterSaleNo("AS20261107000001")).thenReturn(Optional.of(afterSale));

        afterSaleAppService.markRefunded("MO20261106000001", "AS20261107000001", new BigDecimal("39.80"));

        verify(afterSaleRepository, never()).applyOrderRefund(anyLong(), any());
        verify(afterSaleRepository, never()).update(any(MallAfterSale.class));
    }

    @Test
    @DisplayName("退款回调：查不到售后单时按累计额推算增量，不抛错")
    void markRefundedWithoutAfterSale() {
        when(orderRepository.findByOrderNo("MO20261106000001")).thenReturn(Optional.of(paidOrder()));
        when(afterSaleRepository.findByAfterSaleNo("RF20261107000001")).thenReturn(Optional.empty());
        when(afterSaleRepository.applyOrderRefund(5L, new BigDecimal("39.80"))).thenReturn(true);

        afterSaleAppService.markRefunded("MO20261106000001", "RF20261107000001", new BigDecimal("39.80"));

        verify(afterSaleRepository).applyOrderRefund(5L, new BigDecimal("39.80"));
        verify(afterSaleRepository, never()).update(any(MallAfterSale.class));
    }

    @Test
    @DisplayName("整单退款：覆盖全部可退行、订单转 REFUNDING、以售后单号发起退款")
    void applyFullRefundCoversAllLines() {
        MallOrder order = paidOrder();
        when(afterSaleRepository.occupyItemQuota(eq(11L), eq(2), any())).thenReturn(true);
        when(afterSaleRepository.insert(any(MallAfterSale.class))).thenAnswer(invocation -> {
            MallAfterSale afterSale = invocation.getArgument(0);
            afterSale.setId(88L);
            return 88L;
        });

        String afterSaleNo = afterSaleAppService.applyFullRefund(order, "管理端整单退款", operator);

        assertThat(afterSaleNo).startsWith("AS");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDING);
        ArgumentCaptor<MallAfterSale> captor = ArgumentCaptor.forClass(MallAfterSale.class);
        verify(afterSaleRepository).insert(captor.capture());
        assertThat(captor.getValue().getRefundAmount()).isEqualByComparingTo("39.80");
        assertThat(captor.getValue().getStatus()).isEqualTo(AfterSaleStatus.APPROVED);
        assertThat(captor.getValue().getItems()).hasSize(1);
        assertThat(captor.getValue().getItems().get(0).getQuantity()).isEqualTo(2);
        verify(payInternalClient).refund(eq("MO20261106000001"), eq(afterSaleNo),
                eq(new BigDecimal("39.80")), anyString());
    }

    @Test
    @DisplayName("整单退款：已无商品可退时拒绝")
    void applyFullRefundRejectsWhenNothingRefundable() {
        MallOrder order = paidOrder();
        order.getItems().get(0).setRefundedQuantity(2);

        assertThatThrownBy(() -> afterSaleAppService.applyFullRefund(order, "退款", operator))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_QUANTITY_EXCEEDED));
        verify(payInternalClient, never()).refund(anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("分页：管理端需运营权限，非运营拒绝")
    void pageRejectsBuyerForManageAll() {
        assertThatThrownBy(() -> afterSaleAppService.page(null, null, 1L, 10L, true, buyer))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.ORDER_FORBIDDEN));
    }

    @Test
    @DisplayName("分页：买家只查本人，不传 userId 过滤")
    void pageFiltersByBuyer() {
        when(afterSaleRepository.page(eq(7L), any(), any(), anyLong(), anyInt())).thenReturn(List.of());
        when(afterSaleRepository.count(eq(7L), any(), any())).thenReturn(0L);

        PageResult<AfterSaleView> result = afterSaleAppService.page(null, null, 1L, 10L, false, buyer);

        assertThat(result.getTotal()).isZero();
        verify(afterSaleRepository).page(eq(7L), any(), any(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("详情：非本人且非运营拒绝")
    void detailRejectsStranger() {
        when(afterSaleRepository.findById(88L)).thenReturn(Optional.of(approvedPending()));
        MallActor stranger = new MallActor(8L, "stranger", Set.of(), Set.of());

        assertThatThrownBy(() -> afterSaleAppService.detail(88L, stranger))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_NOT_OWNER));
    }

    @Test
    @DisplayName("详情：售后单不存在抛 NOT_FOUND")
    void detailRejectsMissing() {
        when(afterSaleRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> afterSaleAppService.detail(404L, operator))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.AFTER_SALE_NOT_FOUND));
    }

    @Test
    @DisplayName("部分退款按行实付额计算，不按快照价")
    void applyUsesLineNetAmount() {
        MallOrder order = discountedOrder();
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(afterSaleRepository.occupyItemQuota(eq(11L), eq(1), any())).thenReturn(true);
        when(afterSaleRepository.insert(any(MallAfterSale.class))).thenAnswer(invocation -> {
            MallAfterSale afterSale = invocation.getArgument(0);
            afterSale.setId(88L);
            return 1L;
        });

        afterSaleAppService.apply(command(11L, 1), buyer);

        ArgumentCaptor<MallAfterSale> captor = ArgumentCaptor.forClass(MallAfterSale.class);
        verify(afterSaleRepository).insert(captor.capture());
        // 行实付 39.80 - 3.99 + 1.20 = 37.01，退 1 件 = 18.505 → 18.51
        assertThat(captor.getValue().getRefundAmount()).isEqualByComparingTo("18.51");
    }

    @Test
    @DisplayName("同一行分次退完：最后一次取剩余金额，合计等于行实付额")
    void applyTakesRemainingOnFinalRefund() {
        MallOrder order = discountedOrder();
        order.getItems().get(0).setRefundedQuantity(1);
        order.getItems().get(0).setRefundedAmount(new BigDecimal("18.51"));
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(afterSaleRepository.occupyItemQuota(eq(11L), eq(1), any())).thenReturn(true);
        when(afterSaleRepository.insert(any(MallAfterSale.class))).thenAnswer(invocation -> {
            MallAfterSale afterSale = invocation.getArgument(0);
            afterSale.setId(89L);
            return 1L;
        });

        afterSaleAppService.apply(command(11L, 1), buyer);

        ArgumentCaptor<MallAfterSale> captor = ArgumentCaptor.forClass(MallAfterSale.class);
        verify(afterSaleRepository).insert(captor.capture());
        // 37.01 - 18.51 = 18.50，两次相加严格等于行实付额
        assertThat(captor.getValue().getRefundAmount()).isEqualByComparingTo("18.50");
    }

    @Test
    @DisplayName("整单退满后把券退回未使用")
    void markRefundedReleasesCouponWhenFullyRefunded() {
        MallOrder order = paidOrder();
        order.setCouponUserId(77L);
        order.setStatus(OrderStatus.REFUNDING);
        when(orderRepository.findByOrderNo("MO20261106000001")).thenReturn(Optional.of(order));
        when(afterSaleRepository.findByAfterSaleNo("AS20261107000001"))
                .thenReturn(Optional.of(approvedAfterSale()));
        when(afterSaleRepository.applyOrderRefund(eq(5L), any())).thenReturn(true);
        MallOrder refunded = paidOrder();
        refunded.setCouponUserId(77L);
        refunded.setStatus(OrderStatus.REFUNDED);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(refunded));

        afterSaleAppService.markRefunded("MO20261106000001", "AS20261107000001", new BigDecimal("39.80"));

        verify(couponAppService).releaseByOrderNo("MO20261106000001");
    }

    @Test
    @DisplayName("部分退款未退满时不动券")
    void markRefundedKeepsCouponWhenPartiallyRefunded() {
        MallOrder order = paidOrder();
        order.setCouponUserId(77L);
        when(orderRepository.findByOrderNo("MO20261106000001")).thenReturn(Optional.of(order));
        when(afterSaleRepository.findByAfterSaleNo("AS20261107000001"))
                .thenReturn(Optional.of(approvedAfterSale()));
        when(afterSaleRepository.applyOrderRefund(eq(5L), any())).thenReturn(true);
        MallOrder stillPaid = paidOrder();
        stillPaid.setCouponUserId(77L);
        stillPaid.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(stillPaid));

        afterSaleAppService.markRefunded("MO20261106000001", "AS20261107000001", new BigDecimal("19.90"));

        verify(couponAppService, never()).releaseByOrderNo(anyString());
    }

    /**
     * 构造带优惠与运费分摊的已支付订单。
     *
     * <p>行实付 39.80 - 3.99 + 1.20 = 37.01，除不尽，用来验证末次取剩余。</p>
     *
     * @return 订单
     */
    private MallOrder discountedOrder() {
        MallOrder order = paidOrder();
        OrderItem item = order.getItems().get(0);
        item.setShareDiscount(new BigDecimal("3.99"));
        item.setShareFreight(new BigDecimal("1.20"));
        return order;
    }

    /**
     * 构造一张已审核通过的售后单（回调结单的前置状态）。
     *
     * @return 售后单
     */
    private MallAfterSale approvedAfterSale() {
        MallAfterSale afterSale = approvedPending();
        afterSale.approve(99L, "operator", "审核通过");
        return afterSale;
    }

    /**
     * 构造一张待审核售后单（一行 19.90 × 2）。
     *
     * @return 售后单
     */
    private MallAfterSale approvedPending() {
        AfterSaleItem item = new AfterSaleItem();
        item.setOrderItemId(11L);
        item.setSpuId(31L);
        item.setSkuId(21L);
        item.setSpuName("测试商品");
        item.setSkuCode("SKU-1");
        item.setUnitPrice(new BigDecimal("19.90"));
        item.setQuantity(2);
        item.setAmount(new BigDecimal("39.80"));
        List<AfterSaleItem> items = new ArrayList<>();
        items.add(item);
        MallAfterSale afterSale = MallAfterSale.create("AS20261107000001", 5L, "MO20261106000001", 7L,
                AfterSaleType.REFUND_ONLY, "质量问题", "描述", null, items);
        afterSale.setId(88L);
        return afterSale;
    }
}
