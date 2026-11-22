package com.bluewhale.pm.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.pm.infrastructure.config.PmApprovalProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * whale-workflow 内网审批客户端。
 *
 * <p>以 {@code businessKey=工作项ID} 发起流程实例；调用方身份（{@code X-User-Id} /
 * {@code X-Username} / {@code X-Permission-Codes}）由当前请求透传，审批权限仍由 workflow 侧判定。</p>
 *
 * @author BlueWhale
 */
@Component
public class WorkflowApprovalClient {

    /** 发起流程实例路径 */
    private static final String START_PATH = "/workflow/v1/instance";

    /** 统一响应体状态码字段 */
    private static final String FIELD_CODE = "code";

    /** 统一响应体消息字段 */
    private static final String FIELD_MESSAGE = "message";

    /** 统一响应体数据字段 */
    private static final String FIELD_DATA = "data";

    /** 成功状态码 */
    private static final int CODE_SUCCESS = 0;

    private final RestClient restClient;

    /**
     * 按审批配置构建客户端。
     *
     * @param properties 审批配置
     */
    public WorkflowApprovalClient(PmApprovalProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getWorkflowUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 发起审批流程实例。
     *
     * @param definitionId    流程定义ID
     * @param title           实例标题
     * @param businessKey     业务键（工作项ID）
     * @param variables       流程变量
     * @param operatorId      操作人用户ID
     * @param username        操作人账号
     * @param permissionCodes 当前用户的权限码串
     * @return 流程实例ID
     */
    public String start(Long definitionId, String title, String businessKey, Map<String, Object> variables,
                        Long operatorId, String username, String permissionCodes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("definitionId", definitionId);
        body.put("title", title);
        body.put("businessKey", businessKey);
        body.put("variables", variables);
        RestClient.RequestBodySpec spec = restClient.post()
                .uri(START_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(WebHeaders.USER_ID, String.valueOf(operatorId));
        if (StringUtils.hasText(username)) {
            spec = spec.header(WebHeaders.USERNAME, username);
        }
        if (StringUtils.hasText(permissionCodes)) {
            spec = spec.header(WebHeaders.PERMISSION_CODES, permissionCodes);
        }
        Long tenantId = TenantContext.get();
        if (tenantId != null) {
            spec = spec.header(WebHeaders.TENANT_ID, String.valueOf(tenantId));
        }
        Map<String, Object> response = spec.body(body).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        return requireInstanceId(response);
    }

    /**
     * 校验统一响应体并取出实例ID。
     *
     * @param response 响应体
     * @return 流程实例ID
     */
    private String requireInstanceId(Map<String, Object> response) {
        if (response == null) {
            throw failure("空响应");
        }
        Object code = response.get(FIELD_CODE);
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw failure(String.valueOf(response.get(FIELD_MESSAGE)));
        }
        Object data = response.get(FIELD_DATA);
        if (data instanceof Map<?, ?> map && map.get("id") != null) {
            return String.valueOf(map.get("id"));
        }
        throw failure("响应缺少实例ID");
    }

    /**
     * 构造受控失败异常。
     *
     * @param message 失败原因
     * @return 业务异常
     */
    private BizException failure(String message) {
        return new BizException(ErrorCode.SYSTEM_ERROR, "biz.pm.infrastructure.failed-initiator-approval", "发起审批失败: {0}", message);
    }
}
