package com.bluewhale.message.channel.impl;

import com.bluewhale.common.internaltoken.InternalTokenProperties;
import com.bluewhale.message.channel.ChannelMessage;
import com.bluewhale.message.channel.ChannelResult;
import com.bluewhale.message.channel.MessageChannelSender;
import com.bluewhale.message.config.MessageChannelProperties;
import com.bluewhale.message.enums.MessageChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
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
 * <p>回执绑定：投递时携带幂等键 {@code clientMsgId = msg-<消息ID>-<用户ID>}，
 * whale-im 的送达/已读回执按该字段回传；消息服务把它作为 {@code providerMessageId} 落库，
 * 上报回执时填入即可精确匹配。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImChannelSender implements MessageChannelSender {

    private final MessageChannelProperties properties;

    private final ObjectMapper objectMapper;

    private final InternalTokenProperties internalTokenProperties;

    /** 复用的 HTTP 客户端：HttpClient 重量级且线程安全，避免每条通知都重建连接池 */
    private HttpClient httpClient;

    /**
     * 初始化共享 HTTP 客户端（连接超时沿用 IM 渠道超时配置）。
     */
    @PostConstruct
    void initHttpClient() {
        int timeout = properties.getIm().getTimeoutMillis() <= 0 ? 3000 : properties.getIm().getTimeoutMillis();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .build();
    }

    @Override
    public MessageChannel channel() {
        return MessageChannel.IM;
    }

    /** whale-im 回传送达/退信回执 */
    @Override
    public boolean supportsReceipt() {
        return true;
    }

    /** whale-im 回传已读回执 */
    @Override
    public boolean supportsReadReceipt() {
        return true;
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
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (internalTokenProperties.isEnabled()) {
                String token = internalTokenProperties.resolveToken();
                if (!token.isBlank()) {
                    requestBuilder.header(internalTokenProperties.getHeader(), token);
                }
            }
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String providerMessageId = clientMsgId(message);
                log.info("消息已投递 IM: messageId={}, userId={}, clientMsgId={}", message.getMessageId(),
                        message.getRecipient().getUserId(), providerMessageId);
                return ChannelResult.sent(providerMessageId);
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
        payload.put("clientMsgId", clientMsgId(message));
        // 回执上报需显式租户：IM 侧据此调用消息服务回执接口
        payload.put("tenantId", message.getTenantId());
        return payload;
    }

    /**
     * 构造投递幂等键，同时作为回执匹配的供应商消息标识。
     *
     * <p>形如 {@code msg-<消息ID>-<用户ID>}，确定性生成，重试后仍可被同名回执匹配。</p>
     *
     * @param message 渠道消息
     * @return 幂等键
     */
    private static String clientMsgId(ChannelMessage message) {
        Long messageId = message.getMessageId() == null ? 0L : message.getMessageId();
        Long userId = message.getRecipient() == null || message.getRecipient().getUserId() == null
                ? 0L : message.getRecipient().getUserId();
        return "msg-" + messageId + "-" + userId;
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
