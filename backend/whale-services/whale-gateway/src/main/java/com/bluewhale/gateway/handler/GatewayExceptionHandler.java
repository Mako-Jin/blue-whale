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
 * 404/路由不存在（含网关 NotFoundException 与 503）→ {"code":404,"message":"服务不可用"}；
 * 其余异常 → {"code":500,"message":"系统异常"}。
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

        int code;
        String message;
        if (isServiceUnavailable(ex)) {
            code = CODE_NOT_FOUND;
            message = MSG_NOT_FOUND;
            log.warn("路由不可达: path={}, reason={}", exchange.getRequest().getURI().getPath(), ex.getMessage());
        } else {
            code = CODE_SERVER_ERROR;
            message = MSG_SERVER_ERROR;
            log.error("网关系统异常: path={}", exchange.getRequest().getURI().getPath(), ex);
        }

        response.setStatusCode(HttpStatus.valueOf(code));
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
     * 判断异常是否代表下游服务不可用（路由不存在或 503）
     *
     * @param ex 捕获的异常
     * @return 属于服务不可用返回 {@code true}
     */
    private boolean isServiceUnavailable(Throwable ex) {
        if (ex instanceof NotFoundException) {
            return true;
        }
        if (ex instanceof ResponseStatusException rse) {
            int status = rse.getStatusCode().value();
            return status == 404 || status == 503;
        }
        return false;
    }
}
