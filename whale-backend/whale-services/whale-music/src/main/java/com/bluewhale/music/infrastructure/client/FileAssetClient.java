package com.bluewhale.music.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.music.config.MusicProperties;
import com.bluewhale.music.domain.model.FileAsset;
import com.bluewhale.music.domain.repository.FileAssetPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文件资产元数据客户端（M5-2 上传白名单校验）。
 *
 * <p>调用 {@code GET /file/v1/internal/{id}} 读取 MIME 类型、字节数与可用状态；
 * 文件不存在返回空，其他失败按系统异常 fail-closed（无法校验即拒绝入库）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileAssetClient implements FileAssetPort {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 文件状态：正常 */
    private static final int STATUS_NORMAL = 1;

    private final DiscoveryClient discoveryClient;

    private final MusicProperties musicProperties;

    @Override
    public Optional<FileAsset> find(Long fileId, Long tenantId) {
        if (fileId == null || fileId <= 0) {
            return Optional.empty();
        }
        Map<?, ?> response;
        try {
            response = InternalTokenSupport.restClient()
                    .get()
                    .uri(baseUrl() + "/file/v1/internal/" + fileId)
                    .headers(headers -> {
                        if (tenantId != null) {
                            headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId));
                        }
                    })
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件校验服务不可用，请稍后重试");
        } catch (Exception ex) {
            log.warn("[MUSIC] 文件元数据读取失败: fileId={}, error={}", fileId, ex.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件校验服务不可用，请稍后重试");
        }
        if (response == null) {
            return Optional.empty();
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            return Optional.empty();
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> row)) {
            return Optional.empty();
        }
        long sizeBytes = parseLong(row.get("fileSize"));
        int status = (int) parseLong(row.get("status"));
        int deleted = (int) parseLong(row.get("deleted"));
        boolean usable = status == STATUS_NORMAL && deleted == 0;
        return Optional.of(new FileAsset(fileId, text(row.get("contentType")), sizeBytes,
                parseNullableLong(row.get("uploaderId")), usable));
    }

    /**
     * 解析文件服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(musicProperties.getFileServiceName());
        if (instances.isEmpty()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件校验服务不可用，请稍后重试");
        }
        return instances.get(0).getUri().toString();
    }

    /**
     * 解析 JSON 文本字段。
     *
     * @param value 原始值
     * @return 文本，缺失返回 {@code null}
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
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
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
        if (value == null) {
            return null;
        }
        return parseLong(value);
    }
}
