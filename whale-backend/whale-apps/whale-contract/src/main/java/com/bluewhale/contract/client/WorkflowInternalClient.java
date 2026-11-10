package com.bluewhale.contract.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.contract.config.ContractApprovalProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * whale-workflow 任务内网完成客户端。
 *
 * <p>调用 {@code POST /workflow/internal/task/{id}/complete}，供第三方签章回调到达后
 * 推进签署节点（Wave 5.61 §5.3 方案 B）；服务端按任务状态幂等，重复调用不会重复推进。</p>
 *
 * @author BlueWhale
 */
@Component
public class WorkflowInternalClient {

    /** 内网完成任务路径 */
    private static final String COMPLETE_PATH = "/workflow/internal/task/{id}/complete";

    /** 统一响应体状态码字段 */
    private static final String FIELD_CODE = "code";

    /** 统一响应体消息字段 */
    private static final String FIELD_MESSAGE = "message";

    /** 成功状态码 */
    private static final int CODE_SUCCESS = 0;

    /** 默认租户ID */
    private static final long DEFAULT_TENANT_ID = 1L;

    private final RestClient restClient;

    /**
     * 按审批配置构建客户端。
     *
     * @param properties 审批配置
     */
    public WorkflowInternalClient(ContractApprovalProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(properties.getWorkflowUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 完成工作流任务。
     *
     * @param taskId     任务ID
     * @param operatorId 签署人用户ID（可空）
     * @param action     APPROVE/REJECT
     * @param comment    处理意见
     * @param eventId    回调事件ID
     * @param source     操作来源
     */
    public void complete(Long taskId, Long operatorId, String action, String comment,
                         String eventId, String source) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operatorId", operatorId == null ? null : String.valueOf(operatorId));
        body.put("action", action);
        body.put("comment", comment);
        body.put("eventId", eventId);
        body.put("source", source);
        Long tenantId = TenantContext.get();
        try {
            Map<String, Object> response = restClient.post()
                    .uri(COMPLETE_PATH, taskId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(WebHeaders.TENANT_ID, String.valueOf(tenantId == null ? DEFAULT_TENANT_ID : tenantId))
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "工作流任务完成失败: 空响应");
            }
            Object code = response.get(FIELD_CODE);
            if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
                throw new BizException(ErrorCode.SYSTEM_ERROR,
                        "工作流任务完成失败: " + response.get(FIELD_MESSAGE));
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "工作流任务完成失败: " + ex.getMessage());
        }
    }
}
