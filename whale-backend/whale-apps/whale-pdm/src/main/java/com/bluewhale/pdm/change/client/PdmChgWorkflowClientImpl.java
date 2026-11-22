package com.bluewhale.pdm.change.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * whale-workflow 审批客户端实现
 *
 * <p>调用 {@code POST /workflow/v1/instance} 发起审批；未配置流程定义或工作流不可用时
 * 抛出业务异常，由调用方降级为人工审批。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdmChgWorkflowClientImpl implements PdmChgWorkflowClient {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 工作流服务名 */
    private static final String SERVICE_NAME = "whale-workflow";

    private final DiscoveryClient discoveryClient;

    /** 流程定义ID，未配置时视为 OA 不可用 */
    @Value("${pdm.change.definition-id:0}")
    private long definitionId;

    @Override
    public Long start(Long tenantId, String bizType, Long bizId, String bizNo, String title, Long userId) {
        if (definitionId <= 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "biz.pdm.change.configured-change-approval-workflow-definition-degrade",
                          "未配置变更审批流程定义，降级人工审批");
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("bizType", bizType);
        variables.put("bizId", bizId);
        variables.put("bizNo", bizNo);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("definitionId", definitionId);
        body.put("title", title);
        body.put("businessKey", bizType + ":" + bizId);
        body.put("variables", variables);
        Map<String, Object> response = InternalTokenSupport.restClient()
                .post()
                .uri(baseUrl() + "/workflow/v1/instance")
                .header(WebHeaders.USER_ID, String.valueOf(userId))
                .header(WebHeaders.TENANT_ID, String.valueOf(tenantId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        return requireInstanceId(response);
    }

    /**
     * 校验响应并解析实例ID。
     *
     * @param response 响应体
     * @return 实例ID
     */
    private Long requireInstanceId(Map<String, Object> response) {
        if (response == null) {
            throw failure("空响应");
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw failure(String.valueOf(response.get("message")));
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map && map.get("id") != null) {
            try {
                return Long.valueOf(String.valueOf(map.get("id")));
            } catch (NumberFormatException ex) {
                throw failure("实例ID非法");
            }
        }
        throw failure("响应缺少实例ID");
    }

    /**
     * 解析工作流服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw failure("工作流服务不可用");
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
        return new BizException(ErrorCode.SYSTEM_ERROR, "biz.pdm.change.change-approval-call-failed", "变更审批调用失败: {0}", safe);
    }
}
