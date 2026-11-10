package com.bluewhale.doc.ws;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.doc.application.service.DocCollabService;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.support.collab.DocWsTicket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * 文档协作 WS 握手拦截（M2）。
 *
 * <p>在协议升级前原子消费一次性 ticket，并把租户、用户、页面与客户端ID写入会话属性；
 * ticket 缺失、过期、已消费或 Redis 不可用时拒绝升级，前端据此回退 M1 自动保存。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocWsHandshakeInterceptor implements HandshakeInterceptor {

    /** 客户端ID合法格式 */
    private static final String CLIENT_ID_PATTERN = "[A-Za-z0-9_-]{8,64}";

    private final DocCollabService collabService;

    private final DocProperties docProperties;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!originAllowed(request)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        String ticket = queryParam(request, "ticket");
        try {
            DocWsTicket value = collabService.consumeTicket(ticket);
            attributes.put(DocCollabAttributes.TICKET, value);
            attributes.put(DocCollabAttributes.CLIENT_ID, resolveClientId(request));
            return true;
        } catch (BizException ex) {
            log.warn("协作 WS 握手拒绝: {}", ex.getMessage());
            response.setStatusCode(ex.getCode() == DocErrorCode.COLLAB_UNAVAILABLE
                    ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNAUTHORIZED);
            return false;
        } catch (RuntimeException ex) {
            log.warn("协作 WS 握手异常: {}", ex.getMessage());
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手完成后无需处理
    }

    /**
     * 校验浏览器 Origin 是否在允许列表。
     *
     * @param request 握手请求
     * @return 允许返回 {@code true}
     */
    private boolean originAllowed(ServerHttpRequest request) {
        String configured = docProperties.getCollabAllowedOrigins();
        if (configured == null || configured.isBlank() || "*".equals(configured.trim())) {
            return true;
        }
        String origin = request.getHeaders().getOrigin();
        if (origin == null || origin.isBlank()) {
            return true;
        }
        for (String allowed : configured.split(",")) {
            if (allowed.trim().equalsIgnoreCase(origin)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 读取客户端ID，非法时回退为空（由 handler 生成 UUID）。
     *
     * @param request 握手请求
     * @return 客户端ID或 {@code null}
     */
    private String resolveClientId(ServerHttpRequest request) {
        String clientId = queryParam(request, "clientId");
        if (clientId != null && clientId.matches(CLIENT_ID_PATTERN)) {
            return clientId;
        }
        return null;
    }

    /**
     * 读取查询参数。
     *
     * @param request 握手请求
     * @param name    参数名
     * @return 参数值；缺失返回 {@code null}
     */
    private String queryParam(ServerHttpRequest request, String name) {
        if (request.getURI().getQuery() == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst(name);
    }
}
