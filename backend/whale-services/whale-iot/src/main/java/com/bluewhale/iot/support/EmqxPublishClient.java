package com.bluewhale.iot.support;

import com.bluewhale.iot.config.IotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EMQX 管理面发布客户端（{@code POST /api/v5/publish}）。
 *
 * <p>whale-iot <b>不建常驻 MQTT 客户端</b>（避免重连/会话态与多实例订阅竞争），
 * 下行统一走 Broker 的 REST 发布接口：指令下发、影子期望值同步都经本客户端出网。</p>
 *
 * <p>凭据未配置时 {@link #publish} 直接返回 {@code false}（降级为只落库不投递），
 * 使本地无 Broker 环境也能跑通业务链路与单测。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmqxPublishClient {

    /** EMQX 发布接口路径 */
    private static final String PUBLISH_PATH = "/api/v5/publish";

    /** 发布方客户端ID（Broker 侧标识，非设备身份） */
    private static final String PUBLISHER_CLIENT_ID = "whale-iot";

    /** 默认 QoS */
    private static final int DEFAULT_QOS = 1;

    /** 连接超时（秒） */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    private final IotProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    /**
     * 发布一条消息到指定主题。
     *
     * @param topic   MQTT 主题
     * @param payload 报文（JSON 字符串）
     * @return true 投递成功（Broker 返回 2xx）；false 未配置凭据或投递失败
     */
    public boolean publish(String topic, String payload) {
        return publish(topic, payload, DEFAULT_QOS);
    }

    /**
     * 发布一条消息到指定主题（指定 QoS）。
     *
     * @param topic   MQTT 主题
     * @param payload 报文（JSON 字符串）
     * @param qos     服务质量等级
     * @return true 投递成功；false 未配置凭据或投递失败
     */
    public boolean publish(String topic, String payload, int qos) {
        IotProperties.Emqx emqx = properties.getEmqx();
        if (emqx.getApiKey() == null || emqx.getApiKey().isBlank()
                || emqx.getApiSecret() == null || emqx.getApiSecret().isBlank()) {
            log.warn("EMQX API 凭据未配置，跳过投递：topic={}", topic);
            return false;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("topic", topic);
        body.put("payload", payload);
        body.put("qos", qos);
        body.put("clientid", PUBLISHER_CLIENT_ID);
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(emqx.getApiBaseUrl() + PUBLISH_PATH))
                    .timeout(Duration.ofSeconds(emqx.getPublishTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                            (emqx.getApiKey() + ":" + emqx.getApiSecret()).getBytes(StandardCharsets.UTF_8)))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            com.bluewhale.iot.support.JsonCodec.write(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 == 2) {
                return true;
            }
            log.warn("EMQX 发布失败：topic={}, status={}, body={}",
                    topic, response.statusCode(), response.body());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("EMQX 发布异常：topic={}, reason={}", topic, e.getMessage());
            return false;
        }
    }
}
