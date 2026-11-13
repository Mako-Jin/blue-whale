package com.bluewhale.mail.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.mail.config.MailProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 文件服务内网客户端。
 *
 * <p>附件二进制一律归 {@code whale-file}，本服务只保存 {@code fileId}：
 * 收信时把 IMAP 取到的字节上传换取 fileId，发信时按 fileId 取流组装 multipart。
 * 内网直连、不经网关，因此必须自带内网令牌与租户请求头。</p>
 *
 * <p><b>为什么不做缓存</b>：附件是低频大对象，缓存会引入副本一致性问题和磁盘占用，
 * 而文件服务自身已有存储层缓存与 Range 支持。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class MailFileClient {

    /** 内网 multipart 上传路径 */
    private static final String UPLOAD_PATH = "/file/v1/internal/upload";

    /** 内网内容流路径模板 */
    private static final String CONTENT_URI_TEMPLATE = "/file/v1/internal/%d/content";

    /** multipart 文件字段名 */
    private static final String PARAM_FILE = "file";

    /** 统一响应体中的数据字段 */
    private static final String FIELD_DATA = "data";

    /** 统一响应体中的状态码字段 */
    private static final String FIELD_CODE = "code";

    /** 文件响应中的 ID 字段 */
    private static final String FIELD_ID = "id";

    /** 租户请求头（文件服务按租户隔离） */
    private static final String HEADER_TENANT = "X-Tenant-Id";

    private final RestClient restClient;

    private final String baseUrl;

    private final long maxAttachmentBytes;

    /**
     * 按集成配置构建客户端。
     *
     * @param properties 邮箱服务配置
     */
    public MailFileClient(MailProperties properties) {
        MailProperties.FileClient file = properties.getFile();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(file.getConnectTimeoutMillis());
        factory.setReadTimeout(file.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(file.getBaseUrl())
                .requestFactory(factory)
                .build();
        String base = file.getBaseUrl();
        this.baseUrl = base != null && base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.maxAttachmentBytes = file.getMaxAttachmentBytes();
    }

    /**
     * 上传附件字节到文件服务，返回文件ID。
     *
     * @param content 文件字节
     * @param fileName 原始文件名
     * @return 文件ID
     */
    public Long upload(byte[] content, String fileName) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(PARAM_FILE, new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        try {
            Map<String, Object> response = restClient.post()
                    .uri(UPLOAD_PATH)
                    .header(HEADER_TENANT, tenantHeader())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Object data = requireData(response);
            Long fileId = data instanceof Map<?, ?> map ? parseId(map.get(FIELD_ID)) : null;
            if (fileId == null) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "附件上传失败：响应缺少文件ID");
            }
            return fileId;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("附件上传失败: fileName={}, reason={}", fileName, e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "附件上传失败：" + e.getMessage());
        }
    }

    /**
     * 按文件ID取附件字节。
     *
     * @param fileId 文件ID
     * @return 文件字节
     */
    public byte[] download(Long fileId) {
        try {
            byte[] content = restClient.get()
                    .uri(baseUrl + String.format(CONTENT_URI_TEMPLATE, fileId))
                    .header(HEADER_TENANT, tenantHeader())
                    .retrieve()
                    .body(byte[].class);
            if (content == null) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "附件读取失败：空响应");
            }
            if (maxAttachmentBytes > 0 && content.length > maxAttachmentBytes) {
                throw new BizException(ErrorCode.PARAM_ERROR, "附件超出单文件上限，无法处理");
            }
            return content;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("附件读取失败: fileId={}, reason={}", fileId, e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "附件读取失败：" + e.getMessage());
        }
    }

    /**
     * 校验统一响应体并取出 {@code data}。
     *
     * @param response 响应体
     * @return data 字段
     */
    private static Object requireData(Map<String, Object> response) {
        if (response == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "附件服务返回空响应");
        }
        Object code = response.get(FIELD_CODE);
        if (!(code instanceof Number number) || number.intValue() != ErrorCode.SUCCESS.getCode()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "附件服务调用失败：" + response.get(FIELD_DATA));
        }
        return response.get(FIELD_DATA);
    }

    /**
     * 取租户请求头。
     *
     * <p>定时任务等无租户上下文的线程上回落到默认租户并告警：本平台目前单租户部署，
     * 缺头上报 400 会让后台同步整体失败；多租户上线后应改为失败关闭。</p>
     *
     * @return 租户ID字符串
     */
    private static String tenantHeader() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            log.warn("缺少租户上下文，附件调用回落默认租户");
            tenantId = TenantContext.DEFAULT_TENANT_ID;
        }
        return String.valueOf(tenantId);
    }

    /**
     * 解析文件ID：平台统一响应体会把 Long 序列化为字符串（精度保护）。
     *
     * @param value 原始值
     * @return 文件ID；无法解析时为 {@code null}
     */
    private static Long parseId(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
