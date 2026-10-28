package com.bluewhale.message.channel.impl;

import com.bluewhale.message.channel.ChannelMessage;
import com.bluewhale.message.channel.ChannelResult;
import com.bluewhale.message.channel.MessageChannelSender;
import com.bluewhale.message.config.MessageChannelProperties;
import com.bluewhale.message.enums.MessageChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
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
 * 短信渠道适配器：调用部署侧统一 HTTP 网关，平台不持有短信供应商 AccessKey。
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsChannelSender implements MessageChannelSender {

    /** 单条短信内容上限（字符） */
    private static final int MAX_SMS_CONTENT_LENGTH = 480;

    private final MessageChannelProperties properties;

    private final ObjectMapper objectMapper;

    @Override
    public MessageChannel channel() {
        return MessageChannel.SMS;
    }

    @Override
    public ChannelResult send(ChannelMessage message) {
        MessageChannelProperties.Sms sms = properties.getSms();
        if (properties.isMock()) {
            return ChannelResult.mocked();
        }
        if (!sms.isEnabled()) {
            return ChannelResult.skipped("短信渠道未启用");
        }
        if (!StringUtils.hasText(sms.getUrl())) {
            return ChannelResult.skipped("未配置短信网关地址");
        }
        if (message.getRecipient() == null || !StringUtils.hasText(message.getRecipient().getPhone())) {
            return ChannelResult.skipped("缺少接收手机号");
        }
        int timeout = sms.getTimeoutMillis() <= 0 ? 3000 : sms.getTimeoutMillis();
        try {
            String body = objectMapper.writeValueAsString(buildPayload(sms, message));
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
                log.info("消息短信已提交网关: messageId={}, to={}", message.getMessageId(),
                        maskPhone(message.getRecipient().getPhone()));
                return ChannelResult.sent(null);
            }
            return ChannelResult.failed("短信网关返回 " + response.statusCode() + ": "
                    + truncate(response.body(), 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ChannelResult.failed("短信发送被中断");
        } catch (Exception e) {
            log.error("消息短信发送失败: messageId={}, reason={}", message.getMessageId(), e.getMessage(), e);
            return ChannelResult.failed("短信发送失败: " + e.getMessage());
        }
    }

    /**
     * 构造短信网关载荷。
     *
     * @param sms     短信配置
     * @param message 渠道消息
     * @return JSON 载荷
     */
    private static Map<String, Object> buildPayload(MessageChannelProperties.Sms sms, ChannelMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("phones", List.of(message.getRecipient().getPhone().trim()));
        payload.put("templateCode", sms.getTemplateCode());
        payload.put("signName", sms.getSignName());
        payload.put("content", truncate(message.getContent(), MAX_SMS_CONTENT_LENGTH));
        payload.put("bizType", message.getBizType());
        payload.put("bizId", message.getBizId());
        payload.put("tenantId", message.getTenantId());
        return payload;
    }

    /**
     * 手机号脱敏。
     *
     * @param phone 手机号
     * @return 脱敏值
     */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 截断文本。
     *
     * @param value 原值
     * @param max   最大长度
     * @return 截断值
     */
    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
