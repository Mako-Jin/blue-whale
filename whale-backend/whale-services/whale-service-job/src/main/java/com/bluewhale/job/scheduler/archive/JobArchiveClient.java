package com.bluewhale.job.scheduler.archive;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.job.config.JobProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 归档文件上传客户端。
 *
 * <p>向 whale-file 内网上传接口 {@code POST {fileServiceUrl}/file/v1/internal/upload}
 * 以 {@code multipart/form-data} 投递归档文件；该接口不经网关、无鉴权码头，
 * 由服务间内网直连（网关侧对 {@code /file/v1/internal/**} 已做 403 屏蔽）。
 * 使用 Spring {@link RestClient}，不引入新依赖。</p>
 *
 * <p>所有失败（网络异常、响应体缺失、业务码非 0）统一转为 {@link BizException}，
 * 由调用方（归档任务）捕获，绝不外抛打断调度线程。</p>
 *
 * @author BlueWhale
 */
@Component
public class JobArchiveClient {

    /** 归档上传路径（whale-file 内网服务间接口） */
    private static final String UPLOAD_PATH = "/file/v1/internal/upload";

    /** multipart 表单文件字段名 */
    private static final String FORM_FILE_FIELD = "file";

    /** multipart 表单桶字段名 */
    private static final String FORM_BUCKET_FIELD = "bucket";

    /** 默认对象存储桶 */
    private static final String DEFAULT_BUCKET = "bluewhale";

    /** 业务成功码 */
    private static final int SUCCESS_CODE = 0;

    private final JobProperties jobProperties;

    private final RestClient restClient;

    /**
     * 构造归档上传客户端
     *
     * @param jobProperties 任务服务配置（提供 whale-file 地址）
     * @param restClientBuilder RestClient 构建器（由 Spring Boot 自动配置提供）
     */
    public JobArchiveClient(JobProperties jobProperties, RestClient.Builder restClientBuilder) {
        this.jobProperties = jobProperties;
        this.restClient = restClientBuilder.requestInterceptor(InternalTokenSupport.interceptor()).build();
    }

    /**
     * 上传归档文件至 whale-file 内网接口
     *
     * @param fileName 归档文件名（作为上传文件的原始名）
     * @param content 归档文件内容字节
     * @return 上传结果视图（含 bucket 与 objectKey）
     * @throws BizException 网络异常、响应体缺失或业务码非 0 时抛出
     */
    public FileUploadVO upload(String fileName, byte[] content) {
        String url = jobProperties.getFileServiceUrl() + UPLOAD_PATH;
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(FORM_FILE_FIELD, new NamedByteArrayResource(content, fileName));
        body.add(FORM_BUCKET_FIELD, DEFAULT_BUCKET);
        try {
            Result<FileUploadVO> result = restClient.post()
                    .uri(url)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(new ArchiveResultType());
            return extract(result);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("归档上传失败：" + describe(e));
        }
    }

    /**
     * 校验并提取响应数据
     *
     * @param result 上传响应
     * @return 上传结果视图
     * @throws BizException 响应体缺失或业务码非 0 时抛出
     */
    private static FileUploadVO extract(Result<FileUploadVO> result) {
        if (result == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "job.archive.upload-empty-body", "归档上传失败：响应体为空");
        }
        if (result.getCode() != SUCCESS_CODE || result.getData() == null) {
            throw new BizException("归档上传失败：" + result.getMessage());
        }
        return result.getData();
    }

    /**
     * 生成异常摘要文本
     *
     * @param e 异常
     * @return 异常摘要（优先消息，缺失时取简单类名）
     */
    private static String describe(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /**
     * 携带原始文件名的字节数组资源：作为 multipart 文件部分时提供上传文件名。
     *
     * @author BlueWhale
     */
    private static final class NamedByteArrayResource extends ByteArrayResource {

        /** 上传文件名 */
        private final String filename;

        /**
         * 构造带文件名的字节资源
         *
         * @param content 文件内容
         * @param filename 文件名
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
     * 归档上传响应泛型类型引用（{@code Result<FileUploadVO>}），用于反序列化泛型响应。
     *
     * @author BlueWhale
     */
    private static final class ArchiveResultType extends ParameterizedTypeReference<Result<FileUploadVO>> {
    }
}
