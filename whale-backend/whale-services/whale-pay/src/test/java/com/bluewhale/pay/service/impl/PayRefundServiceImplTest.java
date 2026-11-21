package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.pay.channel.PayChannelGateway;
import com.bluewhale.pay.channel.PayChannelResult;
import com.bluewhale.pay.po.PayNotifyTaskPo;
import com.bluewhale.pay.po.PayOrderPo;
import com.bluewhale.pay.po.PayRefundPo;
import com.bluewhale.pay.po.PayTransactionPo;
import com.bluewhale.pay.enums.PayNotifyEvent;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.PayRefundMapper;
import com.bluewhale.pay.mapper.PayTransactionMapper;
import com.bluewhale.pay.service.PayNotifyService;
import com.bluewhale.pay.vo.PayRefundVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PayRefundServiceImpl} 退款单流程单元测试：创建-渠道-累计-通知主链路、
 * 超额拒绝、金额校验与整单退款。
 *
 * @author BlueWhale
 */
class PayRefundServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;

    /**
     * 初始化 MyBatis-Plus 实体元数据缓存。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PayOrderPo.class);
        TableInfoHelper.initTableInfo(assistant, PayRefundPo.class);
        TableInfoHelper.initTableInfo(assistant, PayNotifyTaskPo.class);
    }

    private final PayOrderMapper payOrderMapper = mock(PayOrderMapper.class);

    private final PayRefundMapper payRefundMapper = mock(PayRefundMapper.class);

    private final PayTransactionMapper payTransactionMapper = mock(PayTransactionMapper.class);

    private final PayChannelGateway payChannelGateway = mock(PayChannelGateway.class);

    private final PayNotifyService payNotifyService = mock(PayNotifyService.class);

    private final PayRefundServiceImpl service = new PayRefundServiceImpl(
            payOrderMapper, payRefundMapper, payTransactionMapper,
            payChannelGateway, payNotifyService);

    private static PayOrderPo paidOrder() {
        PayOrderPo order = new PayOrderPo();
        order.setId(10L);
        order.setOrderNo("MO20261107001");
        order.setAmount(new BigDecimal("100.00"));
        order.setRefundAmount(BigDecimal.ZERO);
        order.setStatus("PAID");
        order.setCurrency("CNY");
        order.setChannel("MOCK");
        order.setBizType("MALL_ORDER");
        order.setNotifyUrl("whale-mall/mall/internal/order/refunded");
        return order;
    }

    private void stubHappyPath() {
        PayOrderPo order = paidOrder();
        PayNotifyTaskPo task = new PayNotifyTaskPo();
        task.setId(77L);
        when(payOrderMapper.selectOne(any())).thenReturn(order);
        when(payOrderMapper.selectById(10L)).thenReturn(order);
        when(payOrderMapper.applyRefund(eq(10L), notNull())).thenReturn(1);
        when(payChannelGateway.refund(any(), any(), notNull()))
                .thenReturn(new PayChannelResult("MOCK-R1", "REFUNDED"));
        when(payNotifyService.enqueue(any(), any(), any(), any())).thenReturn(task);
    }

    @Test
    @DisplayName("refundByNo：全额退款成功并累计、落流水、投递通知")
    void shouldRefundFully() {
        stubHappyPath();

        PayRefundVO vo = service.refundByNo("MO20261107001", new BigDecimal("100.00"), "测试退款");

        assertEquals("SUCCESS", vo.getStatus());
        assertEquals("MOCK-R1", vo.getChannelRefundNo());
        ArgumentCaptor<PayRefundPo> captor = ArgumentCaptor.forClass(PayRefundPo.class);
        verify(payRefundMapper).insert(captor.capture());
        assertTrue(captor.getValue().getRefundNo().startsWith("RF"));
        ArgumentCaptor<PayTransactionPo> txCaptor = ArgumentCaptor.forClass(PayTransactionPo.class);
        verify(payTransactionMapper).insert(txCaptor.capture());
        assertEquals("REFUND", txCaptor.getValue().getType());
        verify(payNotifyService).enqueue(eq(PayNotifyEvent.REFUNDED), any(), any(), any());
        verify(payNotifyService).dispatchAfterCommit(77L);
    }

    @Test
    @DisplayName("refundByNo：原子累计 0 行（超额/状态不允许）抛业务异常")
    void shouldRejectWhenApplyRefundMissed() {
        stubHappyPath();
        when(payOrderMapper.applyRefund(eq(10L), notNull())).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> service.refundByNo("MO20261107001", new BigDecimal("50.00"), "测试"));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payTransactionMapper, never()).insert(any(PayTransactionPo.class));
    }

    @Test
    @DisplayName("refundByNo：金额必须大于 0 且最多 2 位小数")
    void shouldValidateAmount() {
        BizException zero = assertThrows(BizException.class,
                () -> service.refundByNo("MO20261107001", BigDecimal.ZERO, "测试"));
        assertEquals(PARAM_ERROR_CODE, zero.getCode());

        BizException scale = assertThrows(BizException.class,
                () -> service.refundByNo("MO20261107001", new BigDecimal("1.999"), "测试"));
        assertEquals(PARAM_ERROR_CODE, scale.getCode());

        verify(payRefundMapper, never()).insert(any(PayRefundPo.class));
    }

    @Test
    @DisplayName("refundFull：支付单不存在抛 NOT_FOUND")
    void shouldRejectFullRefundWhenMissing() {
        when(payOrderMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.refundFull(99L, "测试"));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    @Test
    @DisplayName("refundFull：已全额退款拒绝再次退款")
    void shouldRejectFullRefundWhenNoRemaining() {
        PayOrderPo order = paidOrder();
        order.setRefundAmount(new BigDecimal("100.00"));
        when(payOrderMapper.selectById(10L)).thenReturn(order);

        BizException ex = assertThrows(BizException.class, () -> service.refundFull(10L, "测试"));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payRefundMapper, never()).insert(any(PayRefundPo.class));
    }

    @Test
    @DisplayName("refundFull：按剩余可退金额整单退")
    void shouldRefundRemainingOnFullRefund() {
        PayOrderPo partially = paidOrder();
        partially.setRefundAmount(new BigDecimal("40.00"));
        when(payOrderMapper.selectOne(any())).thenReturn(partially);
        when(payOrderMapper.selectById(10L)).thenReturn(partially);
        when(payOrderMapper.applyRefund(eq(10L), notNull())).thenReturn(1);
        when(payChannelGateway.refund(any(), any(), notNull()))
                .thenReturn(new PayChannelResult("MOCK-R2", "REFUNDED"));
        when(payNotifyService.enqueue(any(), any(), any(), any()))
                .thenReturn(new PayNotifyTaskPo());

        service.refundFull(10L, "测试");

        ArgumentCaptor<PayRefundPo> captor = ArgumentCaptor.forClass(PayRefundPo.class);
        verify(payRefundMapper).insert(captor.capture());
        assertEquals(new BigDecimal("60.00"), captor.getValue().getAmount());
    }

    @Test
    @DisplayName("detail：退款单不存在抛 NOT_FOUND")
    void shouldThrowWhenDetailMissing() {
        when(payRefundMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.detail(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    @Test
    @DisplayName("page：关键词走模糊匹配分支")
    void shouldPageWithKeyword() {
        when(payRefundMapper.selectPage(any(), ArgumentMatchers.<Wrapper<PayRefundPo>>any()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());

        service.page("RF2026", 1L, 10L);

        verify(payRefundMapper).selectPage(any(), ArgumentMatchers.<Wrapper<PayRefundPo>>any());
    }

    @Test
    @DisplayName("enqueue 未配置回调地址时通知为空不投递")
    void shouldSkipNotifyWhenNoUrl() {
        stubHappyPath();
        PayOrderPo order = paidOrder();
        order.setNotifyUrl(null);
        when(payNotifyService.enqueue(any(), any(), any(), any())).thenReturn(null);

        service.refundByNo("MO20261107001", new BigDecimal("100.00"), "测试");

        verify(payNotifyService).dispatchAfterCommit(isNull());
    }

    @Test
    @DisplayName("refundByNo：同一退款请求号重复调用返回原单，不重复出账")
    void shouldReplaySameRefundNo() {
        PayOrderPo order = paidOrder();
        PayRefundPo existing = new PayRefundPo();
        existing.setRefundNo("AS20261107001");
        existing.setAmount(new BigDecimal("30.00"));
        existing.setStatus("SUCCESS");
        when(payOrderMapper.selectOne(any())).thenReturn(order);
        when(payRefundMapper.selectOne(any())).thenReturn(existing);

        PayRefundVO vo = service.refundByNo("MO20261107001", "AS20261107001", new BigDecimal("30.00"), "网络重试");

        assertEquals("AS20261107001", vo.getRefundNo());
        assertEquals("SUCCESS", vo.getStatus());
        verify(payRefundMapper, never()).insert(any(PayRefundPo.class));
        verify(payChannelGateway, never()).refund(any(), any(), any());
        verify(payOrderMapper, never()).applyRefund(any(), any());
        verify(payTransactionMapper, never()).insert(any(PayTransactionPo.class));
    }

    @Test
    @DisplayName("refundByNo：同一退款请求号换金额重试直接拒绝")
    void shouldRejectReplayWithDifferentAmount() {
        when(payOrderMapper.selectOne(any())).thenReturn(paidOrder());
        PayRefundPo existing = new PayRefundPo();
        existing.setRefundNo("AS20261107001");
        existing.setAmount(new BigDecimal("30.00"));
        when(payRefundMapper.selectOne(any())).thenReturn(existing);

        BizException ex = assertThrows(BizException.class,
                () -> service.refundByNo("MO20261107001", "AS20261107001", new BigDecimal("50.00"), "重试"));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payRefundMapper, never()).insert(any(PayRefundPo.class));
    }

    @Test
    @DisplayName("refundByNo：调用方传入的退款请求号原样落库")
    void shouldUseProvidedRefundNo() {
        stubHappyPath();

        service.refundByNo("MO20261107001", "AS20261107002", new BigDecimal("30.00"), "部分退款");

        ArgumentCaptor<PayRefundPo> captor = ArgumentCaptor.forClass(PayRefundPo.class);
        verify(payRefundMapper).insert(captor.capture());
        assertEquals("AS20261107002", captor.getValue().getRefundNo());
        assertEquals(new BigDecimal("30.00"), captor.getValue().getAmount());
    }
}
