package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pay.channel.ChannelBillItem;
import com.bluewhale.pay.channel.PayChannelGateway;
import com.bluewhale.pay.config.PayReconcileProperties;
import com.bluewhale.pay.dto.PayReconcileHandleRequest;
import com.bluewhale.pay.dto.PayReconcileRunRequest;
import com.bluewhale.pay.enums.PayOrderStatus;
import com.bluewhale.pay.enums.PayReconcileBatchStatus;
import com.bluewhale.pay.enums.PayReconcileDiffType;
import com.bluewhale.pay.enums.PayReconcileHandleStatus;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.PayReconcileBatchMapper;
import com.bluewhale.pay.mapper.PayReconcileDiffMapper;
import com.bluewhale.pay.po.PayOrderPo;
import com.bluewhale.pay.po.PayReconcileBatchPo;
import com.bluewhale.pay.po.PayReconcileDiffPo;
import com.bluewhale.pay.service.PaySettlementService;
import com.bluewhale.pay.vo.PayReconcileBatchVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PayReconcileServiceImpl} 单元测试：四类差异、降级、取数失败与补偿限制。
 *
 * @author BlueWhale
 */
class PayReconcileServiceImplTest {

    /** 测试租户 */
    private static final long TENANT_ID = 1L;

    /** 测试渠道 */
    private static final String CHANNEL = "MOCK";

    /** 批次主键 */
    private static final long BATCH_ID = 100L;

    /** 差异主键 */
    private static final long DIFF_ID = 200L;

    /** 业务日 */
    private static final LocalDate BIZ_DATE = LocalDate.of(2026, 11, 20);

    private final PayReconcileBatchMapper batchMapper = mock(PayReconcileBatchMapper.class);

    private final PayReconcileDiffMapper diffMapper = mock(PayReconcileDiffMapper.class);

    private final PayOrderMapper payOrderMapper = mock(PayOrderMapper.class);

    private final PayChannelGateway payChannelGateway = mock(PayChannelGateway.class);

    private final PaySettlementService paySettlementService = mock(PaySettlementService.class);

    private final PayReconcileProperties properties = new PayReconcileProperties();

