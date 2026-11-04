package com.bluewhale.pdm.document.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;

/**
 * whale-file 内网客户端：图文档文件上传与内容读取
 *
 * <p>只调用网关屏蔽的 {@code /file/internal/**}；文件字节由文件服务保存，
 * PDM 只存文件 ID、文件名、大小与校验和。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdmDocFileClient {

    /** 文件服务名 */
    private static final String SERVICE_NAME = "whale-file";

    /** PDM 文件桶 */
    public static final String BUCKET = "pdm";

    private final DiscoveryClient discoveryClient;

    /**
     * 上传文件字节
     *
     * @param content     文件内容
     * @param filename    文件名
     * @param contentType MIME 类型
     * @param userId      上传人
     * @param tenantId    租户
     * @return 上传结果
     */
    public UploadedFile upload(byte[] content, String filename, String contentType, Long userId, Long tenantId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        body.add("bucket", BUCKET);
        Map<?, ?> response = InternalTokenSupport.restClient()
                .post()
                .uri(baseUrl() + "/file/internal/upload")
                .header("X-User-Id", String.valueOf(userId))
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(Map.class);
        Map<?, ?> data = extractData(response);
        return new UploadedFile(
                toLong(data.get("id")),
                asString(data.get("originalName"), filename),
                asString(data.get("contentType"), contentType),
                toLong(data.get("fileSize")));
    }

    /**
     * 读取文件内容
     *
     * @param fileId 文件 ID
     * @return 文件字节
     */
    public byte[] content(Long fileId) {
        byte[] bytes = InternalTokenSupport.restClient()
                .get()
                .uri(baseUrl() + "/file/internal/{id}/content", fileId)
                .retrieve()
                .body(byte[].class);
        if (bytes == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "图文档文件内容不存在");
        }
        return bytes;
    }

    /**
     * 解包文件服务响应信封
     *
     * @param response 文件服务响应
     * @return data 字段
     */
    private Map<?, ?> extractData(Map<?, ?> response) {
        if (response == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件服务响应为空");
        }
        Object code = response.get("code");
        if (code instanceof Number number && number.intValue() != 0) {
            throw new BizException(number.intValue(), asString(response.get("message"), "文件上传失败"));
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> map)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件服务返回数据格式异常");
        }
        return map;
    }

    /**
     * 解析文件服务实例地址
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件服务不可用");
        }
        return instances.get(0).getUri().toString();
    }

    /**
     * 对象转字符串
     *
     * @param value    原始值
     * @param fallback 默认值
     * @return 字符串
     */
    private String asString(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    /**
     * 对象转长整型
     *
     * @param value 原始值
     * @return 长整型，无法解析时返回 {@code null}
     */
    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 文件上传结果
     *
     * @param fileId       文件 ID
     * @param originalName 原始文件名
     * @param contentType  MIME 类型
     * @param fileSize     字节数
     */
    public record UploadedFile(Long fileId, String originalName, String contentType, Long fileSize) {
    }
}
