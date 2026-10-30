package com.bluewhale.agent.support.knowledge;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 租户目录内网客户端（Wave 5.54 Phase 3）。
 *
 * <p>调用 whale-service-auth 的 {@code /tenant/internal/enabled-ids} 获取「启用且未过期」的租户ID，
 * 供文件知识库定时同步逐租户执行；目录不可用时返回空列表，由调度器回退默认租户。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class TenantDirectoryClient {

    /** 可用租户目录路径 */
    private static final String ENABLED_IDS_PATH = "/tenant/internal/enabled-ids";

    private final RestClient restClient;

    /**
     * 按采集配置构建客户端。
     *
     * @param properties Agent 配置
     */
    public TenantDirectoryClient(AgentProperties properties) {
        AgentProperties.Knowledge knowledge = properties.getKnowledge();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(knowledge.getConnectTimeoutMillis());
        factory.setReadTimeout(knowledge.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(knowledge.getAuthUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 查询可用租户ID列表。
     *
     * @return 租户ID列表；目录不可用时返回空列表
     */
    public List<Long> listEnabledTenantIds() {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(ENABLED_IDS_PATH)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Object data = response == null ? null : response.get("data");
            if (!(data instanceof List<?> list) || list.isEmpty()) {
                return List.of();
            }
            List<Long> tenantIds = new ArrayList<>(list.size());
            for (Object item : list) {
                Long tenantId = toTenantId(item);
                if (tenantId != null) {
                    tenantIds.add(tenantId);
                }
            }
            return tenantIds;
        } catch (RuntimeException e) {
            log.warn("读取租户目录失败，回退默认租户: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析租户ID：平台响应体会把 Long 序列化为字符串（精度保护），两种形态都要兼容。
     *
     * @param item 响应数组元素
     * @return 租户ID；无法解析时返回 {@code null}
     */
    private Long toTenantId(Object item) {
        if (item instanceof Number number) {
            return number.longValue();
        }
        if (item instanceof CharSequence text) {
            try {
                return Long.parseLong(text.toString().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
