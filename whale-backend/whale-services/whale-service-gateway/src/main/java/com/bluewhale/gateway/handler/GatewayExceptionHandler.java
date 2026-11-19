package com.bluewhale.gateway.handler;

import com.bluewhale.gateway.common.GatewayMessageResolver;
import com.bluewhale.gateway.common.GatewayResponseWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关全局异常兜底：统一返回 Result 风格 JSON。
 *
 * <p>HTTP 状态码与业务码保持一致，避免「下游 503 被回成 HTTP 404」误导客户端重试策略：</p>
 * <ul>
 *   <li>路由不存在（{@link NotFoundException}）→ HTTP 404，业务码 404，文案「路由不存在」；</li>
 *   <li>下游返回 404（{@link ResponseStatusException}）→ HTTP 404，业务码 404，文案「下游资源不存在」；</li>
 *   <li>下游不可用（502 / 503 / 504）→ 保持原 HTTP 状态码，业务码同 HTTP 码，文案「服务暂时不可用」；</li>
 *   <li>其余异常 → HTTP 500，业务码 500，文案「系统异常」。</li>
 * </ul>
 *
 * <p><b>为何区分两种 404（P2-10）</b>：HTTP 码相同但排障含义相反——「路由不存在」是网关路由配置缺失，
 * 改 Nacos/兜底 YAML；「下游资源不存在」是业务接口返回 404，改下游服务。此前都返回「服务不可用」，
 * 值班时无法从响应体直接定位责任方。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@Order(-2)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    /** 路由不存在（网关未匹配到任何路由）文案键 */
    private static final String KEY_ROUTE_NOT_FOUND = "gateway.route.not-found";

    /** 下游返回 404 文案键 */
    private static final String KEY_DOWNSTREAM_NOT_FOUND = "gateway.route.downstream-not-found";

    /** 下游不可用（502/503/504）文案键 */
    private static final String KEY_UPSTREAM_UNAVAILABLE = "gateway.route.upstream-unavailable";

    /** 网关内部异常文案键 */
    private static final String KEY_SERVER_ERROR = "gateway.route.server-error";

    /** 路由不存在（网关未匹配到任何路由）文案 */
    private static final String MSG_ROUTE_NOT_FOUND = "路由不存在";

    /** 下游返回 404 文案 */
    private static final String MSG_DOWNSTREAM_NOT_FOUND = "下游资源不存在";

    /** 下游不可用（502/503/504）文案 */
    private static final String MSG_UPSTREAM_UNAVAILABLE = "服务暂时不可用";

    /** 网关内部异常文案 */
    private static final String MSG_SERVER_ERROR = "系统异常";

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = resolveStatus(ex);
        int code = status.value();
        String message = resolveMessage(exchange, ex, status);
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            log.error("网关系统异常: path={}", exchange.getRequest().getURI().getPath(), ex);
        } else {
            log.warn("路由异常: path={}, status={}, message={}, reason={}",
                    exchange.getRequest().getURI().getPath(), code, message, ex.getMessage());
        }

        return GatewayResponseWriter.write(exchange, status, code, message, true);
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

    /**
     * 按异常来源生成响应文案，区分网关侧问题与下游侧问题（P2-10），并按请求语言本地化
     *
     * @param exchange 当前请求上下文
     * @param ex       捕获的异常
     * @param status   已解析的对外 HTTP 状态
     * @return 服务端固定文案
     */
    private static String resolveMessage(ServerWebExchange exchange, Throwable ex, HttpStatus status) {
        if (ex instanceof NotFoundException) {
            return GatewayMessageResolver.resolve(exchange, KEY_ROUTE_NOT_FOUND, MSG_ROUTE_NOT_FOUND);
        }
        if (status == HttpStatus.NOT_FOUND) {
            return GatewayMessageResolver.resolve(exchange, KEY_DOWNSTREAM_NOT_FOUND, MSG_DOWNSTREAM_NOT_FOUND);
        }
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            return GatewayMessageResolver.resolve(exchange, KEY_SERVER_ERROR, MSG_SERVER_ERROR);
        }
        return GatewayMessageResolver.resolve(exchange, KEY_UPSTREAM_UNAVAILABLE, MSG_UPSTREAM_UNAVAILABLE);
    }
}
