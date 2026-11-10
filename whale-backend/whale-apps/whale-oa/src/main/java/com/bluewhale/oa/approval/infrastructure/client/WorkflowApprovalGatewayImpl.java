package com.bluewhale.oa.approval.infrastructure.client;

import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.oa.approval.domain.gateway.WorkflowApprovalGateway;
import com.bluewhale.oa.approval.domain.model.WorkflowCcQuery;
import com.bluewhale.oa.approval.domain.model.WorkflowCcSnapshot;
import com.bluewhale.oa.approval.domain.model.WorkflowDelegateCommand;
import com.bluewhale.oa.approval.domain.model.WorkflowDelegateRuleSnapshot;
import com.bluewhale.oa.approval.domain.model.WorkflowInstanceQuery;
import com.bluewhale.oa.approval.domain.model.WorkflowInstanceSnapshot;
import com.bluewhale.oa.approval.domain.model.WorkflowInstanceStatusSnapshot;
import com.bluewhale.oa.approval.domain.model.WorkflowTaskQuery;
import com.bluewhale.oa.approval.domain.model.WorkflowTaskSnapshot;
import com.bluewhale.oa.common.OaErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Workflow 审批内网客户端实现。
 *
 * <p>统一解析 {@code Result{code,message,data}} 信封；业务失败保留 Workflow 提示，
 * 网络/服务不可用统一转换为 {@link OaErrorCode#APPROVAL_WORKFLOW_UNAVAILABLE}。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowApprovalGatewayImpl implements WorkflowApprovalGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** Workflow 服务名 */
    private static final String SERVICE_NAME = "whale-workflow";

    /** 内网审批来源 */
    private static final String OPERATOR_SOURCE = "OA";

    /** 人工操作类型 */
    private static final String OPERATOR_TYPE_USER = "USER";

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 批量状态查询上限 */
    private static final int MAX_STATUS_BATCH = 100;

    private final DiscoveryClient discoveryClient;

    /** 独立 JSON 解析器：忽略 Workflow 多余字段并支持 Java 时间类型 */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public PageResult<WorkflowTaskSnapshot> pageTasks(WorkflowTaskQuery query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl() + "/workflow/internal/task/page");
        add(builder, "assignee", query.getAssignee());
        add(builder, "status", query.getStatus());
        add(builder, "instanceId", query.getInstanceId());
        add(builder, "operatorId", query.getOperatorId());
        add(builder, "handled", query.getHandled());
        add(builder, "actorAccount", query.getActorAccount());
        add(builder, "actorId", query.getActorId());
        addPage(builder, query.getCurrent(), query.getSize());
        return page(builder, new TypeReference<PageResult<WorkflowTaskSnapshot>>() {
        });
    }

    @Override
    public PageResult<WorkflowInstanceSnapshot> pageInstances(WorkflowInstanceQuery query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl() + "/workflow/internal/instance/page");
        add(builder, "title", query.getTitle());
        add(builder, "status", query.getStatus());
        add(builder, "definitionCode", query.getDefinitionCode());
        add(builder, "initiator", query.getInitiator());
        addPage(builder, query.getCurrent(), query.getSize());
        return page(builder, new TypeReference<PageResult<WorkflowInstanceSnapshot>>() {
        });
    }

    @Override
    public WorkflowInstanceSnapshot getInstance(Long instanceId) {
        Map<String, Object> data = requireData(get("/workflow/internal/instance/" + instanceId));
        return objectMapper.convertValue(data, WorkflowInstanceSnapshot.class);
    }

    @Override
    public PageResult<WorkflowCcSnapshot> pageCcs(WorkflowCcQuery query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl() + "/workflow/internal/cc/page");
        add(builder, "instanceId", query.getInstanceId());
        List<String> candidates = query.getRecipientCandidates();
        if (candidates != null && !candidates.isEmpty()) {
            builder.queryParam("recipientCandidates", candidates.toArray());
        } else {
            add(builder, "recipient", query.getRecipient());
        }
        add(builder, "readFlag", query.getReadFlag());
        addPage(builder, query.getCurrent(), query.getSize());
        return page(builder, new TypeReference<PageResult<WorkflowCcSnapshot>>() {
        });
    }

    @Override
    public List<WorkflowInstanceStatusSnapshot> listInstanceStatuses(List<Long> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = instanceIds.stream().filter(Objects::nonNull).distinct()
                .limit(MAX_STATUS_BATCH).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(baseUrl() + "/workflow/internal/instance/status");
        builder.queryParam("instanceIds", ids.toArray());
        Map<String, Object> response = get(builder.build().encode().toUri().toString());
        Object data = requireSuccess(response);
        return objectMapper.convertValue(data, new TypeReference<List<WorkflowInstanceStatusSnapshot>>() {
        });
    }

    @Override
    public WorkflowCcSnapshot getCc(Long ccId) {
        Map<String, Object> data = requireData(get("/workflow/internal/cc/" + ccId));
        return objectMapper.convertValue(data, WorkflowCcSnapshot.class);
    }

    @Override
    public void markCcRead(Long ccId) {
        post("/workflow/internal/cc/" + ccId + "/read", null);
    }

    @Override
    public void completeTask(Long taskId, String action, String comment, Map<String, Object> variables,
                             String operatorId, String operatorAccount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", action);
        body.put("operatorId", operatorId);
        body.put("operatorType", OPERATOR_TYPE_USER);
        body.put("source", OPERATOR_SOURCE);
        body.put("comment", comment);
        body.put("variables", variables);
        post("/workflow/internal/task/" + taskId + "/complete", body);
    }

    @Override
    public void transferTask(Long taskId, String targetUser) {
        put("/workflow/internal/task/" + taskId + "/transfer", Map.of("targetUser", targetUser));
    }

    @Override
    public void addSign(Long taskId, List<String> assignees) {
        post("/workflow/internal/task/" + taskId + "/add-sign", Map.of("assignees", assignees));
    }

    @Override
    public void rollbackTask(Long taskId, String targetNodeKey) {
        post("/workflow/internal/task/" + taskId + "/rollback", Map.of("targetNodeKey", targetNodeKey));
    }

    @Override
    public void urge(Long instanceId, String urger, String targetAccount, String comment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("urger", urger);
        body.put("targetAccount", targetAccount);
        body.put("comment", comment);
        post("/workflow/internal/task/" + instanceId + "/urge", body);
    }

    @Override
    public void cancelInstance(Long instanceId) {
        post("/workflow/internal/instance/" + instanceId + "/cancel", null);
    }

    @Override
    public PageResult<WorkflowDelegateRuleSnapshot> pageDelegateRules(String fromAccount, long current, long size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl() + "/workflow/internal/delegate/page");
        add(builder, "fromAccount", fromAccount);
        addPage(builder, current, size);
        return page(builder, new TypeReference<PageResult<WorkflowDelegateRuleSnapshot>>() {
        });
    }

    @Override
    public WorkflowDelegateRuleSnapshot createDelegateRule(WorkflowDelegateCommand command) {
        Map<String, Object> data = requireData(post("/workflow/internal/delegate", delegateBody(command)));
        return objectMapper.convertValue(data, WorkflowDelegateRuleSnapshot.class);
    }

    @Override
    public WorkflowDelegateRuleSnapshot updateDelegateRule(Long id, WorkflowDelegateCommand command) {
        Map<String, Object> data = requireData(put("/workflow/internal/delegate/" + id, delegateBody(command)));
        return objectMapper.convertValue(data, WorkflowDelegateRuleSnapshot.class);
    }

    @Override
    public void deleteDelegateRule(Long id) {
        delete("/workflow/internal/delegate/" + id);
    }

    /**
     * 构造代理规则请求体。
     *
     * @param command 保存命令
     * @return 请求体
     */
    private Map<String, Object> delegateBody(WorkflowDelegateCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("definitionCode", command.getDefinitionCode());
        body.put("fromAccount", command.getFromAccount());
        body.put("toAccount", command.getToAccount());
        body.put("startTime", command.getStartTime());
        body.put("endTime", command.getEndTime());
        body.put("enabled", command.getEnabled());
        body.put("remark", command.getRemark());
        return body;
    }

    /**
     * 执行 GET 并把 data 转成目标分页类型。
     *
     * @param builder 已拼装查询参数的 URI 构造器
     * @param type 目标类型
     * @return 分页结果
     */
    private <T> PageResult<T> page(UriComponentsBuilder builder, TypeReference<PageResult<T>> type) {
        Map<String, Object> response = get(builder.build().encode().toUri().toString());
        Object data = requireSuccess(response);
        return objectMapper.convertValue(data, type);
    }

    /**
     * 追加非空查询参数。
     *
     * @param builder URI 构造器
     * @param name 参数名
     * @param value 参数值
     */
    private void add(UriComponentsBuilder builder, String name, Object value) {
        if (value != null && (!(value instanceof CharSequence text) || StringUtils.hasText(text))) {
            builder.queryParam(name, value);
        }
    }

    /**
     * 追加分页参数（页码至少 1，条数 1~200）。
     *
     * @param builder URI 构造器
     * @param current 页码
     * @param size 每页条数
     */
    private void addPage(UriComponentsBuilder builder, long current, long size) {
        builder.queryParam("current", Math.max(1L, current));
        builder.queryParam("size", size <= 0 ? 10L : Math.min(size, MAX_PAGE_SIZE));
    }

    /**
     * 发起 GET 请求。
     *
     * @param uri 请求地址；相对路径时自动补服务基地址
     * @return 响应信封
     */
    private Map<String, Object> get(String uri) {
        return exchange(() -> restClient().get().uri(absoluteUri(uri)).headers(this::applyIdentity)
                .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
    }

    /**
     * 相对路径补全为带服务基地址的完整地址。
     *
     * @param uri 请求地址
     * @return 完整地址
     */
    private String absoluteUri(String uri) {
        return uri.startsWith("http://") || uri.startsWith("https://") ? uri : baseUrl() + uri;
    }

    /**
     * 发起 POST 请求。
     *
     * @param path 服务内路径
     * @param body 请求体，可为空
     * @return 响应信封
     */
    private Map<String, Object> post(String path, Object body) {
        RestClient.RequestBodySpec spec = restClient().post().uri(baseUrl() + path).headers(this::applyIdentity)
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            spec.body(body);
        }
        return exchange(() -> spec.retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {
        }));
    }

    /**
     * 发起 PUT 请求。
     *
     * @param path 服务内路径
     * @param body 请求体
     * @return 响应信封
     */
    private Map<String, Object> put(String path, Object body) {
        return exchange(() -> restClient().put().uri(baseUrl() + path).headers(this::applyIdentity)
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
    }

    /**
     * 发起 DELETE 请求。
     *
     * @param path 服务内路径
     */
    private void delete(String path) {
        exchange(() -> restClient().delete().uri(baseUrl() + path).headers(this::applyIdentity)
                .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {
                }));
    }

    /**
     * 统一执行并转换网络异常。
     *
     * @param call 调用
     * @return 响应信封
     */
    private Map<String, Object> exchange(java.util.function.Supplier<Map<String, Object>> call) {
        try {
            return call.get();
        } catch (RestClientResponseException | ResourceAccessException ex) {
            log.warn("Workflow 内网调用失败: {}", ex.getMessage());
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_WORKFLOW_UNAVAILABLE);
        }
    }

    /**
     * 透传租户与当前用户身份请求头。
     *
     * @param headers 请求头
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
     * 校验响应并返回 data 对象。
     *
     * @param response 响应信封
     * @return data 字段
     */
    private Map<String, Object> requireData(Map<String, Object> response) {
        Object data = requireSuccess(response);
        if (data instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) map;
            return result;
        }
        throw new BizException(OaErrorCode.APPROVAL_WORKFLOW_UNAVAILABLE, "流程服务返回数据格式异常");
    }

    /**
     * 校验业务码并返回 data。
     *
     * @param response 响应信封
     * @return data 字段
     */
    private Object requireSuccess(Map<String, Object> response) {
        if (response == null) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_WORKFLOW_UNAVAILABLE);
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            String message = response.get("message") == null ? null : String.valueOf(response.get("message"));
            throw new BizException(OaErrorCode.APPROVAL_WORKFLOW_UNAVAILABLE,
                    StringUtils.hasText(message) ? message : OaErrorCode.messageOf(OaErrorCode.APPROVAL_WORKFLOW_UNAVAILABLE));
        }
        return response.get("data");
    }

    /**
     * 懒加载内网 RestClient。
     *
     * @return 内网客户端
     */
    private RestClient restClient() {
        return InternalTokenSupport.restClient();
    }

    /**
     * 解析 Workflow 服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw OaErrorCode.exception(OaErrorCode.APPROVAL_WORKFLOW_UNAVAILABLE);
        }
        return instances.get(0).getUri().toString();
    }
}
