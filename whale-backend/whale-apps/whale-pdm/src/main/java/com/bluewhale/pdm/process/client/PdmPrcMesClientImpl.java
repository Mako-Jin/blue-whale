package com.bluewhale.pdm.process.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.pdm.process.dto.PdmPrcMesPublishRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MES 内网客户端实现
 *
 * <p>只调用网关屏蔽的 {@code /mes/internal/**}；请求携带内网令牌与租户头。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdmPrcMesClientImpl implements PdmPrcMesClient {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** MES 服务名 */
    private static final String SERVICE_NAME = "whale-mes";

    private final DiscoveryClient discoveryClient;

    @Override
    public String push(Long tenantId, PdmPrcMesPublishRequest request) {
        Map<String, Object> response = InternalTokenSupport.restClient()
                .post()
                .uri(baseUrl() + "/mes/internal/process-route")
                .header(WebHeaders.TENANT_ID, String.valueOf(tenantId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        Object data = requireSuccess(response);
        if (data instanceof Map<?, ?> map && map.get("id") != null) {
            return String.valueOf(map.get("id"));
        }
        return null;
    }

    @Override
    public void withdraw(Long tenantId, String routeNo, String versionNo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("routeNo", routeNo);
        body.put("versionNo", versionNo);
        Map<String, Object> response = InternalTokenSupport.restClient()
                .post()
                .uri(baseUrl() + "/mes/internal/process-route/withdraw")
                .header(WebHeaders.TENANT_ID, String.valueOf(tenantId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        requireSuccess(response);
    }

    /**
     * 校验响应并返回 data 字段。
     *
     * @param response MES 响应
     * @return data 字段
     */
    private Object requireSuccess(Map<String, Object> response) {
        if (response == null) {
            throw failure("MES 响应为空");
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw failure(String.valueOf(response.get("message")));
        }
        return response.get("data");
    }

    /**
     * 解析 MES 服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw failure("MES 服务不可用");
        }
        return instances.get(0).getUri().toString();
    }

    /**
     * 构造受控失败异常。
     *
     * @param message 失败原因
     * @return 业务异常
     */
    private BizException failure(String message) {
        String safe = StringUtils.hasText(message) ? message : "未知错误";
        return new BizException(ErrorCode.SYSTEM_ERROR, "biz.pdm.process.failed-routing-dispatch", "工艺路线下发失败: {0}", safe);
    }
}
