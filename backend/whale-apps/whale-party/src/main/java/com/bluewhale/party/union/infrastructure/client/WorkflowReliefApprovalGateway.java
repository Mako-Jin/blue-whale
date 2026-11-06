package com.bluewhale.party.union.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.union.domain.gateway.ReliefApprovalGateway;
import com.bluewhale.party.union.domain.gateway.ReliefApprovalRequest;
import lombok.RequiredArgsConstructor;
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
 * 慰问审批网关实现（走 whale-workflow 内网接口）。
 *
 * <p>发起失败<b>向上抛出</b>：降级决策属于应用层（保留草稿 + 提示），
 * 客户端只负责把「没发起成功」如实报告出来，避免把网络异常吞在基础设施层后
 * 让应用层误以为审批已经开始。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class WorkflowReliefApprovalGateway implements ReliefApprovalGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 已通过状态码 */
    private static final int STATUS_APPROVED = 1;

    /** 工作流服务名 */
    private static final String SERVICE_NAME = "whale-workflow";

    /** 发起实例路径 */
    private static final String START_PATH = "/workflow/internal/instance/start";

    /** 批量状态路径 */
    private static final String STATUS_PATH = "/workflow/internal/instance/status";

    private final DiscoveryClient discoveryClient;

    private final PartyProperties properties;

    @Override
    public Long start(ReliefApprovalRequest request) {
        if (request == null || request.reliefId() == null) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_APPROVAL_UNAVAILABLE, "缺少慰问申请信息");
        }
        String definitionCode = properties.getReliefDefinitionCode();
        if (!StringUtils.hasText(definitionCode)) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_APPROVAL_UNAVAILABLE, "未配置慰问审批流程定义");
        }
        Map<String, Object> response = InternalTokenSupport.restClient()
                .post()
                .uri(baseUrl() + START_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(startBody(request, definitionCode))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        return requireInstanceId(response);
    }

    @Override
    public Integer queryStatus(Long instanceId) {
        if (instanceId == null || instanceId <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_APPROVAL_SYNC_FAILED, "缺少审批实例 ID");
        }
        try {
            Map<String, Object> response = InternalTokenSupport.restClient()
                    .get()
                    .uri(baseUrl() + STATUS_PATH + "?instanceIds=" + instanceId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return statusOf(response, instanceId);
        } catch (Exception exception) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_APPROVAL_SYNC_FAILED,
                    "审批状态同步失败：" + safeMessage(exception));
        }
    }

    /**
     * 组装发起请求体。
     *
     * @param request 发起请求
     * @param definitionCode 流程定义编码
     * @return 请求体
     */
    private Map<String, Object> startBody(ReliefApprovalRequest request, String definitionCode) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("reliefId", request.reliefId());
        variables.put("amount", request.amount());
        variables.put("type", request.reliefType() == null ? null : request.reliefType().name());
        variables.put("year", request.applyYear());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("definitionCode", definitionCode);
        body.put("title", "工会慰问帮扶申请 " + request.reliefNo());
        body.put("businessKey", "UNION_RELIEF:" + request.reliefId());
        body.put("variables", variables);
        return body;
    }

    /**
     * 校验响应并解析实例 ID。
     *
     * @param response 响应体
     * @return 实例 ID
     */
    private Long requireInstanceId(Map<String, Object> response) {
        if (!isSuccess(response)) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_APPROVAL_UNAVAILABLE,
                    "审批发起失败：" + messageOf(response));
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map && map.get("id") != null) {
            try {
                return Long.valueOf(String.valueOf(map.get("id")));
            } catch (NumberFormatException exception) {
                throw PartyErrorCode.exception(PartyErrorCode.UNION_APPROVAL_UNAVAILABLE, "审批实例 ID 非法");
            }
        }
        throw PartyErrorCode.exception(PartyErrorCode.UNION_APPROVAL_UNAVAILABLE, "审批响应缺少实例 ID");
    }

    /**
     * 从批量状态响应中取指定实例的状态码。
     *
     * @param response 响应体
     * @param instanceId 实例 ID
     * @return 状态码；未找到返回 null
     */
    private Integer statusOf(Map<String, Object> response, Long instanceId) {
        if (!isSuccess(response)) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_APPROVAL_SYNC_FAILED,
                    "审批状态查询失败：" + messageOf(response));
        }
        Object data = response.get("data");
        if (data instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && map.get("id") != null
                        && String.valueOf(instanceId).equals(String.valueOf(map.get("id")))) {
                    Object status = map.get("status");
                    return status instanceof Number number ? number.intValue() : null;
                }
            }
        }
        return null;
    }

    /**
     * 判断响应是否成功。
     *
     * @param response 响应体
     * @return 成功返回 true
     */
    private boolean isSuccess(Map<String, Object> response) {
        if (response == null) {
            return false;
        }
        Object code = response.get("code");
        return code instanceof Number number && number.intValue() == CODE_SUCCESS;
    }

    /**
     * 提取响应提示语。
     *
     * @param response 响应体
     * @return 提示语
     */
    private String messageOf(Map<String, Object> response) {
        if (response == null) {
            return "空响应";
        }
        Object message = response.get("message");
        return message == null ? "未知错误" : String.valueOf(message);
    }

    /**
     * 解析工作流服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_APPROVAL_UNAVAILABLE, "审批服务不可用");
        }
        return instances.get(0).getUri().toString();
    }

    /**
     * 归一异常提示语。
     *
     * @param exception 异常
     * @return 提示语
     */
    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return StringUtils.hasText(message) ? message : exception.getClass().getSimpleName();
    }
}
