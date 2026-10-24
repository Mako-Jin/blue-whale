package com.bluewhale.archive.client;

import com.bluewhale.archive.config.ArchiveProperties;
import com.bluewhale.archive.remote.RemoteFileMeta;
import com.bluewhale.archive.support.RemoteHttpSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 文件服务内网客户端。
 *
 * <p>提供快照上传、元数据读取与归档锁定（{@code status=2}）；服务间直连、不经网关。</p>
 *
 * @author BlueWhale
 */
@Component
public class FileArchiveClient {

    /** 内网上传路径 */
    private static final String UPLOAD_PATH = "/file/internal/upload";

    /** 内网元数据路径模板 */
    private static final String DETAIL_PATH = "/file/internal/{id}";

    /** 内网锁定路径模板 */
    private static final String LOCK_PATH = "/file/internal/{id}/lock";

    /** multipart 文件字段名 */
    private static final String PARAM_FILE = "file";

    /** multipart 桶字段名 */
    private static final String PARAM_BUCKET = "bucket";

    /** 上传动作描述 */
    private static final String ACTION_UPLOAD = "上传档案快照";

    /** 读取文件元数据动作描述 */
    private static final String ACTION_DETAIL = "读取文件元数据";

    /** 锁定文件动作描述 */
    private static final String ACTION_LOCK = "锁定档案文件";

    /** 响应数据中的文件ID字段 */
    private static final String FIELD_ID = "id";

    private final RestClient restClient;

    private final ObjectMapper objectMapper;

    /**
     * 以集成配置构建客户端。
     *
     * @param properties 档案服务配置
     * @param objectMapper JSON 映射器
     */
    public FileArchiveClient(ArchiveProperties properties, ObjectMapper objectMapper) {
        this.restClient = RemoteHttpSupport.build(properties.getIntegration().getFileUrl(),
                properties.getIntegration());
        this.objectMapper = objectMapper;
    }

    /**
     * 上传快照文件到文件服务内网接口。
     *
     * @param content 文件字节内容
     * @param fileName 文件名
     * @param bucket 目标桶
     * @return 文件ID
     */
    public Long upload(byte[] content, String fileName, String bucket) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(PARAM_FILE, new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        body.add(PARAM_BUCKET, bucket);
        Map<String, Object> response = RemoteHttpSupport.call(
                () -> restClient.post()
                        .uri(UPLOAD_PATH)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(body)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {
                        }),
                ACTION_UPLOAD);
        Object data = RemoteHttpSupport.requireData(response, ACTION_UPLOAD);
        Map<?, ?> dataMap = data instanceof Map<?, ?> map ? map : Map.of();
        Object id = dataMap.get(FIELD_ID);
        if (!(id instanceof Number number)) {
            throw RemoteHttpSupport.failure(ACTION_UPLOAD, "响应缺少文件ID");
        }
        return number.longValue();
    }

    /**
     * 读取文件元数据。
     *
     * @param id 文件ID
     * @return 文件元数据；文件不存在时返回 {@code null}
     */
    public RemoteFileMeta detail(Long id) {
        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri(DETAIL_PATH, id)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            throw RemoteHttpSupport.failure(ACTION_DETAIL, e.getMessage());
        }
        Object data = RemoteHttpSupport.requireData(response, ACTION_DETAIL);
        return RemoteHttpSupport.decode(objectMapper, data, RemoteFileMeta.class, ACTION_DETAIL);
    }

    /**
     * 锁定文件（归档不可变）。
     *
     * @param id 文件ID
     */
    public void lock(Long id) {
        Map<String, Object> response = RemoteHttpSupport.call(
                () -> restClient.post()
                        .uri(LOCK_PATH, id)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {
                        }),
                ACTION_LOCK);
        RemoteHttpSupport.requireData(response, ACTION_LOCK);
    }
}
