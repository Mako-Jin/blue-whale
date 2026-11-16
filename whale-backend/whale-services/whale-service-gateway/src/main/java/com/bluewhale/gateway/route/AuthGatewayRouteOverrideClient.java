package com.bluewhale.gateway.route;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import java.util.List;

/**
 * 网关路由业务属性覆盖拉取客户端：调用 whale-service-auth 只读内部接口（GW-10 Phase 2）。
 *
 * <p>复用 {@code @LoadBalanced WebClient.Builder}，显式携带内网令牌头并强制超时。
 * HTTP 失败、超时与响应解析失败均以错误信号返回，由 {@link RouteOverrideProvider}
 * 决定「保留上一份快照」。</p>
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
    private static final int SUCCESS_CODE = 0;

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
     * 解析响应体：仅接受 {@code code=0}，提取 {@code data.items[]}。
     *
     * <p>业务码非 0 视为失败（抛出异常），避免把错误信封当作「空覆盖」清空快照。</p>
     *
     * @param body 响应体 JSON 文本
     * @return 覆盖规则列表；routeId 缺失的条目跳过
     */
    private List<RouteOverrideRule> parseRules(String body) {
        if (!StringUtils.hasText(body)) {
            throw new IllegalStateException("路由覆盖响应体为空");
        }
        SnapshotDto snapshot = objectMapper.readValue(body, SnapshotDto.class);
        if (snapshot.code() != SUCCESS_CODE) {
            throw new IllegalStateException("路由覆盖响应业务码非 0: " + snapshot.code());
        }
        List<SnapshotDto.ItemDto> items = snapshot.data() == null || snapshot.data().items() == null
                ? List.of() : snapshot.data().items();
        List<RouteOverrideRule> rules = new ArrayList<>(items.size());
        for (SnapshotDto.ItemDto item : items) {
            if (item == null || item.routeId() == null || item.routeId().isBlank()) {
                continue;
            }
            boolean enabled = Boolean.TRUE.equals(item.enabled());
            long version = item.version() == null ? 1L : item.version();
            rules.add(new RouteOverrideRule(item.routeId(), enabled, item.effectiveFrom(), item.effectiveTo(),
                    version));
        }
        log.debug("拉取网关路由覆盖成功: {} 条", rules.size());
        return rules;
    }

    /**
     * 响应信封（{@code Result} 结构）：{@code {code, message, data:{items:[]}}}
     *
     * @param code 响应码
     * @param data 覆盖数据体
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SnapshotDto(int code, DataDto data) {

        /**
         * 覆盖数据体
         *
         * @param items 覆盖条目列表
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        record DataDto(List<ItemDto> items) {
        }

        /**
         * 覆盖条目
         *
         * @param routeId 路由 ID
         * @param enabled 是否生效
         * @param effectiveFrom 生效起始时间
         * @param effectiveTo 生效截止时间
         * @param version 版本号
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        record ItemDto(String routeId, Boolean enabled, LocalDateTime effectiveFrom,
                       LocalDateTime effectiveTo, Long version) {
        }
    }
}
