package com.bluewhale.oa.drive.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.oa.drive.domain.gateway.DriveDeptGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * 组织架构网关实现。
 *
 * <p>调用 Auth 内网通讯录 {@code GET /user/v1/internal/directory}，仅接受账号精确匹配；
 * 解析失败一律返回 {@code null}，由应用层按无部门处理。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DriveDeptGatewayImpl implements DriveDeptGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 用户目录服务名 */
    private static final String SERVICE_NAME = "whale-service-auth";

    /** 单次查询条数 */
    private static final long QUERY_SIZE = 10L;

    private final DiscoveryClient discoveryClient;

    @Override
    public Long deptIdOf(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        String uri = UriComponentsBuilder.fromUriString(baseUrl() + "/user/v1/internal/directory")
                .queryParam("keyword", username.trim())
                .queryParam("current", 1)
                .queryParam("size", QUERY_SIZE)
                .build().encode().toUri().toString();
        try {
            Map<?, ?> response = InternalTokenSupport.restClient()
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(Map.class);
            return parse(response, username.trim());
        } catch (RestClientResponseException | ResourceAccessException | IllegalStateException ex) {
            log.warn("网盘部门解析失败: username={}, error={}", username, ex.getMessage());
            return null;
        }
    }

    /**
     * 从响应信封中提取账号精确匹配的部门ID。
     *
     * @param response 响应信封
     * @param username 账号
     * @return 部门ID
     */
    @SuppressWarnings("unchecked")
    private Long parse(Map<?, ?> response, String username) {
        if (response == null) {
            return null;
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            log.warn("网盘部门解析返回失败: username={}, message={}", username, response.get("message"));
            return null;
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> page)) {
            return null;
        }
        Object records = page.get("records");
        if (!(records instanceof List<?> list)) {
            return null;
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .filter(item -> username.equalsIgnoreCase(String.valueOf(item.get("username"))))
                .map(item -> item.get("deptId"))
                .filter(Number.class::isInstance)
                .map(item -> ((Number) item).longValue())
                .findFirst().orElse(null);
    }

    /**
     * 解析用户目录服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw new IllegalStateException("用户目录服务不可用：" + SERVICE_NAME);
        }
        return instances.getFirst().getUri().toString();
    }
}
