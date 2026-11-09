package com.bluewhale.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.alert.config.AlertNotifyProperties;
import com.bluewhale.alert.dto.AlertRecordCreateRequest;
import com.bluewhale.alert.dto.AlertRecordQueryRequest;
import com.bluewhale.alert.dto.PrometheusWebhookPayload;
import com.bluewhale.alert.converter.AlertConverter;
import com.bluewhale.alert.entity.AlertRecord;
import com.bluewhale.alert.entity.AlertRule;
import com.bluewhale.alert.mapper.AlertRecordMapper;
import com.bluewhale.alert.mapper.AlertRuleMapper;
import com.bluewhale.alert.service.AlertNotifyService;
import com.bluewhale.alert.service.AlertRecordService;
import com.bluewhale.alert.vo.AlertRecordVO;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

    /** 告警标签：租户ID（首选） */
    private static final String LABEL_TENANT_ID = "tenant_id";

    /** 告警标签：租户ID（兼容 Prometheus 规则常用写法） */
    private static final String LABEL_TENANT = "tenant";

    /** 指纹最大长度（与 alert_record.fingerprint 列一致） */
    private static final int MAX_FINGERPRINT_LENGTH = 64;

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

    private final AlertNotifyProperties notifyProperties;

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
        Long previousTenant = TenantContext.get();
        for (PrometheusWebhookPayload.PrometheusAlert alert : payload.getAlerts()) {
            if (alert == null || alert.getLabels() == null) {
                continue;
            }
            String ruleCode = alert.getLabels().get(LABEL_ALERT_NAME);
            if (!StringUtils.hasText(ruleCode)) {
                log.warn("Alertmanager 告警缺少 alertname 标签，跳过");
                continue;
            }
            TenantContext.set(resolveTenantId(alert, previousTenant));
            try {
                AlertRule rule = findRule(ruleCode);
                AlertRecord record = toRecord(alert, rule);
                if (isDuplicate(record)) {
                    log.info("窗口内重复告警已去重: ruleCode={}, fingerprint={}, status={}",
                            ruleCode, record.getFingerprint(), record.getStatus());
                    continue;
                }
                alertRecordMapper.insert(record);
                inserted++;
                if (STATUS_FIRING.equals(record.getStatus()) && rule != null
                        && rule.getStatus() != null && rule.getStatus() == RULE_ENABLED) {
                    alertNotifyService.notify(rule, record);
                }
            } finally {
                TenantContext.set(previousTenant);
            }
        }
        return inserted;
    }

    /**
     * 解析告警归属租户：优先 {@code tenant_id}/{@code tenant} 标签，其次当前上下文，最后默认租户。
     *
     * @param alert          单条告警
     * @param currentTenant 当前线程租户（可为 {@code null}）
     * @return 租户ID
     */
    private static Long resolveTenantId(PrometheusWebhookPayload.PrometheusAlert alert, Long currentTenant) {
        Long fromLabel = parsePositive(alert.getLabels().get(LABEL_TENANT_ID));
        if (fromLabel == null) {
            fromLabel = parsePositive(alert.getLabels().get(LABEL_TENANT));
        }
        if (fromLabel != null) {
            return fromLabel;
        }
        return currentTenant == null ? TenantContext.DEFAULT_TENANT_ID : currentTenant;
    }

    /**
     * 解析正整数标签值
     *
     * @param raw 原始文本
     * @return 正整数；非法时返回 {@code null}
     */
    private static Long parsePositive(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value > 0L ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断 firing 告警是否落在去重窗口内。
     *
     * <p>仅对 {@code FIRING} 且开启去重时生效；{@code RECOVERED} 始终落库，保证恢复可追踪。</p>
     *
     * @param record 待落库记录
     * @return 重复返回 {@code true}
     */
    private boolean isDuplicate(AlertRecord record) {
        AlertNotifyProperties.Dedup dedup = notifyProperties.getDedup();
        if (!STATUS_FIRING.equals(record.getStatus()) || !dedup.isEnabled()
                || !StringUtils.hasText(record.getFingerprint())) {
            return false;
        }
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(Math.max(dedup.getWindowSeconds(), 1));
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRecord::getFingerprint, record.getFingerprint())
                .eq(AlertRecord::getStatus, STATUS_FIRING)
                .ge(AlertRecord::getTriggerTime, threshold);
        Long count = alertRecordMapper.selectCount(wrapper);
        return count != null && count > 0L;
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
        record.setFingerprint(resolveFingerprint(alert));
        record.setTriggerTime(parseTime(alert.getStartsAt(), now));
        if (resolved) {
            record.setRecoverTime(parseTime(alert.getEndsAt(), now));
        }
        return record;
    }

    /**
     * 解析告警指纹：优先 Alertmanager 下发的 {@code fingerprint}，否则按标签排序后计算 SHA-256。
     *
     * @param alert 单条告警
     * @return 最长 64 字符的指纹
     */
    private static String resolveFingerprint(PrometheusWebhookPayload.PrometheusAlert alert) {
        String fingerprint = alert.getFingerprint();
        if (StringUtils.hasText(fingerprint)) {
            return truncateFingerprint(fingerprint.trim());
        }
        String canonical = alert.getLabels().entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> entry.getKey() + "=" + Objects.toString(entry.getValue(), ""))
                .collect(Collectors.joining("|"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            log.warn("告警指纹计算失败（SHA-256 不可用），退回标签文本: reason={}", e.getMessage());
            return truncateFingerprint(canonical);
        }
    }

    /**
     * 截断指纹到列宽
     *
     * @param value 原始指纹
     * @return 最长 {@value #MAX_FINGERPRINT_LENGTH} 字符
     */
    private static String truncateFingerprint(String value) {
        return value.length() > MAX_FINGERPRINT_LENGTH
                ? value.substring(0, MAX_FINGERPRINT_LENGTH)
                : value;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int create(AlertRecordCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getContent())) {
            log.warn("内网告警创建请求内容为空，忽略");
            return 0;
        }
        Long previousTenant = TenantContext.get();
        TenantContext.set(request.getTenantId() == null
                ? (previousTenant == null ? TenantContext.DEFAULT_TENANT_ID : previousTenant)
                : request.getTenantId());
        try {
            AlertRecord record = new AlertRecord();
            record.setRuleCode(normalizeRuleCode(request.getRuleCode()));
            record.setAlertLevel(normalizeLevel(request.getAlertLevel()));
            record.setContent(truncateContent(request.getContent()));
            record.setStatus(STATUS_FIRING);
            record.setFingerprint(resolveFingerprint(request));
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
            if (rule != null && rule.getStatus() != null && rule.getStatus() == RULE_ENABLED) {
                alertNotifyService.notify(rule, record);
            }
            return 1;
        } finally {
            TenantContext.set(previousTenant);
        }
    }

    /**
     * 归一化规则编码（空值记为 {@code external}，便于按来源检索）。
     *
     * @param ruleCode 原始规则编码
     * @return 规则编码
     */
    private static String normalizeRuleCode(String ruleCode) {
        return StringUtils.hasText(ruleCode) ? ruleCode.trim() : "external";
    }

    /**
     * 归一化告警级别（未知值回落 {@code WARN}）。
     *
     * @param level 原始级别
     * @return INFO / WARN / ERROR / CRITICAL
     */
    private static String normalizeLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return "WARN";
        }
        String normalized = level.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "INFO", "WARN", "ERROR", "CRITICAL" -> normalized;
            default -> "WARN";
        };
    }

    /**
     * 截断告警内容到列宽。
     *
     * @param content 原始内容
     * @return 最长 {@value #MAX_CONTENT_LENGTH} 字符
     */
    private static String truncateContent(String content) {
        String trimmed = content.trim();
        return trimmed.length() > MAX_CONTENT_LENGTH
                ? trimmed.substring(0, MAX_CONTENT_LENGTH) : trimmed;
    }

    /**
     * 解析内网告警指纹：请求已带则直接用，否则按「规则编码 + 内容」计算 SHA-256。
     *
     * @param request 创建请求
     * @return 最长 64 字符指纹
     */
    private static String resolveFingerprint(AlertRecordCreateRequest request) {
        if (StringUtils.hasText(request.getFingerprint())) {
            return truncateFingerprint(request.getFingerprint().trim());
        }
        String canonical = normalizeRuleCode(request.getRuleCode()) + "|" + request.getContent().trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            log.warn("内网告警指纹计算失败（SHA-256 不可用）: reason={}", e.getMessage());
            return truncateFingerprint(canonical);
        }
    }

    /**
     * 记录实体转视图对象
     *
     * @param record 记录实体
     * @return 记录视图对象
     */
    private static AlertRecordVO toVo(AlertRecord record) {
        AlertRecordVO vo = AlertConverter.INSTANCE.toRecordVo(record);
        return vo;
    }
}
