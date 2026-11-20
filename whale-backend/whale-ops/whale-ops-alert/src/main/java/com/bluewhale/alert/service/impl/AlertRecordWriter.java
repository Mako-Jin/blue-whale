package com.bluewhale.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.alert.config.AlertNotifyProperties;
import com.bluewhale.alert.dto.AlertRecordCreateRequest;
import com.bluewhale.alert.dto.PrometheusWebhookPayload;
import com.bluewhale.alert.entity.AlertRecord;
import com.bluewhale.alert.entity.AlertRule;
import com.bluewhale.alert.mapper.AlertRecordMapper;
import com.bluewhale.alert.mapper.AlertRuleMapper;
import com.bluewhale.alert.service.AlertSilenceService;
import com.bluewhale.alert.support.AlertLevels;
import com.bluewhale.alert.support.AlertNotifyDispatcher;
import com.bluewhale.alert.support.AlertRecordSupport;
import com.bluewhale.common.tenant.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 告警记录写入服务：分片事务、恢复闭环、静默匹配与通知派发。
 *
 * <p>与查询服务分离的原因是事务边界：每片告警由本类的 {@code @Transactional} 方法独立提交，
 * 避免一次 500 条告警把单个事务拖到数十秒。通知在片事务提交后异步派发。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRecordWriter {

    /** 记录状态：触发中 */
    private static final String STATUS_FIRING = AlertRecordSupport.STATUS_FIRING;

    /** 记录状态：已恢复 */
    private static final String STATUS_RECOVERED = AlertRecordSupport.STATUS_RECOVERED;

    /** 记录状态：已静默 */
    private static final String STATUS_SILENCED = AlertRecordSupport.STATUS_SILENCED;

    /** 规则启用状态 */
    private static final int RULE_ENABLED = 1;

    private static final String METRIC_INGEST = "alert_ingest_alerts_total";

    private static final String METRIC_DEDUP = "alert_dedup_total";

    private final AlertRecordMapper alertRecordMapper;

    private final AlertRuleMapper alertRuleMapper;

    private final AlertNotifyDispatcher alertNotifyDispatcher;

    private final AlertNotifyProperties notifyProperties;

    private final AlertSilenceService alertSilenceService;

    private final MeterRegistry meterRegistry;

    /**
     * 写入一片告警（独立事务）。
     *
     * @param alerts 一片告警
     * @return 实际写入条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int ingestChunk(List<PrometheusWebhookPayload.PrometheusAlert> alerts) {
        int inserted = 0;
        List<PendingNotify> pending = new ArrayList<>();
        Long previousTenant = TenantContext.get();
        for (PrometheusWebhookPayload.PrometheusAlert alert : alerts) {
            if (alert == null || alert.getLabels() == null) {
                countIngest("SKIPPED");
                continue;
            }
            String ruleCode = alert.getLabels().get(AlertRecordSupport.LABEL_ALERT_NAME);
            if (!StringUtils.hasText(ruleCode)) {
                log.warn("Alertmanager 告警缺少 alertname 标签，跳过");
                countIngest("SKIPPED");
                continue;
            }
            if (AlertRecordSupport.isOversized(alert)) {
                log.warn("Alertmanager 告警标签/注解体积超限，跳过: ruleCode={}", ruleCode);
                countIngest("SKIPPED");
                continue;
            }
            TenantContext.set(AlertRecordSupport.resolveTenantId(alert, previousTenant));
            try {
                inserted += ingestOne(alert, pending);
            } finally {
                TenantContext.set(previousTenant);
            }
        }
        dispatchAfterCommit(pending);
        return inserted;
    }

    /**
     * 处理单条入站告警。
     *
     * @param alert   单条告警
     * @param pending 待通知列表
     * @return 是否写入
     */
    private int ingestOne(PrometheusWebhookPayload.PrometheusAlert alert, List<PendingNotify> pending) {
        AlertRule rule = findRule(alert.getLabels().get(AlertRecordSupport.LABEL_ALERT_NAME));
        AlertRecord record = AlertRecordSupport.toRecord(alert, rule);
        if (STATUS_RECOVERED.equals(record.getStatus())) {
            return recover(record);
        }
        if (alertSilenceService.isSilenced(record.getRuleCode(), record.getAlertLevel(),
                record.getContent())) {
            record.setStatus(STATUS_SILENCED);
            alertRecordMapper.insert(record);
            countIngest(STATUS_SILENCED);
            return 1;
        }
        if (isDuplicate(record)) {
            log.info("窗口内重复告警已去重: ruleCode={}, fingerprint={}",
                    record.getRuleCode(), record.getFingerprint());
            countIngest("DUPLICATE");
            return 0;
        }
        alertRecordMapper.insert(record);
        countIngest(STATUS_FIRING);
        if (isEnabled(rule)) {
            pending.add(new PendingNotify(rule, record, TenantContext.get()));
        }
        return 1;
    }

    /**
     * 恢复闭环：按 fingerprint 将存量 FIRING 记录更新为 RECOVERED 并回填恢复时间。
     *
     * <p>找不到对应 FIRING 时（如服务重启期间错过触发）仍插入一条 RECOVERED 记录，
     * 保证恢复事件可追溯。</p>
     *
     * @param recovered 由 resolved 告警转换出的记录
     * @return 写入条数（更新已有记录计 0，新增恢复记录计 1）
     */
    private int recover(AlertRecord recovered) {
        LocalDateTime recoverTime = recovered.getRecoverTime() == null
                ? LocalDateTime.now() : recovered.getRecoverTime();
        if (StringUtils.hasText(recovered.getFingerprint())) {
            LambdaUpdateWrapper<AlertRecord> update = new LambdaUpdateWrapper<>();
            update.eq(AlertRecord::getFingerprint, recovered.getFingerprint())
                    .eq(AlertRecord::getStatus, STATUS_FIRING)
                    .set(AlertRecord::getStatus, STATUS_RECOVERED)
                    .set(AlertRecord::getRecoverTime, recoverTime);
            int updated = alertRecordMapper.update(null, update);
            if (updated > 0) {
                log.info("告警恢复闭环: ruleCode={}, fingerprint={}, updated={}",
                        recovered.getRuleCode(), recovered.getFingerprint(), updated);
                countIngest(STATUS_RECOVERED);
                return 0;
            }
        }
        alertRecordMapper.insert(recovered);
        countIngest(STATUS_RECOVERED);
        return 1;
    }

    /**
     * 内网创建一条告警记录（独立事务）。
     *
     * @param request 创建请求（内容已由入口校验）
     * @return 写入条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int createOne(AlertRecordCreateRequest request) {
        Long previousTenant = TenantContext.get();
        Long targetTenant = request.getTenantId() == null
                ? (previousTenant == null ? TenantContext.DEFAULT_TENANT_ID : previousTenant)
                : request.getTenantId();
        TenantContext.set(targetTenant);
        try {
            AlertRecord record = new AlertRecord();
            record.setRuleCode(AlertRecordSupport.normalizeRuleCode(request.getRuleCode()));
            record.setAlertLevel(AlertLevels.normalize(request.getAlertLevel(), AlertLevels.WARN));
            record.setContent(AlertRecordSupport.truncateContent(request.getContent()));
            record.setStatus(STATUS_FIRING);
            record.setFingerprint(AlertRecordSupport.fingerprint(request));
            record.setTriggerTime(request.getTriggerTime() == null
                    ? LocalDateTime.now() : request.getTriggerTime());
            AlertRule rule = StringUtils.hasText(record.getRuleCode())
                    ? findRule(record.getRuleCode()) : null;
            if (alertSilenceService.isSilenced(record.getRuleCode(), record.getAlertLevel(),
                    record.getContent())) {
                record.setStatus(STATUS_SILENCED);
                alertRecordMapper.insert(record);
                countIngest(STATUS_SILENCED);
                return 1;
            }
            if (isDuplicate(record)) {
                log.info("窗口内重复内网告警已去重: ruleCode={}, fingerprint={}",
                        record.getRuleCode(), record.getFingerprint());
                countIngest("DUPLICATE");
                return 0;
            }
            alertRecordMapper.insert(record);
            countIngest(STATUS_FIRING);
            if (isEnabled(rule)) {
                dispatchAfterCommit(List.of(new PendingNotify(rule, record, targetTenant)));
            }
            return 1;
        } finally {
            TenantContext.set(previousTenant);
        }
    }

    /**
     * 判断 firing 告警是否落在去重窗口内（以入库时间为基准）。
     *
     * @param record 待落库记录
     * @return 重复返回 {@code true}
     */
    private boolean isDuplicate(AlertRecord record) {
        AlertNotifyProperties.Dedup dedup = notifyProperties.getDedup();
        if (!dedup.isEnabled() || !StringUtils.hasText(record.getFingerprint())) {
            return false;
        }
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(Math.max(dedup.getWindowSeconds(), 1));
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRecord::getFingerprint, record.getFingerprint())
                .eq(AlertRecord::getStatus, STATUS_FIRING)
                .ge(AlertRecord::getCreateTime, threshold);
        Long count = alertRecordMapper.selectCount(wrapper);
        if (count != null && count > 0L) {
            Counter.builder(METRIC_DEDUP).register(meterRegistry).increment();
            return true;
        }
        return false;
    }

    /**
     * 判断规则是否可用于通知。
     *
     * @param rule 命中的规则（可为 {@code null}）
     * @return 规则存在且已启用时为 {@code true}
     */
    private static boolean isEnabled(AlertRule rule) {
        return rule != null && rule.getStatus() != null && rule.getStatus() == RULE_ENABLED;
    }

    /**
     * 按规则编码查询未被逻辑删除的规则，支持回落平台租户。
     *
     * @param ruleCode 规则编码
     * @return 规则实体；不存在时为 {@code null}
     */
    private AlertRule findRule(String ruleCode) {
        AlertRule rule = selectRule(ruleCode);
        Long tenantId = TenantContext.get();
        if (rule == null && tenantId != null && tenantId != TenantContext.DEFAULT_TENANT_ID) {
            TenantContext.set(TenantContext.DEFAULT_TENANT_ID);
            try {
                rule = selectRule(ruleCode);
            } finally {
                TenantContext.set(tenantId);
            }
            if (rule != null) {
                log.debug("告警规则回落到平台租户: ruleCode={}, tenantId={}", ruleCode, tenantId);
            }
        }
        return rule;
    }

    /**
     * 在当前租户上下文中按规则编码查询规则。
     *
     * @param ruleCode 规则编码
     * @return 规则实体；不存在时为 {@code null}
     */
    private AlertRule selectRule(String ruleCode) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRule::getRuleCode, ruleCode);
        return alertRuleMapper.selectOne(wrapper);
    }

    /**
     * 事务提交后异步派发通知；无事务上下文时立即派发。
     *
     * @param pending 待通知记录
     */
    private void dispatchAfterCommit(List<PendingNotify> pending) {
        if (pending.isEmpty()) {
            return;
        }
        Runnable dispatch = () -> pending.forEach(item ->
                alertNotifyDispatcher.dispatch(item.tenantId(), item.rule(), item.record()));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch.run();
            }
        });
    }

    /**
     * 记录入站告警结果指标。
     *
     * @param status 结果状态
     */
    private void countIngest(String status) {
        Counter.builder(METRIC_INGEST).tag("status", status)
                .register(meterRegistry).increment();
    }

    /**
     * 待通知项：持有规则、记录与归属租户。
     *
     * @param rule     命中的规则
     * @param record   告警记录
     * @param tenantId 归属租户
     */
    private record PendingNotify(AlertRule rule, AlertRecord record, Long tenantId) {
    }
}
