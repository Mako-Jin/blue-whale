package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.ReconcileItemVO;
import com.bluewhale.forum.dto.ReconcileReportVO;
import com.bluewhale.forum.dto.ThreadCountDrift;
import com.bluewhale.forum.entity.ForumCountReconcile;
import com.bluewhale.forum.mapper.ForumCountReconcileMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.CountReconcileService;
import com.bluewhale.forum.support.ForumMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 计数对账实现。
 *
 * <p>整批放在一个事务里：轨迹与修正必须同生共死。若先写轨迹再修数、中途异常，
 * 轨迹会声称「已修」而数据没动，下一次对账又报同样的差异，运维会误判成「修不干净」。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CountReconcileServiceImpl implements CountReconcileService {

    /** 回传明细上限：对账结果可能上百条，全量塞进响应体没有意义 */
    private static final int ITEM_RETURN_LIMIT = 20;

    /** 单次核对帖子数上限 */
    private static final int MAX_BATCH = 5_000;

    private static final String METRIC_REPLY = "REPLY";

    private static final String METRIC_LIKE = "LIKE";

    private static final String METRIC_COLLECT = "COLLECT";

    private final ForumThreadMapper threadMapper;

    private final ForumCountReconcileMapper reconcileMapper;

    private final ForumProperties properties;

    private final ForumMetrics metrics;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReconcileReportVO reconcile(boolean fix, Integer limit, Long operatorId) {
        Long tenantId = TenantContext.requireId();
        int batch = limit == null || limit < 1
                ? Math.max(1, properties.getCountReconcileBatch())
                : Math.min(limit, MAX_BATCH);
        String batchNo = "RC-" + IdWorker.getId();
        List<ThreadCountDrift> drifts = threadMapper.selectCountDrift(tenantId, batch);
        List<ReconcileItemVO> items = new ArrayList<>();
        int diffCount = 0;
        int fixedCount = 0;
        for (ThreadCountDrift drift : drifts) {
            int beforeReply = intOf(drift.getReplyCount());
            int actualReply = intOf(drift.getActualReply());
            int beforeLike = intOf(drift.getLikeCount());
            int actualLike = intOf(drift.getActualLike());
            int beforeCollect = intOf(drift.getCollectCount());
            int actualCollect = intOf(drift.getActualCollect());
            boolean needFix = fix;
            if (needFix) {
                threadMapper.fixCounts(drift.getThreadId(), tenantId, actualReply, actualLike, actualCollect);
            }
            diffCount += record(items, batchNo, tenantId, drift.getThreadId(), METRIC_REPLY,
                    beforeReply, actualReply, needFix, operatorId);
            diffCount += record(items, batchNo, tenantId, drift.getThreadId(), METRIC_LIKE,
                    beforeLike, actualLike, needFix, operatorId);
            diffCount += record(items, batchNo, tenantId, drift.getThreadId(), METRIC_COLLECT,
                    beforeCollect, actualCollect, needFix, operatorId);
            if (needFix) {
                fixedCount += countDiff(beforeReply, actualReply)
                        + countDiff(beforeLike, actualLike)
                        + countDiff(beforeCollect, actualCollect);
            }
        }
        ReconcileReportVO report = new ReconcileReportVO();
        report.setBatchNo(batchNo);
        report.setBatchSize(batch);
        report.setDiffCount(diffCount);
        report.setFixedCount(fixedCount);
        report.setDryRun(!fix);
        report.setTruncated(diffCount > items.size());
        report.setItems(items);
        if (diffCount > 0) {
            log.info("论坛计数对账 tenantId={} batch={} diff={} fixed={} dryRun={}",
                    tenantId, batchNo, diffCount, fixedCount, !fix);
        }
        return report;
    }

    /**
     * 记录一项差异并留轨迹（无差异时既不留轨迹也不计入明细）。
     *
     * @param items 明细容器
     * @param batchNo 批次号
     * @param tenantId 租户ID
     * @param threadId 帖子ID
     * @param metric 计数项
     * @param before 展示值
     * @param actual 真实值
     * @param fixed 是否已修正
     * @param operatorId 触发人
     * @return 计入的差异条数（0 或 1）
     */
    private int record(List<ReconcileItemVO> items, String batchNo, Long tenantId, Long threadId,
                       String metric, int before, int actual, boolean fixed, Long operatorId) {
        if (before == actual) {
            return 0;
        }
        ForumCountReconcile trace = new ForumCountReconcile();
        trace.setId(IdWorker.getId());
        trace.setTenantId(tenantId);
        trace.setThreadId(threadId);
        trace.setMetric(metric);
        trace.setValueBefore(before);
        trace.setValueActual(actual);
        trace.setFixed(fixed ? 1 : 0);
        trace.setOperatorId(operatorId);
        trace.setBatchNo(batchNo);
        trace.setCreateTime(LocalDateTime.now());
        reconcileMapper.insert(trace);
        metrics.recordReconcileDiff(metric);
        if (items.size() < ITEM_RETURN_LIMIT) {
            ReconcileItemVO item = new ReconcileItemVO();
            item.setThreadId(threadId);
            item.setMetric(metric);
            item.setBefore(before);
            item.setActual(actual);
            item.setFixed(fixed);
            items.add(item);
        }
        return 1;
    }

    /**
     * 差异计数（相等记 0）。
     *
     * @param before 展示值
     * @param actual 真实值
     * @return 差异条数
     */
    private int countDiff(int before, int actual) {
        return before == actual ? 0 : 1;
    }

    /**
     * 空值按 0 处理（历史数据里计数列可能为 NULL）。
     *
     * @param value 计数
     * @return 非空计数
     */
    private int intOf(Integer value) {
        return value == null ? 0 : value;
    }
}
