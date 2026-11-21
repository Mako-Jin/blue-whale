package com.bluewhale.alert.support;

import com.bluewhale.alert.dto.AlertRecordCreateRequest;
import com.bluewhale.alert.dto.PrometheusWebhookPayload;
import com.bluewhale.alert.po.AlertRecordPo;
import com.bluewhale.alert.po.AlertRulePo;
import com.bluewhale.common.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 告警记录转换与归一化工具。
 *
 * <p>承载 {@code Alertmanager} 入站与内网创建共用的纯函数：租户解析、记录装配、
 * 指纹计算、内容拼接、时间解析与列宽截断。抽离为独立类后可直接单元测试，
 * 也避免两处入口重复实现导致行为漂移。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public final class AlertRecordSupport {

    /** 告警内容最大长度（与 alert_record.content 列一致） */
    public static final int MAX_CONTENT_LENGTH = 512;

    /** 指纹最大长度（与 alert_record.fingerprint 列一致） */
    public static final int MAX_FINGERPRINT_LENGTH = 64;

    /** 告警标签：告警名 */
    public static final String LABEL_ALERT_NAME = "alertname";

    /** 告警标签：级别 */
    static final String LABEL_SEVERITY = "severity";

    /** 告警标签：租户ID（首选） */
    static final String LABEL_TENANT_ID = "tenant_id";

    /** 告警标签：租户ID（兼容 Prometheus 规则常用写法） */
    static final String LABEL_TENANT = "tenant";

    /** 告警注解：摘要 */
    private static final String ANNOTATION_SUMMARY = "summary";

    /** 告警注解：描述 */
    private static final String ANNOTATION_DESCRIPTION = "description";

    /** 记录状态：触发中 */
    public static final String STATUS_FIRING = "FIRING";

    /** 记录状态：已恢复 */
    public static final String STATUS_RECOVERED = "RECOVERED";

    /** 记录状态：已静默（落库但不通知） */
    public static final String STATUS_SILENCED = "SILENCED";

    /** 内网告警未提供规则编码时的兜底值 */
    private static final String EXTERNAL_RULE_CODE = "external";

    /** 单条告警标签数量上限 */
    static final int MAX_LABEL_COUNT = 64;

    /** 标签键最大长度 */
    static final int MAX_LABEL_KEY_LENGTH = 128;

    /** 标签值最大长度 */
    static final int MAX_LABEL_VALUE_LENGTH = 512;

    /** 单条告警注解数量上限 */
    static final int MAX_ANNOTATION_COUNT = 32;

    /** 注解值最大长度 */
    static final int MAX_ANNOTATION_VALUE_LENGTH = 1024;

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private AlertRecordSupport() {
    }

    /**
     * 判断告警标签 / 注解是否超出体积上限。
     *
     * <p>入站 payload 由外部系统控制，超大标签集会在指纹拼接与落库阶段放大内存占用，
     * 因此在写库前直接拒绝。上限按 Alertmanager 常规规模设定，正常告警不会触发。</p>
     *
     * @param alert 单条告警
     * @return 超限返回 {@code true}
     */
    public static boolean isOversized(PrometheusWebhookPayload.PrometheusAlert alert) {
        Map<String, String> labels = alert.getLabels();
        if (labels != null) {
            if (labels.size() > MAX_LABEL_COUNT) {
                return true;
            }
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                if (entry.getKey() == null || entry.getKey().length() > MAX_LABEL_KEY_LENGTH
                        || (entry.getValue() != null && entry.getValue().length() > MAX_LABEL_VALUE_LENGTH)) {
                    return true;
                }
            }
        }
        Map<String, String> annotations = alert.getAnnotations();
        if (annotations == null) {
            return false;
        }
        if (annotations.size() > MAX_ANNOTATION_COUNT) {
            return true;
        }
        return annotations.values().stream()
                .anyMatch(value -> value != null && value.length() > MAX_ANNOTATION_VALUE_LENGTH);
    }

    /**
     * 解析告警归属租户：优先 {@code tenant_id}/{@code tenant} 标签，其次当前上下文，最后默认租户。
     *
     * @param alert         单条告警
     * @param currentTenant 当前线程租户（可为 {@code null}）
     * @return 租户ID
     */
    public static Long resolveTenantId(PrometheusWebhookPayload.PrometheusAlert alert, Long currentTenant) {
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
     * 解析正整数标签值。
     *
     * @param raw 原始文本
     * @return 正整数；非法时返回 {@code null}
     */
    public static Long parsePositive(String raw) {
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
     * 将 Alertmanager 告警转换为记录实体。
     *
     * @param alert 单条告警
     * @param rule  命中的规则（可为 {@code null}）
     * @return 记录实体
     */
    public static AlertRecordPo toRecord(PrometheusWebhookPayload.PrometheusAlert alert, AlertRulePo rule) {
        boolean resolved = "resolved".equalsIgnoreCase(alert.getStatus());
        LocalDateTime now = LocalDateTime.now();
        AlertRecordPo record = new AlertRecordPo();
        record.setRuleId(rule == null ? null : rule.getId());
        record.setRuleCode(alert.getLabels().get(LABEL_ALERT_NAME));
        record.setAlertLevel(AlertLevels.normalize(resolveSeverity(alert, rule), AlertLevels.INFO));
        record.setContent(buildContent(alert));
        record.setStatus(resolved ? STATUS_RECOVERED : STATUS_FIRING);
        record.setFingerprint(fingerprint(alert));
        record.setTriggerTime(parseTime(alert.getStartsAt(), now));
        if (resolved) {
            record.setRecoverTime(parseTime(alert.getEndsAt(), now));
        }
        return record;
    }

    /**
     * 解析告警级别来源：优先 severity 标签，其次规则级别。
     *
     * @param alert 单条告警
     * @param rule  命中的规则（可为 {@code null}）
     * @return 原始级别文本；均为空时返回 {@code null}
     */
    private static String resolveSeverity(PrometheusWebhookPayload.PrometheusAlert alert, AlertRulePo rule) {
        String raw = alert.getLabels().get(LABEL_SEVERITY);
        if (!StringUtils.hasText(raw) && rule != null) {
            raw = rule.getLevel();
        }
        return raw;
    }

    /**
     * 解析告警指纹：优先 Alertmanager 下发的 {@code fingerprint}，否则按标签排序后计算 SHA-256。
     *
     * @param alert 单条告警
     * @return 最长 {@value #MAX_FINGERPRINT_LENGTH} 字符的指纹
     */
    public static String fingerprint(PrometheusWebhookPayload.PrometheusAlert alert) {
        String fingerprint = alert.getFingerprint();
        if (StringUtils.hasText(fingerprint)) {
            return truncateFingerprint(fingerprint.trim());
        }
        String canonical = alert.getLabels().entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> entry.getKey() + "=" + Objects.toString(entry.getValue(), ""))
                .collect(Collectors.joining("|"));
        return sha256Hex(canonical);
    }

    /**
     * 解析内网告警指纹：请求已带则直接用，否则按「规则编码 + 内容」计算 SHA-256。
     *
     * @param request 创建请求
     * @return 最长 {@value #MAX_FINGERPRINT_LENGTH} 字符的指纹
     */
    public static String fingerprint(AlertRecordCreateRequest request) {
        if (StringUtils.hasText(request.getFingerprint())) {
            return truncateFingerprint(request.getFingerprint().trim());
        }
        String canonical = normalizeRuleCode(request.getRuleCode()) + "|" + request.getContent().trim();
        return sha256Hex(canonical);
    }

    /**
     * 计算 SHA-256 摘要；算法不可用时退回原文（调用方负责截断）。
     *
     * @param canonical 规范化文本
     * @return 十六进制摘要或原文
     */
    private static String sha256Hex(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            log.warn("告警指纹计算失败（SHA-256 不可用），退回原文: reason={}", e.getMessage());
            return truncateFingerprint(canonical);
        }
    }

    /**
     * 拼接告警内容（summary + description），超长截断到列宽。
     *
     * @param alert 单条告警
     * @return 告警内容
     */
    public static String buildContent(PrometheusWebhookPayload.PrometheusAlert alert) {
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
        return truncateContent(content.toString());
    }

    /**
     * 解析 RFC3339 时间，失败时回退到指定默认值。
     *
     * @param value        时间字符串
     * @param defaultValue 默认时间
     * @return 本地时区时间
     */
    public static LocalDateTime parseTime(String value, LocalDateTime defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return OffsetDateTime.parse(value)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (RuntimeException e) {
            log.warn("告警时间解析失败，使用默认时间: value={}", value);
            return defaultValue;
        }
    }

    /**
     * 归一化规则编码（空值记为 {@code external}，便于按来源检索）。
     *
     * @param ruleCode 原始规则编码
     * @return 规则编码
     */
    public static String normalizeRuleCode(String ruleCode) {
        return StringUtils.hasText(ruleCode) ? ruleCode.trim() : EXTERNAL_RULE_CODE;
    }

    /**
     * 截断告警内容到列宽。
     *
     * @param content 原始内容
     * @return 最长 {@value #MAX_CONTENT_LENGTH} 字符
     */
    public static String truncateContent(String content) {
        String trimmed = content.trim();
        return trimmed.length() > MAX_CONTENT_LENGTH
                ? trimmed.substring(0, MAX_CONTENT_LENGTH) : trimmed;
    }

    /**
     * 截断指纹到列宽。
     *
     * @param value 原始指纹
     * @return 最长 {@value #MAX_FINGERPRINT_LENGTH} 字符
     */
    public static String truncateFingerprint(String value) {
        return value.length() > MAX_FINGERPRINT_LENGTH
                ? value.substring(0, MAX_FINGERPRINT_LENGTH)
                : value;
    }
}
