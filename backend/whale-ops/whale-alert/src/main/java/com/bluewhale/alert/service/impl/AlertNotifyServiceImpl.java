package com.bluewhale.alert.service.impl;

import com.bluewhale.alert.config.AlertNotifyProperties;
import com.bluewhale.alert.entity.AlertRecord;
import com.bluewhale.alert.entity.AlertRule;
import com.bluewhale.alert.service.AlertNotifyService;
import com.bluewhale.common.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 告警通知服务实现：邮件、Webhook 与短信三种通道。
 *
 * <p>通道取值来自告警规则 {@code channel}：{@code EMAIL} / {@code SMS} / {@code WEBHOOK} /
 * {@code NONE}。短信经部署侧 HTTP 网关发送，平台不绑定具体供应商。任一通道失败均只记日志，
 * 不抛出异常。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertNotifyServiceImpl implements AlertNotifyService {

    /** 邮件通道 */
    private static final String CHANNEL_EMAIL = "EMAIL";

    /** Webhook 通道 */
    private static final String CHANNEL_WEBHOOK = "WEBHOOK";

    /** 短信通道 */
    private static final String CHANNEL_SMS = "SMS";

    /** 不通知 */
    private static final String CHANNEL_NONE = "NONE";

    /** 单条短信内容上限（字符），超长截断避免被网关拒绝或拆成多条计费 */
    private static final int MAX_SMS_CONTENT_LENGTH = 480;

    private final AlertNotifyProperties properties;

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    private final ObjectMapper objectMapper;

    @Override
    public void notify(AlertRule rule, AlertRecord record) {
        if (rule == null || record == null) {
            return;
        }
        String channel = rule.getChannel() == null ? CHANNEL_NONE : rule.getChannel().toUpperCase();
        if (CHANNEL_EMAIL.equals(channel)) {
            sendEmail(rule, record);
        } else if (CHANNEL_WEBHOOK.equals(channel)) {
            sendWebhook(rule, record);
        } else if (CHANNEL_SMS.equals(channel)) {
            sendSms(rule, record);
        } else if (!CHANNEL_NONE.equals(channel)) {
            log.warn("告警通道暂未实现，跳过通知: channel={}, ruleCode={}", channel, rule.getRuleCode());
        }
    }

    /**
     * 发送邮件通知。
     *
     * @param rule   告警规则
     * @param record 告警记录
     */
    private void sendEmail(AlertRule rule, AlertRecord record) {
        AlertNotifyProperties.Email email = properties.getEmail();
        if (!email.isEnabled()) {
            log.debug("邮件通道未启用，跳过通知: ruleCode={}", rule.getRuleCode());
            return;
        }
        List<String> recipients = email.getTo() == null ? List.of() : email.getTo().stream()
                .filter(StringUtils::hasText)
                .toList();
        if (recipients.isEmpty() || !StringUtils.hasText(email.getFrom())) {
            log.warn("邮件通道缺少发件人或收件人，跳过通知: ruleCode={}", rule.getRuleCode());
            return;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("未配置 spring.mail.host，JavaMailSender 不可用，跳过邮件通知: ruleCode={}",
                    rule.getRuleCode());
            return;
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
        } catch (Exception e) {
            log.error("告警邮件发送失败: ruleCode={}, reason={}", rule.getRuleCode(), e.getMessage(), e);
        }
    }

    /**
     * 发送 Webhook 通知（JSON POST）。
     *
     * @param rule   告警规则
     * @param record 告警记录
     */
    private void sendWebhook(AlertRule rule, AlertRecord record) {
        AlertNotifyProperties.Webhook webhook = properties.getWebhook();
        if (!webhook.isEnabled() || !StringUtils.hasText(webhook.getUrl())) {
            log.debug("Webhook 通道未启用或未配置地址，跳过通知: ruleCode={}", rule.getRuleCode());
            return;
        }
        int timeout = webhook.getTimeoutMillis() <= 0 ? 3000 : webhook.getTimeoutMillis();
        try {
            String body = objectMapper.writeValueAsString(buildPayload(rule, record));
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhook.getUrl()))
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeout))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("告警 Webhook 已送达: ruleCode={}, status={}",
                        rule.getRuleCode(), response.statusCode());
            } else {
                log.error("告警 Webhook 返回非 2xx: ruleCode={}, status={}, body={}",
                        rule.getRuleCode(), response.statusCode(), response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("告警 Webhook 发送被中断: ruleCode={}", rule.getRuleCode(), e);
        } catch (Exception e) {
            log.error("告警 Webhook 发送失败: ruleCode={}, reason={}", rule.getRuleCode(), e.getMessage(), e);
        }
    }

    /**
     * 发送短信通知（F-03b：统一 HTTP 网关协议）。
     *
     * <p>网关协议为 JSON POST：{@code phones/templateCode/signName/content} 加告警元数据；
     * 配置 {@code token} 时携带 {@code Authorization: Bearer}。供应商专有签名应由网关完成，
     * 平台不持有短信供应商 AccessKey。失败只记日志，不影响告警落库。</p>
     *
     * @param rule   告警规则
     * @param record 告警记录
     */
    private void sendSms(AlertRule rule, AlertRecord record) {
        AlertNotifyProperties.Sms sms = properties.getSms();
        if (!sms.isEnabled()) {
            log.debug("短信通道未启用，跳过通知: ruleCode={}", rule.getRuleCode());
            return;
        }
        List<String> recipients = sms.getTo() == null ? List.of() : sms.getTo().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        if (recipients.isEmpty() || !StringUtils.hasText(sms.getUrl())) {
            log.warn("短信通道缺少网关地址或接收手机号，跳过通知: ruleCode={}", rule.getRuleCode());
            return;
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
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeout))
                    .build()
                    .send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("告警短信已提交网关: ruleCode={}, to={}",
                        rule.getRuleCode(), maskPhones(recipients));
            } else {
                log.error("告警短信网关返回非 2xx: ruleCode={}, status={}, body={}",
                        rule.getRuleCode(), response.statusCode(), truncate(response.body(), 256));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("告警短信发送被中断: ruleCode={}", rule.getRuleCode(), e);
        } catch (Exception e) {
            log.error("告警短信发送失败: ruleCode={}, reason={}", rule.getRuleCode(), e.getMessage(), e);
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
        if (content.length() <= MAX_SMS_CONTENT_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_SMS_CONTENT_LENGTH - 3) + "...";
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
}
