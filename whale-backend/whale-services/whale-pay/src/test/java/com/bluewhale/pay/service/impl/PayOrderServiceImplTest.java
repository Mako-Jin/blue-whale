package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pay.channel.MockPayChannelAdapter;
import com.bluewhale.pay.channel.PayChannelGateway;
import com.bluewhale.pay.config.PayChannelProperties;
import com.bluewhale.pay.config.PayReconcileProperties;
import com.bluewhale.pay.dto.PayOrderCreateRequest;
import com.bluewhale.pay.dto.PayOrderQueryRequest;
import com.bluewhale.pay.po.PayOrderPo;
import com.bluewhale.pay.po.PayTransactionPo;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.PayTransactionMapper;
import com.bluewhale.pay.mapper.stat.PayStaleOrderPo;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PayOrderPo.class);
    }

    private final PayOrderMapper payOrderMapper = mock(PayOrderMapper.class);

    private final PayTransactionMapper payTransactionMapper = mock(PayTransactionMapper.class);

    private final PayChannelGateway payChannelGateway = new PayChannelGateway(
            new PayChannelProperties(), List.of(new MockPayChannelAdapter(new PayChannelProperties())));

    private final PayNotifyService payNotifyService = mock(PayNotifyService.class);

    private final PayRefundService payRefundService = mock(PayRefundService.class);

    private final TransactionTemplate transactionTemplate = transactionTemplate();

    private final PayReconcileProperties reconcileProperties = new PayReconcileProperties();

    private final PayOrderServiceImpl payOrderService = new PayOrderServiceImpl(
            payOrderMapper, payTransactionMapper, payChannelGateway, payNotifyService, payRefundService,
            transactionTemplate, reconcileProperties);

    /**
     * 构造不落真实事务的模板：数据库操作全部为 mock，只需走通 getTransaction/commit 流程。
     *
     * @return 事务模板
     */
    private static TransactionTemplate transactionTemplate() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new TransactionTemplate(manager);
    }

    /**
     * 构造支付单实体桩对象。
     *
     * @param id     主键ID
     * @param orderNo 支付单号
     * @param status  状态
     * @return 支付单实体
     */
    private static PayOrderPo order(Long id, String orderNo, String status) {
        PayOrderPo entity = new PayOrderPo();
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
        request.setChannel("MOCK");
        return request;
    }

    @Test
    @DisplayName("page：按条件分页查询并映射结果")
    void shouldPageWithConditions() {
        PayOrderPo entity = order(1L, "PAY20261020001", "CREATED");
        Page<PayOrderPo> page = new Page<>(1L, 10L);
        page.setRecords(List.of(entity));
        page.setTotal(1L);
        when(payOrderMapper.selectPage(ArgumentMatchers.<Page<PayOrderPo>>any(),
                ArgumentMatchers.<Wrapper<PayOrderPo>>any())).thenReturn(page);

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
        when(payOrderMapper.selectCount(ArgumentMatchers.<Wrapper<PayOrderPo>>any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> payOrderService.create(createRequest("PAY20261020001")));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payOrderMapper, never()).insert(any(PayOrderPo.class));
    }

    @Test
    @DisplayName("create：orderNo 唯一时落库并置初始状态 CREATED")
    void shouldCreateWithCreatedStatus() {
        when(payOrderMapper.selectCount(ArgumentMatchers.<Wrapper<PayOrderPo>>any())).thenReturn(0L);
        PayOrderPo saved = order(100L, "PAY20261020002", "CREATED");
        when(payOrderMapper.selectById(any())).thenReturn(saved);

        PayOrderVO vo = payOrderService.create(createRequest("PAY20261020002"));

        assertEquals("CREATED", vo.getStatus());
        ArgumentCaptor<PayOrderPo> captor = ArgumentCaptor.forClass(PayOrderPo.class);
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
        verify(payOrderMapper, never()).insert(any(PayOrderPo.class));
    }

    @Test
    @DisplayName("create：金额最多 2 位小数")
    void shouldRejectAmountScaleOverTwo() {
        PayOrderCreateRequest request = createRequest("PAY20261020007");
        request.setAmount(new BigDecimal("1.234"));

        BizException ex = assertThrows(BizException.class, () -> payOrderService.create(request));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payOrderMapper, never()).insert(any(PayOrderPo.class));
    }

    @Test
    @DisplayName("create：不支持的渠道拒绝创建")
    void shouldRejectUnsupportedChannel() {
        when(payOrderMapper.selectCount(ArgumentMatchers.<Wrapper<PayOrderPo>>any())).thenReturn(0L);
        PayOrderCreateRequest request = createRequest("PAY20261020008");
        request.setChannel("UNKNOWN");

        BizException ex = assertThrows(BizException.class, () -> payOrderService.create(request));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payOrderMapper, never()).insert(any(PayOrderPo.class));
    }

    @Test
    @DisplayName("pay：CREATED → PAID 并回填支付时间、渠道流水号与流水")
    void shouldPayFromCreated() {
        PayOrderPo existing = order(1L, "PAY20261020001", "CREATED");
        PayOrderPo paid = order(1L, "PAY20261020001", "PAID");
        paid.setChannelTradeNo("MOCK-1");
        when(payOrderMapper.selectById(1L)).thenReturn(existing, paid);
        when(payOrderMapper.update(isNull(), ArgumentMatchers.<Wrapper<PayOrderPo>>any())).thenReturn(1);
        when(payOrderMapper.updateById(any(PayOrderPo.class))).thenReturn(1);
        when(payTransactionMapper.insert(any(PayTransactionPo.class))).thenReturn(1);

        PayOrderVO vo = payOrderService.pay(1L);

        assertEquals("PAID", vo.getStatus());
        ArgumentCaptor<PayOrderPo> orderCaptor = ArgumentCaptor.forClass(PayOrderPo.class);
        verify(payOrderMapper).updateById(orderCaptor.capture());
        assertEquals("PAID", orderCaptor.getValue().getStatus());
        assertNotNull(orderCaptor.getValue().getPayTime());
        assertNotNull(orderCaptor.getValue().getChannelTradeNo());
        ArgumentCaptor<PayTransactionPo> txCaptor = ArgumentCaptor.forClass(PayTransactionPo.class);
        verify(payTransactionMapper).insert(txCaptor.capture());
        assertEquals("PAY", txCaptor.getValue().getType());
        assertEquals("SUCCESS", txCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("pay：非 CREATED 状态拒绝流转且不落库")
    void shouldRejectPayWhenNotCreated() {
        PayOrderPo existing = order(2L, "PAY20261020003", "PAID");
        when(payOrderMapper.selectById(2L)).thenReturn(existing);

        BizException ex = assertThrows(BizException.class, () -> payOrderService.pay(2L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payOrderMapper, never()).updateById(any(PayOrderPo.class));
    }

    @Test
    @DisplayName("pay：并发抢占失败（条件更新 0 行）拒绝支付且不落流水")
    void shouldRejectPayWhenClaimLost() {
        when(payOrderMapper.selectById(1L)).thenReturn(order(1L, "PAY20261020001", "CREATED"));
        // 条件更新未命中：状态已被并发事务改变
        when(payOrderMapper.update(isNull(), ArgumentMatchers.<Wrapper<PayOrderPo>>any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> payOrderService.pay(1L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payOrderMapper, never()).updateById(any(PayOrderPo.class));
        verify(payTransactionMapper, never()).insert(any(PayTransactionPo.class));
    }

    @Test
    @DisplayName("pay：mock 关闭时因无真实适配器拒绝支付")
    void shouldRejectPayWhenMockDisabled() {
        PayChannelProperties properties = new PayChannelProperties();
        properties.setMock(false);
        PayOrderServiceImpl service = new PayOrderServiceImpl(
                payOrderMapper, payTransactionMapper,
                new PayChannelGateway(properties, List.of(new MockPayChannelAdapter(properties))),
                payNotifyService, payRefundService, transactionTemplate, reconcileProperties);
        when(payOrderMapper.selectById(1L)).thenReturn(order(1L, "PAY20261020001", "CREATED"));
        when(payOrderMapper.update(isNull(), ArgumentMatchers.<Wrapper<PayOrderPo>>any())).thenReturn(1);

        BizException ex = assertThrows(BizException.class, () -> service.pay(1L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("真实支付渠道尚未接入"));
        verify(payOrderMapper, never()).updateById(any(PayOrderPo.class));
    }

    @Test
    @DisplayName("pay：渠道失败回退 PAYING→CREATED 并原样抛出异常")
    void shouldReleaseClaimWhenChannelFails() {
        when(payOrderMapper.selectById(1L)).thenReturn(order(1L, "PAY20261020013", "CREATED"));
        when(payOrderMapper.update(isNull(), ArgumentMatchers.<Wrapper<PayOrderPo>>any()))
                .thenReturn(1, 1);
        PayChannelGateway failing = mock(PayChannelGateway.class);
        when(failing.pay(any())).thenThrow(new BizException(ErrorCode.PARAM_ERROR, "渠道超时"));
        PayOrderServiceImpl service = new PayOrderServiceImpl(
                payOrderMapper, payTransactionMapper, failing,
                payNotifyService, payRefundService, transactionTemplate, reconcileProperties);

        BizException ex = assertThrows(BizException.class, () -> service.pay(1L));

        assertEquals("渠道超时", ex.getMessage());
        // 第一次抢占、第二次回退
        verify(payOrderMapper, times(2)).update(isNull(), ArgumentMatchers.<Wrapper<PayOrderPo>>any());
        verify(payOrderMapper, never()).updateById(any(PayOrderPo.class));
        verify(payTransactionMapper, never()).insert(any(PayTransactionPo.class));
    }

    @Test
    @DisplayName("pay：PAYING 中间态拒绝重复发起渠道请求")
    void shouldRejectWhenPaying() {
        when(payOrderMapper.selectById(1L)).thenReturn(order(1L, "PAY20261020014", "PAYING"));

        BizException ex = assertThrows(BizException.class, () -> payOrderService.pay(1L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payOrderMapper, never()).update(isNull(), ArgumentMatchers.<Wrapper<PayOrderPo>>any());
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
        PayOrderPo existing = order(3L, "PAY20261020004", "CREATED");
        PayOrderPo cancelled = order(3L, "PAY20261020004", "CANCELLED");
        when(payOrderMapper.selectById(3L)).thenReturn(existing, cancelled);
        when(payOrderMapper.update(isNull(), ArgumentMatchers.<Wrapper<PayOrderPo>>any())).thenReturn(1);
        when(payOrderMapper.updateById(any(PayOrderPo.class))).thenReturn(1);

        PayOrderVO vo = payOrderService.cancel(3L);

        assertEquals("CANCELLED", vo.getStatus());
        ArgumentCaptor<PayOrderPo> captor = ArgumentCaptor.forClass(PayOrderPo.class);
        verify(payOrderMapper).updateById(captor.capture());
        assertEquals("CANCELLED", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("cancel：非 CREATED 状态拒绝流转且不落库")
    void shouldRejectCancelWhenNotCreated() {
        PayOrderPo existing = order(4L, "PAY20261020005", "CANCELLED");
        when(payOrderMapper.selectById(4L)).thenReturn(existing);

        BizException ex = assertThrows(BizException.class, () -> payOrderService.cancel(4L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payOrderMapper, never()).updateById(any(PayOrderPo.class));
    }

    @Test
    @DisplayName("refund：委托退款服务整单退款并回读最新状态")
    void shouldRefundFromPaid() {
        PayOrderPo existing = order(5L, "PAY20261020009", "PAID");
        existing.setChannelTradeNo("MOCK-9");
        PayOrderPo refunded = order(5L, "PAY20261020009", "REFUNDED");
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
        verify(payTransactionMapper, never()).insert(any(PayTransactionPo.class));
    }

    @Test
    @DisplayName("reconcile：mock 渠道状态镜像本地，结果一致")
    void shouldReconcileConsistently() {
        PayOrderPo existing = order(7L, "PAY20261020011", "PAID");
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
        PayTransactionPo transaction = new PayTransactionPo();
        transaction.setId(900L);
        transaction.setOrderId(8L);
        transaction.setOrderNo("PAY20261020012");
        transaction.setType("PAY");
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setStatus("SUCCESS");
        when(payOrderMapper.selectById(8L)).thenReturn(order(8L, "PAY20261020012", "PAID"));
        when(payTransactionMapper.selectList(ArgumentMatchers.<Wrapper<PayTransactionPo>>any()))
                .thenReturn(List.of(transaction));

        List<PayTransactionVO> list = payOrderService.listTransactions(8L);

        assertEquals(1, list.size());
        assertEquals("PAY", list.get(0).getType());
        assertEquals("SUCCESS", list.get(0).getStatus());
        assertFalse(list.isEmpty());
    }

    @Test
    @DisplayName("reconcileStalePaying：渠道明确 PAID 时收敛为 PAID 并补流水与通知")
    void shouldConvergeStalePayingWhenChannelPaid() {
        when(payOrderMapper.selectStalePaying(any(), anyInt())).thenReturn(List.of(stale(11L, 7L)));
        PayOrderPo paying = order(11L, "PAY20261020015", "PAYING");
        paying.setNotifyUrl("whale-mall/mall/internal/order/paid");
        when(payOrderMapper.selectById(11L)).thenReturn(paying);
        PayChannelGateway gateway = mock(PayChannelGateway.class);
        when(gateway.queryStatus(any())).thenAnswer(invocation -> {
            // 对账线程必须已还原扫描结果中的租户，否则流水与通知会落到默认租户
            assertEquals(Long.valueOf(7L), TenantContext.get());
            return "PAID";
        });
        when(payOrderMapper.update(isNull(), ArgumentMatchers.<Wrapper<PayOrderPo>>any())).thenReturn(1);

        int converged = serviceWith(gateway).reconcileStalePaying();

        assertEquals(1, converged);
        ArgumentCaptor<PayTransactionPo> txCaptor = ArgumentCaptor.forClass(PayTransactionPo.class);
        verify(payTransactionMapper).insert(txCaptor.capture());
        assertEquals("PAY", txCaptor.getValue().getType());
        assertEquals("SUCCESS", txCaptor.getValue().getStatus());
        verify(payNotifyService).enqueue(any(), any(), any(), any());
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("reconcileStalePaying：渠道非 PAID 时保持 PAYING，不回退也不写流水")
    void shouldKeepPayingWhenChannelNotPaid() {
        when(payOrderMapper.selectStalePaying(any(), anyInt())).thenReturn(List.of(stale(12L, 8L)));
        when(payOrderMapper.selectById(12L)).thenReturn(order(12L, "PAY20261020016", "PAYING"));
        PayChannelGateway gateway = mock(PayChannelGateway.class);
        when(gateway.queryStatus(any())).thenReturn("CREATED");

        int converged = serviceWith(gateway).reconcileStalePaying();

        assertEquals(0, converged);
        verify(payOrderMapper, never()).update(isNull(), ArgumentMatchers.<Wrapper<PayOrderPo>>any());
        verify(payTransactionMapper, never()).insert(any(PayTransactionPo.class));
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("reconcileStalePaying：渠道查询异常时保持 PAYING 并清理租户上下文")
    void shouldKeepPayingWhenChannelQueryFails() {
        when(payOrderMapper.selectStalePaying(any(), anyInt())).thenReturn(List.of(stale(13L, 9L)));
        when(payOrderMapper.selectById(13L)).thenReturn(order(13L, "PAY20261020017", "PAYING"));
        PayChannelGateway gateway = mock(PayChannelGateway.class);
        when(gateway.queryStatus(any())).thenThrow(new BizException(ErrorCode.PARAM_ERROR, "渠道超时"));

        int converged = serviceWith(gateway).reconcileStalePaying();

        assertEquals(0, converged);
        verify(payOrderMapper, never()).update(isNull(), ArgumentMatchers.<Wrapper<PayOrderPo>>any());
        verify(payTransactionMapper, never()).insert(any(PayTransactionPo.class));
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("reconcileStalePaying：已被其他实例收敛时跳过流水与通知")
    void shouldSkipWhenAnotherInstanceConverged() {
        when(payOrderMapper.selectStalePaying(any(), anyInt())).thenReturn(List.of(stale(14L, 10L)));
        when(payOrderMapper.selectById(14L)).thenReturn(order(14L, "PAY20261020018", "PAYING"));
        PayChannelGateway gateway = mock(PayChannelGateway.class);
        when(gateway.queryStatus(any())).thenReturn("PAID");
        // 条件抢占未命中：并发实例已完成 PAYING → PAID
        when(payOrderMapper.update(isNull(), ArgumentMatchers.<Wrapper<PayOrderPo>>any())).thenReturn(0);

        int converged = serviceWith(gateway).reconcileStalePaying();

        assertEquals(0, converged);
        verify(payTransactionMapper, never()).insert(any(PayTransactionPo.class));
        verify(payNotifyService, never()).enqueue(any(), any(), any(), any());
        assertNull(TenantContext.get());
    }

    /**
     * 构造对账扫描结果。
     *
     * @param id       支付单 ID
     * @param tenantId 所属租户
     * @return 停留单摘要
     */
    private static PayStaleOrderPo stale(Long id, Long tenantId) {
        PayStaleOrderPo item = new PayStaleOrderPo();
        item.setId(id);
        item.setTenantId(tenantId);
        return item;
    }

    /**
     * 用指定渠道网关构造服务（对账用例需要控制渠道返回值）。
     *
     * @param gateway 渠道网关
     * @return 支付单服务
     */
    private PayOrderServiceImpl serviceWith(PayChannelGateway gateway) {
        return new PayOrderServiceImpl(payOrderMapper, payTransactionMapper, gateway,
                payNotifyService, payRefundService, transactionTemplate, reconcileProperties);
    }
}
