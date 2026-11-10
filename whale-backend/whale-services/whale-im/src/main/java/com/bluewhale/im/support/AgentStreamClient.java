package com.bluewhale.im.support;

import com.bluewhale.common.web.WebHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * 智能体流式调用客户端：以 SSE 消费 {@code POST /agent/chat} 并逐事件回调。
 *
 * <p>复用调用方的 {@code X-User-Id} / {@code X-Permission-Codes}，不做服务身份提权，
 * 因此智能体侧的权限校验结果与用户在网关下一致。超时参数 {@code whale.im.agent.timeout-seconds}
 * 同时作用于建连、首字节与**流式读取的空闲间隔**：任意一行超过该时长未到达即按超时中断，
 * 避免上游挂起导致机器人回答线程被永久占用。</p>
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

    /** 写操作确认/拒绝路径前缀 */
    private static final String OPS_PATH_PREFIX = "/agent/ops/";

    /** 确认动作 */
    private static final String ACTION_CONFIRM = "confirm";

    /** 拒绝动作 */
    private static final String ACTION_REJECT = "reject";

    private static final String CONTENT_TYPE = "Content-Type";

    private static final String ACCEPT = "Accept";

    private static final String APPLICATION_JSON = "application/json";

    private static final String TEXT_EVENT_STREAM = "text/event-stream";

    /** 秒 → 毫秒换算 */
    private static final long MILLIS_PER_SECOND = 1000L;

    private final HttpClient httpClient;

    private final String baseUrl;

    private final long timeoutSeconds;

    private final ObjectMapper objectMapper;

    /**
     * 构造客户端。
     *
     * @param baseUrl        智能体服务地址（whale-agent）
     * @param timeoutSeconds 建连与首字节超时（秒）
     * @param objectMapper   JSON 解析器
     */
    public AgentStreamClient(@Value("${whale.im.agent.base-url:http://127.0.0.1:8090}") String baseUrl,
                             @Value("${whale.im.agent.timeout-seconds:120}") long timeoutSeconds,
                             ObjectMapper objectMapper) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.timeoutSeconds = timeoutSeconds;
        this.objectMapper = objectMapper;
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
            closeQuietly(response.body());
            throw new IOException("智能体服务返回状态 " + response.statusCode());
        }
        SseEventParser parser = new SseEventParser();
        // 逐行读取改为「后台读取线程 + 空闲超时」模式：HttpClient 仅对首字节生效，
        // 无法约束流式读取中途的挂起，必须在上游沉默超时时主动失败并关闭流。
        try (LinePump pump = new LinePump(response.body(), timeoutSeconds * MILLIS_PER_SECOND)) {
            Optional<String> line = pump.next();
            while (line.isPresent()) {
                dispatch(parser.accept(line.get()), handler);
                line = pump.next();
            }
        }
        dispatch(parser.flush(), handler);
    }

    /**
     * 确认或拒绝一次平台写操作（一次性令牌）。
     *
     * @param confirmationId  确认令牌ID
     * @param approved        {@code true} 执行；{@code false} 拒绝
     * @param userId          当前用户ID（必须与令牌发起人一致）
     * @param permissionCodes 当前用户权限码
     * @return 智能体返回的 {@code Result.data} 节点
     * @throws IOException          HTTP 非 2xx、业务码非 0 或响应解析失败
     * @throws InterruptedException 线程被中断
     */
    public JsonNode confirmOperation(String confirmationId, boolean approved, Long userId, String permissionCodes)
            throws IOException, InterruptedException {
        String action = approved ? ACTION_CONFIRM : ACTION_REJECT;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + OPS_PATH_PREFIX + confirmationId + "/" + action))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString("{}"));
        if (userId != null) {
            builder.header(WebHeaders.USER_ID, String.valueOf(userId));
        }
        if (permissionCodes != null && !permissionCodes.isBlank()) {
            builder.header(WebHeaders.PERMISSION_CODES, permissionCodes);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("平台操作接口返回状态 " + response.statusCode());
        }
        JsonNode body;
        try {
            body = objectMapper.readTree(response.body());
        } catch (RuntimeException e) {
            throw new IOException("平台操作响应解析失败", e);
        }
        if (body == null || body.path("code").asInt(-1) != 0) {
            String message = body == null ? "平台操作响应为空" : body.path("message").asText("平台操作确认失败");
            throw new IOException(message);
        }
        return body.path("data");
    }

    /**
     * 分发解析出的完整事件。
     *
     * @param event   解析结果
     * @param handler 事件回调
     * @throws IOException 回调主动中止流
     */
    private static void dispatch(Optional<SseEventParser.SseEvent> event, EventHandler handler)
            throws IOException {
        if (event.isEmpty()) {
            return;
        }
        SseEventParser.SseEvent sseEvent = event.get();
        handler.onEvent(sseEvent.name(), sseEvent.data());
    }

    /**
     * 静默关闭响应流（仅释放连接，不影响主流程异常）。
     *
     * @param stream 响应流
     */
    private static void closeQuietly(Stream<String> stream) {
        try {
            stream.close();
        } catch (RuntimeException ignored) {
            // 状态码异常优先抛出，关闭失败不覆盖原始异常
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
     * 带空闲超时的逐行读取泵。
     *
     * <p>{@link HttpClient} 的请求超时只覆盖建连与响应头，一旦响应头返回，后续
     * {@code Stream#iterator()} 的阻塞读不再受任何超时约束。本类把读取放到守护线程，
     * 通过有界队列交给调用线程；调用线程按「相邻两行之间的最大间隔」计时，
     * 超时即抛 {@link SocketTimeoutException} 并关闭底层流，防止上游静默挂起。</p>
     */
    static final class LinePump implements AutoCloseable {

        /** 流结束哨兵 */
        private static final Object END = new Object();

        /** 队列容量：足以缓冲一段突发的事件行，同时给上游施加背压 */
        private static final int QUEUE_CAPACITY = 256;

        /** 读取线程与消费线程之间的交接队列 */
        private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

        /** 读取线程异常（流结束哨兵到达后由消费线程抛出） */
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        /** 底层响应流（关闭时用于中断阻塞读） */
        private final Stream<String> source;

        /** 后台读取线程 */
        private final Thread worker;

        /** 空闲超时（毫秒） */
        private final long idleTimeoutMillis;

        /** 是否已关闭 */
        private volatile boolean closed;

        /**
         * 启动后台读取。
         *
         * @param source            响应行流
         * @param idleTimeoutMillis 相邻两行最大间隔（毫秒）
         */
        LinePump(Stream<String> source, long idleTimeoutMillis) {
            this.source = source;
            this.idleTimeoutMillis = idleTimeoutMillis;
            this.worker = new Thread(this::drain, "agent-sse-reader");
            this.worker.setDaemon(true);
            this.worker.start();
        }

        /**
         * 取下一行，超过空闲超时即抛异常。
         *
         * @return 下一行；流正常结束时返回空
         * @throws IOException          读取失败或空闲超时
         * @throws InterruptedException 等待被中断
         */
        Optional<String> next() throws IOException, InterruptedException {
            long deadline = deadlineFromNow();
            while (true) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    throw new SocketTimeoutException(
                            "智能体流式响应空闲超过 " + idleTimeoutMillis + "ms");
                }
                Object item = queue.poll(remainingNanos, TimeUnit.NANOSECONDS);
                if (item == null) {
                    continue;
                }
                if (item == END) {
                    return endOfStream();
                }
                deadline = deadlineFromNow();
                return Optional.of((String) item);
            }
        }

        /**
         * 关闭读取：置位标记并中断后台线程，同时关闭底层流解除阻塞读。
         */
        @Override
        public void close() {
            closed = true;
            worker.interrupt();
            closeQuietly(source);
        }

        /**
         * 处理流结束：有异常则包装为 {@link IOException}，否则返回空。
         *
         * @return 空（正常结束）
         * @throws IOException 读取线程异常
         */
        private Optional<String> endOfStream() throws IOException {
            Throwable cause = failure.get();
            if (cause == null) {
                return Optional.empty();
            }
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("智能体流式响应读取失败", cause);
        }

        /**
         * 计算下一次空闲截止时间。
         *
         * @return 截止时间（{@link System#nanoTime()} 口径）
         */
        private long deadlineFromNow() {
            return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(idleTimeoutMillis);
        }

        /**
         * 后台消费响应流，逐行入队，结束或异常时投递哨兵。
         */
        private void drain() {
            try (Stream<String> lines = source) {
                Iterator<String> iterator = lines.iterator();
                while (!closed && iterator.hasNext()) {
                    queue.put(iterator.next());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                failure.set(e);
            } finally {
                queue.offer(END);
            }
        }
    }
}
