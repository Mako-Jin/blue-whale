package com.bluewhale.contract.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.contract.config.ContractApprovalProperties;
import com.bluewhale.contract.dto.OperatorContext;
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
 * <p>发起审批走 {@code POST /workflow/instance}（身份与权限码头由当前请求透传）；
 * 审批结果同步走 {@code GET /workflow/internal/instance/{id}/detail}（内网只读，无权限码）。</p>
 *
 * @author BlueWhale
 */
@Component
public class ContractApprovalClient {

    /** 发起流程实例路径 */
    private static final String START_PATH = "/workflow/instance";

    /** 撤销流程实例路径 */
    private static final String CANCEL_PATH = "/workflow/instance/{id}/cancel";

    /** 实例归档详情路径（内网） */
    private static final String DETAIL_PATH = "/workflow/internal/instance/{id}/detail";

    /** 统一响应体状态码字段 */
    private static final String FIELD_CODE = "code";

    /** 统一响应体消息字段 */
    private static final String FIELD_MESSAGE = "message";

    /** 统一响应体数据字段 */
    private static final String FIELD_DATA = "data";

    /** 统一响应体实例状态字段 */
    private static final String FIELD_STATUS = "status";

    /** 成功状态码 */
    private static final int CODE_SUCCESS = 0;

    private final RestClient restClient;

    /**
     * 按审批配置构建客户端。
     *
     * @param properties 审批配置
     */
    public ContractApprovalClient(ContractApprovalProperties properties) {
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
     * @param businessKey     业务键（contract:{id}:v{version}）
     * @param variables       流程变量
     * @param operator        当前操作人（身份与权限码头透传）
     * @return 流程实例ID
     */
    public Long start(Long definitionId, String title, String businessKey, Map<String, Object> variables,
                      OperatorContext operator) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("definitionId", definitionId);
        body.put("title", title);
        body.put("businessKey", businessKey);
        body.put("variables", variables);
        RestClient.RequestBodySpec spec = restClient.post()
                .uri(START_PATH)
                .contentType(MediaType.APPLICATION_JSON);
        spec = applyOperatorHeaders(spec, operator);
        Map<String, Object> response = spec.body(body).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        Long instanceId = requireInstanceId(response);
        if (instanceId == null) {
            throw failure("响应缺少实例ID");
        }
        return instanceId;
    }

    /**
     * 查询流程实例状态（0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销）。
     *
     * @param instanceId 流程实例ID
     * @return 实例状态；详情缺失时返回 {@code null}
     */
    public Integer fetchStatus(Long instanceId) {
        Map<String, Object> response = restClient.get()
                .uri(DETAIL_PATH, instanceId)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        if (response == null) {
            return null;
        }
        Object code = response.get(FIELD_CODE);
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            return null;
        }
        Object data = response.get(FIELD_DATA);
        if (data instanceof Map<?, ?> map && map.get(FIELD_STATUS) instanceof Number status) {
            return status.intValue();
        }
        return null;
    }

    /**
     * 撤销流程实例（撤回审批时尽力调用，失败仅记录由调用方处理）。
     *
     * @param instanceId 流程实例ID
     * @param operator   当前操作人
     */
    public void cancel(Long instanceId, OperatorContext operator) {
        RestClient.RequestBodySpec spec = restClient.post()
                .uri(CANCEL_PATH, instanceId)
                .contentType(MediaType.APPLICATION_JSON);
        spec = applyOperatorHeaders(spec, operator);
        spec.retrieve().toBodilessEntity();
    }

    /**
     * 透传操作人可信头。
     *
     * <p>工作流服务的权限拦截器优先读权限码头，缺省回落 {@code X-User-Roles}；
     * 两者都需透传，否则 super_admin 角色会被判成无权限。</p>
     *
     * @param spec     请求构造器
     * @param operator 当前操作人
     * @return 追加头后的请求构造器
     */
    private RestClient.RequestBodySpec applyOperatorHeaders(RestClient.RequestBodySpec spec,
                                                            OperatorContext operator) {
        if (operator == null) {
            return spec;
        }
        if (StringUtils.hasText(operator.userId())) {
            spec = spec.header(WebHeaders.USER_ID, operator.userId());
        }
        if (StringUtils.hasText(operator.username())) {
            spec = spec.header(WebHeaders.USERNAME, operator.username());
        }
        if (StringUtils.hasText(operator.permissionCodes())) {
            spec = spec.header(WebHeaders.PERMISSION_CODES, operator.permissionCodes());
        }
        if (StringUtils.hasText(operator.roles())) {
            spec = spec.header(WebHeaders.USER_ROLES, operator.roles());
        }
        Long tenantId = TenantContext.get();
        if (tenantId != null) {
            spec = spec.header(WebHeaders.TENANT_ID, String.valueOf(tenantId));
        }
        return spec;
    }

    /**
     * 校验统一响应体并取出实例ID。
     *
     * @param response 响应体
     * @return 流程实例ID，缺失返回 {@code null}
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
            } catch (NumberFormatException ex) {
                throw failure("实例ID非法: " + id);
            }
        }
        return null;
    }

    /**
     * 构造受控失败异常。
     *
     * @param message 失败原因
     * @return 业务异常
     */
    private BizException failure(String message) {
        return new BizException(ErrorCode.SYSTEM_ERROR, "发起审批失败: " + message);
    }
}
