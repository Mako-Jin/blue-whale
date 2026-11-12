package com.bluewhale.gateway.whitelist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 网关白名单拉取客户端：调用 whale-service-auth 只读内部接口。
 *
 * <p>复用 {@code @LoadBalanced WebClient.Builder}（与 {@code PermissionHeaderGlobalFilter} 一致），
 * 显式携带内网令牌头并强制超时。HTTP 失败、超时与响应解析失败均以错误信号返回，
 * 由 {@link GatewayWhitelistProvider} 决定「保留上一份快照」，避免短暂抖动清空白名单。</p>
 *
 * <p>whale-gateway 为 WebFlux 栈，不能引入 Servlet 栈的 whale-common starter，
 * 因此内网令牌头常量在此独立定义（与 {@code InternalTokenProperties} 约定一致）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class AuthGatewayWhitelistClient {

    /** 响应体中的业务码字段名 */
    private static final String FIELD_CODE = "code";

    /** 响应体中的业务数据字段名 */
    private static final String FIELD_DATA = "data";

    /** 白名单项中的路径字段名 */
    private static final String FIELD_PATH_PATTERN = "pathPattern";

    /** 白名单项中的方法字段名 */
    private static final String FIELD_HTTP_METHOD = "httpMethod";

    /** 成功业务码 */
    private static final int SUCCESS_CODE = 0;

    private final GatewayWhitelistDynamicProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    /**
     * 构造白名单拉取客户端
     *
     * @param properties       动态白名单配置
     * @param webClientBuilder 负载均衡 WebClient 构造器
     * @param objectMapper     Jackson 3 对象映射器
     */
    public AuthGatewayWhitelistClient(GatewayWhitelistDynamicProperties properties,
                                      @Qualifier("loadBalancedWebClientBuilder") WebClient.Builder webClientBuilder,
                                      ObjectMapper objectMapper) {
        this.properties = properties;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * 拉取启用的白名单规则
     *
     * @return 成功时发出规则列表（可能为空）；HTTP/超时/解析失败时发出错误信号
     */
    public Mono<List<GatewayWhitelistRule>> fetch() {
        return webClientBuilder.build()
                .get()
                .uri(properties.getUri())
                .headers(headers -> {
                    if (StringUtils.hasText(properties.getInternalToken())) {
                        headers.set(properties.getInternalTokenHeader(), properties.getInternalToken().trim());
                    }
                })
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(Math.max(1L, properties.getTimeoutMillis())))
                .map(this::parseRules);
    }

    /**
     * 解析响应体：仅接受 {@code code=0}，提取 {@code data[].pathPattern/httpMethod}。
     *
     * <p>业务码非 0 视为失败（抛出异常），避免把错误信封当作「空白名单」清空快照。</p>
     *
     * @param body 响应体 JSON 文本
     * @return 白名单规则列表；字段缺失的条目跳过
     */
    private List<GatewayWhitelistRule> parseRules(String body) {
        if (!StringUtils.hasText(body)) {
            throw new IllegalStateException("白名单响应体为空");
        }
        Map<?, ?> root = objectMapper.readValue(body, Map.class);
        Object code = root.get(FIELD_CODE);
        if (!(code instanceof Number number) || number.intValue() != SUCCESS_CODE) {
            throw new IllegalStateException("白名单响应业务码非 0: " + code);
        }
        Object data = root.get(FIELD_DATA);
        if (!(data instanceof Collection<?> items)) {
            return List.of();
        }
        List<GatewayWhitelistRule> rules = new ArrayList<>(items.size());
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            String pathPattern = stringify(row.get(FIELD_PATH_PATTERN));
            if (!StringUtils.hasText(pathPattern)) {
                continue;
            }
            rules.add(new GatewayWhitelistRule(pathPattern.trim(), stringify(row.get(FIELD_HTTP_METHOD))));
        }
        log.debug("拉取网关白名单成功: {} 条", rules.size());
        return rules;
    }

    /**
     * 空安全字符串化
     *
     * @param value 原始值
     * @return 字符串值；{@code null} 返回空串
     */
    private static String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
