package com.bluewhale.gateway.route;

import com.bluewhale.security.contract.GatewayContract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 网关路由业务属性覆盖拉取客户端：调用 whale-service-auth 只读内部接口（GW-10 Phase 2）。
 *
 * <p>复用 {@code @LoadBalanced WebClient.Builder}，显式携带内网令牌头并强制超时。
 * HTTP 失败、超时与响应解析失败均以错误信号返回，由 {@link RouteOverrideProvider}
 * 决定「保留上一份快照」。</p>
 *
 * <p><b>响应形状</b>：auth 透传 {@code Result<List<GatewayRouteOverrideItemResponse>>}，
 * 即 {@code data} 直接是数组。本客户端同时兼容 {@code data.items[]} 包装（便于后续演进），
 * 但以 {@code data[]} 为准——两者不一致会让网关静默保留空快照，契约测试覆盖该形状。</p>
 *
 * <p>whale-service-gateway 为 WebFlux 栈，不能引入 Servlet 栈的 whale-common starter，
 * 内网令牌头常量在此独立定义（与 {@code InternalTokenProperties} 约定一致）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class AuthGatewayRouteOverrideClient {

    /** 成功业务码 */
    private static final int SUCCESS_CODE = GatewayContract.Envelope.SUCCESS_CODE;

    private final RouteOverrideProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    /**
     * 构造路由覆盖拉取客户端
     *
     * @param properties       路由覆盖配置
     * @param webClientBuilder 负载均衡 WebClient 构造器
     * @param objectMapper     Jackson 3 对象映射器
     */
    public AuthGatewayRouteOverrideClient(RouteOverrideProperties properties,
                                          @Qualifier("loadBalancedWebClientBuilder") WebClient.Builder webClientBuilder,
                                          ObjectMapper objectMapper) {
        this.properties = properties;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * 拉取全部路由覆盖条目（含停用与窗口外条目，生效判定由网关按当前时间执行）
     *
     * @return 成功时发出覆盖规则列表（可能为空）；HTTP/超时/解析失败时发出错误信号
     */
    public Mono<List<RouteOverrideRule>> fetch() {
        if (!StringUtils.hasText(properties.getUri())) {
            // 显式转错误信号：WebClient.uri(null/空白) 会同步抛 IllegalArgumentException，
            // 调用方的防重入标记来不及复位（见 RouteOverrideProvider#refresh）
            return Mono.error(new IllegalStateException("网关路由覆盖接口地址未配置"));
        }
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
     * 解析响应体：仅接受 {@code code=0}，提取 {@code data[]}（兼容 {@code data.items[]}）。
     *
     * <p>业务码非 0 视为失败（抛出异常），避免把错误信封当作「空覆盖」清空快照。</p>
     *
     * @param body 响应体 JSON 文本
     * @return 覆盖规则列表；routeId 缺失的条目跳过
     */
    List<RouteOverrideRule> parseRules(String body) {
        if (!StringUtils.hasText(body)) {
            throw new IllegalStateException("路由覆盖响应体为空");
        }
        Map<?, ?> root = objectMapper.readValue(body, Map.class);
        Object code = root.get(GatewayContract.Envelope.CODE);
        if (!(code instanceof Number number) || number.intValue() != SUCCESS_CODE) {
            throw new IllegalStateException("路由覆盖响应业务码非 0: " + code);
        }
        Collection<?> items = extractItems(root.get(GatewayContract.Envelope.DATA));
        List<RouteOverrideRule> rules = new ArrayList<>(items.size());
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            String routeId = stringify(row.get(GatewayContract.RouteOverride.ROUTE_ID));
            if (!StringUtils.hasText(routeId)) {
                continue;
            }
            boolean enabled = Boolean.parseBoolean(stringify(row.get(GatewayContract.RouteOverride.ENABLED)));
            long version = longOrDefault(row.get(GatewayContract.RouteOverride.VERSION));
            rules.add(new RouteOverrideRule(routeId.trim(), enabled,
                    parseTime(row.get(GatewayContract.RouteOverride.EFFECTIVE_FROM)),
                    parseTime(row.get(GatewayContract.RouteOverride.EFFECTIVE_TO)), version));
        }
        log.debug("拉取网关路由覆盖成功: {} 条", rules.size());
        return rules;
    }

    /**
     * 提取条目集合：{@code data} 直接是数组，或 {@code data.items} 是数组
     *
     * @param data 响应体 data 字段
     * @return 条目集合；形状不符时返回空集合
     */
    private static Collection<?> extractItems(Object data) {
        if (data instanceof Collection<?> collection) {
            return collection;
        }
        if (data instanceof Map<?, ?> map && map.get(GatewayContract.Envelope.ITEMS) instanceof Collection<?> items) {
            return items;
        }
        return List.of();
    }

    /**
     * 空安全字符串化
     *
     * @param value 原始值
     * @return 字符串；缺失返回空串
     */
    private static String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 长整型解析，缺失或非法回落 1（与 auth 侧默认版本一致）
     *
     * @param value 原始值
     * @return 版本号
     */
    private static long longOrDefault(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(stringify(value).trim());
        } catch (NumberFormatException e) {
            return 1L;
        }
    }

    /**
     * 解析 ISO-8601 时间；空值或非法值返回 {@code null}（视为不限窗口）
     *
     * @param value 原始值
     * @return 时间
     */
    private static LocalDateTime parseTime(Object value) {
        String text = stringify(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (RuntimeException e) {
            log.warn("忽略无法解析的路由覆盖时间: {}", text);
            return null;
        }
    }
}
