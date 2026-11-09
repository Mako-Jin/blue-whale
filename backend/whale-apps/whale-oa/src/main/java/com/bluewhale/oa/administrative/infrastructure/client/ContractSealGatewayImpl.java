package com.bluewhale.oa.administrative.infrastructure.client;

import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.oa.administrative.domain.gateway.ContractSealGateway;
import com.bluewhale.oa.common.OaErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Contract 印章授权内网客户端。
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractSealGatewayImpl implements ContractSealGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** Contract 服务名 */
    private static final String SERVICE_NAME = "whale-contract";

    /** 授权校验路径 */
    private static final String GRANT_CHECK_PATH = "/contract/internal/seal/%s/grant-check?userId=%s";

    /** 可用印章列表路径 */
    private static final String GRANTED_PATH = "/contract/internal/seal/granted?userId=%s";

    private final DiscoveryClient discoveryClient;

    @Override
    public boolean isGranted(Long sealId, Long userId) {
        if (sealId == null || userId == null) {
            return false;
        }
        Map<String, Object> response = get(String.format(GRANT_CHECK_PATH, sealId, userId));
        Object data = requireSuccess(response);
        if (data instanceof Boolean value) {
            return value;
        }
        if (data instanceof Map<?, ?> map) {
            Object granted = map.get("granted");
            return granted instanceof Boolean value && value;
        }
        return false;
    }

    @Override
    public List<SealInfo> grantedSeals(Long userId) {
        if (userId == null) {
            return List.of();
        }
        Map<String, Object> response = get(String.format(GRANTED_PATH, userId));
        Object data = requireSuccess(response);
        if (!(data instanceof List<?> list)) {
            return List.of();
        }
        List<SealInfo> seals = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> map)) {
                continue;
            }
            Object id = map.get("id");
            Object name = map.get("name");
            if (id == null) {
                continue;
            }
            try {
                seals.add(new SealInfo(Long.valueOf(String.valueOf(id)),
                        name == null ? null : String.valueOf(name)));
            } catch (NumberFormatException exception) {
                log.warn("Contract 印章ID格式异常: {}", id);
            }
        }
        return seals;
    }

    /**
     * 发起合同服务内网 GET 请求。
     *
     * @param path 请求路径
     * @return 响应信封
     */
    private Map<String, Object> get(String path) {
        try {
            return restClient().get().uri(baseUrl() + path).headers(this::applyIdentity)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientResponseException | ResourceAccessException ex) {
            log.warn("Contract 印章内网调用失败: {}", ex.getMessage());
            throw OaErrorCode.exception(OaErrorCode.SEAL_CONTRACT_UNAVAILABLE);
        }
    }

    /**
     * 填充当前请求上下文的租户、用户与用户名请求头。
     *
     * @param headers HTTP 请求头
     */
    private void applyIdentity(HttpHeaders headers) {
        Long tenantId = RequestContextHolder.getTenantId();
        if (tenantId != null) {
            headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId));
        }
        Long userId = RequestContextHolder.getUserId();
        if (userId != null) {
            headers.set(WebHeaders.USER_ID, String.valueOf(userId));
        }
        String username = RequestContextHolder.getUsername();
        if (StringUtils.hasText(username)) {
            headers.set(WebHeaders.USERNAME, username);
        }
    }

    /**
     * 校验响应信封的业务码并返回数据体。
     *
     * @param response 响应信封
     * @return 响应数据体
     */
    private Object requireSuccess(Map<String, Object> response) {
        if (response == null) {
            throw OaErrorCode.exception(OaErrorCode.SEAL_CONTRACT_UNAVAILABLE);
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            String message = response.get("message") == null ? null : String.valueOf(response.get("message"));
            throw new BizException(OaErrorCode.SEAL_CONTRACT_UNAVAILABLE,
                    StringUtils.hasText(message) ? message
                            : OaErrorCode.messageOf(OaErrorCode.SEAL_CONTRACT_UNAVAILABLE));
        }
        return response.get("data");
    }

    /**
     * 获取带内网令牌的 REST 客户端。
     *
     * @return REST 客户端
     */
    private RestClient restClient() {
        return InternalTokenSupport.restClient();
    }

    /**
     * 获取合同服务实例的基础地址。
     *
     * @return 合同服务基础地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw OaErrorCode.exception(OaErrorCode.SEAL_CONTRACT_UNAVAILABLE);
        }
        return instances.get(0).getUri().toString();
    }
}
