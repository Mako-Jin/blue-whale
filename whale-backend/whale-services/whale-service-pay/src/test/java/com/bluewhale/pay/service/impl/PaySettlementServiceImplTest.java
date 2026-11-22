package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.pay.channel.PayChannelResult;
import com.bluewhale.pay.enums.PayNotifyEvent;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.PayRefundMapper;
import com.bluewhale.pay.mapper.PayTransactionMapper;
import com.bluewhale.pay.po.PayOrderPo;
import com.bluewhale.pay.po.PayRefundPo;
import com.bluewhale.pay.po.PayTransactionPo;
import com.bluewhale.pay.service.PayNotifyService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * {@link PaySettlementServiceImpl} 单元测试：条件抢占幂等、受理中不落账、退款累计失败回滚。
 *
 * @author BlueWhale
 */
class PaySettlementServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PayOrderPo.class);
    }

    private final PayOrderMapper payOrderMapper = mock(PayOrderMapper.class);

    private final PayRefundMapper payRefundMapper = mock(PayRefundMapper.class);

    private final PayTransactionMapper payTransactionMapper = mock(PayTransactionMapper.class);

    private final PayNotifyService payNotifyService = mock(PayNotifyService.class);

    private final TransactionTemplate transactionTemplate = transactionTemplate();

    private final PaySettlementServiceImpl service = new PaySettlementServiceImpl(
            payOrderMapper, payRefundMapper, payTransactionMapper, payNotifyService, transactionTemplate);

    private static TransactionTemplate transactionTemplate() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new TransactionTemplate(manager);
    }

    private static PayOrderPo order(String status) {
        PayOrderPo order = new PayOrderPo();
        order.setId(1L);
        order.setOrderNo("PAY20261122001");
        order.setAmount(new BigDecimal("100.00"));
        order.setCurrency("CNY");
        order.setChannel("MOCK");
        order.setStatus(status);
        return order;
    }

    @Test
    @DisplayName("markPaid：条件抢占成功时写支付流水并投递 PAID 通知")
    void shouldSettleWhenClaimWins() {
        when(payOrderMapper.update(isNull(), ArgumentMatchers.<Wrapper<PayOrderPo>>any())).thenReturn(1);
        when(payNotifyService.enqueue(eq(PayNotifyEvent.PAID), any(), any(), any()))
                .thenReturn(new com.bluewhale.pay.po.PayNotifyTaskPo());

        boolean settled = service.markPaid(order("PAYING"), new PayChannelResult("CH-1"));

        assertTrue(settled);
        ArgumentCaptor<PayTransactionPo> captor = ArgumentCaptor.forClass(PayTransactionPo.class);
        verify(payTransactionMapper).insert(captor.capture());
        assertEquals("PAY", captor.getValue().getType());
        assertEquals("CH-1", captor.getValue().getProviderTradeNo());
        verify(payNotifyService).enqueue(eq(PayNotifyEvent.PAID), any(), any(), any());
    }

    @Test
    @DisplayName("markPaid：并发已推进时返回 false，不重复写流水与通知")
    void shouldSkipWhenAlreadySettled() {
        when(payOrderMapper.update(isNull(), ArgumentMatchers.<Wrapper<PayOrderPo>>any())).thenReturn(0);

        boolean settled = service.markPaid(order("PAYING"), new PayChannelResult("CH-2"));

        assertFalse(settled);
        verify(payTransactionMapper, never()).insert(any(PayTransactionPo.class));
        verify(payNotifyService, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    @DisplayName("markPaid：渠道受理中不落账，订单保持 PAYING")
    void shouldNotSettleWhenAccepted() {
        boolean settled = service.markPaid(order("PAYING"), PayChannelResult.accepted("CH-3"));

        assertFalse(settled);
        verify(payOrderMapper, never()).update(isNull(), ArgumentMatchers.<Wrapper<PayOrderPo>>any());
        verify(payTransactionMapper, never()).insert(any(PayTransactionPo.class));
    }

    @Test
    @DisplayName("markRefunded：累计 0 行时整段回滚并抛业务异常")
    void shouldThrowWhenRefundAccumulateMissed() {
        when(payOrderMapper.applyRefund(eq(1L), notNull())).thenReturn(0);
        PayRefundPo refund = new PayRefundPo();
        refund.setId(9L);
        refund.setRefundNo("RF1");

        assertThrows(BizException.class,
                () -> service.markRefunded(order("PAID"), refund, new BigDecimal("10.00"),
                        new PayChannelResult("CH-R1")));

        verify(payRefundMapper, never()).updateById(any(PayRefundPo.class));
        verify(payTransactionMapper, never()).insert(any(PayTransactionPo.class));
    }
}
