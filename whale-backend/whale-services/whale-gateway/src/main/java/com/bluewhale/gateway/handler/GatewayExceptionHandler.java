package com.bluewhale.gateway.handler;

import com.bluewhale.gateway.common.GatewayResponse;
import com.bluewhale.gateway.filter.TraceIdGlobalFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 网关全局异常兜底：统一返回 Result 风格 JSON。
 *
 * <p>HTTP 状态码与业务码保持一致，避免「下游 503 被回成 HTTP 404」误导客户端重试策略：</p>
 * <ul>
 *   <li>路由不存在（{@link NotFoundException}）→ HTTP 404，业务码 404；</li>
 *   <li>下游不可用（502 / 503 / 504）→ 保持原 HTTP 状态码，业务码同 HTTP 码；</li>
 *   <li>其余异常 → HTTP 500，业务码 500。</li>
 * </ul>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@Order(-2)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final int CODE_NOT_FOUND = 404;
    private static final int CODE_SERVER_ERROR = 500;
    private static final String MSG_NOT_FOUND = "服务不可用";
    private static final String MSG_SERVER_ERROR = "系统异常";

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = resolveStatus(ex);
        int code = status.value();
        String message = code == CODE_SERVER_ERROR ? MSG_SERVER_ERROR : MSG_NOT_FOUND;
        if (code == CODE_SERVER_ERROR) {
            log.error("网关系统异常: path={}", exchange.getRequest().getURI().getPath(), ex);
        } else {
            log.warn("路由不可达: path={}, status={}, reason={}",
                    exchange.getRequest().getURI().getPath(), code, ex.getMessage());
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.parseMediaType("application/json;charset=UTF-8"));
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdGlobalFilter.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        response.getHeaders().set(TraceIdGlobalFilter.TRACE_ID_HEADER, traceId);

        String body = GatewayResponse.of(code, message).toJson();
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 将异常映射为对外的 HTTP 状态：路由不存在 → 404，下游不可用 → 保留 502/503/504，其余 → 500
     *
     * @param ex 捕获的异常
     * @return 对外 HTTP 状态
     */
    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof NotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND
                    || status == HttpStatus.BAD_GATEWAY
                    || status == HttpStatus.SERVICE_UNAVAILABLE
                    || status == HttpStatus.GATEWAY_TIMEOUT) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
