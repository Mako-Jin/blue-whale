package com.bluewhale.auth.infrastructure.importexport;

import com.bluewhale.auth.application.port.ExportFileUploader;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link ExportFileUploader} 的 whale-file 实现（权限中心扩展 M4）。
 *
 * <p>沿用审计归档上传模式（{@code POST /file/v1/internal/upload}，multipart，
 * 服务间直连不经网关、无 JWT / 权限码头要求）；返回 {@code data} 与 whale-file 的
 * {@code FileUploadVO} 对齐（含 {@code objectKey}/{@code bucket}/{@code fileSize} 等）。</p>
 *
 * @author BlueWhale
 */
@Component
public class WhaleFileUploadClient implements ExportFileUploader {

    /** 上传接口路径 */
    private static final String UPLOAD_PATH = "/file/v1/internal/upload";

    /** multipart 文件字段名 */
    private static final String PARAM_FILE = "file";

    /** multipart 桶字段名 */
    private static final String PARAM_BUCKET = "bucket";

    /** 统一响应体状态码字段 */
    private static final String FIELD_CODE = "code";

    /** 统一响应体消息字段 */
    private static final String FIELD_MESSAGE = "message";

    /** 统一响应体数据字段 */
    private static final String FIELD_DATA = "data";

    /** whale-file 服务调用客户端（基址取配置 {@code whale.auth.export.file-service-url}） */
    private final RestClient restClient;

    /** 导出文件目标桶 */
    private final String bucket;

    /**
     * 以导出配置构建上传客户端
     *
     * @param properties 用户导出配置
     */
    public WhaleFileUploadClient(UserExportProperties properties) {
        this.restClient = InternalTokenSupport.builder().baseUrl(properties.getFileServiceUrl()).build();
        this.bucket = properties.getBucket();
    }

    @Override
    public Map<String, Object> upload(byte[] content, String fileName) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(PARAM_FILE, new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        body.add(PARAM_BUCKET, bucket);
        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(UPLOAD_PATH)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "auth.import.upload-failed",
                    "上传用户导出文件失败: " + e.getMessage(), e.getMessage());
        }
        if (response == null || !isSuccess(response.get(FIELD_CODE))) {
            String message = response == null ? "空响应" : String.valueOf(response.get(FIELD_MESSAGE));
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "auth.import.upload-failed",
                    "上传用户导出文件失败: " + message, message);
        }
        return toDataMap(response.get(FIELD_DATA));
    }

    /**
     * 判定统一响应体状态码是否为成功
     *
     * @param code 响应体 {@code code} 字段值
     * @return 状态码为 {@code 0} 时返回 {@code true}，否则 {@code false}
     */
    private static boolean isSuccess(Object code) {
        return code instanceof Number number && number.intValue() == ErrorCode.SUCCESS.getCode();
    }

    /**
     * 将响应 {@code data} 对象规整为字符串键的 Map
     *
     * @param data 响应体 {@code data} 字段值
     * @return 键为字符串的 Map；{@code data} 非 Map 时返回空 Map
     */
    private static Map<String, Object> toDataMap(Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (data instanceof Map<?, ?> dataMap) {
            dataMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        }
        return result;
    }
}
