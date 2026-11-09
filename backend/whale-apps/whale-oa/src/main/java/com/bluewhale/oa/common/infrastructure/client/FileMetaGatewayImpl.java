package com.bluewhale.oa.common.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.domain.gateway.FileMetaGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文件服务元数据网关实现：调用 whale-file 内网接口 {@code GET /file/internal/{id}}。
 *
 * <p>服务不可达或业务失败时按文件服务不可用处理，供附件登记链路快速失败。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileMetaGatewayImpl implements FileMetaGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 文件服务名 */
    private static final String SERVICE_NAME = "whale-file";

    private final DiscoveryClient discoveryClient;

    @Override
    public Optional<FileMeta> findFile(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return Optional.empty();
        }
        Map<?, ?> response;
        try {
            response = InternalTokenSupport.restClient()
                    .get()
                    .uri(baseUrl() + "/file/internal/" + fileId)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            log.warn("文件元数据查询失败: fileId={}, error={}", fileId, ex.getMessage());
            throw OaErrorCode.exception(OaErrorCode.FILE_REF_UNAVAILABLE);
        } catch (ResourceAccessException | IllegalStateException ex) {
            log.warn("文件元数据查询失败: fileId={}, error={}", fileId, ex.getMessage());
            throw OaErrorCode.exception(OaErrorCode.FILE_REF_UNAVAILABLE);
        }
        if (response == null) {
            return Optional.empty();
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw OaErrorCode.exception(OaErrorCode.FILE_REF_UNAVAILABLE);
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> row)) {
            return Optional.empty();
        }
        return Optional.of(new FileMeta(
                fileId,
                text(row.get("originalName")),
                parseLong(row.get("fileSize")),
                text(row.get("contentType")),
                parseNullableLong(row.get("uploaderId")),
                (int) parseLong(row.get("deleted")),
                (int) parseLong(row.get("status"))));
    }

    /**
     * 解析文件服务实例地址。
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
