package com.bluewhale.crm.commerce.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.commerce.domain.port.InvoiceApprovalPort;
import com.bluewhale.crm.config.CrmInvoiceApprovalProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * whale-workflow 开票审批客户端。
 *
 * <p>发起审批走 {@code POST /workflow/v1/instance}，身份与权限码头由当前请求透传。</p>
 *
 * @author BlueWhale
 */
@Component
public class WorkflowInvoiceApprovalClient implements InvoiceApprovalPort {

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

    /** 默认租户 ID */
    private static final long DEFAULT_TENANT_ID = 1L;

    private final RestClient restClient;

    /**
     * 按开票审批配置构建客户端。
     *
     * @param properties 审批配置
     */
    public WorkflowInvoiceApprovalClient(CrmInvoiceApprovalProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(properties.getWorkflowUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public Long start(Long definitionId, String title, String businessKey, Map<String, Object> variables,
                      CrmActor actor) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("definitionId", definitionId);
        body.put("title", title);
        body.put("businessKey", businessKey);
        body.put("variables", variables);
        RestClient.RequestBodySpec spec = restClient.post()
                .uri(START_PATH)
                .contentType(MediaType.APPLICATION_JSON);
        spec = applyActorHeaders(spec, actor);
        Map<String, Object> response = spec.body(body).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        return requireInstanceId(response);
    }

    /**
     * 透传操作人可信头。
     *
     * @param spec 请求构造器
     * @param actor 当前操作人
     * @return 追加头后的请求构造器
     */
    private RestClient.RequestBodySpec applyActorHeaders(RestClient.RequestBodySpec spec, CrmActor actor) {
        if (actor != null) {
            if (actor.getUserId() != null) {
                spec = spec.header(WebHeaders.USER_ID, String.valueOf(actor.getUserId()));
            }
            if (StringUtils.hasText(actor.getUsername())) {
                spec = spec.header(WebHeaders.USERNAME, actor.getUsername());
            }
            if (!actor.getPermissions().isEmpty()) {
                spec = spec.header(WebHeaders.PERMISSION_CODES, String.join(",", actor.getPermissions()));
            }
            if (!actor.getRoles().isEmpty()) {
                spec = spec.header(WebHeaders.USER_ROLES, String.join(",", actor.getRoles()));
            }
        }
        Long tenantId = TenantContext.get();
        spec = spec.header(WebHeaders.TENANT_ID, String.valueOf(tenantId == null ? DEFAULT_TENANT_ID : tenantId));
        return spec;
    }

    /**
     * 校验统一响应体并取出实例 ID。
     *
     * @param response 响应体
     * @return 流程实例 ID
     */
    private Long requireInstanceId(Map<String, Object> response) {
        if (response == null) {
            throw failure("空响应");
        }
        Object code = response.get(FIELD_CODE);
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw failure(String.valueOf(response.get(FIELD_MESSAGE)));
        }
        Object data = response.get(FIELD_DATA);
        if (data instanceof Map<?, ?> map && map.get("id") != null) {
            String id = String.valueOf(map.get("id")).trim();
            try {
                return Long.valueOf(id);
            } catch (NumberFormatException exception) {
                throw failure("实例ID非法: " + id);
            }
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
        return new BizException(ErrorCode.SYSTEM_ERROR, "biz.crm.commerce.failed-initiator-invoicing-approval", "发起开票审批失败: {0}", message);
    }
}
