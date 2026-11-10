package com.bluewhale.agent.support.langchain;

import com.bluewhale.agent.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * ai/whale-ai 流式客户端：调用 {@code POST /v1/agent/stream} 并逐帧回调。
 *
 * <p>只透传提问者的 {@code X-User-Id} / {@code X-Permission-Codes}，不携带密钥；
 * 非 200 响应直接抛 {@link IOException}，由上层统一降级。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LangchainStreamClient {

    /** SSE 数据字段前缀 */
    private static final String DATA_PREFIX = "data:";

    /** SSE 事件字段前缀 */
    private static final String EVENT_PREFIX = "event:";

    /** 连接超时（秒） */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final AgentProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    /**
     * 发起流式问答。
     *
     * @param body            JSON 请求体（conversationId/question/scope）
     * @param userId          提问者ID
     * @param permissionCodes 网关透传的权限码（逗号分隔）
     * @param consumer        事件回调（事件名, 原始 data）
     * @throws IOException          HTTP 非 200 或网络失败
     * @throws InterruptedException 线程中断
     */
    public void stream(String body, Long userId, String permissionCodes,
                       BiConsumer<String, String> consumer) throws IOException, InterruptedException {
        AgentProperties.Langchain config = properties.getQa().getLangchain();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.getBaseUrl() + "/v1/agent/stream"))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (userId != null) {
            builder.header("X-User-Id", String.valueOf(userId));
        }
        if (StringUtils.hasText(permissionCodes)) {
            builder.header("X-Permission-Codes", permissionCodes);
        }
        HttpResponse<Stream<String>> response = httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() != 200) {
            throw new IOException("whale-ai 返回 HTTP " + response.statusCode());
        }
        parse(response.body(), consumer);
    }

    /**
     * 解析 SSE 流并按事件回调。
     *
     * @param lines    SSE 行流
     * @param consumer 事件回调
     */
    private void parse(Stream<String> lines, BiConsumer<String, String> consumer) {
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
        log.debug("whale-ai 流式回答结束");
    }
}
