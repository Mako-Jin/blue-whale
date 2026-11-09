package com.bluewhale.crm.analytics.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.crm.analytics.domain.port.AnalyticsExportFilePort;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmAnalyticsProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 分析导出文件的 whale-file 上传适配器。
 *
 * <p>走内网接口 {@code POST {fileServiceUrl}/file/internal/upload}（multipart/form-data），
 * 不经网关。上传时带 {@code X-User-Id} 与 {@code X-Tenant-Id}：whale-file 按用户头记录文件归属、
 * 按租户头把对象落在 {@code tenants/{tenantId}/...} 前缀下，下载时依此做归属与租户可见性校验；
 * 缺租户头会落到未知租户目录，导出人自己也下载不到。</p>
 *
 * <p>所有失败统一抛 {@link BizException}({@link CrmErrorCode#EXPORT_TASK_FAILED})，
 * 由应用层把导出任务置为失败并保留原因，不让异常静默吞掉。</p>
 *
 * @author BlueWhale
 */
@Component
public class AnalyticsExportFileClient implements AnalyticsExportFilePort {

    /** 内网上传路径 */
    private static final String UPLOAD_PATH = "/file/internal/upload";

    /** multipart 文件字段 */
    private static final String FORM_FILE_FIELD = "file";

    /** multipart 桶字段 */
    private static final String FORM_BUCKET_FIELD = "bucket";

    /** 业务成功码 */
    private static final int SUCCESS_CODE = 0;

    private final CrmAnalyticsProperties properties;

    private final RestClient restClient;

    /**
     * 构造导出文件上传客户端。
     *
     * @param properties        分析配置（提供 whale-file 地址与桶）
     * @param restClientBuilder RestClient 构建器（Spring Boot 自动配置）
     */
    public AnalyticsExportFileClient(CrmAnalyticsProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.requestInterceptor(InternalTokenSupport.interceptor()).build();
    }

    @Override
    public Long upload(String fileName, byte[] content, Long uploaderId, Long tenantId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(FORM_FILE_FIELD, new NamedByteArrayResource(content, fileName));
        body.add(FORM_BUCKET_FIELD, properties.getFileBucket());
        try {
            Result<CrmFileUploadVO> result = restClient.post()
                    .uri(properties.getFileServiceUrl() + UPLOAD_PATH)
                    .headers(headers -> {
                        if (uploaderId != null) {
                            headers.set(WebHeaders.USER_ID, String.valueOf(uploaderId));
                        }
                        if (tenantId != null) {
                            headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId));
                        }
                    })
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(new UploadResultType());
            return extract(result);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(CrmErrorCode.EXPORT_TASK_FAILED, "导出文件上传失败：" + describe(e));
        }
    }

    /**
     * 校验并提取文件 ID。
     *
     * @param result 上传响应
     * @return 文件 ID
     */
    private static Long extract(Result<CrmFileUploadVO> result) {
        if (result == null) {
            throw new BizException(CrmErrorCode.EXPORT_TASK_FAILED, "导出文件上传失败：响应体为空");
        }
        if (result.getCode() != SUCCESS_CODE || result.getData() == null || result.getData().getId() == null) {
            throw new BizException(CrmErrorCode.EXPORT_TASK_FAILED, "导出文件上传失败：" + result.getMessage());
        }
        return result.getData().getId();
    }

    /**
     * 生成异常摘要文本。
     *
     * @param e 异常
     * @return 异常摘要
     */
    private static String describe(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /**
     * 携带原始文件名的字节资源：multipart 文件部分需要它提供上传文件名。
     *
     * @author BlueWhale
     */
    private static final class NamedByteArrayResource extends ByteArrayResource {

        /** 上传文件名 */
        private final String filename;

        /**
         * 构造资源。
         *
         * @param content  文件内容
         * @param filename 上传文件名
         */
        private NamedByteArrayResource(byte[] content, String filename) {
            super(content);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    /**
     * 上传响应泛型类型引用（{@code Result<CrmFileUploadVO>}）。
     *
     * @author BlueWhale
     */
    private static final class UploadResultType extends ParameterizedTypeReference<Result<CrmFileUploadVO>> {
    }
}
