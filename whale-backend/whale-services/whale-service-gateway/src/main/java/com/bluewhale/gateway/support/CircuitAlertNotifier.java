package com.bluewhale.gateway.support;

import com.bluewhale.gateway.config.CircuitBreakerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 熔断状态告警上报客户端：把熔断打开/恢复上报到 whale-alert 内网记录接口。
 *
 * <p>只用内网 {@code POST /alert/internal/record}（网关对 {@code /internal/**} 统一 403，
 * 不经网关转发）；whale-alert 侧按 fingerprint 在窗口内去重（重开抖动不刷屏），
 * 并在存在同名启用规则时触发邮件/webhook/短信通知。</p>
 *
 * <p><b>失败策略</b>：任何异常只记 WARN——告警上报是熔断链路的旁路，告警系统不可用
 * 不能反过来影响熔断与降级。上报在 {@code boundedElastic} 上异步执行，不阻塞
 * reactor 事件线程；未配置 {@code alert-base-url} 时整体跳过。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class CircuitAlertNotifier {
    /** whale-alert 内网告警记录路径 */
    private static final String RECORD_PATH = "/alert/internal/record";

    /** 上报来源标识 */
    private static final String SOURCE = "gateway";

    /** 熔断打开告警级别 */
    private static final String LEVEL_OPEN = "ERROR";

    /** 熔断恢复告警级别 */
    private static final String LEVEL_RECOVERED = "INFO";

    /** 上报超时（秒），超时按失败记 WARN 放弃 */
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    /** 熔断配置（读 alert-base-url） */
    private final CircuitBreakerProperties properties;

    /** 内网令牌（环境变量未配置时为空串，dev 侧 fail-open 可省略请求头） */
    private final String internalToken;

    /** 出站 WebClient（测试可注入桩 ExchangeFunction） */
    private final WebClient webClient;

    /**
     * 构造熔断告警上报客户端
     *
     * @param properties 熔断配置
     */
    @org.springframework.beans.factory.annotation.Autowired
    public CircuitAlertNotifier(CircuitBreakerProperties properties) {
        this(properties, buildWebClient());
    }

    /**
     * 构造熔断告警上报客户端（测试用，可注入定制 WebClient）
     *
     * @param properties 熔断配置
     * @param webClient  出站 WebClient
     */
    CircuitAlertNotifier(CircuitBreakerProperties properties, WebClient webClient) {
        this.properties = properties;
        this.webClient = webClient;
        String token = System.getenv("WHALE_INTERNAL_TOKEN");
        this.internalToken = token == null ? "" : token.trim();
    }

    /**
     * 异步上报「熔断打开」：失败率达阈值，请求已短路降级（旁路，不抛异常）
     *
     * @param routeId 打开的路由 id
     */
    public void notifyOpenAsync(String routeId) {
        async(() -> send(routeId, LEVEL_OPEN, "gateway:circuit:" + routeId,
                "gateway-circuit-" + routeId,
                "网关熔断打开：route=" + routeId + "（失败率达阈值，请求已短路降级 503）"));
    }

    /**
     * 异步上报「熔断恢复」：半开探测成功，恢复闭合（旁路，不抛异常）。
     * 指纹与打开告警刻意不同，避免恢复记录被打开告警的去重窗口吞掉。
     *
     * @param routeId 恢复的路由 id
     */
    public void notifyRecoveredAsync(String routeId) {
        async(() -> send(routeId, LEVEL_RECOVERED, "gateway:circuit:" + routeId,
                "gateway-circuit-recovered-" + routeId,
                "网关熔断恢复：route=" + routeId + "（半开探测成功，已恢复闭合）"));
    }

    /**
     * 在 boundedElastic 上异步执行上报（错误只记日志）
     *
     * @param task 同步上报任务
     */
    private void async(Runnable task) {
        Mono.fromRunnable(task)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.warn("熔断告警上报任务异常（忽略）: {}", e.getMessage()))
                .onErrorComplete()
                .subscribe();
    }

    /**
     * 同步上报一条告警记录
     *
     * @param routeId     路由 id
     * @param level       告警级别（ERROR=打开 / INFO=恢复）
     * @param ruleCode    规则编码（{@code gateway:circuit:{routeId}}）
     * @param fingerprint 指纹（打开与恢复各不相同，供告警侧窗口去重）
     * @param content     告警内容
     * @return true 告警侧受理；false 未配置地址或调用失败
     */
    boolean send(String routeId, String level, String ruleCode, String fingerprint, String content) {
        String baseUrl = properties.getAlertBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            log.debug("熔断告警地址未配置，跳过上报: route={}", routeId);
            return false;
        }
        Map<String, Object> body = buildBody(level, ruleCode, fingerprint, content);
        try {
            WebClient.RequestHeadersSpec<?> spec = webClient.post()
                    .uri(baseUrl + RECORD_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body);
            if (!internalToken.isBlank()) {
                // 与其他服务的 InternalTokenFilter 对齐：配置了内网令牌才附加（dev 空令牌 fail-open）
                spec = spec.header("X-Internal-Token", internalToken);
            }
            String response = spec.retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
            log.info("熔断告警已上报: route={}, level={}, response={}", routeId, level, response);
            return true;
        } catch (RuntimeException e) {
            log.warn("熔断告警上报失败（忽略，不影响熔断主流程）: route={}, reason={}", routeId, e.getMessage());
            return false;
        }
    }

    /**
     * 构造 whale-alert 记录创建请求体（字段与 {@code AlertRecordCreateRequest} 对齐；
     * tenantId 不传，告警侧回落平台租户的共享规则）
     *
     * @param level       告警级别
     * @param ruleCode    规则编码
     * @param fingerprint 去重指纹
     * @param content     告警内容
     * @return 请求体字段
     */
    static Map<String, Object> buildBody(String level, String ruleCode, String fingerprint, String content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", SOURCE);
        body.put("ruleCode", ruleCode);
        body.put("alertLevel", level);
        body.put("content", content);
        body.put("fingerprint", fingerprint);
        body.put("triggerTime", java.time.LocalDateTime.now().toString());
        return body;
    }

    /**
     * 构造默认出站 WebClient
     *
     * @return WebClient
     */
    private static WebClient buildWebClient() {
        return WebClient.create();
    }
}
