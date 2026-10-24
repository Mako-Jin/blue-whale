package com.bluewhale.archive.client;

import com.bluewhale.archive.config.ArchiveProperties;
import com.bluewhale.archive.remote.RemoteApprovedPage;
import com.bluewhale.archive.remote.RemoteInstanceDetail;
import com.bluewhale.archive.support.RemoteHttpSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 工作流服务内网客户端。
 *
 * <p>调用 {@code /workflow/internal/instance/**} 拉取审批通过实例与归档详情；
 * 服务间直连、不经网关、无权限码头。</p>
 *
 * @author BlueWhale
 */
@Component
public class WorkflowArchiveClient {

    /** 已通过实例分页路径 */
    private static final String APPROVED_PATH = "/workflow/internal/instance/approved";

    /** 实例归档详情路径模板 */
    private static final String DETAIL_PATH = "/workflow/internal/instance/{id}/detail";

    /** 拉取已通过实例动作描述 */
    private static final String ACTION_APPROVED = "拉取已通过实例";

    /** 拉取实例归档详情动作描述 */
    private static final String ACTION_DETAIL = "拉取实例归档详情";

    private final RestClient restClient;

    private final ObjectMapper objectMapper;

    /**
     * 以集成配置构建客户端。
     *
     * @param properties 档案服务配置
     * @param objectMapper JSON 映射器
     */
    public WorkflowArchiveClient(ArchiveProperties properties, ObjectMapper objectMapper) {
        this.restClient = RemoteHttpSupport.build(properties.getIntegration().getWorkflowUrl(),
                properties.getIntegration());
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询已通过实例。
     *
     * @param updatedAfter 游标下界（不含），可为 {@code null}
     * @param updatedBefore 游标上界（含），可为 {@code null}
     * @param current 页码
     * @param size 每页条数
     * @return 已通过实例分页
     */
    public RemoteApprovedPage approved(LocalDateTime updatedAfter, LocalDateTime updatedBefore,
                                       long current, long size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(APPROVED_PATH)
                .queryParam("current", current)
                .queryParam("size", size);
        if (updatedAfter != null) {
            builder.queryParam("updatedAfter", updatedAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (updatedBefore != null) {
            builder.queryParam("updatedBefore", updatedBefore.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        String uri = builder.build().toUriString();
        Map<String, Object> response = RemoteHttpSupport.call(
                () -> getMap(uri), ACTION_APPROVED);
        Object data = RemoteHttpSupport.requireData(response, ACTION_APPROVED);
        return RemoteHttpSupport.decode(objectMapper, data, RemoteApprovedPage.class, ACTION_APPROVED);
    }

    /**
     * 查询实例归档详情（实例 + 定义快照 + 任务轨迹）。
     *
     * @param id 流程实例ID
     * @return 归档详情
     */
    public RemoteInstanceDetail detail(Long id) {
        Map<String, Object> response = RemoteHttpSupport.call(
                () -> getMap(DETAIL_PATH, id), ACTION_DETAIL);
        Object data = RemoteHttpSupport.requireData(response, ACTION_DETAIL);
        return RemoteHttpSupport.decode(objectMapper, data, RemoteInstanceDetail.class, ACTION_DETAIL);
    }

    /**
     * 发起 GET 请求并读取统一响应体。
     *
     * @param uri 请求路径
     * @param uriVariables 路径变量
     * @return 响应体
     */
    private Map<String, Object> getMap(String uri, Object... uriVariables) {
        return restClient.get()
                .uri(uri, uriVariables)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
    }
}
