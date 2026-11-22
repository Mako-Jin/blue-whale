package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pay.channel.ChannelCallbackRequest;
import com.bluewhale.pay.channel.PayChannelGateway;
import com.bluewhale.pay.enums.PayCallbackEvent;
import com.bluewhale.pay.enums.PayCallbackResult;
import com.bluewhale.pay.enums.PayOrderStatus;
import com.bluewhale.pay.enums.PayRefundStatus;
import com.bluewhale.pay.mapper.PayChannelCallbackLogMapper;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.PayRefundMapper;
import com.bluewhale.pay.po.PayChannelCallbackLogPo;
import com.bluewhale.pay.po.PayOrderPo;
import com.bluewhale.pay.po.PayRefundPo;
import com.bluewhale.pay.service.PaySettlementService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PayChannelCallbackServiceImpl} 单元测试：验签失败、金额不符、未知单号、
 * 重复回调幂等、失败重试处理与退款/关单事件。
 *
 * @author BlueWhale
 */
class PayChannelCallbackServiceImplTest {

    /** 日志主键 */
    private static final long LOG_ID = 10L;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PayOrderPo.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PayRefundPo.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PayChannelCallbackLogPo.class);
    }

    private final PayChannelGateway payChannelGateway = mock(PayChannelGateway.class);

    private final PayOrderMapper payOrderMapper = mock(PayOrderMapper.class);

    private final PayRefundMapper payRefundMapper = mock(PayRefundMapper.class);

    private final PayChannelCallbackLogMapper callbackLogMapper = mock(PayChannelCallbackLogMapper.class);

    private final PaySettlementService paySettlementService = mock(PaySettlementService.class);

    private final TransactionTemplate transactionTemplate = transactionTemplate();

    private final PayChannelCallbackServiceImpl service = new PayChannelCallbackServiceImpl(
            payChannelGateway, payOrderMapper, payRefundMapper, callbackLogMapper,
            paySettlementService, transactionTemplate);

    /**
     * 构造不落真实事务的模板：数据库操作全部为 mock，只需走通事务流程。
     *
     * @return 事务模板
     */
    private static TransactionTemplate transactionTemplate() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new TransactionTemplate(manager);
    }

    @BeforeEach
    void setUp() {
        when(payChannelGateway.successResponse(anyString())).thenReturn("SUCCESS");
        when(payChannelGateway.failureResponse(anyString())).thenReturn("FAIL");
        doAnswer(invocation -> {
            PayChannelCallbackLogPo row = invocation.getArgument(0);
            row.setId(LOG_ID);
            return 1;
        }).when(callbackLogMapper).insert(any(PayChannelCallbackLogPo.class));
    }

    @Test
    @DisplayName("PAID 回调：复用统一落账并回写 PROCESSED")
    void shouldProcessPaidCallback() {
        mockPaidRequest("PAY001", "100.00");
        PayOrderPo order = order(PayOrderStatus.PAYING.code());
        when(payOrderMapper.selectOne(ArgumentMatchers.<Wrapper<PayOrderPo>>any())).thenReturn(order);
        when(callbackLogMapper.selectByIdForUpdate(LOG_ID)).thenReturn(logRow(PayCallbackResult.FAILED));
        when(paySettlementService.markPaid(any(), any())).thenReturn(true);

        String body = service.handle("mock", "{}", Map.of());

        assertEquals("SUCCESS", body);
        PayChannelCallbackLogPo updated = captureUpdated();
        assertEquals(PayCallbackResult.PROCESSED.code(), updated.getResult());
        assertEquals(Long.valueOf(1L), updated.getTenantId());
        assertNull(TenantContext.get());
        verify(paySettlementService).markPaid(eq(order), any());
    }

    @Test
    @DisplayName("验签失败：落 REJECTED/SIGN_INVALID 并返回失败体，不推进状态")
    void shouldRejectWhenSignatureInvalid() {
        when(payChannelGateway.parseCallback(anyString(), anyString(), anyMap()))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR, "签名不合法"));

        String body = service.handle("MOCK", "bad-body", Map.of());

        assertEquals("FAIL", body);
        ArgumentCaptor<PayChannelCallbackLogPo> captor =
                ArgumentCaptor.forClass(PayChannelCallbackLogPo.class);
        verify(callbackLogMapper).insert(captor.capture());
        assertEquals(PayCallbackResult.REJECTED.code(), captor.getValue().getResult());
        assertEquals("SIGN_INVALID", captor.getValue().getRejectReason());
        verify(paySettlementService, never()).markPaid(any(), any());
    }

    @Test
    @DisplayName("金额不符：落 REJECTED/AMOUNT_MISMATCH，禁止自动改状态")
    void shouldRejectWhenAmountMismatch() {
        mockPaidRequest("PAY001", "99.00");
        when(payOrderMapper.selectOne(ArgumentMatchers.<Wrapper<PayOrderPo>>any()))
                .thenReturn(order(PayOrderStatus.PAYING.code()));
        when(callbackLogMapper.selectByIdForUpdate(LOG_ID)).thenReturn(logRow(PayCallbackResult.FAILED));

        String body = service.handle("MOCK", "{}", Map.of());

        assertEquals("FAIL", body);
        PayChannelCallbackLogPo updated = captureUpdated();
        assertEquals(PayCallbackResult.REJECTED.code(), updated.getResult());
        assertEquals("AMOUNT_MISMATCH", updated.getRejectReason());
        verify(paySettlementService, never()).markPaid(any(), any());
    }

    @Test
    @DisplayName("未知单号：落 REJECTED/ORDER_NOT_FOUND")
    void shouldRejectWhenOrderNotFound() {
        when(payChannelGateway.parseCallback(anyString(), anyString(), anyMap()))
                .thenReturn(request(PayCallbackEvent.PAID, "CH-1", "PAY404", null, "100.00", "CNY"));
        when(payOrderMapper.selectTenantIdsByOrderNo("PAY404")).thenReturn(List.of());
        when(callbackLogMapper.selectByIdForUpdate(LOG_ID)).thenReturn(logRow(PayCallbackResult.FAILED));

        String body = service.handle("MOCK", "{}", Map.of());

        assertEquals("FAIL", body);
        PayChannelCallbackLogPo updated = captureUpdated();
        assertEquals(PayCallbackResult.REJECTED.code(), updated.getResult());
        assertEquals("ORDER_NOT_FOUND", updated.getRejectReason());
        verify(paySettlementService, never()).markPaid(any(), any());
    }

    @Test
    @DisplayName("重复回调：已有终态记录时直接返回成功，不重复推进")
    void shouldTreatDuplicateAsSuccess() {
        when(payChannelGateway.parseCallback(anyString(), anyString(), anyMap()))
                .thenReturn(request(PayCallbackEvent.PAID, "CH-1", "PAY001", null, "100.00", "CNY"));
        doThrow(new DuplicateKeyException("dup"))
                .when(callbackLogMapper).insert(any(PayChannelCallbackLogPo.class));
        when(callbackLogMapper.selectOne(ArgumentMatchers.<Wrapper<PayChannelCallbackLogPo>>any()))
                .thenReturn(logRow(PayCallbackResult.PROCESSED));

        String body = service.handle("MOCK", "{}", Map.of());

        assertEquals("SUCCESS", body);
        verify(callbackLogMapper, never()).selectByIdForUpdate(any());
        verify(paySettlementService, never()).markPaid(any(), any());
    }

    @Test
    @DisplayName("上次处理中断：幂等键已存在但为 FAILED 时重新处理")
    void shouldReprocessWhenPreviousAttemptFailed() {
        mockPaidRequest("PAY001", "100.00");
        doThrow(new DuplicateKeyException("dup"))
                .when(callbackLogMapper).insert(any(PayChannelCallbackLogPo.class));
        when(callbackLogMapper.selectOne(ArgumentMatchers.<Wrapper<PayChannelCallbackLogPo>>any()))
                .thenReturn(logRow(PayCallbackResult.FAILED));
        when(callbackLogMapper.selectByIdForUpdate(LOG_ID))
                .thenReturn(logRow(PayCallbackResult.FAILED));
        when(payOrderMapper.selectOne(ArgumentMatchers.<Wrapper<PayOrderPo>>any()))
                .thenReturn(order(PayOrderStatus.PAYING.code()));
        when(paySettlementService.markPaid(any(), any())).thenReturn(true);

        String body = service.handle("MOCK", "{}", Map.of());

        assertEquals("SUCCESS", body);
        assertEquals(PayCallbackResult.PROCESSED.code(), captureUpdated().getResult());
        verify(paySettlementService).markPaid(any(), any());
    }

    @Test
    @DisplayName("REFUNDED 回调：按退款单号定位并复用统一退款落账")
    void shouldProcessRefundCallback() {
        when(payChannelGateway.parseCallback(anyString(), anyString(), anyMap()))
                .thenReturn(request(PayCallbackEvent.REFUNDED, "RF-CH-1", "PAY001", "RF1", "30.00", "CNY"));
        when(payOrderMapper.selectTenantIdsByOrderNo("PAY001")).thenReturn(List.of(1L));
        PayOrderPo order = order(PayOrderStatus.PAID.code());
        when(payOrderMapper.selectOne(ArgumentMatchers.<Wrapper<PayOrderPo>>any())).thenReturn(order);
        PayRefundPo refund = refund();
        when(payRefundMapper.selectOne(ArgumentMatchers.<Wrapper<PayRefundPo>>any())).thenReturn(refund);
        when(callbackLogMapper.selectByIdForUpdate(LOG_ID)).thenReturn(logRow(PayCallbackResult.FAILED));

        String body = service.handle("MOCK", "{}", Map.of());

        assertEquals("SUCCESS", body);
        assertEquals(PayCallbackResult.PROCESSED.code(), captureUpdated().getResult());
        verify(paySettlementService).markRefunded(eq(order), eq(refund),
                eq(new BigDecimal("30.00")), any());
    }

    @Test
    @DisplayName("CLOSED 回调：本地已支付视为状态冲突，不取消支付单")
    void shouldRejectCloseWhenAlreadyPaid() {
        when(payChannelGateway.parseCallback(anyString(), anyString(), anyMap()))
                .thenReturn(request(PayCallbackEvent.CLOSED, "CL-CH-1", "PAY001", null, null, null));
        when(payOrderMapper.selectTenantIdsByOrderNo("PAY001")).thenReturn(List.of(1L));
        when(payOrderMapper.selectOne(ArgumentMatchers.<Wrapper<PayOrderPo>>any()))
                .thenReturn(order(PayOrderStatus.PAID.code()));
        when(payOrderMapper.closeIfUnpaid(1L)).thenReturn(0);
        when(payOrderMapper.selectById(1L)).thenReturn(order(PayOrderStatus.PAID.code()));
        when(callbackLogMapper.selectByIdForUpdate(LOG_ID)).thenReturn(logRow(PayCallbackResult.FAILED));

        String body = service.handle("MOCK", "{}", Map.of());

        assertEquals("FAIL", body);
        PayChannelCallbackLogPo updated = captureUpdated();
        assertEquals(PayCallbackResult.REJECTED.code(), updated.getResult());
        assertEquals("STATUS_CONFLICT", updated.getRejectReason());
    }

    /**
     * 桩：支付回调解析成功 + 租户反查命中。
     *
     * @param orderNo 支付单号
     * @param amount  渠道金额
     */
    private void mockPaidRequest(String orderNo, String amount) {
        when(payChannelGateway.parseCallback(anyString(), anyString(), anyMap()))
                .thenReturn(request(PayCallbackEvent.PAID, "CH-1", orderNo, null, amount, "CNY"));
        when(payOrderMapper.selectTenantIdsByOrderNo(orderNo)).thenReturn(List.of(1L));
    }

    /**
     * 读取最近一次日志更新。
     *
     * @return 被更新的日志实体
     */
    private PayChannelCallbackLogPo captureUpdated() {
        ArgumentCaptor<PayChannelCallbackLogPo> captor =
                ArgumentCaptor.forClass(PayChannelCallbackLogPo.class);
        verify(callbackLogMapper).updateById(captor.capture());
        return captor.getValue();
    }

    /**
     * 构造回调请求。
     *
     * @param event      事件
     * @param tradeNo    渠道流水号
     * @param outTradeNo 支付单号
     * @param refundNo   退款单号
     * @param amount     金额
     * @param currency   币种
     * @return 回调请求
     */
    private static ChannelCallbackRequest request(PayCallbackEvent event, String tradeNo, String outTradeNo,
                                                  String refundNo, String amount, String currency) {
        return new ChannelCallbackRequest("MOCK", tradeNo, outTradeNo, refundNo, event,
                amount == null ? null : new BigDecimal(amount), currency, "{}", Map.of());
    }

    /**
     * 构造支付单。
     *
     * @param status 状态
     * @return 支付单
     */
    private static PayOrderPo order(String status) {
        PayOrderPo order = new PayOrderPo();
        order.setId(1L);
        order.setOrderNo("PAY001");
        order.setAmount(new BigDecimal("100.00"));
        order.setCurrency("CNY");
        order.setChannel("MOCK");
        order.setStatus(status);
        order.setRefundAmount(BigDecimal.ZERO);
        return order;
    }

    /**
     * 构造退款单。
     *
     * @return 退款单
     */
    private static PayRefundPo refund() {
        PayRefundPo refund = new PayRefundPo();
        refund.setId(9L);
        refund.setRefundNo("RF1");
        refund.setPayOrderId(1L);
        refund.setPayOrderNo("PAY001");
        refund.setAmount(new BigDecimal("30.00"));
        refund.setStatus(PayRefundStatus.CREATED.code());
        return refund;
    }

    /**
     * 构造回调日志行。
     *
     * @param result 结果
     * @return 日志行
     */
    private static PayChannelCallbackLogPo logRow(PayCallbackResult result) {
        PayChannelCallbackLogPo row = new PayChannelCallbackLogPo();
        row.setId(LOG_ID);
        row.setResult(result.code());
        return row;
    }
}
