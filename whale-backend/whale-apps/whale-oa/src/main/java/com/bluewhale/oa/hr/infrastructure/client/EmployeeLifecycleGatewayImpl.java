package com.bluewhale.oa.hr.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.hr.domain.gateway.EmployeeLifecycleGateway;
import com.bluewhale.oa.hr.domain.gateway.LifecycleResult;
import com.bluewhale.oa.hr.domain.model.EmployeeChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Auth 人员生命周期网关实现。
 *
 * <p>调用 Auth 内网接口 {@code POST /user/v1/internal/lifecycle}；服务不可达或返回失败时抛出
 * 业务异常，由应用服务落 {@code EXEC_FAILED} 状态并支持稍后重试。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeLifecycleGatewayImpl implements EmployeeLifecycleGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** Auth 服务名 */
    private static final String SERVICE_NAME = "whale-service-auth";

    private final DiscoveryClient discoveryClient;

    @Override
    public LifecycleResult apply(EmployeeChange change) {
        String baseUrl = baseUrl();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", change.getChangeType().name());
        body.put("userId", change.getUserId());
        body.put("username", change.getUsername());
        body.put("nickname", change.getEmployeeName());
        body.put("deptId", change.getToDeptId());
        try {
            Map<?, ?> response = InternalTokenSupport.restClient().post()
                    .uri(baseUrl + "/user/v1/internal/lifecycle")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return parse(response);
        } catch (RestClientResponseException | ResourceAccessException exception) {
            log.warn("人员生命周期同步失败: changeNo={}, error={}", change.getChangeNo(), exception.getMessage());
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_AUTH_UNAVAILABLE, exception.getMessage());
        }
    }

    /**
     * 解析响应信封。
     *
     * @param response 响应体
     * @return 生命周期结果
     */
    private LifecycleResult parse(Map<?, ?> response) {
        if (response == null) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_AUTH_UNAVAILABLE, "Auth 返回空响应");
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_AUTH_UNAVAILABLE,
                    "Auth 生命周期调用失败：" + response.get("message"));
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> payload)) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_AUTH_UNAVAILABLE, "Auth 生命周期响应缺少数据");
        }
        Object userId = payload.get("userId");
        Object status = payload.get("status");
        Object temporaryPassword = payload.get("temporaryPassword");
        return new LifecycleResult(
                userId instanceof Number id ? id.longValue() : null,
                status instanceof Number value ? value.intValue() : null,
                temporaryPassword == null ? null : String.valueOf(temporaryPassword));
    }

    /**
     * 解析 Auth 服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_AUTH_UNAVAILABLE,
                    "Auth 服务未注册：" + SERVICE_NAME);
        }
        return instances.getFirst().getUri().toString();
    }
}
