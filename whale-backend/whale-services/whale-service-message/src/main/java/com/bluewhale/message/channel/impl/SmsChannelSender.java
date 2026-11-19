package com.bluewhale.message.channel.impl;

import com.bluewhale.message.channel.ChannelMessage;
import com.bluewhale.message.channel.ChannelResult;
import com.bluewhale.message.channel.MessageChannelSender;
import com.bluewhale.message.config.MessageChannelProperties;
import com.bluewhale.message.enums.MessageChannel;
import com.bluewhale.message.support.MaskUtils;
import com.fasterxml.jackson.databind.JsonNode;
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
 * 短信渠道适配器：调用部署侧统一 HTTP 网关，平台不持有短信供应商 AccessKey。
 *
 * <p>回执绑定：网关同步响应为 JSON 时，按 {@code successCodeField}/{@code successCode}
 * 判定业务成败，并按 {@code providerIdField}（默认 {@code data.messageId}）解析供应商消息标识，
 * 作为 {@code providerMessageId} 落库；异步回执携带同一标识，上报时填入即可精确匹配。
 * 非 JSON 响应保持原有 2xx 即成功的语义，标识为空（回执将落 UNMATCHED 供对账）。</p>
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

    /** 复用的 HTTP 客户端：HttpClient 重量级且线程安全，避免每条短信都重建连接池 */
    private HttpClient httpClient;

    /**
     * 初始化共享 HTTP 客户端（连接超时沿用短信渠道超时配置）。
     */
    @PostConstruct
    void initHttpClient() {
        int timeout = properties.getSms().getTimeoutMillis() <= 0 ? 3000 : properties.getSms().getTimeoutMillis();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .build();
    }

    @Override
    public MessageChannel channel() {
        return MessageChannel.SMS;
    }

    /** 短信网关回传送达/退信回执 */
    @Override
    public boolean supportsReceipt() {
        return true;
    }

    /** 短信不提供已读回执，上报 READ 按脏数据拒绝 */
    @Override
    public boolean supportsReadReceipt() {
        return false;
    }

    @Override
    public int maxContentLength() {
        return MAX_SMS_CONTENT_LENGTH;
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
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String providerMessageId = resolveProviderMessageId(sms, response.body());
                log.info("消息短信已提交网关: messageId={}, to={}, providerMessageId={}", message.getMessageId(),
                        MaskUtils.maskPhone(message.getRecipient().getPhone()), providerMessageId);
                return ChannelResult.sent(providerMessageId);
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
     * 解析网关响应中的供应商消息标识。
     *
     * <p>响应为 JSON 对象且配置了业务码字段时先校验业务码，失败抛 {@link IllegalStateException}
     * 由调用方按失败处理；随后按 {@code providerIdField} 取标识，缺失时返回 {@code null}。</p>
     *
     * @param sms  短信配置
     * @param body 响应体
     * @return 供应商消息标识，可能为 {@code null}
     */
    String resolveProviderMessageId(MessageChannelProperties.Sms sms, String body) {
        JsonNode root = parseJson(body);
        if (root == null) {
            return null;
        }
        JsonNode code = readPath(root, sms.getSuccessCodeField());
        if (code != null && code.isNumber() && code.asInt() != sms.getSuccessCode()) {
            throw new IllegalStateException("短信网关业务失败: code=" + code.asInt()
                    + ", body=" + truncate(body, 200));
        }
        JsonNode providerId = readPath(root, sms.getProviderIdField());
        return providerId == null || providerId.asText().isBlank() ? null : providerId.asText();
    }

    /**
     * 解析 JSON 对象响应；非 JSON 或解析失败返回 {@code null}（保持 2xx 即成功）。
     *
     * @param body 响应体
     * @return JSON 对象节点，不可解析时为 {@code null}
     */
    private JsonNode parseJson(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按点号路径读取 JSON 节点。
     *
     * @param root 根节点
     * @param path 点号路径（如 {@code data.messageId}）
     * @return 目标节点；路径为空或不存在时返回 {@code null}
     */
    private static JsonNode readPath(JsonNode root, String path) {
        if (root == null || !StringUtils.hasText(path)) {
            return null;
        }
        JsonNode node = root;
        for (String segment : path.trim().split("\\.")) {
            if (!StringUtils.hasText(segment)) {
                return null;
            }
            node = node.path(segment);
            if (node.isMissingNode() || node.isNull()) {
                return null;
            }
        }
        return node;
    }

    /**
     * 构造短信网关载荷。
     *
     * @param sms     短信配置
     * @param message 渠道消息
     * @return JSON 载荷
     */
    private Map<String, Object> buildPayload(MessageChannelProperties.Sms sms, ChannelMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("phones", List.of(message.getRecipient().getPhone().trim()));
        payload.put("templateCode", sms.getTemplateCode());
        payload.put("signName", sms.getSignName());
        payload.put("content", truncate(message.getContent(), maxContentLength()));
        payload.put("bizType", message.getBizType());
        payload.put("bizId", message.getBizId());
        payload.put("tenantId", message.getTenantId());
        return payload;
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
