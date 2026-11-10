package com.bluewhale.crm.analytics.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.crm.analytics.domain.port.AnalyticsFilePort;
import com.bluewhale.crm.config.CrmFileProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * whale-file 内网文件端口实现（分析导出场景）。
 *
 * <p>上传走 {@code POST /file/internal/upload}（multipart：{@code file} + {@code bucket}），
 * 显式携带 {@code X-Tenant-Id}（缺失按默认租户 1 兜底，保证 objectKey 租户前缀正确）
 * 与 {@code X-User-Id}（导出文件归属发起人）；下载走 {@code GET /file/internal/{id}/content}。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class AnalyticsFilePortImpl implements AnalyticsFilePort {

    /** 内网上传路径 */
    private static final String UPLOAD_PATH = "/file/internal/upload";

    /** 内网内容路径 */
    private static final String CONTENT_PATH = "/file/internal/{id}/content";

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 默认租户 */
    private static final long DEFAULT_TENANT_ID = 1L;

    private final CrmFileProperties properties;

    private final RestClient restClient;

    /**
     * 构造客户端。
     *
     * @param properties 文件服务配置
     */
    public AnalyticsFilePortImpl(CrmFileProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public Long upload(byte[] content, String fileName, Long userId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        body.add("bucket", properties.getBucket());
        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(UPLOAD_PATH)
                    .header(WebHeaders.TENANT_ID, tenantHeader())
                    .header(WebHeaders.USER_ID, String.valueOf(userId))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("分析导出上传文件失败: fileName={}, error={}", fileName, ex.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "上传导出文件失败: " + ex.getMessage());
        }
        Object fileId = data(response).get("id");
        if (fileId instanceof Number number) {
            return number.longValue();
        }
        if (fileId instanceof String string && !string.isBlank()) {
            return Long.parseLong(string.trim());
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "文件服务响应缺少文件ID");
    }

    @Override
    public byte[] download(Long fileId) {
        try {
            return restClient.get()
                    .uri(CONTENT_PATH, fileId)
                    .header(WebHeaders.TENANT_ID, tenantHeader())
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception ex) {
            log.warn("分析导出下载文件失败: fileId={}, error={}", fileId, ex.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "读取导出文件失败: " + ex.getMessage());
        }
    }

    /**
     * 提取响应 data 字段。
     *
     * @param response 响应体
     * @return data 映射（缺失返回空映射）
     */
    private static Map<String, Object> data(Map<String, Object> response) {
        if (response == null || !(response.get("code") instanceof Number code)
                || code.intValue() != CODE_SUCCESS
                || !(response.get("data") instanceof Map)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件服务响应异常");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        return data;
    }

    /**
     * 解析租户头，缺失按默认租户兜底（与 CRM 其它内网客户端一致）。
     *
     * @return 租户头值
     */
    private static String tenantHeader() {
        Long tenantId = TenantContext.get();
        return String.valueOf(tenantId == null ? DEFAULT_TENANT_ID : tenantId);
    }
}
