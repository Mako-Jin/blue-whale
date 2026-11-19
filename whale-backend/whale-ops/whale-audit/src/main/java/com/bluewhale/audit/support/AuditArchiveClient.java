package com.bluewhale.audit.support;

import com.bluewhale.audit.config.AuditExportProperties;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计归档文件上传客户端（AUTH-08）。
 *
 * <p>以 Spring 6 {@link RestClient} 直接调用 whale-file 内网接口
 * {@code POST /file/v1/internal/upload}（{@code multipart/form-data}，字段 {@code file} 与 {@code bucket}），
 * 服务间直连不经网关、无 JWT / 权限码头要求；网关已对该路径做 403 屏蔽，外部不可达。</p>
 *
 * <p>返回 {@code data} 字段与 whale-file 的 {@code FileUploadVO} 对齐：
 * {@code id / fileName / originalName / bucket / objectKey / fileSize / contentType / md5}。
 * 本模块不重复定义 {@code FileUploadVO}，直接以 {@code Map<String, Object>} 承载，避免跨模块类型重复。</p>
 *
 * <p>边界：网络异常与 {@code code != 0} 均被捕获并转换为受控业务异常（{@link BizException}），
 * 绝不向调度线程抛出原始 {@code RestClientException}；上传失败语义由调用方（导出接口 / 归档任务）决定。</p>
 *
 * @author BlueWhale
 */
@Component
public class AuditArchiveClient {

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

    /** 连接超时：whale-file 不可达时快速失败，避免归档线程长时间挂起 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** 读超时：单次上传响应等待上限 */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    /** whale-file 服务调用客户端（基址取配置 {@code whale.audit.export.file-service-url}） */
    private final RestClient restClient;

    /**
     * 以导出配置构建上传客户端（JDK HttpClient 实现，显式设置连接/读超时）
     *
     * @param properties 导出/归档配置（提供 whale-file 基址）
     */
    public AuditArchiveClient(AuditExportProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(properties.getFileServiceUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 上传归档 CSV 到 whale-file 内网接口
     *
     * @param content CSV 字节内容
     * @param fileName 上传文件名（{@code auditLog-archive-{yyyyMMddHHmmss}.csv}）
     * @param bucket 目标桶名称
     * @return 上传结果 {@code data}（字段与 whale-file 的 {@code FileUploadVO} 对齐，含 {@code objectKey}）
     * @throws BizException 网络异常或响应 {@code code != 0} 时抛出（受控失败，不泄露原始网络异常）
     */
    public Map<String, Object> upload(byte[] content, String fileName, String bucket) {
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
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "上传审计归档文件失败: " + e.getMessage());
        }
        if (response == null || !isSuccess(response.get(FIELD_CODE))) {
            String message = response == null ? "空响应" : String.valueOf(response.get(FIELD_MESSAGE));
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "上传审计归档文件失败: " + message);
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
