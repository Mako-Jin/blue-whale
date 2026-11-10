package com.bluewhale.auth.infrastructure.mail;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 邮箱代开客户端：调 {@code whale-mail} 的内网接口开通邮箱。
 *
 * <p><b>为什么不让 whale-mail 订阅事件</b>：项目里没有 MQ（auth 的 {@code DomainEventPublisher}
 * 是进程内的 Spring 事件，跨不了服务）。HTTP 直连是当前唯一可行的跨服务通路，
 * 代价是把「邮件服务是否在线」引入了建用户链路，所以调用必须异步 + 失败不阻塞。</p>
 *
 * <p>内网直连不经网关：网关对 {@code /mail/internal/**} 一律 403，走内网反而少一层鉴权开销，
 * 但仍带内网令牌头，便于后续在服务侧做来源校验。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class MailProvisionClient {

    /** 统一响应体里的状态码字段 */
    private static final String FIELD_CODE = "code";

    /** 统一响应体里的消息字段 */
    private static final String FIELD_MESSAGE = "message";

    /** 统一响应体里的数据字段 */
    private static final String FIELD_DATA = "data";

    /** 数据体里的开通状态 */
    private static final String FIELD_STATE = "state";

    /** 数据体里的邮箱地址 */
    private static final String FIELD_EMAIL = "email";

    /** 数据体里的说明 */
    private static final String FIELD_DETAIL = "message";

    /** 成功状态码（与 {@code ErrorCode.SUCCESS} 一致） */
    private static final int SUCCESS_CODE = 0;

    /** 集成配置 */
    private final AuthMailProperties properties;

    /** HTTP 客户端；未启用时为 null */
    private final RestClient restClient;

    /**
     * 按配置构建客户端
     *
     * @param properties 集成配置
     */
    public MailProvisionClient(AuthMailProperties properties) {
        this.properties = properties;
        if (!properties.isProvisionEnabled() || !StringUtils.hasText(properties.getProvisionUrl())) {
            this.restClient = null;
            return;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder().requestFactory(factory).build();
    }

    /**
     * 是否启用了自动开邮箱
     *
     * @return 启用返回 true
     */
    public boolean isEnabled() {
        return restClient != null;
    }

    /**
     * 请求开通邮箱
     *
     * @param command 开通命令
     * @return 开通状态（CREATED / EXISTS），供调用方记录
     */
    public String provision(MailProvisionCommand command) {
        if (restClient == null) {
            throw new IllegalStateException("未启用自动开邮箱");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", command.tenantId());
        body.put("userId", command.userId());
        body.put("username", command.username());
        body.put("displayName", command.displayName());
        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(properties.getProvisionUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientException e) {
            throw new IllegalStateException("调用邮箱服务失败：" + e.getMessage(), e);
        }
        requireSuccess(response);
        Map<String, Object> data = asMap(response.get(FIELD_DATA));
        String state = data == null ? null : String.valueOf(data.get(FIELD_STATE));
        String email = data == null ? null : String.valueOf(data.get(FIELD_EMAIL));
        String detail = data == null ? null : String.valueOf(data.get(FIELD_DETAIL));
        if (!"CREATED".equals(state) && !"EXISTS".equals(state)) {
            // DISABLED / CONFLICT / FAILED 都不是异常，而是需要人工介入的状态，
            // 直接抛出以便调用方按失败记日志（含原因），不要静默吞掉
            throw new IllegalStateException("邮箱未开通：state=" + state + "，原因=" + detail);
        }
        log.debug("邮箱代开返回: userId={}, state={}, email={}", command.userId(), state, email);
        return state;
    }

    /**
     * 校验响应码并抛出一致性异常
     *
     * @param response 响应体
     */
    private void requireSuccess(Map<String, Object> response) {
        if (response == null) {
            throw new IllegalStateException("邮箱服务返回空响应");
        }
        Object code = response.get(FIELD_CODE);
        int value = code instanceof Number number ? number.intValue() : -1;
        if (value != SUCCESS_CODE) {
            Object message = response.get(FIELD_MESSAGE);
            throw new IllegalStateException("邮箱服务返回失败：code=" + value + "，message=" + message);
        }
    }

    /**
     * 把 data 字段安全转成 Map
     *
     * @param data 原始值
     * @return Map；结构不符返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object data) {
        return data instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }
}
