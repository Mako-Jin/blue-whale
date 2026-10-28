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
 * IM 渠道适配器：调用 {@code whale-im} 内网站内通知入口 {@code POST /im/internal/notify}。
 *
 * <p>只负责“把通知送进 IM”，不拥有会话历史与已读状态。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImChannelSender implements MessageChannelSender {

    private final MessageChannelProperties properties;

    private final ObjectMapper objectMapper;

    @Override
    public MessageChannel channel() {
        return MessageChannel.IM;
    }

    @Override
    public ChannelResult send(ChannelMessage message) {
        MessageChannelProperties.Im im = properties.getIm();
        if (properties.isMock()) {
            return ChannelResult.mocked();
        }
        if (!im.isEnabled()) {
            return ChannelResult.skipped("IM 渠道未启用");
        }
        if (!StringUtils.hasText(im.getBaseUrl())) {
            return ChannelResult.skipped("未配置 whale-im 地址");
        }
        if (message.getRecipient() == null || message.getRecipient().getUserId() == null) {
            return ChannelResult.skipped("缺少接收用户ID");
        }
        int timeout = im.getTimeoutMillis() <= 0 ? 3000 : im.getTimeoutMillis();
        try {
            String url = trimTrailingSlash(im.getBaseUrl()) + "/im/internal/notify";
            String body = objectMapper.writeValueAsString(buildPayload(message));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeout))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("消息已投递 IM: messageId={}, userId={}", message.getMessageId(),
                        message.getRecipient().getUserId());
                return ChannelResult.sent(null);
            }
            return ChannelResult.failed("IM 返回 " + response.statusCode() + ": "
                    + truncate(response.body(), 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ChannelResult.failed("IM 投递被中断");
        } catch (Exception e) {
            log.error("消息 IM 投递失败: messageId={}, reason={}", message.getMessageId(), e.getMessage(), e);
            return ChannelResult.failed("IM 投递失败: " + e.getMessage());
        }
    }

    /**
     * 构造 IM 通知载荷。
     *
     * @param message 渠道消息
     * @return JSON 载荷
     */
    private static Map<String, Object> buildPayload(ChannelMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userIds", List.of(message.getRecipient().getUserId()));
        payload.put("title", message.getTitle());
        payload.put("content", message.getContent());
        payload.put("bizType", message.getBizType());
        payload.put("bizId", message.getBizId());
        return payload;
    }

    /**
     * 去除末尾斜杠。
     *
     * @param value 地址
     * @return 规范化地址
     */
    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
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
