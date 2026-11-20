package com.bluewhale.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.alert.config.AlertNotifyProperties;
import com.bluewhale.alert.converter.AlertConverter;
import com.bluewhale.alert.dto.AlertRecordCreateRequest;
import com.bluewhale.alert.dto.AlertRecordQueryRequest;
import com.bluewhale.alert.dto.PrometheusWebhookPayload;
import com.bluewhale.alert.entity.AlertRecord;
import com.bluewhale.alert.entity.AlertRule;
import com.bluewhale.alert.mapper.AlertRecordMapper;
import com.bluewhale.alert.mapper.AlertRuleMapper;
import com.bluewhale.alert.service.AlertRecordService;
import com.bluewhale.alert.support.AlertLevels;
import com.bluewhale.alert.support.AlertNotifyDispatcher;
import com.bluewhale.alert.support.AlertRecordSupport;
import com.bluewhale.alert.vo.AlertRecordVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
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
 * 告警记录服务实现
 *
 * <p>查询为纯分页；{@link #ingest(PrometheusWebhookPayload)} 将 Alertmanager 入站告警
 * 转换为 {@code alert_record} 记录。落库与通知解耦：事务内只负责写库并收集待通知项，
 * 事务提交后再交由 {@link AlertNotifyDispatcher} 异步发送，避免外部 IO 长时间持有
 * 数据库连接，也避免「事务回滚但通知已发出」。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRecordServiceImpl implements AlertRecordService {

    /** 规则启用状态 */
    private static final int RULE_ENABLED = 1;

    private final AlertRecordMapper alertRecordMapper;

    private final AlertRuleMapper alertRuleMapper;

    private final AlertNotifyDispatcher alertNotifyDispatcher;

    private final AlertNotifyProperties notifyProperties;

    @Override
    public PageResult<AlertRecordVO> page(AlertRecordQueryRequest request) {
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getRuleCode()), AlertRecord::getRuleCode, request.getRuleCode());
        wrapper.eq(StringUtils.hasText(request.getAlertLevel()), AlertRecord::getAlertLevel, request.getAlertLevel());
        wrapper.eq(StringUtils.hasText(request.getStatus()), AlertRecord::getStatus, request.getStatus());
        wrapper.orderByDesc(AlertRecord::getTriggerTime);
        Page<AlertRecord> page = alertRecordMapper.selectPage(request.toPage(), wrapper);
        List<AlertRecordVO> records = page.getRecords().stream().map(AlertRecordServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int ingest(PrometheusWebhookPayload payload) {
        if (payload == null || payload.getAlerts() == null || payload.getAlerts().isEmpty()) {
            log.warn("Alertmanager 入站告警为空，忽略");
            return 0;
        }
        int inserted = 0;
        List<PendingNotify> pending = new ArrayList<>();
        Long previousTenant = TenantContext.get();
        for (PrometheusWebhookPayload.PrometheusAlert alert : payload.getAlerts()) {
            if (alert == null || alert.getLabels() == null) {
                continue;
            }
            String ruleCode = alert.getLabels().get(AlertRecordSupport.LABEL_ALERT_NAME);
            if (!StringUtils.hasText(ruleCode)) {
                log.warn("Alertmanager 告警缺少 alertname 标签，跳过");
                continue;
            }
            TenantContext.set(AlertRecordSupport.resolveTenantId(alert, previousTenant));
            try {
                AlertRule rule = findRule(ruleCode);
                AlertRecord record = AlertRecordSupport.toRecord(alert, rule);
                if (isDuplicate(record)) {
                    log.info("窗口内重复告警已去重: ruleCode={}, fingerprint={}, status={}",
                            ruleCode, record.getFingerprint(), record.getStatus());
                    continue;
                }
                alertRecordMapper.insert(record);
                inserted++;
                if (AlertRecordSupport.STATUS_FIRING.equals(record.getStatus()) && isEnabled(rule)) {
                    pending.add(new PendingNotify(rule, record, TenantContext.get()));
                }
            } finally {
                TenantContext.set(previousTenant);
            }
        }
        dispatchAfterCommit(pending);
        return inserted;
    }

    /**
     * 判断 firing 告警是否落在去重窗口内。
     *
     * <p>仅对 {@code FIRING} 且开启去重时生效；{@code RECOVERED} 始终落库，保证恢复可追踪。
     * 窗口以<b>入库时间</b>（{@code create_time}）为基准，而非 {@code trigger_time}：
     * Alertmanager 重发旧告警时 {@code startsAt} 是首次触发时间，用它判断会让窗口永久失效。</p>
     *
     * @param record 待落库记录
     * @return 重复返回 {@code true}
     */
    private boolean isDuplicate(AlertRecord record) {
        AlertNotifyProperties.Dedup dedup = notifyProperties.getDedup();
        if (!AlertRecordSupport.STATUS_FIRING.equals(record.getStatus()) || !dedup.isEnabled()
                || !StringUtils.hasText(record.getFingerprint())) {
            return false;
        }
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(Math.max(dedup.getWindowSeconds(), 1));
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRecord::getFingerprint, record.getFingerprint())
                .eq(AlertRecord::getStatus, AlertRecordSupport.STATUS_FIRING)
                .ge(AlertRecord::getCreateTime, threshold);
        Long count = alertRecordMapper.selectCount(wrapper);
        return count != null && count > 0L;
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
     * 按规则编码查询未被逻辑删除的规则。
     *
     * <p>规则在告警归属租户内优先匹配；业务租户通常没有自己的规则，
     * 此时回落到平台租户（{@link TenantContext#DEFAULT_TENANT_ID}）的共享规则，
     * 避免带 {@code tenant_id} 标签的告警因查不到规则而静默不通知。</p>
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int create(AlertRecordCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getContent())) {
            log.warn("内网告警创建请求内容为空，忽略");
            return 0;
        }
        Long requestedTenant = request.getTenantId();
        if (requestedTenant != null && requestedTenant <= 0L) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Long previousTenant = TenantContext.get();
        Long targetTenant = requestedTenant == null
                ? (previousTenant == null ? TenantContext.DEFAULT_TENANT_ID : previousTenant)
                : requestedTenant;
        TenantContext.set(targetTenant);
        try {
            log.info("内网告警接入: source={}, tenantId={}, ruleCode={}",
                    request.getSource(), targetTenant, request.getRuleCode());
            AlertRecord record = new AlertRecord();
            record.setRuleCode(AlertRecordSupport.normalizeRuleCode(request.getRuleCode()));
            record.setAlertLevel(AlertLevels.normalize(request.getAlertLevel(), AlertLevels.WARN));
            record.setContent(AlertRecordSupport.truncateContent(request.getContent()));
            record.setStatus(AlertRecordSupport.STATUS_FIRING);
            record.setFingerprint(AlertRecordSupport.fingerprint(request));
            record.setTriggerTime(request.getTriggerTime() == null
                    ? LocalDateTime.now() : request.getTriggerTime());
            if (isDuplicate(record)) {
                log.info("窗口内重复内网告警已去重: ruleCode={}, fingerprint={}",
                        record.getRuleCode(), record.getFingerprint());
                return 0;
            }
            alertRecordMapper.insert(record);
            AlertRule rule = StringUtils.hasText(record.getRuleCode())
                    ? findRule(record.getRuleCode()) : null;
            if (isEnabled(rule)) {
                dispatchAfterCommit(List.of(new PendingNotify(rule, record, targetTenant)));
            }
            return 1;
        } finally {
            TenantContext.set(previousTenant);
        }
    }

    /**
     * 事务提交后异步派发通知；无事务上下文时立即派发（便于单元测试与直调场景）。
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
     * 记录实体转视图对象
     *
     * @param record 记录实体
     * @return 记录视图对象
     */
    private static AlertRecordVO toVo(AlertRecord record) {
        return AlertConverter.INSTANCE.toRecordVo(record);
    }

    /**
     * 待通知项：持有规则、记录与归属租户。
     *
     * <p>租户必须在业务线程内捕获，异步线程没有原始 {@link TenantContext}。</p>
     *
     * @param rule     命中的规则
     * @param record   告警记录
     * @param tenantId 归属租户
     */
    private record PendingNotify(AlertRule rule, AlertRecord record, Long tenantId) {
    }
}
