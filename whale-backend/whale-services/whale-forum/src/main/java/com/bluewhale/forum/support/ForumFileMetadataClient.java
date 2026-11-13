package com.bluewhale.forum.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.forum.config.ForumProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.Optional;

/**
 * whale-file 内网元数据客户端，用于申诉材料归属校验。
 *
 * <p>读取 {@code GET /file/v1/internal/{id}}：文件字节归 whale-file，论坛只落 {@code fileId}，
 * 但落引用的前提是「文件确实存在、未删除、状态正常，且上传人是当前用户」——
 * 否则用户可以把自己的申诉挂到别人的文件上，复核人点开就等于替攻击者取了一次文件。</p>
 *
 * <p>与 {@link ForumUserDirectoryClient} 同口径：自建带超时的客户端，
 * 但<b>校验失败必须拒绝提交</b>（fail-closed），不能像 @提及那样静默跳过。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class ForumFileMetadataClient {

    /** 文件状态：正常 */
    private static final int FILE_STATUS_NORMAL = 1;

    /** 逻辑删除：未删除 */
    private static final int NOT_DELETED = 0;

    /** 内网元数据路径 */
    private static final String METADATA_PATH = "/file/v1/internal/%d";

    /** 内网内容路径（支持 Range，代理预览只需完整字节） */
    private static final String CONTENT_PATH = "/file/v1/internal/%d/content";

    private final ForumProperties properties;

    /** 测试注入的客户端；为空时按当前内网令牌配置现场创建 */
    private final RestClient client;

    /**
     * 生产用构造。
     *
     * @param properties 论坛配置
     */
    @Autowired
    public ForumFileMetadataClient(ForumProperties properties) {
        this(properties, buildTimeoutClient(properties));
    }

    /**
     * 构造带连接/读超时的内网客户端。
     *
     * @param properties 论坛配置
     * @return 客户端
     */
    private static RestClient buildTimeoutClient(ForumProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getFileConnectTimeoutMillis());
        factory.setReadTimeout(properties.getFileReadTimeoutMillis());
        return InternalTokenSupport.builder().requestFactory(factory).build();
    }

    /**
     * 测试用构造。
     *
     * @param properties 论坛配置
     * @param client 外部客户端
     */
    ForumFileMetadataClient(ForumProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    /**
     * 查询文件元数据；文件不存在（含 404）时返回空。
     *
     * @param fileId 文件ID
     * @return 元数据；缺失返回空
     */
    @SuppressWarnings("unchecked")
    public Optional<FileMeta> find(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return Optional.empty();
        }
        Map<String, Object> response;
        try {
            response = target().get()
                    .uri(properties.getFileBaseUrl() + METADATA_PATH.formatted(fileId))
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(this::applyTenantHeader)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            log.warn("申诉材料元数据查询失败: fileId={}, error={}", fileId, ex.getMessage());
            throw unavailable();
        } catch (ResourceAccessException | IllegalStateException ex) {
            log.warn("申诉材料元数据查询失败: fileId={}, error={}", fileId, ex.getMessage());
            throw unavailable();
        }
        if (response == null) {
            return Optional.empty();
        }
        Object code = response.get("code");
        if (!isSuccess(code)) {
            log.warn("申诉材料元数据返回非成功: fileId={}, code={}", fileId, code);
            throw unavailable();
        }
        if (!(response.get("data") instanceof Map<?, ?> row)) {
            return Optional.empty();
        }
        return Optional.of(new FileMeta(
                fileId,
                text(row.get("originalName")),
                parseNullableLong(row.get("fileSize")),
                text(row.get("contentType")),
                parseNullableLong(row.get("uploaderId")),
                (int) parseLong(row.get("deleted")),
                (int) parseLong(row.get("status"))));
    }

    /**
     * 校验文件可被当前用户引用，通过后返回元数据。
     *
     * @param fileId 文件ID
     * @param userId 当前用户ID
     * @return 元数据
     */
    public FileMeta requireOwned(Long fileId, Long userId) {
        FileMeta meta = find(fileId)
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_ERROR, "申诉材料不存在或已删除"));
        if (meta.deleted() != NOT_DELETED || meta.status() != FILE_STATUS_NORMAL) {
            throw new BizException(ErrorCode.PARAM_ERROR, "申诉材料不存在或已删除");
        }
        if (meta.uploaderId() == null || !meta.uploaderId().equals(userId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "只能上传本人的文件作为申诉材料");
        }
        return meta;
    }

    /**
     * 读取文件字节（内网内容接口）。
     *
     * <p>只供论坛服务代理预览：内网接口不做归属校验，调用方必须先在论坛侧
     * 判完「本人或复核人」，否则等于把任意文件内容开放给登录用户。</p>
     *
     * @param fileId 文件ID
     * @return 字节与 MIME 类型
     */
    public FileContent loadContent(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "申诉材料不存在或已删除");
        }
        try {
            ResponseEntity<byte[]> response = target().get()
                    .uri(properties.getFileBaseUrl() + CONTENT_PATH.formatted(fileId))
                    .accept(MediaType.ALL)
                    .headers(this::applyTenantHeader)
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw new BizException(ErrorCode.NOT_FOUND, "申诉材料不存在或已删除");
            }
            MediaType contentType = response.getHeaders().getContentType();
            return new FileContent(body, contentType == null
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                    : contentType.toString());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new BizException(ErrorCode.NOT_FOUND, "申诉材料不存在或已删除");
            }
            log.warn("申诉材料内容读取失败: fileId={}, error={}", fileId, ex.getMessage());
            throw unavailable();
        } catch (ResourceAccessException | IllegalStateException ex) {
            log.warn("申诉材料内容读取失败: fileId={}, error={}", fileId, ex.getMessage());
            throw unavailable();
        }
    }

    /**
     * 取内网客户端（生产构造下为自建超时客户端）。
     *
     * @return 客户端
     */
    private RestClient target() {
        return client == null ? InternalTokenSupport.restClient() : client;
    }

    /**
     * 补租户头：文件服务按租户隔离元数据与内容。
     *
     * @param headers 请求头
     */
    private void applyTenantHeader(org.springframework.http.HttpHeaders headers) {
        Long tenantId = TenantContext.get();
        if (tenantId != null) {
            headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId));
        }
    }

    /**
     * 构造「文件服务不可用」异常。
     *
     * @return 业务异常
     */
    private static BizException unavailable() {
        return new BizException(ErrorCode.PARAM_ERROR, "申诉材料校验服务暂不可用，请稍后重试");
    }

    /**
     * 判断业务码是否成功（文件服务返回整型 0，部分序列化场景为字符串 "0"）。
     *
     * @param code 业务码
     * @return 是否成功
     */
    private static boolean isSuccess(Object code) {
        if (code instanceof Number number) {
            return number.intValue() == 0;
        }
        return "0".equals(String.valueOf(code));
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

    /**
     * 文件元数据快照。
     *
     * @param fileId 文件ID
     * @param originalName 原始文件名
     * @param fileSize 字节数
     * @param contentType MIME 类型
     * @param uploaderId 上传人
     * @param deleted 逻辑删除标记：0 正常 / 1 删除
     * @param status 文件状态：1 正常
     */
    public record FileMeta(Long fileId,
                           String originalName,
                           Long fileSize,
                           String contentType,
                           Long uploaderId,
                           int deleted,
                           int status) {
    }

    /**
     * 文件内容。
     *
     * @param bytes 字节
     * @param contentType MIME 类型
     */
    public record FileContent(byte[] bytes, String contentType) {
    }
}
