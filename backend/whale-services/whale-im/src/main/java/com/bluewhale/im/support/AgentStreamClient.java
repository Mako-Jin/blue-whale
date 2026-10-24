package com.bluewhale.im.support;

import com.bluewhale.common.web.WebHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 智能体流式调用客户端：以 SSE 消费 {@code POST /agent/chat} 并逐事件回调。
 *
 * <p>复用调用方的 {@code X-User-Id} / {@code X-Permission-Codes}，不做服务身份提权，
 * 因此智能体侧的权限校验结果与用户在网关下一致。请求超时 {@code whale.im.agent.timeout-seconds}
 * 作用于建立响应（首字节），流式中途断开会以 {@link IOException} 抛给调用方做降级。</p>
 *
 * @author BlueWhale
 */
@Component
public class AgentStreamClient {

    /** 事件回调：允许抛受检异常以便由调用方统一降级 */
    @FunctionalInterface
    public interface EventHandler {

        /**
         * 处理单个 SSE 事件。
         *
         * @param event 事件名
         * @param data  事件数据（JSON 文本）
         * @throws IOException 解析或终止流
         */
        void onEvent(String event, String data) throws IOException;
    }

    private static final String CHAT_PATH = "/agent/chat";

    private static final String CONTENT_TYPE = "Content-Type";

    private static final String ACCEPT = "Accept";

    private static final String APPLICATION_JSON = "application/json";

    private static final String TEXT_EVENT_STREAM = "text/event-stream";

    private final HttpClient httpClient;

    private final String baseUrl;

    private final long timeoutSeconds;

    /**
     * 构造客户端。
     *
     * @param baseUrl        智能体服务地址（whale-agent）
     * @param timeoutSeconds 建连与首字节超时（秒）
     */
    public AgentStreamClient(@Value("${whale.im.agent.base-url:http://127.0.0.1:8090}") String baseUrl,
                             @Value("${whale.im.agent.timeout-seconds:120}") long timeoutSeconds) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    /**
     * 发起流式提问并逐事件回调。
     *
     * @param body            请求体 JSON
     * @param userId          当前用户ID（透传 {@code X-User-Id}）
     * @param permissionCodes 当前用户权限码（透传 {@code X-Permission-Codes}，可空）
     * @param handler         事件回调
     * @throws IOException          HTTP 非 2xx、网络中断或回调中止
     * @throws InterruptedException 线程被中断
     */
    public void stream(String body, Long userId, String permissionCodes, EventHandler handler)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + CHAT_PATH))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .header(ACCEPT, TEXT_EVENT_STREAM);
        if (userId != null) {
            builder.header(WebHeaders.USER_ID, String.valueOf(userId));
        }
        if (permissionCodes != null && !permissionCodes.isBlank()) {
            builder.header(WebHeaders.PERMISSION_CODES, permissionCodes);
        }
        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("智能体服务返回状态 " + response.statusCode());
        }
        SseEventParser parser = new SseEventParser();
        try (Stream<String> lines = response.body()) {
            lines.forEach(line -> dispatch(parser.accept(line), handler));
        }
        dispatch(parser.flush(), handler);
    }

    /**
     * 分发解析出的完整事件。
     *
     * @param event   解析结果
     * @param handler 事件回调
     */
    private static void dispatch(Optional<SseEventParser.SseEvent> event, EventHandler handler) {
        if (event.isEmpty()) {
            return;
        }
        SseEventParser.SseEvent sseEvent = event.get();
        try {
            handler.onEvent(sseEvent.name(), sseEvent.data());
        } catch (IOException e) {
            throw new StreamAbortException(e);
        }
    }

    /**
     * 去掉末尾斜杠，避免路径拼接出现双斜杠。
     *
     * @param url 原始地址
     * @return 规整后的地址
     */
    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * 回调中断包装：把受检异常从 {@code Stream#forEach} 中带出。
     */
    private static final class StreamAbortException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /**
         * 包装原始异常。
         *
         * @param cause 原始异常
         */
        private StreamAbortException(IOException cause) {
            super(cause);
        }
    }
}
