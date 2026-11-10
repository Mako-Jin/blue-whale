package com.bluewhale.oa.document.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.document.domain.gateway.DocumentFileGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * 文件服务网关实现：读取 whale-file 内网文件内容。
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentFileGatewayImpl implements DocumentFileGateway {

    /** 文件服务名 */
    private static final String SERVICE_NAME = "whale-file";

    /** 租户请求头 */
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    private final DiscoveryClient discoveryClient;

    @Override
    public byte[] content(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw OaErrorCode.exception(OaErrorCode.DOC_FILE_UNAVAILABLE);
        }
        try {
            byte[] content = InternalTokenSupport.restClient()
                    .get()
                    .uri(baseUrl() + "/file/internal/" + fileId + "/content")
                    .headers(headers -> addTenantHeader(headers))
                    .retrieve()
                    .body(byte[].class);
            if (content == null) {
                throw OaErrorCode.exception(OaErrorCode.DOC_FILE_UNAVAILABLE);
            }
            return content;
        } catch (RestClientResponseException | ResourceAccessException | IllegalStateException ex) {
            log.warn("公文附件读取失败: fileId={}, error={}", fileId, ex.getMessage());
            throw OaErrorCode.exception(OaErrorCode.DOC_FILE_UNAVAILABLE);
        }
    }

    /**
     * 有租户上下文时补 {@code X-Tenant-Id} 头，避免 whale-file 侧租户过滤被跳过。
     *
     * @param headers 请求头构建器
     */
    private static void addTenantHeader(HttpHeaders headers) {
        Long tenantId = TenantContext.get();
        if (tenantId != null) {
            headers.set(HEADER_TENANT_ID, String.valueOf(tenantId));
        }
    }

    /**
     * 解析文件服务的基础地址，服务无可用实例时抛出异常。
     *
     * @return 文件服务基础地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw new IllegalStateException("文件服务不可用：" + SERVICE_NAME);
        }
        return instances.getFirst().getUri().toString();
    }
}
