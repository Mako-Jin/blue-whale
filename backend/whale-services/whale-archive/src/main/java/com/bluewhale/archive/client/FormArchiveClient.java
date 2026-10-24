package com.bluewhale.archive.client;

import com.bluewhale.archive.config.ArchiveProperties;
import com.bluewhale.archive.remote.RemoteFormArchive;
import com.bluewhale.archive.support.RemoteHttpSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 表单服务内网客户端。
 *
 * <p>调用 {@code /form/internal/data/{id}} 读取表单值与字段定义；按配置租户传递
 * {@code X-Tenant-Id}；数据不存在（404）时返回 {@code null}，由档案侧按「无表单」降级。</p>
 *
 * @author BlueWhale
 */
@Component
public class FormArchiveClient {

    /** 表单归档详情路径模板 */
    private static final String DETAIL_PATH = "/form/internal/data/{id}";

    /** 租户请求头 */
    private static final String HEADER_TENANT = "X-Tenant-Id";

    /** 读取表单归档详情动作描述 */
    private static final String ACTION_DETAIL = "读取表单归档详情";

    private final RestClient restClient;

    private final ObjectMapper objectMapper;

    private final long tenantId;

    /**
     * 以集成配置构建客户端。
     *
     * @param properties 档案服务配置
     * @param objectMapper JSON 映射器
     */
    public FormArchiveClient(ArchiveProperties properties, ObjectMapper objectMapper) {
        this.restClient = RemoteHttpSupport.build(properties.getIntegration().getFormUrl(),
                properties.getIntegration());
        this.objectMapper = objectMapper;
        this.tenantId = properties.getIntegration().getTenantId();
    }

    /**
     * 读取表单归档详情。
     *
     * @param dataId 表单数据ID
     * @return 归档详情；表单数据不存在时返回 {@code null}
     */
    public RemoteFormArchive detail(Long dataId) {
        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri(DETAIL_PATH, dataId)
                    .header(HEADER_TENANT, String.valueOf(tenantId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            throw RemoteHttpSupport.failure(ACTION_DETAIL, e.getMessage());
        }
        Object data = RemoteHttpSupport.requireData(response, ACTION_DETAIL);
        return RemoteHttpSupport.decode(objectMapper, data, RemoteFormArchive.class, ACTION_DETAIL);
    }
}
