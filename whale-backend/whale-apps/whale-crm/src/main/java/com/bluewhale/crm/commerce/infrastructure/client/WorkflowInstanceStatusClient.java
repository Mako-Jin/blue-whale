package com.bluewhale.crm.commerce.infrastructure.client;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.crm.commerce.domain.port.WorkflowStatusPort;
import com.bluewhale.crm.config.CrmOrderApprovalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * whale-workflow 流程实例状态批量查询客户端（审批结果对账用）。
 *
 * <p>走内网令牌直连 {@code GET /workflow/internal/instance/status}，单次最多 100 个实例；
 * whale-workflow 实例办结后没有主动回调实现，本客户端是对账拉取链路的查询端。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class WorkflowInstanceStatusClient implements WorkflowStatusPort {

    /** 批量状态查询路径 */
    private static final String STATUS_PATH = "/workflow/internal/instance/status";

    /** 批量查询单次上限（与 workflow 侧约束一致） */
    public static final int MAX_BATCH_SIZE = 100;

    /** 实例状态：运行中 */
    public static final int STATUS_RUNNING = 0;

    /** 实例状态：已通过 */
    public static final int STATUS_APPROVED = 1;

    /** 实例状态：已驳回 */
    public static final int STATUS_REJECTED = 2;

    /** 实例状态：已撤销 */
    public static final int STATUS_CANCELLED = 3;

    private final RestClient restClient;

    /**
     * 按审批配置构建客户端。
     *
     * @param properties 审批配置
     */
    public WorkflowInstanceStatusClient(CrmOrderApprovalProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(properties.getWorkflowUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 批量查询流程实例状态摘要。
     *
     * <p>调用失败（workflow 不可用、响应异常）返回空映射，由调用方按无办结处理，
     * 保证对账任务不因 workflow 抖动而中断。</p>
     *
     * @param instanceIds 实例 ID 列表（最多 {@value #MAX_BATCH_SIZE} 个，超出部分截断）
     * @return 实例 ID → 状态码（0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销）
     */
    public Map<Long, Integer> batchStatus(List<Long> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = instanceIds.size() > MAX_BATCH_SIZE
                ? instanceIds.subList(0, MAX_BATCH_SIZE) : instanceIds;
        Result<List<InstanceStatusItem>> result;
        try {
            result = restClient.get()
                    .uri(builder -> builder.path(STATUS_PATH)
                            .queryParam("instanceIds", ids.toArray()).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<List<InstanceStatusItem>>>() {
                    });
        } catch (RestClientException exception) {
            log.warn("workflow 实例状态批量查询失败 instanceIds={}", ids, exception);
            return Map.of();
        }
        if (result == null || result.getData() == null) {
            return Map.of();
        }
        Map<Long, Integer> statusMap = new HashMap<>();
        for (InstanceStatusItem item : result.getData()) {
            if (item.id() != null && item.status() != null) {
                statusMap.put(item.id(), item.status());
            }
        }
        return statusMap;
    }

    /**
     * workflow 实例状态摘要（仅取对账所需字段）。
     *
     * @param id 流程实例 ID
     * @param businessKey 业务键
     * @param status 状态码
     */
    record InstanceStatusItem(Long id, String businessKey, Integer status) {
    }
}
