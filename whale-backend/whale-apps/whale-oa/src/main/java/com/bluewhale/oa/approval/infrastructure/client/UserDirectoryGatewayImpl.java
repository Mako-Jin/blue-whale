package com.bluewhale.oa.approval.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.oa.approval.domain.gateway.UserDirectoryGateway;
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
import java.util.Optional;

/**
 * 用户目录查询客户端实现。
 *
 * <p>调用 Auth 内网通讯录 {@code GET /user/v1/internal/directory}，仅接受账号精确匹配；
 * 找不到或存在多个同名账号时返回空，由调用方跳过该接收人。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDirectoryGatewayImpl implements UserDirectoryGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 用户目录服务名 */
    private static final String SERVICE_NAME = "whale-service-auth";

    /** 单次查询条数 */
    private static final long QUERY_SIZE = 10L;

    private final DiscoveryClient discoveryClient;

    @Override
    public Optional<Long> findUserIdByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return Optional.empty();
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
        } catch (RestClientResponseException | ResourceAccessException ex) {
            log.warn("用户目录查询失败: username={}, error={}", username, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 从响应信封中提取账号精确匹配的用户ID。
     *
     * @param response 响应信封
     * @param username 账号
     * @return 用户ID
     */
    @SuppressWarnings("unchecked")
    private Optional<Long> parse(Map<?, ?> response, String username) {
        if (response == null) {
            return Optional.empty();
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            log.warn("用户目录返回失败: username={}, message={}", username, response.get("message"));
            return Optional.empty();
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> page)) {
            return Optional.empty();
        }
        Object records = page.get("records");
        if (!(records instanceof List<?> list)) {
            return Optional.empty();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .filter(item -> username.equalsIgnoreCase(String.valueOf(item.get("username"))))
                .map(item -> item.get("id"))
                .filter(Number.class::isInstance)
                .map(item -> ((Number) item).longValue())
                .findFirst();
    }

    /**
     * 解析用户目录服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "biz.oa.approval.directory-service-unavailable", "用户目录服务不可用");
        }
        return instances.getFirst().getUri().toString();
    }
}
