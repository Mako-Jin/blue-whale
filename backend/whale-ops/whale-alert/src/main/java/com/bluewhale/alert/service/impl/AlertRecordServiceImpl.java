package com.bluewhale.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.alert.dto.AlertRecordQueryRequest;
import com.bluewhale.alert.dto.PrometheusWebhookPayload;
import com.bluewhale.alert.entity.AlertRecord;
import com.bluewhale.alert.entity.AlertRule;
import com.bluewhale.alert.mapper.AlertRecordMapper;
import com.bluewhale.alert.mapper.AlertRuleMapper;
import com.bluewhale.alert.service.AlertNotifyService;
import com.bluewhale.alert.service.AlertRecordService;
import com.bluewhale.alert.vo.AlertRecordVO;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 告警记录服务实现
 *
 * <p>查询为纯分页；{@link #ingest(PrometheusWebhookPayload)} 将 Alertmanager 入站告警
 * 转换为 {@code alert_record} 记录，并在 {@code firing} 且命中启用规则时调用
 * {@link AlertNotifyService} 发送通知。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRecordServiceImpl implements AlertRecordService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 告警内容最大长度（与 alert_record.content 列一致） */
    private static final int MAX_CONTENT_LENGTH = 512;

    /** 告警标签：告警名 */
    private static final String LABEL_ALERT_NAME = "alertname";

    /** 告警标签：级别 */
    private static final String LABEL_SEVERITY = "severity";

    /** 告警注解：摘要 */
    private static final String ANNOTATION_SUMMARY = "summary";

    /** 告警注解：描述 */
    private static final String ANNOTATION_DESCRIPTION = "description";

    /** 记录状态：触发中 */
    private static final String STATUS_FIRING = "FIRING";

    /** 记录状态：已恢复 */
    private static final String STATUS_RECOVERED = "RECOVERED";

    /** 规则启用状态 */
    private static final int RULE_ENABLED = 1;

    private final AlertRecordMapper alertRecordMapper;

    private final AlertRuleMapper alertRuleMapper;

    private final AlertNotifyService alertNotifyService;

    @Override
    public PageResult<AlertRecordVO> page(AlertRecordQueryRequest request) {
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getRuleCode()), AlertRecord::getRuleCode, request.getRuleCode());
        wrapper.eq(StringUtils.hasText(request.getAlertLevel()), AlertRecord::getAlertLevel, request.getAlertLevel());
        wrapper.eq(StringUtils.hasText(request.getStatus()), AlertRecord::getStatus, request.getStatus());
        wrapper.orderByDesc(AlertRecord::getTriggerTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<AlertRecord> page = alertRecordMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
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
        for (PrometheusWebhookPayload.PrometheusAlert alert : payload.getAlerts()) {
            if (alert == null || alert.getLabels() == null) {
                continue;
            }
            String ruleCode = alert.getLabels().get(LABEL_ALERT_NAME);
            if (!StringUtils.hasText(ruleCode)) {
                log.warn("Alertmanager 告警缺少 alertname 标签，跳过");
                continue;
            }
            AlertRule rule = findRule(ruleCode);
            AlertRecord record = toRecord(alert, rule);
            alertRecordMapper.insert(record);
            inserted++;
            if (STATUS_FIRING.equals(record.getStatus()) && rule != null
                    && rule.getStatus() != null && rule.getStatus() == RULE_ENABLED) {
                alertNotifyService.notify(rule, record);
            }
        }
        return inserted;
    }

    /**
     * 按规则编码查询未被逻辑删除的规则。
     *
     * @param ruleCode 规则编码
     * @return 规则实体；不存在时为 {@code null}
     */
    private AlertRule findRule(String ruleCode) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRule::getRuleCode, ruleCode);
        return alertRuleMapper.selectOne(wrapper);
    }

    /**
     * 将 Alertmanager 告警转换为记录实体。
     *
     * @param alert 单条告警
     * @param rule  命中的规则（可为 {@code null}）
     * @return 记录实体
     */
    private static AlertRecord toRecord(PrometheusWebhookPayload.PrometheusAlert alert, AlertRule rule) {
        boolean resolved = "resolved".equalsIgnoreCase(alert.getStatus());
        LocalDateTime now = LocalDateTime.now();
        AlertRecord record = new AlertRecord();
        record.setRuleId(rule == null ? null : rule.getId());
        record.setRuleCode(alert.getLabels().get(LABEL_ALERT_NAME));
        record.setAlertLevel(resolveLevel(alert, rule));
        record.setContent(buildContent(alert));
        record.setStatus(resolved ? STATUS_RECOVERED : STATUS_FIRING);
        record.setTriggerTime(parseTime(alert.getStartsAt(), now));
        if (resolved) {
            record.setRecoverTime(parseTime(alert.getEndsAt(), now));
        }
        return record;
    }

    /**
     * 解析告警级别：优先 severity 标签，其次规则级别，最后 {@code INFO}。
     *
     * @param alert 单条告警
     * @param rule  命中的规则（可为 {@code null}）
     * @return 归一化级别：INFO / WARN / ERROR / CRITICAL
     */
    private static String resolveLevel(PrometheusWebhookPayload.PrometheusAlert alert, AlertRule rule) {
        String raw = alert.getLabels().get(LABEL_SEVERITY);
        if (!StringUtils.hasText(raw) && rule != null) {
            raw = rule.getLevel();
        }
        if (!StringUtils.hasText(raw)) {
            return "INFO";
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "WARNING" -> "WARN";
            case "CRITICAL" -> "CRITICAL";
            case "ERROR" -> "ERROR";
            case "WARN" -> "WARN";
            default -> "INFO";
        };
    }

    /**
     * 拼接告警内容（summary + description），超长截断到列宽。
     *
     * @param alert 单条告警
     * @return 告警内容
     */
    private static String buildContent(PrometheusWebhookPayload.PrometheusAlert alert) {
        Map<String, String> annotations = alert.getAnnotations();
        String summary = annotations == null ? null : annotations.get(ANNOTATION_SUMMARY);
        String description = annotations == null ? null : annotations.get(ANNOTATION_DESCRIPTION);
        StringBuilder content = new StringBuilder();
        if (StringUtils.hasText(summary)) {
            content.append(summary.trim());
        }
        if (StringUtils.hasText(description)) {
            if (!content.isEmpty()) {
                content.append("；");
            }
            content.append(description.trim());
        }
        if (content.isEmpty()) {
            content.append(alert.getLabels().get(LABEL_ALERT_NAME));
        }
        return content.length() > MAX_CONTENT_LENGTH
                ? content.substring(0, MAX_CONTENT_LENGTH)
                : content.toString();
    }

    /**
     * 解析 RFC3339 时间，失败时回退到指定默认值。
     *
     * @param value       时间字符串
     * @param defaultValue 默认时间
     * @return 本地时区时间
     */
    private static LocalDateTime parseTime(String value, LocalDateTime defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return OffsetDateTime.parse(value)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (Exception e) {
            log.warn("告警时间解析失败，使用当前时间: value={}", value);
            return defaultValue;
        }
    }

    /**
     * 记录实体转视图对象
     *
     * @param record 记录实体
     * @return 记录视图对象
     */
    private static AlertRecordVO toVo(AlertRecord record) {
        AlertRecordVO vo = new AlertRecordVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }
}
