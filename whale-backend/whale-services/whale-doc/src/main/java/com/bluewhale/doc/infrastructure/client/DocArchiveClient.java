package com.bluewhale.doc.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.doc.config.DocProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * whale-archive 内网客户端（M3 页面归档快照）。
 *
 * <p>只调用 {@code POST /archive/internal/items}；档案服务按 {@code (sourceType, sourceId)}
 * 幂等，重复推送返回既有条目。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class DocArchiveClient {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    private final DiscoveryClient discoveryClient;

    private final DocProperties docProperties;

    /**
     * 推送页面快照入档。
     *
     * @param tenantId 租户ID
     * @param payload  归档请求体
     * @return 入档结果
     */
    public IngestOutcome ingest(Long tenantId, Map<String, Object> payload) {
        Map<?, ?> response = InternalTokenSupport.restClient()
                .post()
                .uri(baseUrl() + "/archive/internal/items")
                .header(WebHeaders.TENANT_ID, String.valueOf(tenantId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "doc.archive.response-empty", "档案服务响应为空");
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "doc.archive.remote-failed",
                    "档案服务返回失败: " + response.get("message"), response.get("message"));
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> map)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "doc.archive.response-format-invalid", "档案服务返回数据格式异常");
        }
        return new IngestOutcome(value(map.get("itemId")), value(map.get("snapshotFileId")),
                Boolean.parseBoolean(String.valueOf(map.get("created"))));
    }

    /**
     * 解析档案服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(docProperties.getArchiveServiceName());
        if (instances.isEmpty()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "doc.archive.service-unavailable", "档案服务不可用");
        }
        return instances.get(0).getUri().toString();
    }

    /**
     * 空值安全的字符串转换。
     *
     * @param value 原始值
     * @return 字符串；null 返回 {@code null}
     */
    private String value(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 归档结果。
     *
     * @param itemId         档案条目ID
     * @param snapshotFileId 快照文件ID
     * @param created        是否本次新建
     */
    public record IngestOutcome(String itemId, String snapshotFileId, boolean created) {
    }
}
