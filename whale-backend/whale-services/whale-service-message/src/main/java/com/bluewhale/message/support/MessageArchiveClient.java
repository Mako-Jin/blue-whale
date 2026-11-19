package com.bluewhale.message.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.message.config.MessageArchiveProperties;
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
 * 消息归档文件上传客户端。
 *
 * <p>调用 whale-file 内网接口 {@code POST /file/v1/internal/upload}（multipart，字段
 * {@code file} 与 {@code bucket}）。网关已对内网路径返回 403，外部不可达。</p>
 *
 * <p>网络异常与 {@code code != 0} 统一转换为 {@link BizException}，由归档任务按批次失败处理。</p>
 *
 * @author BlueWhale
 */
@Component
public class MessageArchiveClient {

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

    /** 连接超时 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** 读超时 */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private final RestClient restClient;

    /**
     * 按归档配置构建上传客户端。
     *
     * @param properties 归档配置（提供 whale-file 基址）
     */
    public MessageArchiveClient(MessageArchiveProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(properties.getFileServiceUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 上传归档文件。
     *
     * @param content  文件内容
     * @param fileName 文件名
     * @param bucket   目标桶
     * @return 响应 {@code data}（含 {@code objectKey}）
     * @throws BizException 网络异常或响应 {@code code != 0} 时抛出
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
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "上传消息归档文件失败: " + e.getMessage());
        }
        if (response == null || !isSuccess(response.get(FIELD_CODE))) {
            String message = response == null ? "空响应" : String.valueOf(response.get(FIELD_MESSAGE));
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "上传消息归档文件失败: " + message);
        }
        return toDataMap(response.get(FIELD_DATA));
    }

    /**
     * 判定统一响应体状态码是否成功。
     *
     * @param code 响应体 {@code code}
     * @return 成功返回 {@code true}
     */
    private static boolean isSuccess(Object code) {
        return code instanceof Number number && number.intValue() == ErrorCode.SUCCESS.getCode();
    }

    /**
     * 把响应 {@code data} 规整为字符串键 Map。
     *
     * @param data 响应体 {@code data}
     * @return 字符串键 Map
     */
    private static Map<String, Object> toDataMap(Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (data instanceof Map<?, ?> dataMap) {
            dataMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        }
        return result;
    }
}
