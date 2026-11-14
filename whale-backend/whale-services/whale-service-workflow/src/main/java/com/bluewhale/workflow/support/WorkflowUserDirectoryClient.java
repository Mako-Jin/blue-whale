package com.bluewhale.workflow.support;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.workflow.config.WorkflowTimeoutProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * whale-service-auth 内网通讯录客户端：把审批人账号解析为用户ID。
 *
 * <p>复用 {@code GET /user/internal/directory}（通讯录手机号/邮箱已脱敏），与 forum 同口径：
 * 只做精确匹配（{@code username} 或 {@code nickname} 等于账号），模糊命中多条也不猜；
 * 解析失败只告警，不影响超时扫描的其余任务。</p>
 *
 * <p><b>为什么必须带租户头</b>：通讯录按租户隔离，缺 {@code X-Tenant-Id} 会直接返回
 * 「缺少租户上下文」，整个请求失败而不是查不到。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class WorkflowUserDirectoryClient {

    /** 内网通讯录路径（auth 未启用 Spring API 版本策略，实际映射无 {@code /v1} 段） */
    private static final String DIRECTORY_PATH = "/user/internal/directory";

    /** 单次查询条数（模糊命中取前 10 条足够做精确筛选） */
    private static final long QUERY_SIZE = 10L;

    private final WorkflowTimeoutProperties properties;

    /** 测试注入的客户端；为空时按当前内网令牌配置现场创建 */
    private final RestClient client;

    /**
     * 生产用构造：自建带超时的客户端，避免 auth 不可达时阻塞调度线程。
     *
     * @param properties 超时与提醒配置
     */
    @Autowired
    public WorkflowUserDirectoryClient(WorkflowTimeoutProperties properties) {
        this(properties, buildTimeoutClient(properties));
    }

    /**
     * 构造带连接/读超时的内网客户端。
     *
     * @param properties 超时与提醒配置
     * @return 客户端
     */
    private static RestClient buildTimeoutClient(WorkflowTimeoutProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        return InternalTokenSupport.builder().requestFactory(factory).build();
    }

    /**
     * 测试用构造。
     *
     * @param properties 超时与提醒配置
     * @param client 外部客户端（可绑定 MockRestServiceServer）
     */
    WorkflowUserDirectoryClient(WorkflowTimeoutProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    /**
     * 按账号精确解析用户ID。
     *
     * @param account 审批人账号（可空）
     * @param tenantId 租户ID
     * @return 用户ID；账号为空、查不到或多条同名时返回 {@code null}
     */
    public Long resolve(String account, Long tenantId) {
        if (!StringUtils.hasText(account) || tenantId == null) {
            return null;
        }
        String name = account.trim();
        try {
            Map<String, Object> page = fetchDirectory(name, tenantId);
            if (page == null || !(page.get("records") instanceof List<?> records)) {
                return null;
            }
            for (Object item : records) {
                if (item instanceof Map<?, ?> row) {
                    Long id = matchId(row, name);
                    if (id != null) {
                        return id;
                    }
                }
            }
            log.debug("通讯录未精确命中：account={}, records={}", name, records.size());
            return null;
        } catch (RuntimeException error) {
            log.warn("审批人账号解析失败：account={}, tenantId={}, reason={}",
                    name, tenantId, error.getMessage());
            return null;
        }
    }

    /**
     * 请求通讯录并返回分页数据体。
     *
     * @param name 账号
     * @param tenantId 租户ID
     * @return 分页数据体；非成功响应返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchDirectory(String name, Long tenantId) {
        String uri = UriComponentsBuilder.fromUriString(properties.getAuthBaseUrl() + DIRECTORY_PATH)
                .queryParam("keyword", name)
                .queryParam("current", 1)
                .queryParam("size", QUERY_SIZE)
                .build().encode().toUriString();
        RestClient target = client == null ? InternalTokenSupport.restClient() : client;
        Map<String, Object> response = target.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId)))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        if (response == null || !"0".equals(String.valueOf(response.get("code")))) {
            log.debug("通讯录返回非成功：account={}, code={}", name,
                    response == null ? null : response.get("code"));
            return null;
        }
        return response.get("data") instanceof Map<?, ?> page ? (Map<String, Object>) page : null;
    }

    /**
     * 判断记录是否与账号精确匹配（忽略大小写），命中则解析用户ID。
     *
     * @param user 通讯录记录
     * @param account 审批人账号
     * @return 用户ID；不匹配或无法解析返回 {@code null}
     */
    private static Long matchId(Map<?, ?> user, String account) {
        boolean usernameHit = account.equalsIgnoreCase(String.valueOf(user.get("username")));
        boolean nicknameHit = account.equalsIgnoreCase(String.valueOf(user.get("nickname")));
        if (!usernameHit && !nicknameHit) {
            return null;
        }
        return toUserId(user.get("id"));
    }

    /**
     * 把通讯录返回的 id 解析为长整型（平台长整型可能以字符串下发）。
     *
     * @param id 原始 id
     * @return 用户ID；无法解析返回 {@code null}
     */
    private static Long toUserId(Object id) {
        if (id instanceof Number number) {
            return number.longValue();
        }
        if (id instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
