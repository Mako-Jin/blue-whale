package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pay.channel.MockPayChannelAdapter;
import com.bluewhale.pay.channel.PayChannelGateway;
import com.bluewhale.pay.config.PayChannelProperties;
import com.bluewhale.pay.dto.PayOrderCreateRequest;
import com.bluewhale.pay.dto.PayOrderQueryRequest;
import com.bluewhale.pay.entity.PayOrder;
import com.bluewhale.pay.entity.PayTransaction;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.PayTransactionMapper;
import com.bluewhale.pay.service.PayNotifyService;
import com.bluewhale.pay.service.PayRefundService;
import com.bluewhale.pay.vo.PayOrderVO;
import com.bluewhale.pay.vo.PayReconcileVO;
import com.bluewhale.pay.vo.PayTransactionVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PayOrderServiceImpl} 分页、创建校验、状态流转、退款、对账与流水单元测试。
 *
 * @author BlueWhale
 */
class PayOrderServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;

    /**
     * 初始化 MyBatis-Plus 实体元数据缓存：条件更新（LambdaUpdateWrapper）依赖
     * PayOrder 的 TableInfo，纯单测环境无 MP 启动流程，需手动注册。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PayOrder.class);
    }

    private final PayOrderMapper payOrderMapper = mock(PayOrderMapper.class);

    private final PayTransactionMapper payTransactionMapper = mock(PayTransactionMapper.class);

    private final PayChannelGateway payChannelGateway = new PayChannelGateway(
            new PayChannelProperties(), List.of(new MockPayChannelAdapter(new PayChannelProperties())));

    private final PayNotifyService payNotifyService = mock(PayNotifyService.class);

    private final PayRefundService payRefundService = mock(PayRefundService.class);

    private final PayOrderServiceImpl payOrderService = new PayOrderServiceImpl(
            payOrderMapper, payTransactionMapper, payChannelGateway, payNotifyService, payRefundService);

    /**
     * 构造支付单实体桩对象。
     *
     * @param id     主键ID
     * @param orderNo 支付单号
     * @param status  状态
     * @return 支付单实体
     */
    private static PayOrder order(Long id, String orderNo, String status) {
        PayOrder entity = new PayOrder();
        entity.setId(id);
        entity.setOrderNo(orderNo);
        entity.setStatus(status);
        entity.setAmount(new BigDecimal("100.00"));
        entity.setCurrency("CNY");
        entity.setChannel("MOCK");
        entity.setRefundAmount(BigDecimal.ZERO);
        return entity;
    }

    /**
     * 构造创建请求桩对象。
     *
     * @param orderNo 支付单号
     * @return 创建请求
     */
    private static PayOrderCreateRequest createRequest(String orderNo) {
        PayOrderCreateRequest request = new PayOrderCreateRequest();
        request.setOrderNo(orderNo);
        request.setBusinessKey("BIZ-1");
        request.setAmount(new BigDecimal("88.88"));
        request.setCurrency("CNY");
        request.setChannel("BALANCE");
        return request;
    }

    @Test
    @DisplayName("page：按条件分页查询并映射结果")
    void shouldPageWithConditions() {
        PayOrder entity = order(1L, "PAY20261020001", "CREATED");
        Page<PayOrder> page = new Page<>(1L, 10L);
        page.setRecords(List.of(entity));
        page.setTotal(1L);
        when(payOrderMapper.selectPage(ArgumentMatchers.<Page<PayOrder>>any(),
                ArgumentMatchers.<Wrapper<PayOrder>>any())).thenReturn(page);

        PayOrderQueryRequest request = new PayOrderQueryRequest();
        request.setKeyword("PAY2026");
        request.setStatus("CREATED");
        PageResult<PayOrderVO> result = payOrderService.page(request);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("PAY20261020001", result.getRecords().get(0).getOrderNo());
        assertEquals("CREATED", result.getRecords().get(0).getStatus());
    }

    @Test
    @DisplayName("getDetail：支付单不存在抛 NOT_FOUND")
    void shouldThrowWhenDetailMissing() {
        when(payOrderMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> payOrderService.getDetail(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    @Test
    @DisplayName("create：orderNo 冲突抛参数错误且不落库")
    void shouldRejectDuplicateOrderNo() {
        when(payOrderMapper.selectCount(ArgumentMatchers.<Wrapper<PayOrder>>any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> payOrderService.create(createRequest("PAY20261020001")));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payOrderMapper, never()).insert(any(PayOrder.class));
    }

    @Test
    @DisplayName("create：orderNo 唯一时落库并置初始状态 CREATED")
    void shouldCreateWithCreatedStatus() {
        when(payOrderMapper.selectCount(ArgumentMatchers.<Wrapper<PayOrder>>any())).thenReturn(0L);
        PayOrder saved = order(100L, "PAY20261020002", "CREATED");
        when(payOrderMapper.selectById(any())).thenReturn(saved);

        PayOrderVO vo = payOrderService.create(createRequest("PAY20261020002"));

        assertEquals("CREATED", vo.getStatus());
        ArgumentCaptor<PayOrder> captor = ArgumentCaptor.forClass(PayOrder.class);
        verify(payOrderMapper).insert(captor.capture());
        assertEquals("PAY20261020002", captor.getValue().getOrderNo());
        assertEquals("CREATED", captor.getValue().getStatus());
        assertEquals(BigDecimal.ZERO, captor.getValue().getRefundAmount());
    }

    @Test
    @DisplayName("create：金额必须大于 0")
    void shouldRejectNonPositiveAmount() {
        PayOrderCreateRequest request = createRequest("PAY20261020006");
        request.setAmount(BigDecimal.ZERO);

        BizException ex = assertThrows(BizException.class, () -> payOrderService.create(request));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payOrderMapper, never()).insert(any(PayOrder.class));
    }

    @Test
    @DisplayName("create：金额最多 2 位小数")
    void shouldRejectAmountScaleOverTwo() {
        PayOrderCreateRequest request = createRequest("PAY20261020007");
        request.setAmount(new BigDecimal("1.234"));

        BizException ex = assertThrows(BizException.class, () -> payOrderService.create(request));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payOrderMapper, never()).insert(any(PayOrder.class));
    }

    @Test
    @DisplayName("create：不支持的渠道拒绝创建")
    void shouldRejectUnsupportedChannel() {
        when(payOrderMapper.selectCount(ArgumentMatchers.<Wrapper<PayOrder>>any())).thenReturn(0L);
        PayOrderCreateRequest request = createRequest("PAY20261020008");
        request.setChannel("UNKNOWN");

        BizException ex = assertThrows(BizException.class, () -> payOrderService.create(request));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payOrderMapper, never()).insert(any(PayOrder.class));
    }

    @Test
    @DisplayName("pay：CREATED → PAID 并回填支付时间、渠道流水号与流水")
    void shouldPayFromCreated() {
        PayOrder existing = order(1L, "PAY20261020001", "CREATED");
        PayOrder paid = order(1L, "PAY20261020001", "PAID");
        paid.setChannelTradeNo("MOCK-1");
        when(payOrderMapper.selectById(1L)).thenReturn(existing, paid);
        when(payOrderMapper.update(isNull(), ArgumentMatchers.<Wrapper<PayOrder>>any())).thenReturn(1);
        when(payOrderMapper.updateById(any(PayOrder.class))).thenReturn(1);
        when(payTransactionMapper.insert(any(PayTransaction.class))).thenReturn(1);

        PayOrderVO vo = payOrderService.pay(1L);

        assertEquals("PAID", vo.getStatus());
        ArgumentCaptor<PayOrder> orderCaptor = ArgumentCaptor.forClass(PayOrder.class);
        verify(payOrderMapper).updateById(orderCaptor.capture());
        assertEquals("PAID", orderCaptor.getValue().getStatus());
        assertNotNull(orderCaptor.getValue().getPayTime());
        assertNotNull(orderCaptor.getValue().getChannelTradeNo());
        ArgumentCaptor<PayTransaction> txCaptor = ArgumentCaptor.forClass(PayTransaction.class);
        verify(payTransactionMapper).insert(txCaptor.capture());
        assertEquals("PAY", txCaptor.getValue().getType());
        assertEquals("SUCCESS", txCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("pay：非 CREATED 状态拒绝流转且不落库")
    void shouldRejectPayWhenNotCreated() {
        PayOrder existing = order(2L, "PAY20261020003", "PAID");
        when(payOrderMapper.selectById(2L)).thenReturn(existing);

        BizException ex = assertThrows(BizException.class, () -> payOrderService.pay(2L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payOrderMapper, never()).updateById(any(PayOrder.class));
    }

    @Test
    @DisplayName("pay：并发抢占失败（条件更新 0 行）拒绝支付且不落流水")
    void shouldRejectPayWhenClaimLost() {
        when(payOrderMapper.selectById(1L)).thenReturn(order(1L, "PAY20261020001", "CREATED"));
        // 条件更新未命中：状态已被并发事务改变
        when(payOrderMapper.update(isNull(), ArgumentMatchers.<Wrapper<PayOrder>>any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> payOrderService.pay(1L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payOrderMapper, never()).updateById(any(PayOrder.class));
        verify(payTransactionMapper, never()).insert(any(PayTransaction.class));
    }

    @Test
    @DisplayName("pay：mock 关闭时因无真实适配器拒绝支付")
    void shouldRejectPayWhenMockDisabled() {
        PayChannelProperties properties = new PayChannelProperties();
        properties.setMock(false);
        PayOrderServiceImpl service = new PayOrderServiceImpl(
                payOrderMapper, payTransactionMapper,
                new PayChannelGateway(properties, List.of(new MockPayChannelAdapter(properties))),
                payNotifyService, payRefundService);
        when(payOrderMapper.selectById(1L)).thenReturn(order(1L, "PAY20261020001", "CREATED"));
        when(payOrderMapper.update(isNull(), ArgumentMatchers.<Wrapper<PayOrder>>any())).thenReturn(1);

        BizException ex = assertThrows(BizException.class, () -> service.pay(1L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("真实支付渠道尚未接入"));
        verify(payOrderMapper, never()).updateById(any(PayOrder.class));
    }

    @Test
    @DisplayName("pay：支付单不存在抛 NOT_FOUND")
    void shouldThrowWhenPayMissing() {
        when(payOrderMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> payOrderService.pay(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    @Test
    @DisplayName("cancel：CREATED → CANCELLED")
    void shouldCancelFromCreated() {
        PayOrder existing = order(3L, "PAY20261020004", "CREATED");
        PayOrder cancelled = order(3L, "PAY20261020004", "CANCELLED");
        when(payOrderMapper.selectById(3L)).thenReturn(existing, cancelled);
        when(payOrderMapper.update(isNull(), ArgumentMatchers.<Wrapper<PayOrder>>any())).thenReturn(1);
        when(payOrderMapper.updateById(any(PayOrder.class))).thenReturn(1);

        PayOrderVO vo = payOrderService.cancel(3L);

        assertEquals("CANCELLED", vo.getStatus());
        ArgumentCaptor<PayOrder> captor = ArgumentCaptor.forClass(PayOrder.class);
        verify(payOrderMapper).updateById(captor.capture());
        assertEquals("CANCELLED", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("cancel：非 CREATED 状态拒绝流转且不落库")
    void shouldRejectCancelWhenNotCreated() {
        PayOrder existing = order(4L, "PAY20261020005", "CANCELLED");
        when(payOrderMapper.selectById(4L)).thenReturn(existing);

        BizException ex = assertThrows(BizException.class, () -> payOrderService.cancel(4L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payOrderMapper, never()).updateById(any(PayOrder.class));
    }

    @Test
    @DisplayName("refund：委托退款服务整单退款并回读最新状态")
    void shouldRefundFromPaid() {
        PayOrder existing = order(5L, "PAY20261020009", "PAID");
        existing.setChannelTradeNo("MOCK-9");
        PayOrder refunded = order(5L, "PAY20261020009", "REFUNDED");
        refunded.setRefundAmount(new BigDecimal("100.00"));
        when(payOrderMapper.selectById(5L)).thenReturn(existing, refunded);

        PayOrderVO vo = payOrderService.refund(5L);

        assertEquals("REFUNDED", vo.getStatus());
        verify(payRefundService).refundFull(5L, "管理端整单退款");
    }

    @Test
    @DisplayName("refund：支付单不存在抛 NOT_FOUND")
    void shouldRejectRefundWhenNotPaid() {
        when(payOrderMapper.selectById(6L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> payOrderService.refund(6L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(payRefundService, never()).refundFull(any(), any());
    }

    @Test
    @DisplayName("refund：可退余额不足时透传退款服务异常且不落流水")
    void shouldRejectRefundWhenClaimLost() {
        when(payOrderMapper.selectById(6L)).thenReturn(order(6L, "PAY20261020010", "PAID"));
        when(payRefundService.refundFull(6L, "管理端整单退款"))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR, "退款失败：支付单不可退款或累计退款超出支付金额"));

        BizException ex = assertThrows(BizException.class, () -> payOrderService.refund(6L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payTransactionMapper, never()).insert(any(PayTransaction.class));
    }

    @Test
    @DisplayName("reconcile：mock 渠道状态镜像本地，结果一致")
    void shouldReconcileConsistently() {
        PayOrder existing = order(7L, "PAY20261020011", "PAID");
        existing.setChannelTradeNo("MOCK-11");
        when(payOrderMapper.selectById(7L)).thenReturn(existing);

        PayReconcileVO vo = payOrderService.reconcile(7L);

        assertEquals("PAID", vo.getLocalStatus());
        assertEquals("PAID", vo.getChannelStatus());
        assertTrue(vo.isConsistent());
        assertEquals("MOCK-11", vo.getProviderTradeNo());
        assertNotNull(vo.getCheckedAt());
    }

    @Test
    @DisplayName("listTransactions：按支付单返回流水且状态映射正确")
    void shouldListTransactions() {
        PayTransaction transaction = new PayTransaction();
        transaction.setId(900L);
        transaction.setOrderId(8L);
        transaction.setOrderNo("PAY20261020012");
        transaction.setType("PAY");
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setStatus("SUCCESS");
        when(payOrderMapper.selectById(8L)).thenReturn(order(8L, "PAY20261020012", "PAID"));
        when(payTransactionMapper.selectList(ArgumentMatchers.<Wrapper<PayTransaction>>any()))
                .thenReturn(List.of(transaction));

        List<PayTransactionVO> list = payOrderService.listTransactions(8L);

        assertEquals(1, list.size());
        assertEquals("PAY", list.get(0).getType());
        assertEquals("SUCCESS", list.get(0).getStatus());
        assertFalse(list.isEmpty());
    }
}
