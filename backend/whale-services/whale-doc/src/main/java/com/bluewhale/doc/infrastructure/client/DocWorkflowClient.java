package com.bluewhale.doc.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.support.DocOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * whale-workflow 客户端（M3 发布审批）。
 *
 * <p>发起实例透传当前用户身份与权限码头；状态同步走内网只读详情接口，
 * 避免后台轮询依赖用户权限。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class DocWorkflowClient {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    private final DiscoveryClient discoveryClient;

    private final DocProperties docProperties;

    /**
     * 发起发布审批流程实例。
     *
     * @param definitionId 流程定义ID
     * @param title        实例标题
     * @param businessKey  业务键
     * @param variables    流程变量
     * @param operator     当前操作人
     * @return 流程实例ID
     */
    public Long start(Long definitionId, String title, String businessKey, Map<String, Object> variables,
                      DocOperator operator) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("definitionId", definitionId);
        body.put("title", title);
        body.put("businessKey", businessKey);
        body.put("variables", variables);
        RestClient.RequestBodySpec spec = RestClient.create()
                .post()
                .uri(baseUrl() + "/workflow/instance")
                .contentType(MediaType.APPLICATION_JSON);
        spec = applyOperatorHeaders(spec, operator);
        Map<String, Object> response = spec.body(body).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        return requireInstanceId(response);
    }

    /**
     * 查询流程实例状态。
     *
     * @param instanceId 流程实例ID
     * @return 状态；0 运行中 / 1 通过 / 2 驳回 / 3 撤销；不可读返回 {@code null}
     */
    public Integer fetchStatus(Long instanceId) {
        Map<String, Object> response = RestClient.create()
                .get()
                .uri(baseUrl() + "/workflow/internal/instance/{id}/detail", instanceId)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        if (response == null) {
            return null;
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            return null;
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map && map.get("status") instanceof Number status) {
            return status.intValue();
        }
        return null;
    }

    /**
     * 透传操作人可信请求头。
     *
     * @param spec     请求构造器
     * @param operator 操作人
     * @return 追加头后的请求构造器
     */
    private RestClient.RequestBodySpec applyOperatorHeaders(RestClient.RequestBodySpec spec,
                                                            DocOperator operator) {
        if (operator == null) {
            return spec;
        }
        RestClient.RequestBodySpec target = spec;
        if (operator.userId() != null) {
            target = target.header(WebHeaders.USER_ID, String.valueOf(operator.userId()));
        }
        if (StringUtils.hasText(operator.username())) {
            target = target.header(WebHeaders.USERNAME, operator.username());
        }
        if (StringUtils.hasText(operator.permissionCodes())) {
            target = target.header(WebHeaders.PERMISSION_CODES, operator.permissionCodes());
        }
        if (StringUtils.hasText(operator.roles())) {
            target = target.header(WebHeaders.USER_ROLES, operator.roles());
        }
        Long tenantId = TenantContext.get();
        if (tenantId != null) {
            target = target.header(WebHeaders.TENANT_ID, String.valueOf(tenantId));
        }
        return target;
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
        List<ServiceInstance> instances = discoveryClient.getInstances(docProperties.getWorkflowServiceName());
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
        return new BizException(ErrorCode.SYSTEM_ERROR, "发布审批调用失败: " + message);
    }
}
