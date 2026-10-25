package com.bluewhale.alert.service.impl;

import com.bluewhale.alert.config.AlertNotifyProperties;
import com.bluewhale.alert.entity.AlertRecord;
import com.bluewhale.alert.entity.AlertRule;
import com.bluewhale.alert.service.AlertNotifyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警通知服务实现：邮件与 Webhook 两种通道。
 *
 * <p>通道取值来自告警规则 {@code channel}：{@code EMAIL} / {@code WEBHOOK} / {@code NONE}
 * （{@code SMS} 暂未接入，记录告警日志后跳过）。任一通道失败均只记日志，不抛出异常。</p>
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

    /** 不通知 */
    private static final String CHANNEL_NONE = "NONE";

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
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(email.getFrom());
            message.setTo(recipients.toArray(String[]::new));
            message.setSubject(buildSubject(rule));
            message.setText(buildText(rule, record));
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
     * 构造邮件主题。
     *
     * @param rule 告警规则
     * @return 邮件主题
     */
    private static String buildSubject(AlertRule rule) {
        return "[BlueWhale][" + rule.getLevel() + "] " + rule.getRuleName();
    }

    /**
     * 构造邮件正文。
     *
     * @param rule   告警规则
     * @param record 告警记录
     * @return 邮件正文
     */
    private static String buildText(AlertRule rule, AlertRecord record) {
        return "规则编码: " + rule.getRuleCode()
                + "\n规则名称: " + rule.getRuleName()
                + "\n告警级别: " + record.getAlertLevel()
                + "\n监控目标: " + (rule.getTarget() == null ? "-" : rule.getTarget())
                + "\n告警内容: " + record.getContent()
                + "\n触发时间: " + record.getTriggerTime();
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
        payload.put("triggerTime",
                record.getTriggerTime() == null ? null : record.getTriggerTime().toString());
        return payload;
    }
}
