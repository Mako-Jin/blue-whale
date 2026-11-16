package com.bluewhale.gateway.common;

import com.bluewhale.security.contract.GatewayContract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 网关统一响应写出器。
 *
 * <p>集中处理鉴权、限流、熔断、内网屏蔽、WS ticket 与全局异常兜底六类短路响应：
 * 设置 HTTP 状态码、{@code application/json;charset=UTF-8}、透传入站 {@code X-Trace-Id}，
 * 并写出 {@link GatewayResponse} 同结构 JSON。此前六处各自复制同一段写出逻辑，
 * 漏改一处即出现「某个状态码没有 traceId」等排障断层。</p>
 *
 * <p><b>已提交保护</b>：响应已提交时不再二次写出（仅返回完成信号），避免
 * {@code IllegalStateException} 覆盖真实错误。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public final class GatewayResponseWriter {

    /** JSON 响应内容类型 */
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private GatewayResponseWriter() {
    }

    /**
     * 写出短路响应：透传入站 traceId，缺失时不生成（由链路追踪过滤器保证正常情况下存在）。
     *
     * @param exchange 当前请求上下文
     * @param status   HTTP 状态码
     * @param code     业务状态码
     * @param message  提示信息（服务端固定文案）
     * @return 响应写出完成的信号
     */
    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        return write(exchange, status, code, message, false);
    }

    /**
     * 写出短路响应，可选在入站 traceId 缺失时生成一个。
     *
     * <p>异常兜底处理器不经过 {@code TraceIdGlobalFilter} 的请求改写时，需要自行补齐 traceId；
     * 其余过滤器运行在 TraceId 之后，只需透传，不应覆盖。</p>
     *
     * @param exchange              当前请求上下文
     * @param status                HTTP 状态码
     * @param code                  业务状态码
     * @param message               提示信息（服务端固定文案）
     * @param generateTraceIdIfMissing 入站 traceId 缺失时是否生成
     * @return 响应写出完成的信号
     */
    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, int code, String message,
                                   boolean generateTraceIdIfMissing) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            log.warn("响应已提交，跳过网关统一响应写出: status={}", status.value());
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.parseMediaType(JSON_CONTENT_TYPE));
        applyTraceId(exchange, response, generateTraceIdIfMissing);
        String body = GatewayResponse.of(code, message).toJson();
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 透传入站 traceId（必要时生成），与 {@code TraceIdGlobalFilter} 使用同一请求头契约。
     *
     * @param exchange    当前请求上下文
     * @param response    当前响应
     * @param generateIfMissing 缺失时是否生成
     */
    private static void applyTraceId(ServerWebExchange exchange, ServerHttpResponse response,
                                     boolean generateIfMissing) {
        String traceId = exchange.getRequest().getHeaders().getFirst(GatewayContract.Headers.TRACE_ID);
        if ((traceId == null || traceId.isBlank()) && generateIfMissing) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        if (traceId != null && !traceId.isBlank()) {
            response.getHeaders().set(GatewayContract.Headers.TRACE_ID, traceId);
        }
    }
}