    private final PayReconcileServiceImpl service = new PayReconcileServiceImpl(batchMapper, diffMapper,
            payOrderMapper, payChannelGateway, paySettlementService, properties);

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), PayOrderPo.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), PayReconcileBatchPo.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), PayReconcileDiffPo.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        when(batchMapper.insert(any(PayReconcileBatchPo.class))).thenAnswer(invocation -> {
            PayReconcileBatchPo batch = invocation.getArgument(0);
            batch.setId(BATCH_ID);
            return 1;
        });
        when(diffMapper.insert(any(PayReconcileDiffPo.class))).thenReturn(1);
        when(diffMapper.selectCount(any())).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("本地已支付、渠道账单无此单 → LOCAL_ONLY")
    void shouldMarkLocalOnly() {
        when(payChannelGateway.supportsBillDownload(CHANNEL)).thenReturn(true);
        when(payChannelGateway.downloadBill(CHANNEL, BIZ_DATE)).thenReturn(List.of());
        when(payOrderMapper.selectReconcileOrders(eq(TENANT_ID), eq(CHANNEL), any(), any()))
                .thenReturn(List.of(order("PAY001", PayOrderStatus.PAID.code(), "100.00")));

        PayReconcileBatchVO batch = runOnce();

        assertEquals(PayReconcileBatchStatus.SUCCESS.code(), batch.getStatus());
        assertEquals(1, batch.getLocalCount().intValue());
        assertEquals(PayReconcileDiffType.LOCAL_ONLY.code(), capturedDiff().getDiffType());
    }

    @Test
    @DisplayName("渠道账单有、本地无 → CHANNEL_ONLY")
    void shouldMarkChannelOnly() {
        when(payChannelGateway.supportsBillDownload(CHANNEL)).thenReturn(true);
        when(payChannelGateway.downloadBill(CHANNEL, BIZ_DATE)).thenReturn(List.of(
                new ChannelBillItem("PAY404", "TRADE-404", new BigDecimal("66.00"), "CNY", "PAID")));
        when(payOrderMapper.selectReconcileOrders(eq(TENANT_ID), eq(CHANNEL), any(), any()))
                .thenReturn(List.of());

        PayReconcileBatchVO batch = runOnce();

        assertEquals(1, batch.getChannelCount().intValue());
        assertEquals(PayReconcileDiffType.CHANNEL_ONLY.code(), capturedDiff().getDiffType());
    }

    @Test
    @DisplayName("单号一致、金额不同 → AMOUNT_MISMATCH")
    void shouldMarkAmountMismatch() {
        when(payChannelGateway.supportsBillDownload(CHANNEL)).thenReturn(true);
        when(payChannelGateway.downloadBill(CHANNEL, BIZ_DATE)).thenReturn(List.of(
                new ChannelBillItem("PAY001", "TRADE-1", new BigDecimal("90.00"), "CNY", "PAID")));
        when(payOrderMapper.selectReconcileOrders(eq(TENANT_ID), eq(CHANNEL), any(), any()))
                .thenReturn(List.of(order("PAY001", PayOrderStatus.PAID.code(), "100.00")));

        runOnce();

        assertEquals(PayReconcileDiffType.AMOUNT_MISMATCH.code(), capturedDiff().getDiffType());
    }

    @Test
    @DisplayName("本地支付中、渠道已支付 → STATUS_MISMATCH")
    void shouldMarkStatusMismatch() {
        when(payChannelGateway.supportsBillDownload(CHANNEL)).thenReturn(true);
        when(payChannelGateway.downloadBill(CHANNEL, BIZ_DATE)).thenReturn(List.of(
                new ChannelBillItem("PAY001", "TRADE-1", new BigDecimal("100.00"), "CNY", "PAID")));
        when(payOrderMapper.selectReconcileOrders(eq(TENANT_ID), eq(CHANNEL), any(), any()))
                .thenReturn(List.of(order("PAY001", PayOrderStatus.PAYING.code(), "100.00")));

        runOnce();

        PayReconcileDiffPo diff = capturedDiff();
        assertEquals(PayReconcileDiffType.STATUS_MISMATCH.code(), diff.getDiffType());
        assertEquals(PayOrderStatus.PAYING.code(), diff.getLocalStatus());
        assertEquals(PayOrderStatus.PAID.code(), diff.getChannelStatus());
    }

    @Test
    @DisplayName("渠道不支持账单下载 → 降级逐笔查询并标记 PARTIAL")
    void shouldDegradeWhenBillUnsupported() {
        when(payChannelGateway.supportsBillDownload(CHANNEL)).thenReturn(false);
        PayOrderPo paying = order("PAY001", PayOrderStatus.PAYING.code(), "100.00");
        when(payOrderMapper.selectReconcileOrders(eq(TENANT_ID), eq(CHANNEL), any(), any()))
                .thenReturn(List.of(paying));
        when(payChannelGateway.queryStatus(paying)).thenReturn(PayOrderStatus.PAID.code());

        PayReconcileBatchVO batch = runOnce();

        assertEquals(PayReconcileBatchStatus.PARTIAL.code(), batch.getStatus());
        assertEquals(PayReconcileDiffType.STATUS_MISMATCH.code(), capturedDiff().getDiffType());
        verify(payChannelGateway, never()).downloadBill(anyString(), any());
    }

    @Test
    @DisplayName("渠道账单下载失败 → 批次 FAILED 且不产出差异")
    void shouldFailWithoutDiffsWhenBillUnavailable() {
        when(payChannelGateway.supportsBillDownload(CHANNEL)).thenReturn(true);
        when(payChannelGateway.downloadBill(CHANNEL, BIZ_DATE)).thenThrow(new IllegalStateException("网关超时"));
        when(payOrderMapper.selectReconcileOrders(eq(TENANT_ID), eq(CHANNEL), any(), any()))
                .thenReturn(List.of(order("PAY001", PayOrderStatus.PAID.code(), "100.00")));

        PayReconcileBatchVO batch = runOnce();

        assertEquals(PayReconcileBatchStatus.FAILED.code(), batch.getStatus());
        verify(diffMapper, never()).insert(any(PayReconcileDiffPo.class));
    }

    @Test
    @DisplayName("批次已存在且成功 → 直接返回，不重复比对")
    void shouldReturnExistingBatch() {
        when(batchMapper.insert(any(PayReconcileBatchPo.class)))
                .thenThrow(new DuplicateKeyException("uk_batch"));
        PayReconcileBatchPo existing = new PayReconcileBatchPo();
        existing.setId(BATCH_ID);
        existing.setStatus(PayReconcileBatchStatus.SUCCESS.code());
        when(batchMapper.selectOne(any())).thenReturn(existing);

        PayReconcileBatchVO batch = runOnce();

        assertEquals(PayReconcileBatchStatus.SUCCESS.code(), batch.getStatus());
        verify(payChannelGateway, never()).downloadBill(anyString(), any());
        verify(batchMapper, never()).updateById(any(PayReconcileBatchPo.class));
    }

    @Test
    @DisplayName("补偿：金额差异与非 PAYING 状态一律拒绝")
    void shouldRejectCompensateOnNonStatusMismatch() {
        when(diffMapper.selectById(DIFF_ID)).thenReturn(diff(PayReconcileDiffType.AMOUNT_MISMATCH,
                PayOrderStatus.PAID.code(), PayOrderStatus.PAID.code()));

        BizException ex = assertThrows(BizException.class, () -> service.handle(DIFF_ID,
                handle("COMPENSATE"), 9L));

        assertTrue(ex.getMessage().contains("仅"), ex.getMessage());
        verify(paySettlementService, never()).markPaid(any(), any());
    }

    @Test
    @DisplayName("补偿成功：状态不一致且渠道 PAID、本地 PAYING")
    void shouldCompensateStatusMismatch() {
        PayReconcileDiffPo diff = diff(PayReconcileDiffType.STATUS_MISMATCH,
                PayOrderStatus.PAYING.code(), PayOrderStatus.PAID.code());
        when(diffMapper.selectById(DIFF_ID)).thenReturn(diff);
        when(payOrderMapper.selectOne(any()))
                .thenReturn(order("PAY001", PayOrderStatus.PAYING.code(), "100.00"));
        when(paySettlementService.markPaid(any(), any())).thenReturn(true);
        when(diffMapper.markHandled(eq(DIFF_ID), eq(PayReconcileHandleStatus.COMPENSATED.code()),
                any(), eq(9L))).thenReturn(1);

        service.handle(DIFF_ID, handle("COMPENSATE"), 9L);

        verify(paySettlementService).markPaid(any(), any());
        verify(diffMapper).markHandled(eq(DIFF_ID), eq(PayReconcileHandleStatus.COMPENSATED.code()),
                any(), eq(9L));
    }

    /**
     * 触发一次手动对账。
     *
     * @return 单批次结果
     */
    private PayReconcileBatchVO runOnce() {
        PayReconcileRunRequest request = new PayReconcileRunRequest();
        request.setBizDate(BIZ_DATE);
        request.setChannel(CHANNEL);
        List<PayReconcileBatchVO> batches = service.run(request);
        assertEquals(1, batches.size());
        return batches.get(0);
    }

    /**
     * 取最后一次差异写入参数。
     *
     * @return 差异实体
     */
    private PayReconcileDiffPo capturedDiff() {
        ArgumentCaptor<PayReconcileDiffPo> captor = ArgumentCaptor.forClass(PayReconcileDiffPo.class);
        verify(diffMapper).insert(captor.capture());
        return captor.getValue();
    }

    /**
     * 构造支付单。
     *
     * @param orderNo 单号
     * @param status  状态
     * @param amount  金额
     * @return 支付单
     */
    private static PayOrderPo order(String orderNo, String status, String amount) {
        PayOrderPo order = new PayOrderPo();
        order.setId(1L);
        order.setOrderNo(orderNo);
        order.setChannel(CHANNEL);
        order.setStatus(status);
        order.setAmount(new BigDecimal(amount));
        order.setCurrency("CNY");
        return order;
    }

    /**
     * 构造差异。
     *
     * @param type          类型
     * @param localStatus   本地状态
     * @param channelStatus 渠道状态
     * @return 差异实体
     */
    private static PayReconcileDiffPo diff(PayReconcileDiffType type, String localStatus,
                                           String channelStatus) {
        PayReconcileDiffPo diff = new PayReconcileDiffPo();
        diff.setId(DIFF_ID);
        diff.setBatchId(BATCH_ID);
        diff.setChannel(CHANNEL);
        diff.setBizDate(BIZ_DATE);
        diff.setOrderNo("PAY001");
        diff.setChannelTradeNo("TRADE-1");
        diff.setDiffType(type.code());
        diff.setLocalStatus(localStatus);
        diff.setChannelStatus(channelStatus);
        diff.setHandleStatus(PayReconcileHandleStatus.OPEN.code());
        return diff;
    }

    /**
     * 构造处置请求。
     *
     * @param action 动作
     * @return 请求
     */
    private static PayReconcileHandleRequest handle(String action) {
        PayReconcileHandleRequest request = new PayReconcileHandleRequest();
        request.setAction(action);
        request.setRemark("单元测试");
        return request;
    }
}
