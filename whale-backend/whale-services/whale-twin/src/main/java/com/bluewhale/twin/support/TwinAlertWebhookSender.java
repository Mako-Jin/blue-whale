package com.bluewhale.twin.support;

import com.bluewhale.twin.config.TwinProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * 告警 webhook 投递器（Wave 6.10 M0）：把一个告警事件 POST 到外部地址。
 *
 * <p><b>刻意不用 {@code InternalTokenSupport.restClient()}</b>：内网令牌不能随请求
 * 泄漏给第三方地址，这里用独立 RestClient，只带可选的 Bearer 令牌。</p>
 *
 * <p>地址形态校验：只接受 {@code http/https}、必须有 host、不得包含 userInfo——
 * 运维配置写错时记 WARN 跳过，绝不把带凭据的 URL 发出去。</p>
 *
 * <p>失败返回 {@code false} 不抛异常、不重试：告警有级别变化语义，下次变化会再发；
 * 无脑重试只会在对方故障时堆请求。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class TwinAlertWebhookSender {

    /** 告警级别请求头（便于接收端按级别分流） */
    private static final String HEADER_LEVEL = "X-Twin-Alert-Level";

    private final TwinProperties properties;

    private final RestClient client;

    /**
     * 生产用构造：按配置超时构建独立 RestClient。
     *
     * <p>类里有测试用的第二个构造器，必须显式标注 {@code @Autowired}，
     * 否则 Spring 会去找无参构造而启动失败。</p>
     *
     * @param properties 孪生配置
     */
    @Autowired
    public TwinAlertWebhookSender(TwinProperties properties) {
        this(properties, buildClient(properties));
    }

    /**
     * 测试用构造：外部传入 RestClient（可绑定 MockRestServiceServer）。
     *
     * @param properties 孪生配置
     * @param client     投递客户端
     */
    TwinAlertWebhookSender(TwinProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    /**
     * 按配置超时构建独立 RestClient。
     *
     * @param properties 孪生配置
     * @return RestClient
     */
    private static RestClient buildClient(TwinProperties properties) {
        int timeoutSeconds = Math.max(properties.getAlertNotify().getTimeoutSeconds(), 1);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 投递一条告警事件。
     *
     * @param url     目标地址
     * @param payload 事件载荷（含 level / stateKey 等字段）
     * @return 投递成功（HTTP 2xx）返回 {@code true}
     */
    public boolean send(String url, Map<String, Object> payload) {
        if (!isHttpUrl(url)) {
            log.warn("告警 webhook 地址非法（仅支持 http/https 且不得含凭据），已跳过：{}", url);
            return false;
        }
        TwinProperties.AlertNotify config = properties.getAlertNotify();
        try {
            client.post()
                    .uri(url.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        String token = config.getWebhookToken();
                        if (StringUtils.hasText(token)) {
                            headers.setBearerAuth(token.trim());
                        }
                        headers.set(HEADER_LEVEL, String.valueOf(payload.get("level")));
                    })
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("告警 webhook 外发成功：stateKey={}, level={}, url={}",
                    payload.get("stateKey"), payload.get("level"), url);
            return true;
        } catch (RuntimeException error) {
            log.warn("告警 webhook 外发失败（不重试，等下次级别变化）：url={}, reason={}",
                    url, error.getMessage());
            return false;
        }
    }

    /**
     * 校验地址形态：http/https、必须有 host、不得含 userInfo。
     *
     * @param url 目标地址
     * @return 合法返回 {@code true}
     */
    static boolean isHttpUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            boolean http = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            return http && StringUtils.hasText(uri.getHost()) && uri.getUserInfo() == null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}
