package com.bluewhale.alert.service.impl;

import com.bluewhale.alert.config.AlertNotifyProperties;
import com.bluewhale.alert.entity.AlertNotifyLog;
import com.bluewhale.alert.entity.AlertRecord;
import com.bluewhale.alert.entity.AlertRule;
import com.bluewhale.alert.mapper.AlertNotifyLogMapper;
import com.bluewhale.alert.service.AlertNotifyService;
import com.bluewhale.common.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 告警通知服务实现：邮件、Webhook 与短信三种通道。
 *
 * <p>通道取值来自告警规则 {@code channel}：{@code EMAIL} / {@code SMS} / {@code WEBHOOK} /
 * {@code NONE}。短信经部署侧 HTTP 网关发送，平台不绑定具体供应商。</p>
 *
 * <p>可观测性与可靠性（V2 扩展）：</p>
 * <ul>
 *   <li>每次发送记录 {@code alert_notify_total{channel,result}} 计数与耗时直方图；</li>
 *   <li>失败按 {@code whale.alert.notify.retry} 配置做有限次退避重试；</li>
 *   <li>发送结果（成功/最终失败）落 {@code alert_notify_log}，支持"为什么没收到"排查；</li>
 *   <li>规则配置 {@code notifyTargets} 时覆盖全局收件人，实现按规则投递。</li>
 * </ul>
 * <p>任何发送异常都不会抛出到调用方，告警落库不受影响。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class AlertNotifyServiceImpl implements AlertNotifyService {

    /** 邮件通道 */
    private static final String CHANNEL_EMAIL = "EMAIL";

    /** Webhook 通道 */
    private static final String CHANNEL_WEBHOOK = "WEBHOOK";

    /** 短信通道 */
    private static final String CHANNEL_SMS = "SMS";

    /** 不通知 */
    private static final String CHANNEL_NONE = "NONE";

    /** 发送结果：成功 */
    private static final String RESULT_SUCCESS = "SUCCESS";

    /** 发送结果：失败 */
    private static final String RESULT_FAILED = "FAILED";

    /** 发送结果：跳过（通道未启用或缺少配置） */
    private static final String RESULT_SKIPPED = "SKIPPED";

    /** 单条短信内容上限（字符），超长截断避免被网关拒绝或拆成多条计费 */
    private static final int MAX_SMS_CONTENT_LENGTH = 480;

    /** 默认连接超时（毫秒）：请求级超时由各通道配置通过 {@code HttpRequest.timeout} 控制 */
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3000;

    /** 共享 HTTP 客户端：复用连接池与 Selector 线程，避免每次发送都新建 */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(DEFAULT_CONNECT_TIMEOUT_MILLIS))
            .build();

    private final AlertNotifyProperties properties;

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    private final ObjectMapper objectMapper;

    private final MeterRegistry meterRegistry;

    private final AlertNotifyLogMapper alertNotifyLogMapper;

    /**
     * 构造通知服务
     *
     * @param properties         通知配置
     * @param mailSenderProvider 邮件发送器（未配置 spring.mail.host 时为空）
     * @param objectMapper       内部 Jackson 2 映射器（显式限定，避免与 MVC 的 Jackson 3 混淆）
     * @param meterRegistry      指标注册表
     * @param alertNotifyLogMapper 通知发送日志 Mapper
     */
    public AlertNotifyServiceImpl(AlertNotifyProperties properties,
                                  ObjectProvider<JavaMailSender> mailSenderProvider,
                                  @Qualifier("alertObjectMapper") ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry,
                                  AlertNotifyLogMapper alertNotifyLogMapper) {
        this.properties = properties;
        this.mailSenderProvider = mailSenderProvider;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.alertNotifyLogMapper = alertNotifyLogMapper;
    }

    @Override
    public void notify(AlertRule rule, AlertRecord record) {
        if (rule == null || record == null) {
            return;
        }
        String channel = rule.getChannel() == null
                ? CHANNEL_NONE : rule.getChannel().trim().toUpperCase(Locale.ROOT);
        if (CHANNEL_NONE.equals(channel)) {
            return;
        }
        if (!CHANNEL_EMAIL.equals(channel) && !CHANNEL_WEBHOOK.equals(channel)
                && !CHANNEL_SMS.equals(channel)) {
            log.warn("告警通道暂未实现，跳过通知: channel={}, ruleCode={}", channel, rule.getRuleCode());
            recordResult(channel, RESULT_SKIPPED);
            return;
        }
        Timer.Sample sample = Timer.start(meterRegistry);
        Attempt attempt = sendWithRetry(channel, rule, record);
        sample.stop(Timer.builder("alert_notify_duration")
                .tag("channel", channel)
                .register(meterRegistry));
        recordResult(channel, attempt.result());
        persistLog(rule, record, channel, attempt);
        if (RESULT_FAILED.equals(attempt.result())) {
            log.error("告警通知最终失败: ruleCode={}, channel={}, retries={}, reason={}",
                    rule.getRuleCode(), channel, attempt.retries(), attempt.error());
        }
    }

    /**
     * 按配置做有限次退避重试。
     *
     * @param channel 通道
     * @param rule    告警规则
     * @param record  告警记录
     * @return 发送结果
     */
    private Attempt sendWithRetry(String channel, AlertRule rule, AlertRecord record) {
        AlertNotifyProperties.Retry retry = properties.getRetry();
        int maxAttempts = Math.max(1, retry.getMaxAttempts());
        long backoffMillis = Math.max(0L, retry.getBackoffMillis());
        String lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                boolean sent = dispatch(channel, rule, record);
                return new Attempt(sent ? RESULT_SUCCESS : RESULT_SKIPPED, attempt - 1, null);
            } catch (NotifyFailure failure) {
                lastError = failure.getMessage();
                if (!failure.isRetryable() || attempt == maxAttempts) {
                    return new Attempt(RESULT_FAILED, attempt - 1, lastError);
                }
            } catch (RuntimeException e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                if (attempt == maxAttempts) {
                    return new Attempt(RESULT_FAILED, attempt - 1, lastError);
                }
            }
            sleepBackoff(backoffMillis * attempt);
        }
        return new Attempt(RESULT_FAILED, maxAttempts - 1, lastError);
    }

    /**
     * 退避等待，保持中断标志。
     *
     * @param millis 等待毫秒数
     */
    private static void sleepBackoff(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 分发到具体通道。
     *
     * @param channel 通道
     * @param rule    告警规则
     * @param record  告警记录
     * @return 是否真正发送（{@code false} 表示通道未启用/缺配置而跳过）
     */
    private boolean dispatch(String channel, AlertRule rule, AlertRecord record) {
        return switch (channel) {
            case CHANNEL_EMAIL -> sendEmail(rule, record);
            case CHANNEL_WEBHOOK -> sendWebhook(rule, record);
            case CHANNEL_SMS -> sendSms(rule, record);
            default -> false;
        };
    }

    /**
     * 记录发送指标。
     *
     * @param channel 通道
     * @param result  结果
     */
    private void recordResult(String channel, String result) {
        Counter.builder("alert_notify_total")
                .tag("channel", channel)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 持久化发送日志；失败不影响主流程。
     *
     * @param rule     告警规则
     * @param record   告警记录
     * @param channel  通道
     * @param attempt  发送结果
     */
    private void persistLog(AlertRule rule, AlertRecord record, String channel, Attempt attempt) {
        try {
            AlertNotifyLog notifyLog = new AlertNotifyLog();
            notifyLog.setRecordId(record.getId());
            notifyLog.setRuleCode(rule.getRuleCode());
            notifyLog.setChannel(channel);
            notifyLog.setStatus(attempt.result());
            notifyLog.setRetryCount(attempt.retries());
            notifyLog.setError(attempt.error() == null ? null : truncate(attempt.error(), 512));
            alertNotifyLogMapper.insert(notifyLog);
        } catch (RuntimeException e) {
            log.warn("告警通知日志落库失败: ruleCode={}, channel={}, reason={}",
                    rule.getRuleCode(), channel, e.getMessage());
        }
    }

    /**
     * 解析规则级接收人；未配置时回落到全局配置。
     *
     * @param rule     告警规则
     * @param fallback 全局接收人
     * @return 接收人列表
     */
    private static List<String> resolveTargets(AlertRule rule, List<String> fallback) {
        if (!StringUtils.hasText(rule.getNotifyTargets())) {
            return fallback;
        }
        return Arrays.stream(rule.getNotifyTargets().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    /**
     * 发送邮件通知。
     *
     * @param rule   告警规则
     * @param record 告警记录
     * @return 是否已发送
     */
    private boolean sendEmail(AlertRule rule, AlertRecord record) {
        AlertNotifyProperties.Email email = properties.getEmail();
        if (!email.isEnabled()) {
            log.debug("邮件通道未启用，跳过通知: ruleCode={}", rule.getRuleCode());
            return false;
        }
        List<String> globalRecipients = email.getTo() == null ? List.of() : email.getTo().stream()
                .filter(StringUtils::hasText)
                .toList();
        List<String> recipients = resolveTargets(rule, globalRecipients).stream()
                .filter(StringUtils::hasText)
                .toList();
        if (recipients.isEmpty() || !StringUtils.hasText(email.getFrom())) {
            log.warn("邮件通道缺少发件人或收件人，跳过通知: ruleCode={}", rule.getRuleCode());
            return false;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("未配置 spring.mail.host，JavaMailSender 不可用，跳过邮件通知: ruleCode={}",
                    rule.getRuleCode());
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(email.getFrom());
            helper.setTo(recipients.toArray(String[]::new));
            helper.setSubject(buildSubject(rule));
            helper.setText(buildHtml(rule, record), true);
            mailSender.send(message);
            log.info("告警邮件已发送: ruleCode={}, to={}", rule.getRuleCode(), recipients);
            return true;
        } catch (Exception e) {
            throw new NotifyFailure("邮件发送失败", true, e);
        }
    }

    /**
     * 发送 Webhook 通知（JSON POST）。
     *
     * @param rule   告警规则
     * @param record 告警记录
     * @return 是否已发送
     */
    private boolean sendWebhook(AlertRule rule, AlertRecord record) {
        AlertNotifyProperties.Webhook webhook = properties.getWebhook();
        if (!webhook.isEnabled() || !StringUtils.hasText(webhook.getUrl())) {
            log.debug("Webhook 通道未启用或未配置地址，跳过通知: ruleCode={}", rule.getRuleCode());
            return false;
        }
        int timeout = webhook.getTimeoutMillis() <= 0 ? 3000 : webhook.getTimeoutMillis();
        try {
            String body = objectMapper.writeValueAsString(buildPayload(rule, record));
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhook.getUrl()))
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("告警 Webhook 已送达: ruleCode={}, status={}",
                        rule.getRuleCode(), response.statusCode());
                return true;
            }
            boolean retryable = response.statusCode() >= 500 || response.statusCode() == 429;
            throw new NotifyFailure("Webhook 返回 " + response.statusCode()
                    + ": " + truncate(response.body(), 256), retryable);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NotifyFailure("Webhook 发送被中断", false, e);
        } catch (NotifyFailure e) {
            throw e;
        } catch (Exception e) {
            throw new NotifyFailure("Webhook 发送失败", true, e);
        }
    }

    /**
     * 发送短信通知（F-03b：统一 HTTP 网关协议）。
     *
     * <p>网关协议为 JSON POST：{@code phones/templateCode/signName/content} 加告警元数据；
     * 配置 {@code token} 时携带 {@code Authorization: Bearer}。供应商专有签名应由网关完成，
     * 平台不持有短信供应商 AccessKey。</p>
     *
     * @param rule   告警规则
     * @param record 告警记录
     * @return 是否已发送
     */
    private boolean sendSms(AlertRule rule, AlertRecord record) {
        AlertNotifyProperties.Sms sms = properties.getSms();
        if (!sms.isEnabled()) {
            log.debug("短信通道未启用，跳过通知: ruleCode={}", rule.getRuleCode());
            return false;
        }
        List<String> globalRecipients = sms.getTo() == null ? List.of() : sms.getTo().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        List<String> recipients = resolveTargets(rule, globalRecipients);
        if (recipients.isEmpty() || !StringUtils.hasText(sms.getUrl())) {
            log.warn("短信通道缺少网关地址或接收手机号，跳过通知: ruleCode={}", rule.getRuleCode());
            return false;
        }
        int timeout = sms.getTimeoutMillis() <= 0 ? 3000 : sms.getTimeoutMillis();
        try {
            String body = objectMapper.writeValueAsString(
                    buildSmsPayload(sms, rule, record, recipients));
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(sms.getUrl().trim()))
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (StringUtils.hasText(sms.getToken())) {
                builder.header("Authorization", "Bearer " + sms.getToken().trim());
            }
            HttpResponse<String> response = HTTP_CLIENT.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("告警短信已提交网关: ruleCode={}, to={}",
                        rule.getRuleCode(), maskPhones(recipients));
                return true;
            }
            boolean retryable = response.statusCode() >= 500 || response.statusCode() == 429;
            throw new NotifyFailure("短信网关返回 " + response.statusCode()
                    + ": " + truncate(response.body(), 256), retryable);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NotifyFailure("短信发送被中断", false, e);
        } catch (NotifyFailure e) {
            throw e;
        } catch (Exception e) {
            throw new NotifyFailure("短信发送失败", true, e);
        }
    }

    /**
     * 构造短信网关载荷。
     *
     * @param sms        短信通道配置
     * @param rule       告警规则
     * @param record     告警记录
     * @param recipients 接收手机号
     * @return 网关 JSON 载荷
     */
    private static Map<String, Object> buildSmsPayload(AlertNotifyProperties.Sms sms, AlertRule rule,
                                                       AlertRecord record, List<String> recipients) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("phones", recipients);
        payload.put("templateCode", sms.getTemplateCode());
        payload.put("signName", sms.getSignName());
        payload.put("content", buildSmsContent(rule, record));
        payload.put("ruleCode", rule.getRuleCode());
        payload.put("ruleName", rule.getRuleName());
        payload.put("level", record.getAlertLevel());
        payload.put("target", rule.getTarget());
        payload.put("status", record.getStatus());
        payload.put("tenantId", TenantContext.get());
        payload.put("triggerTime",
                record.getTriggerTime() == null ? null : record.getTriggerTime().toString());
        return payload;
    }

    /**
     * 构造短信正文并截断到单条上限。
     *
     * <p>外部输入（规则名、目标、告警内容）先清洗控制字符（{@code \r\n\t} 等），
     * 避免伪造短信换行或破坏网关模板渲染。</p>
     *
     * @param rule   告警规则
     * @param record 告警记录
     * @return 短信正文
     */
    private static String buildSmsContent(AlertRule rule, AlertRecord record) {
        String content = "[BlueWhale][" + Objects.toString(record.getAlertLevel(), "-") + "] "
                + Objects.toString(rule.getRuleName(), rule.getRuleCode()) + ": "
                + Objects.toString(record.getContent(), "-")
                + " (target=" + Objects.toString(rule.getTarget(), "-")
                + ", status=" + Objects.toString(record.getStatus(), "-") + ")";
        String sanitized = sanitizeSmsText(content);
        if (sanitized.length() <= MAX_SMS_CONTENT_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_SMS_CONTENT_LENGTH - 3) + "...";
    }

    /**
     * 清洗短信文本：控制字符替换为空格，压缩连续空白。
     *
     * @param raw 原始文本
     * @return 清洗后的单行文本
     */
    private static String sanitizeSmsText(String raw) {
        StringBuilder cleaned = new StringBuilder(raw.length());
        boolean lastBlank = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            boolean blank = Character.isISOControl(ch) || Character.isWhitespace(ch);
            if (blank) {
                if (!lastBlank) {
                    cleaned.append(' ');
                    lastBlank = true;
                }
            } else {
                cleaned.append(ch);
                lastBlank = false;
            }
        }
        return cleaned.toString().trim();
    }

    /**
     * 手机号脱敏，避免日志泄露完整号码。
     *
     * @param phones 手机号列表
     * @return 脱敏后的手机号列表
     */
    private static List<String> maskPhones(List<String> phones) {
        return phones.stream().map(AlertNotifyServiceImpl::maskPhone).toList();
    }

    /**
     * 单个手机号脱敏。
     *
     * @param phone 手机号
     * @return 脱敏值
     */
    private static String maskPhone(String phone) {
        if (phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 截断响应体，避免异常响应写满日志。
     *
     * @param value 原值
     * @param max   最大长度
     * @return 截断后的值
     */
    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }

    /**
     * 构造邮件主题。
     *
     * @param rule 告警规则
     * @return 邮件主题
     */
    private static String buildSubject(AlertRule rule) {
        return "[BlueWhale][" + rule.getLevel() + "] " + rule.getRuleName();
    }

    /**
     * 构造 HTML 邮件正文（F-03）：所有动态字段做 HTML 转义，避免告警内容注入。
     *
     * @param rule   告警规则
     * @param record 告警记录
     * @return HTML 正文
     */
    private static String buildHtml(AlertRule rule, AlertRecord record) {
        Long tenantId = TenantContext.get();
        String level = HtmlUtils.htmlEscape(Objects.toString(record.getAlertLevel(), "-"));
        return "<html><body style=\"font-family:Arial,Helvetica,sans-serif;color:#1f2329\">"
                + "<h3 style=\"margin:0 0 12px\">[BlueWhale] 告警通知</h3>"
                + "<table cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;font-size:14px\">"
                + row("规则编码", rule.getRuleCode())
                + row("规则名称", rule.getRuleName())
                + row("告警级别", level)
                + row("监控目标", rule.getTarget())
                + row("告警内容", record.getContent())
                + row("租户ID", tenantId == null ? "-" : tenantId.toString())
                + row("状态", record.getStatus())
                + row("触发时间", record.getTriggerTime() == null ? "-" : record.getTriggerTime().toString())
                + "</table>"
                + "<p style=\"color:#8f959e;font-size:12px;margin-top:16px\">"
                + "本邮件由 BlueWhale 告警平台自动发送，请勿直接回复。</p>"
                + "</body></html>";
    }

    /**
     * 构造 HTML 表格行
     *
     * @param label 字段名
     * @param value 字段值（自动转义）
     * @return 表格行 HTML
     */
    private static String row(String label, String value) {
        return "<tr><td style=\"border:1px solid #dee0e3;background:#f7f8fa;"
                + "white-space:nowrap\">" + HtmlUtils.htmlEscape(label)
                + "</td><td style=\"border:1px solid #dee0e3\">"
                + HtmlUtils.htmlEscape(Objects.toString(value, "-")) + "</td></tr>";
    }

    /**
     * 构造 Webhook JSON 载荷。
     *
     * @param rule   告警规则
     * @param record 告警记录
     * @return 载荷键值
     */
    private static Map<String, Object> buildPayload(AlertRule rule, AlertRecord record) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleCode", rule.getRuleCode());
        payload.put("ruleName", rule.getRuleName());
        payload.put("metric", rule.getMetric());
        payload.put("target", rule.getTarget());
        payload.put("level", record.getAlertLevel());
        payload.put("status", record.getStatus());
        payload.put("content", record.getContent());
        payload.put("tenantId", TenantContext.get());
        payload.put("triggerTime",
                record.getTriggerTime() == null ? null : record.getTriggerTime().toString());
        return payload;
    }

    /**
     * 一次通知的最终结果。
     *
     * @param result  结果：SUCCESS / FAILED / SKIPPED
     * @param retries 已重试次数
     * @param error   失败原因
     */
    private record Attempt(String result, int retries, String error) {
    }

    /**
     * 通知失败异常，携带是否可重试标记。
     *
     * @author BlueWhale
     */
    private static class NotifyFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /** 是否可重试 */
        private final boolean retryable;

        /**
         * 构造失败异常
         *
         * @param message   失败描述
         * @param retryable 是否可重试
         */
        NotifyFailure(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        /**
         * 构造失败异常
         *
         * @param message   失败描述
         * @param retryable 是否可重试
         * @param cause     原始异常
         */
        NotifyFailure(String message, boolean retryable, Throwable cause) {
            super(message, cause);
            this.retryable = retryable;
        }

        /**
         * 是否可重试
         *
         * @return 可重试返回 {@code true}
         */
        boolean isRetryable() {
            return retryable;
        }
    }
}
