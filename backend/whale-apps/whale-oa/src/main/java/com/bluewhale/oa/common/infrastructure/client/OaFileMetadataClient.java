package com.bluewhale.oa.common.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.domain.gateway.FileMetadataGateway;
import com.bluewhale.oa.common.domain.model.FileMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OA 共享的 whale-file 内网元数据客户端。
 *
 * <p>读取 {@code GET /file/internal/{id}} 元数据，供附件归属校验使用；
 * 请求显式携带 {@code X-Tenant-Id}，避免 whale-file 侧因租户上下文缺失而跳过租户过滤。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OaFileMetadataClient implements FileMetadataGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 文件服务名 */
    private static final String SERVICE_NAME = "whale-file";

    /** 租户请求头 */
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    private final DiscoveryClient discoveryClient;

    /**
     * 查询文件元数据；文件不存在（含 404）时返回空。
     *
     * @param fileId 文件ID
     * @return 元数据；缺失返回空
     */
    @Override
    public Optional<FileMetadata> findFile(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return Optional.empty();
        }
        Map<?, ?> response;
        try {
            var request = InternalTokenSupport.restClient()
                    .get()
                    .uri(baseUrl() + "/file/internal/" + fileId);
            Long tenantId = TenantContext.get();
            if (tenantId != null) {
                request = request.header(HEADER_TENANT_ID, String.valueOf(tenantId));
            }
            response = request.retrieve().body(Map.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            log.warn("附件元数据查询失败: fileId={}, error={}", fileId, ex.getMessage());
            throw OaErrorCode.exception(OaErrorCode.ATTACHMENT_UNAVAILABLE);
        } catch (ResourceAccessException | IllegalStateException ex) {
            log.warn("附件元数据查询失败: fileId={}, error={}", fileId, ex.getMessage());
            throw OaErrorCode.exception(OaErrorCode.ATTACHMENT_UNAVAILABLE);
        }
        if (response == null) {
            return Optional.empty();
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw OaErrorCode.exception(OaErrorCode.ATTACHMENT_UNAVAILABLE);
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> row)) {
            return Optional.empty();
        }
        return Optional.of(new FileMetadata(
                fileId,
                text(row.get("originalName")),
                parseNullableLong(row.get("uploaderId")),
                (int) parseLong(row.get("deleted")),
                (int) parseLong(row.get("status"))));
    }

    /**
     * 解析文件服务实例地址，无可用实例时抛出异常。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw new IllegalStateException("文件服务不可用：" + SERVICE_NAME);
        }
        return instances.getFirst().getUri().toString();
    }

    /**
     * 解析字符串字段。
     *
     * @param value 原始值
     * @return 文本；缺失返回 {@code null}
     */
    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 解析长整型，缺失或非法返回 {@code 0}。
     *
     * @param value 原始值
     * @return 长整型
     */
    private static long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            try {
                return Long.parseLong(string.trim());
            } catch (NumberFormatException ex) {
                return 0L;
            }
        }
        return 0L;
    }

    /**
     * 解析可空长整型。
     *
     * @param value 原始值
     * @return 长整型或 {@code null}
     */
    private static Long parseNullableLong(Object value) {
        return value == null ? null : parseLong(value);
    }
}
