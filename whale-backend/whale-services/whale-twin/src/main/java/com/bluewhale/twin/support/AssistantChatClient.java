package com.bluewhale.twin.support;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.twin.config.TwinProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * whale-assistant 开放 API 客户端：AI 解读问答（Wave 6.04 M1）。
 *
 * <p>调 {@code POST /assistant/openapi/v1/chat}（Wave 6.01 M2 开放 API）：
 * 经 {@link InternalTokenSupport} 自动附带 {@code X-Internal-Token}，
 * 并以 {@code X-User-Id}/{@code X-Tenant-Id} 透传最终用户与租户。</p>
 *
 * <p><b>失败策略</b>：与 {@link IotStateClient} 的旁路降级不同，解读是用户主动
 * 触发的主链路——失败须抛 {@link BizException} 让前端弹窗可见。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantChatClient {

    /** 开放问答路径 */
    private static final String CHAT_PATH = "/assistant/openapi/v1/chat";

    /** 成功业务码 */
    private static final String CODE_SUCCESS = "0";

    /** SSE 数据字段前缀 */
    private static final String DATA_PREFIX = "data:";

    /** SSE 事件字段前缀 */
    private static final String EVENT_PREFIX = "event:";

    /** 流式 HttpClient 连接超时 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final TwinProperties properties;

    /** 流式问答专用 HttpClient（RestClient 拿不到逐行流） */
    private final HttpClient streamHttpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    /**
     * 同步问答。
     *
     * @param question   用户问题（含孪生上下文）
     * @param userId     最终用户ID
     * @param tenantId   租户ID（可空，缺省 1）
     * @param channelRef 业务侧会话引用（twin:{sceneId}:{entityId|-}）
     * @return 开放问答结果（answer/tokens/elapsedMillis/degraded 等键）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(String question, Long userId, String tenantId, String channelRef) {
        TwinProperties.Assistant config = properties.getAssistant();
        if (config.getAssistantCode() == null || config.getAssistantCode().isBlank()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.assistant.not-configured",
                    "未配置 AI 解读助手（whale.twin.assistant.assistant-code）");
        }
        Map<String, Object> body = Map.of(
                "assistantCode", config.getAssistantCode(),
                "question", question,
                "channelRef", channelRef);
        try {
            Map<String, Object> response = InternalTokenSupport.restClient()
                    .post()
                    .uri(config.getBaseUrl() + CHAT_PATH)
                    .header(WebHeaders.USER_ID, String.valueOf(userId))
                    .header(WebHeaders.TENANT_ID, tenantId == null || tenantId.isBlank() ? "1" : tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Object code = response == null ? null : response.get("code");
            if (code == null || !CODE_SUCCESS.equals(String.valueOf(code))) {
                log.warn("AI 解读问答业务失败：userId={}, code={}, message={}",
                        userId, code, response == null ? null : response.get("message"));
                throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.assistant.response-abnormal",
                        "AI 解读服务响应异常，请稍后重试");
            }
            Object data = response.get("data");
            if (!(data instanceof Map)) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.assistant.empty-content",
                        "AI 解读服务返回内容为空");
            }
            return (Map<String, Object>) data;
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("AI 解读问答调用失败：userId={}, reason={}", userId, e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.assistant.unavailable",
                    "AI 解读服务不可用，请稍后重试");
        }
    }

    /**
     * 流式问答（Wave 6.05）：内网直连 assistant 开放 SSE，逐帧回调。
     *
     * <p>流式通道无法走 RestClient 拦截器，直接以 {@link InternalTokenSupport#token()}
     * 附加内网令牌（与拦截器同一配置源）。事件契约：{@code delta}/{@code done}/{@code error}。</p>
     *
     * @param body        JSON 请求体（assistantCode/question/channelRef）
     * @param userId      最终用户ID
     * @param tenantId    租户ID
     * @param consumer    事件回调（事件名, 原始 data JSON）
     * @throws IOException          HTTP 非 200 或网络失败
     * @throws InterruptedException 线程中断
     */
    public void chatStream(String body, Long userId, String tenantId, BiConsumer<String, String> consumer)
            throws IOException, InterruptedException {
        TwinProperties.Assistant config = properties.getAssistant();
        if (config.getAssistantCode() == null || config.getAssistantCode().isBlank()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.assistant.not-configured",
                    "未配置 AI 解读助手（whale.twin.assistant.assistant-code）");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.getBaseUrl() + CHAT_PATH + "/stream"))
                .timeout(Duration.ofSeconds(Math.max(10, config.getTimeoutSeconds())))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header(WebHeaders.USER_ID, String.valueOf(userId))
                .header(WebHeaders.TENANT_ID, tenantId == null || tenantId.isBlank() ? "1" : tenantId)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        String token = InternalTokenSupport.token();
        if (StringUtils.hasText(token)) {
            builder.header(InternalTokenSupport.headerName(), token);
        }
        HttpResponse<Stream<String>> response = streamHttpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() != 200) {
            throw new IOException("whale-assistant 流式接口返回 HTTP " + response.statusCode());
        }
        parseSse(response.body(), consumer);
    }

    /**
     * 解析 SSE 行流并按事件回调（与 whale-assistant 的消费端同款手工分帧）。
     *
     * @param lines    SSE 行流
     * @param consumer 事件回调
     */
    private void parseSse(Stream<String> lines, BiConsumer<String, String> consumer) {
        String event = "message";
        StringBuilder data = new StringBuilder();
        Iterator<String> iterator = lines.iterator();
        while (iterator.hasNext()) {
            String line = iterator.next();
            if (line.isEmpty()) {
                if (data.length() > 0) {
                    consumer.accept(event, data.toString());
                    event = "message";
                    data.setLength(0);
                }
            } else if (line.startsWith(EVENT_PREFIX)) {
                event = line.substring(EVENT_PREFIX.length()).trim();
            } else if (line.startsWith(DATA_PREFIX)) {
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(line.substring(DATA_PREFIX.length()).stripLeading());
            }
        }
        if (data.length() > 0) {
            consumer.accept(event, data.toString());
        }
    }
}
