package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pay.channel.ChannelBillItem;
import com.bluewhale.pay.channel.PayChannelGateway;
import com.bluewhale.pay.channel.PayChannelResult;
import com.bluewhale.pay.channel.PayChannelStatus;
import com.bluewhale.pay.config.PayReconcileProperties;
import com.bluewhale.pay.dto.PayReconcileBatchQueryRequest;
import com.bluewhale.pay.dto.PayReconcileDiffQueryRequest;
import com.bluewhale.pay.dto.PayReconcileHandleRequest;
import com.bluewhale.pay.dto.PayReconcileRunRequest;
import com.bluewhale.pay.enums.PayOrderStatus;
import com.bluewhale.pay.enums.PayReconcileAction;
import com.bluewhale.pay.enums.PayReconcileBatchStatus;
import com.bluewhale.pay.enums.PayReconcileDiffType;
import com.bluewhale.pay.enums.PayReconcileHandleStatus;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.PayReconcileBatchMapper;
import com.bluewhale.pay.mapper.PayReconcileDiffMapper;
import com.bluewhale.pay.po.PayOrderPo;
import com.bluewhale.pay.po.PayReconcileBatchPo;
import com.bluewhale.pay.po.PayReconcileDiffPo;
import com.bluewhale.pay.service.PayReconcileService;
import com.bluewhale.pay.service.PaySettlementService;
import com.bluewhale.pay.vo.PayReconcileBatchVO;
import com.bluewhale.pay.vo.PayReconcileDiffVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 批量对账与差异闭环实现。
 *
 * <p><b>单实例执行</b>：批次表唯一键 {@code (tenant_id, channel, biz_date)} 同时承担幂等与抢占，
 * 插入成功者才执行；重复触发读到 {@code RUNNING}/{@code SUCCESS} 直接返回，
 * {@code FAILED}/{@code PARTIAL} 通过条件更新重新领取。因此不需要额外引入分布式锁表。</p>
 *
 * <p><b>降级与取数失败</b>：渠道不支持账单下载时按本地订单逐笔查询状态，批次标记
 * {@code PARTIAL}；账单下载抛异常时批次 {@code FAILED} 且不写任何差异，
 * 避免把「取不到账单」误判成「渠道无单」。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayReconcileServiceImpl implements PayReconcileService {

    /** 全渠道占位符 */
    private static final String CHANNEL_ALL = "ALL";

    /** 错误信息最大长度 */
    private static final int ERROR_MAX_LENGTH = 500;

    /** 处置备注最大长度 */
    private static final int REMARK_MAX_LENGTH = 255;

    private final PayReconcileBatchMapper batchMapper;

    private final PayReconcileDiffMapper diffMapper;

    private final PayOrderMapper payOrderMapper;

    private final PayChannelGateway payChannelGateway;

    private final PaySettlementService paySettlementService;

    private final PayReconcileProperties properties;

    @Override
    public List<PayReconcileBatchVO> run(PayReconcileRunRequest request) {
        Long tenantId = TenantContext.requireId();
        LocalDate bizDate = resolveBizDate(request);
        List<String> channels = resolveChannels(tenantId, bizDate, request == null ? null : request.getChannel());
        List<PayReconcileBatchVO> result = new ArrayList<>(channels.size());
        for (String channel : channels) {
            result.add(toBatchVo(execute(tenantId, channel, bizDate)));
        }
        return List.copyOf(result);
    }

    @Override
    @Scheduled(cron = "${whale.pay.reconcile.batch-cron:0 30 2 * * ?}")
    public int runScheduled() {
        if (!properties.isBatchEnabled()) {
            return 0;
        }
        LocalDate bizDate = LocalDate.now().minusDays(Math.max(properties.getLookbackDays(), 0));
        int executed = 0;
        for (Long tenantId : payOrderMapper.selectDistinctTenantIds()) {
            executed += runTenant(tenantId, bizDate);
        }
        if (executed > 0 && log.isInfoEnabled()) {
            log.info("批量对账日切完成: bizDate={} batches={}", bizDate, executed);
        }
        return executed;
    }

    /**
     * 单租户日切对账：还原租户上下文后逐渠道执行。
     *
     * @param tenantId 租户 ID
     * @param bizDate  业务日
     * @return 执行的批次数
     */
    private int runTenant(Long tenantId, LocalDate bizDate) {
        TenantContext.set(tenantId);
        try {
            List<String> channels = payOrderMapper.selectReconcileChannels(tenantId,
                    bizDate.atStartOfDay(), bizDate.plusDays(1L).atStartOfDay());
            int executed = 0;
            for (String channel : channels) {
                try {
                    execute(tenantId, channel, bizDate);
                    executed++;
                } catch (RuntimeException ex) {
                    // 单渠道失败不能中断其他租户/渠道：批次本身已在 execute 内落 FAILED
                    log.error("批量对账执行异常: tenantId={} channel={} bizDate={}", tenantId, channel, bizDate, ex);
                }
            }
            return executed;
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 执行单个批次：抢占 → 比对 → 回写批次。
     *
     * @param tenantId 租户 ID
     * @param channel  渠道
     * @param bizDate  业务日
     * @return 批次（已终态或本轮执行结果）
     */
    private PayReconcileBatchPo execute(Long tenantId, String channel, LocalDate bizDate) {
        ClaimedBatch claimed = claim(tenantId, channel, bizDate);
        if (claimed.batch() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.reconcile.batch-claim-failed",
                    "对账批次占位失败，请稍后重试");
        }
        if (!claimed.owned()) {
            return claimed.batch();
        }
        PayReconcileBatchPo batch = claimed.batch();
        LocalDateTime from = bizDate.atStartOfDay();
        LocalDateTime to = bizDate.plusDays(1L).atStartOfDay();
        try {
            ReconcileOutcome outcome = reconcile(tenantId, channel, bizDate, from, to, batch.getId());
            batch.setStatus(outcome.status().code());
            batch.setLocalCount(outcome.localCount());
            batch.setChannelCount(outcome.channelCount());
            batch.setDiffCount(countDiffs(batch.getId()));
            batch.setErrorMessage(truncate(outcome.error()));
        } catch (RuntimeException ex) {
            log.error("批量对账失败，不产出差异: tenantId={} channel={} bizDate={}", tenantId, channel, bizDate, ex);
            batch.setStatus(PayReconcileBatchStatus.FAILED.code());
            batch.setErrorMessage(truncate(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        }
        batch.setFinishedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
        return batch;
    }

    /**
     * 抢占批次：新建插入成功即持有；撞唯一键时按状态决定返回还是重新领取。
     *
     * @param tenantId 租户 ID
     * @param channel  渠道
     * @param bizDate  业务日
     * @return 批次与是否由本次调用持有执行权
     */
    private ClaimedBatch claim(Long tenantId, String channel, LocalDate bizDate) {
        PayReconcileBatchPo created = newBatch(tenantId, channel, bizDate);
        try {
            batchMapper.insert(created);
            return new ClaimedBatch(created, true);
        } catch (DuplicateKeyException ex) {
            PayReconcileBatchPo existing = findByKey(tenantId, channel, bizDate);
            if (existing == null || !isRerunnable(existing.getStatus())) {
                return new ClaimedBatch(existing, false);
            }
            if (batchMapper.claimRerun(existing.getId()) == 0) {
                return new ClaimedBatch(findByKey(tenantId, channel, bizDate), false);
            }
            existing.setStatus(PayReconcileBatchStatus.RUNNING.code());
            existing.setStartedAt(LocalDateTime.now());
            existing.setFinishedAt(null);
            existing.setErrorMessage(null);
            return new ClaimedBatch(existing, true);
        }
    }

    /**
     * 构造待执行的批次行。
     *
     * @param tenantId 租户 ID
     * @param channel  渠道
     * @param bizDate  业务日
     * @return 批次实体
     */
    private static PayReconcileBatchPo newBatch(Long tenantId, String channel, LocalDate bizDate) {
        PayReconcileBatchPo batch = new PayReconcileBatchPo();
        batch.setTenantId(tenantId);
        batch.setChannel(channel);
        batch.setBizDate(bizDate);
        batch.setStatus(PayReconcileBatchStatus.RUNNING.code());
        batch.setLocalCount(0);
        batch.setChannelCount(0);
        batch.setDiffCount(0);
        batch.setStartedAt(LocalDateTime.now());
        return batch;
    }

    /**
     * 比对本地与渠道：支持账单下载走全量比对，否则降级为逐笔状态查询。
     *
     * @param tenantId 租户 ID
     * @param channel  渠道
     * @param bizDate  业务日
     * @param from     业务日起（含）
     * @param to       业务日止（不含）
     * @param batchId  批次 ID
     * @return 比对结果
     */
    private ReconcileOutcome reconcile(Long tenantId, String channel, LocalDate bizDate,
                                       LocalDateTime from, LocalDateTime to, Long batchId) {
        List<PayOrderPo> localOrders = payOrderMapper.selectReconcileOrders(tenantId, channel, from, to);
        if (!payChannelGateway.supportsBillDownload(channel)) {
            return reconcileByStatus(channel, bizDate, batchId, localOrders);
        }
        List<ChannelBillItem> bill = payChannelGateway.downloadBill(channel, bizDate);
        List<ChannelBillItem> safeBill = bill == null ? List.of() : bill;
        int diffs = compareBill(channel, bizDate, batchId, localOrders, safeBill);
        return new ReconcileOutcome(PayReconcileBatchStatus.SUCCESS, localOrders.size(), safeBill.size(),
                diffs, null);
    }

    /**
     * 降级比对：逐笔查询渠道状态，只判断状态不一致，无法发现渠道多出的单。
     *
     * @param channel    渠道
     * @param bizDate    业务日
     * @param batchId    批次 ID
     * @param localOrders 本地订单
     * @return 比对结果（存在查询失败时为 {@code PARTIAL}）
     */
    private ReconcileOutcome reconcileByStatus(String channel, LocalDate bizDate, Long batchId,
                                               List<PayOrderPo> localOrders) {
        int diffs = 0;
        String error = null;
        for (PayOrderPo order : localOrders) {
            try {
                String channelStatus = payChannelGateway.queryStatus(order);
                diffs += diffByStatus(channel, bizDate, batchId, order, channelStatus);
            } catch (RuntimeException ex) {
                error = "渠道状态查询失败: " + order.getOrderNo();
                log.warn("降级对账查询渠道状态失败: channel={} orderNo={}", channel, order.getOrderNo(), ex);
            }
        }
        PayReconcileBatchStatus status = PayReconcileBatchStatus.PARTIAL;
        return new ReconcileOutcome(status, localOrders.size(), 0, diffs, error);
    }

    /**
     * 账单比对：本地有渠道无 → LOCAL_ONLY，渠道有本地无 → CHANNEL_ONLY，
     * 单号一致时再比金额与状态。
     *
     * @param channel     渠道
     * @param bizDate     业务日
     * @param batchId     批次 ID
     * @param localOrders 本地订单
     * @param bill        渠道账单
     * @return 新增差异数
     */
    private int compareBill(String channel, LocalDate bizDate, Long batchId,
                            List<PayOrderPo> localOrders, List<ChannelBillItem> bill) {
        Map<String, ChannelBillItem> byOrderNo = indexBill(bill);
        Set<String> localOrderNos = new HashSet<>();
        int diffs = 0;
        for (PayOrderPo order : localOrders) {
            localOrderNos.add(order.getOrderNo());
            ChannelBillItem item = byOrderNo.get(order.getOrderNo());
            if (item == null) {
                // PAYING 单本来就可能尚未入账，不因账单缺失判定本地有渠道无，避免噪音
                if (isSettled(order.getStatus())) {
                    diffs += insertDiff(batchId, channel, bizDate, order.getOrderNo(),
                            order.getChannelTradeNo(), PayReconcileDiffType.LOCAL_ONLY,
                            order.getStatus(), null, order.getAmount(), null, order.getCurrency());
                }
                continue;
            }
            diffs += compareMatched(channel, bizDate, batchId, order, item);
        }
        diffs += channelOnly(channel, bizDate, batchId, localOrderNos, bill);
        return diffs;
    }

    /**
     * 索引账单：只保留带商户单号的条目，空单号无法与本地对齐。
     *
     * @param bill 账单
     * @return 单号 → 账单条目
     */
    private static Map<String, ChannelBillItem> indexBill(List<ChannelBillItem> bill) {
        Map<String, ChannelBillItem> byOrderNo = new HashMap<>();
        for (ChannelBillItem item : bill) {
            if (item != null && StringUtils.hasText(item.orderNo())) {
                byOrderNo.put(item.orderNo(), item);
            }
        }
        return byOrderNo;
    }

    /**
     * 单号一致时的金额与状态比对。
     *
     * @param channel 渠道
     * @param bizDate 业务日
     * @param batchId 批次 ID
     * @param order   本地订单
     * @param item    账单条目
     * @return 新增差异数
     */
    private int compareMatched(String channel, LocalDate bizDate, Long batchId,
                               PayOrderPo order, ChannelBillItem item) {
        String channelStatus = StringUtils.hasText(item.status())
                ? item.status().trim().toUpperCase(Locale.ROOT) : PayOrderStatus.PAID.code();
        if (amountMismatch(item.amount(), order.getAmount(), item.currency(), order.getCurrency())) {
            return insertDiff(batchId, channel, bizDate, order.getOrderNo(), item.channelTradeNo(),
                    PayReconcileDiffType.AMOUNT_MISMATCH, order.getStatus(), channelStatus,
                    order.getAmount(), item.amount(), order.getCurrency());
        }
        if (PayOrderStatus.PAYING.code().equals(order.getStatus())
                && PayOrderStatus.PAID.code().equals(channelStatus)) {
            return insertDiff(batchId, channel, bizDate, order.getOrderNo(), item.channelTradeNo(),
                    PayReconcileDiffType.STATUS_MISMATCH, order.getStatus(), channelStatus,
                    order.getAmount(), item.amount(), order.getCurrency());
        }
        if (isSettled(order.getStatus()) && !PayOrderStatus.PAID.code().equals(channelStatus)) {
            return insertDiff(batchId, channel, bizDate, order.getOrderNo(), item.channelTradeNo(),
                    PayReconcileDiffType.STATUS_MISMATCH, order.getStatus(), channelStatus,
                    order.getAmount(), item.amount(), order.getCurrency());
        }
        return 0;
    }

    /**
     * 渠道多出的单：本地当日没有对应支付单。
     *
     * @param channel       渠道
     * @param bizDate       业务日
     * @param batchId       批次 ID
     * @param localOrderNos 本地单号集合
     * @param bill          渠道账单
     * @return 新增差异数
     */
    private int channelOnly(String channel, LocalDate bizDate, Long batchId,
                            Set<String> localOrderNos, List<ChannelBillItem> bill) {
        int diffs = 0;
        for (ChannelBillItem item : bill) {
            if (item == null) {
                continue;
            }
            String key = StringUtils.hasText(item.orderNo()) ? item.orderNo() : item.channelTradeNo();
            if (!StringUtils.hasText(key) || localOrderNos.contains(key)) {
                continue;
            }
            diffs += insertDiff(batchId, channel, bizDate, key, item.channelTradeNo(),
                    PayReconcileDiffType.CHANNEL_ONLY, null, item.status(),
                    null, item.amount(), item.currency());
        }
        return diffs;
    }

    /**
     * 降级模式下按状态判断差异：只覆盖本地有单的情形。
     *
     * @param channel       渠道
     * @param bizDate       业务日
     * @param batchId       批次 ID
     * @param order         本地订单
     * @param channelStatus 渠道状态
     * @return 新增差异数
     */
    private int diffByStatus(String channel, LocalDate bizDate, Long batchId,
                             PayOrderPo order, String channelStatus) {
        if (!StringUtils.hasText(channelStatus)) {
            return 0;
        }
        String normalized = channelStatus.trim().toUpperCase(Locale.ROOT);
        boolean payingButPaid = PayOrderStatus.PAYING.code().equals(order.getStatus())
                && PayOrderStatus.PAID.code().equals(normalized);
        boolean settledButNotPaid = isSettled(order.getStatus())
                && !PayOrderStatus.PAID.code().equals(normalized);
        if (!payingButPaid && !settledButNotPaid) {
            return 0;
        }
        return insertDiff(batchId, channel, bizDate, order.getOrderNo(), order.getChannelTradeNo(),
                PayReconcileDiffType.STATUS_MISMATCH, order.getStatus(), normalized,
                order.getAmount(), null, order.getCurrency());
    }

    /**
     * 写入差异；撞唯一键视为同一批次重复对账，不重复计数。
     *
     * @param batchId       批次 ID
     * @param channel       渠道
     * @param bizDate       业务日
     * @param orderNo       支付单号
     * @param channelTradeNo 渠道流水号
     * @param type          差异类型
     * @param localStatus   本地状态
     * @param channelStatus 渠道状态
     * @param localAmount   本地金额
     * @param channelAmount 渠道金额
     * @param currency      币种
     * @return 新增差异数（0 或 1）
     */
    private int insertDiff(Long batchId, String channel, LocalDate bizDate, String orderNo,
                           String channelTradeNo, PayReconcileDiffType type, String localStatus,
                           String channelStatus, BigDecimal localAmount, BigDecimal channelAmount,
                           String currency) {
        PayReconcileDiffPo row = new PayReconcileDiffPo();
        row.setTenantId(TenantContext.get());
        row.setBatchId(batchId);
        row.setChannel(channel);
        row.setBizDate(bizDate);
        row.setOrderNo(orderNo);
        row.setChannelTradeNo(channelTradeNo);
        row.setDiffType(type.code());
        row.setLocalStatus(localStatus);
        row.setChannelStatus(channelStatus);
        row.setLocalAmount(localAmount);
        row.setChannelAmount(channelAmount);
        row.setCurrency(currency);
        row.setHandleStatus(PayReconcileHandleStatus.OPEN.code());
        try {
            diffMapper.insert(row);
            return 1;
        } catch (DuplicateKeyException ex) {
            log.debug("对账差异已存在，跳过重复写入: batchId={} orderNo={} type={}", batchId, orderNo, type);
            return 0;
        }
    }

    @Override
    public PageResult<PayReconcileBatchVO> pageBatches(PayReconcileBatchQueryRequest request) {
        LambdaQueryWrapper<PayReconcileBatchPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getChannel()), PayReconcileBatchPo::getChannel,
                normalize(request.getChannel()));
        wrapper.eq(StringUtils.hasText(request.getStatus()), PayReconcileBatchPo::getStatus,
                normalize(request.getStatus()));
        wrapper.eq(request.getBizDate() != null, PayReconcileBatchPo::getBizDate, request.getBizDate());
        wrapper.orderByDesc(PayReconcileBatchPo::getBizDate).orderByDesc(PayReconcileBatchPo::getId);
        Page<PayReconcileBatchPo> page = batchMapper.selectPage(request.toPage(), wrapper);
        return PageResult.of(page.getRecords().stream().map(PayReconcileServiceImpl::toBatchVo).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResult<PayReconcileDiffVO> pageDiffs(PayReconcileDiffQueryRequest request) {
        LambdaQueryWrapper<PayReconcileDiffPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getBatchId() != null, PayReconcileDiffPo::getBatchId, request.getBatchId());
        wrapper.eq(StringUtils.hasText(request.getChannel()), PayReconcileDiffPo::getChannel,
                normalize(request.getChannel()));
        wrapper.eq(StringUtils.hasText(request.getDiffType()), PayReconcileDiffPo::getDiffType,
                normalize(request.getDiffType()));
        wrapper.eq(StringUtils.hasText(request.getHandleStatus()), PayReconcileDiffPo::getHandleStatus,
                normalize(request.getHandleStatus()));
        wrapper.eq(request.getBizDate() != null, PayReconcileDiffPo::getBizDate, request.getBizDate());
        wrapper.orderByDesc(PayReconcileDiffPo::getCreateTime);
        Page<PayReconcileDiffPo> page = diffMapper.selectPage(request.toPage(), wrapper);
        return PageResult.of(page.getRecords().stream().map(PayReconcileServiceImpl::toDiffVo).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PayReconcileDiffVO handle(Long id, PayReconcileHandleRequest request, Long operatorId) {
        PayReconcileDiffPo diff = id == null ? null : diffMapper.selectById(id);
        if (diff == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "pay.reconcile.diff-not-found", "对账差异不存在");
        }
        ensurePending(diff);
        PayReconcileAction action = PayReconcileAction.fromCode(normalize(request.getAction()));
        if (action == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.reconcile.action-invalid",
                    "不支持的处置动作: " + request.getAction(), request.getAction());
        }
        if (action == PayReconcileAction.COMPENSATE) {
            compensate(diff);
        }
        String remark = truncateRemark(request.getRemark());
        if (diffMapper.markHandled(id, action.targetStatus().code(), remark, operatorId) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.reconcile.diff-handled",
                    "差异已被处置，请刷新后查看");
        }
        return toDiffVo(diffMapper.selectById(id));
    }

    /**
     * 校验差异仍处于可处置状态。
     *
     * @param diff 差异
     */
    private static void ensurePending(PayReconcileDiffPo diff) {
        String status = diff.getHandleStatus();
        boolean pending = PayReconcileHandleStatus.OPEN.code().equals(status)
                || PayReconcileHandleStatus.SUSPENDED.code().equals(status);
        if (!pending) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.reconcile.diff-handled",
                    "差异已被处置，请刷新后查看");
        }
    }

    /**
     * 以渠道为准补偿：仅 STATUS_MISMATCH 且渠道 PAID、本地 PAYING 时允许。
     *
     * @param diff 差异
     */
    private void compensate(PayReconcileDiffPo diff) {
        boolean allowed = PayReconcileDiffType.STATUS_MISMATCH.code().equals(diff.getDiffType())
                && PayOrderStatus.PAID.code().equals(diff.getChannelStatus())
                && PayOrderStatus.PAYING.code().equals(diff.getLocalStatus());
        if (!allowed) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.reconcile.compensate-not-allowed",
                    "仅「渠道已支付、本地支付中」的状态不一致差异允许补偿");
        }
        PayOrderPo order = findOrder(diff.getOrderNo());
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "pay.reconcile.order-not-found",
                    "差异对应的支付单不存在: " + diff.getOrderNo(), diff.getOrderNo());
        }
        if (isSettled(order.getStatus())) {
            // 补偿重试：上次已落账但差异状态没来得及回写，直接按成功处理
            return;
        }
        boolean settled = paySettlementService.markPaid(order,
                new PayChannelResult(diff.getChannelTradeNo(), PayChannelStatus.SUCCESS));
        if (settled) {
            return;
        }
        PayOrderPo fresh = payOrderMapper.selectById(order.getId());
        if (fresh == null || !isSettled(fresh.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "pay.reconcile.compensate-failed",
                    "补偿失败，本地状态已变化，请重新对账");
        }
    }

    /**
     * 按单号查支付单（租户插件按当前上下文过滤）。
     *
     * @param orderNo 支付单号
     * @return 支付单；不存在返回 {@code null}
     */
    private PayOrderPo findOrder(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        return payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrderPo>()
                .eq(PayOrderPo::getOrderNo, orderNo));
    }

    /**
     * 统计批次已落库的差异数（重复执行时以表为准，避免只统计本次新增）。
     *
     * @param batchId 批次 ID
     * @return 差异数
     */
    private int countDiffs(Long batchId) {
        Long count = diffMapper.selectCount(new LambdaQueryWrapper<PayReconcileDiffPo>()
                .eq(PayReconcileDiffPo::getBatchId, batchId));
        return count == null ? 0 : Math.toIntExact(count);
    }

    /**
     * 按唯一键读取批次。
     *
     * @param tenantId 租户 ID
     * @param channel  渠道
     * @param bizDate  业务日
     * @return 批次；不存在返回 {@code null}
     */
    private PayReconcileBatchPo findByKey(Long tenantId, String channel, LocalDate bizDate) {
        return batchMapper.selectOne(new LambdaQueryWrapper<PayReconcileBatchPo>()
                .eq(PayReconcileBatchPo::getTenantId, tenantId)
                .eq(PayReconcileBatchPo::getChannel, channel)
                .eq(PayReconcileBatchPo::getBizDate, bizDate));
    }

    /**
     * 失败或降级批次允许重新执行；成功与执行中不再重复跑。
     *
     * @param status 批次状态
     * @return 是否可重新领取
     */
    private static boolean isRerunnable(String status) {
        return PayReconcileBatchStatus.FAILED.code().equals(status)
                || PayReconcileBatchStatus.PARTIAL.code().equals(status);
    }

    /**
     * 解析业务日：缺省按配置回看天数取 T-N。
     *
     * @param request 请求
     * @return 业务日
     */
    private LocalDate resolveBizDate(PayReconcileRunRequest request) {
        if (request != null && request.getBizDate() != null) {
            return request.getBizDate();
        }
        return LocalDate.now().minusDays(Math.max(properties.getLookbackDays(), 0));
    }

    /**
     * 解析待对账渠道：指定单渠道时直接用，否则取当日实际发生交易的渠道。
     *
     * @param tenantId  租户 ID
     * @param bizDate   业务日
     * @param requested 请求渠道
     * @return 渠道清单
     */
    private List<String> resolveChannels(Long tenantId, LocalDate bizDate, String requested) {
        if (StringUtils.hasText(requested) && !CHANNEL_ALL.equalsIgnoreCase(requested.trim())) {
            return List.of(requested.trim().toUpperCase(Locale.ROOT));
        }
        List<String> channels = payOrderMapper.selectReconcileChannels(tenantId,
                bizDate.atStartOfDay(), bizDate.plusDays(1L).atStartOfDay());
        return channels == null ? List.of() : channels;
    }

    /**
     * 金额或币种不一致。
     *
     * @param channelAmount   渠道金额
     * @param localAmount     本地金额
     * @param channelCurrency 渠道币种
     * @param localCurrency   本地币种
     * @return 是否不一致
     */
    private static boolean amountMismatch(BigDecimal channelAmount, BigDecimal localAmount,
                                          String channelCurrency, String localCurrency) {
        if (channelAmount == null || localAmount == null || channelAmount.compareTo(localAmount) != 0) {
            return true;
        }
        return StringUtils.hasText(channelCurrency) && StringUtils.hasText(localCurrency)
                && !channelCurrency.equalsIgnoreCase(localCurrency);
    }

    /**
     * 本地是否已落账（已支付或已退款）。
     *
     * @param status 状态
     * @return 是否已落账
     */
    private static boolean isSettled(String status) {
        return PayOrderStatus.PAID.code().equals(status) || PayOrderStatus.REFUNDED.code().equals(status);
    }

    /**
     * 归一化筛选值。
     *
     * @param value 原始值
     * @return 去空白并大写；空值返回 {@code null}
     */
    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    /**
     * 截断错误信息到列宽。
     *
     * @param error 原始错误
     * @return 截断后错误
     */
    private static String truncate(String error) {
        if (error == null || error.length() <= ERROR_MAX_LENGTH) {
            return error;
        }
        return error.substring(0, ERROR_MAX_LENGTH);
    }

    /**
     * 截断处置备注到列宽。
     *
     * @param remark 原始备注
     * @return 截断后备注
     */
    private static String truncateRemark(String remark) {
        if (!StringUtils.hasText(remark)) {
            return null;
        }
        String trimmed = remark.trim();
        return trimmed.length() <= REMARK_MAX_LENGTH ? trimmed : trimmed.substring(0, REMARK_MAX_LENGTH);
    }

    /**
     * 批次实体转视图对象。
     *
     * @param batch 批次
     * @return 视图对象
     */
    private static PayReconcileBatchVO toBatchVo(PayReconcileBatchPo batch) {
        PayReconcileBatchVO vo = new PayReconcileBatchVO();
        vo.setId(batch.getId());
        vo.setChannel(batch.getChannel());
        vo.setBizDate(batch.getBizDate());
        vo.setStatus(batch.getStatus());
        vo.setLocalCount(batch.getLocalCount());
        vo.setChannelCount(batch.getChannelCount());
        vo.setDiffCount(batch.getDiffCount());
        vo.setStartedAt(batch.getStartedAt());
        vo.setFinishedAt(batch.getFinishedAt());
        vo.setErrorMessage(batch.getErrorMessage());
        vo.setCreateTime(batch.getCreateTime());
        return vo;
    }

    /**
     * 差异实体转视图对象。
     *
     * @param diff 差异
     * @return 视图对象
     */
    private static PayReconcileDiffVO toDiffVo(PayReconcileDiffPo diff) {
        PayReconcileDiffVO vo = new PayReconcileDiffVO();
        vo.setId(diff.getId());
        vo.setBatchId(diff.getBatchId());
        vo.setChannel(diff.getChannel());
        vo.setBizDate(diff.getBizDate());
        vo.setOrderNo(diff.getOrderNo());
        vo.setChannelTradeNo(diff.getChannelTradeNo());
        vo.setDiffType(diff.getDiffType());
        vo.setLocalStatus(diff.getLocalStatus());
        vo.setChannelStatus(diff.getChannelStatus());
        vo.setLocalAmount(diff.getLocalAmount());
        vo.setChannelAmount(diff.getChannelAmount());
        vo.setCurrency(diff.getCurrency());
        vo.setHandleStatus(diff.getHandleStatus());
        vo.setHandleRemark(diff.getHandleRemark());
        vo.setHandledBy(diff.getHandledBy());
        vo.setHandledAt(diff.getHandledAt());
        vo.setCreateTime(diff.getCreateTime());
        return vo;
    }

    /**
     * 抢占结果：批次与是否由本次调用持有执行权。
     *
     * @param batch 批次
     * @param owned 是否持有执行权
     */
    private record ClaimedBatch(PayReconcileBatchPo batch, boolean owned) {
    }

    /**
     * 单批次比对结果。
     *
     * @param status       批次状态
     * @param localCount   本地记录数
     * @param channelCount 渠道记录数
     * @param diffCount    本次新增差异数
     * @param error        降级原因
     */
    private record ReconcileOutcome(PayReconcileBatchStatus status, int localCount, int channelCount,
                                    int diffCount, String error) {
    }
}
