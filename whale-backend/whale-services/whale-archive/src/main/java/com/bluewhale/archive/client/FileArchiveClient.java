package com.bluewhale.archive.client;

import com.bluewhale.archive.config.ArchiveProperties;
import com.bluewhale.archive.remote.RemoteContentStream;
import com.bluewhale.archive.remote.RemoteFileMeta;
import com.bluewhale.archive.support.RemoteHttpSupport;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenProperties;
import com.bluewhale.common.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 文件服务内网客户端。
 *
 * <p>提供快照上传、元数据读取、归档锁定（{@code status=2}）与流式内容代理
 * （下载与在线预览共用，透传 HTTP Range）；服务间直连、不经网关。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class FileArchiveClient {

    /** 内网上传路径 */
    private static final String UPLOAD_PATH = "/file/v1/internal/upload";

    /** 内网元数据路径模板 */
    private static final String DETAIL_PATH = "/file/v1/internal/{id}";

    /** 内网锁定路径模板 */
    private static final String LOCK_PATH = "/file/v1/internal/{id}/lock";

    /** 内网内容流路径模板（URI 拼接形态） */
    private static final String CONTENT_URI_TEMPLATE = "/file/v1/internal/%d/content";

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

    /** 流式读取文件内容动作描述（下载与预览共用） */
    private static final String ACTION_PREVIEW = "读取档案文件内容";

    /** 响应数据中的文件ID字段 */
    private static final String FIELD_ID = "id";

    /** 租户请求头（文件服务按租户隔离） */
    private static final String HEADER_TENANT = "X-Tenant-Id";

    /** 流式预览请求超时（分钟）：足够承载大文件顺序读取 */
    private static final long PREVIEW_TIMEOUT_MINUTES = 5L;

    /** 建连超时（秒） */
    private static final long CONNECT_TIMEOUT_SECONDS = 5L;

    /**
     * 进程内共享 HTTP 客户端（流式响应，无整体缓冲）。
     *
     * <p>固定 HTTP/1.1：{@code whale-file} 内容接口由 Tomcat HTTP/1.1 连接器提供，
     * JDK 客户端默认 HTTP/2 会先发 h2c 探测，多付一次往返。</p>
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();

    private final RestClient restClient;

    private final ObjectMapper objectMapper;

    private final InternalTokenProperties internalTokenProperties;

    private final String fileBaseUrl;

    /**
     * 以集成配置构建客户端。
     *
     * @param properties 档案服务配置
     * @param objectMapper JSON 映射器
     * @param internalTokenProperties 内网令牌配置（服务间调用 {@code /internal/**} 必带）
     */
    public FileArchiveClient(ArchiveProperties properties, ObjectMapper objectMapper,
                             InternalTokenProperties internalTokenProperties) {
        this.restClient = RemoteHttpSupport.build(properties.getIntegration().getFileUrl(),
                properties.getIntegration());
        this.objectMapper = objectMapper;
        this.internalTokenProperties = internalTokenProperties;
        String baseUrl = properties.getIntegration().getFileUrl();
        this.fileBaseUrl = baseUrl != null && baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
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
                        .header(HEADER_TENANT, tenantHeader())
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(body)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {
                        }),
                ACTION_UPLOAD);
        Object data = RemoteHttpSupport.requireData(response, ACTION_UPLOAD);
        Map<?, ?> dataMap = data instanceof Map<?, ?> map ? map : Map.of();
        Long fileId = parseId(dataMap.get(FIELD_ID));
        if (fileId == null) {
            throw RemoteHttpSupport.failure(ACTION_UPLOAD, "响应缺少文件ID");
        }
        return fileId;
    }

    /**
     * 取当前租户请求头；缺失租户上下文时失败关闭，避免跨租户读写文件。
     *
     * @return 租户ID字符串
     */
    private String tenantHeader() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("缺少租户上下文，无法调用文件服务");
        }
        return String.valueOf(tenantId);
    }

    /**
     * 解析文件ID：平台统一响应体会把 {@code Long} 序列化为字符串（精度保护），
     * 因此同时兼容数字与数字字符串两种形态。
     *
     * @param value 响应中的原始ID值
     * @return 文件ID；无法解析时返回 {@code null}
     */
    static Long parseId(Object value) {
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
                    .header(HEADER_TENANT, tenantHeader())
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
                        .header(HEADER_TENANT, tenantHeader())
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {
                        }),
                ACTION_LOCK);
        RemoteHttpSupport.requireData(response, ACTION_LOCK);
    }

    /**
     * 以流式方式打开文件内容，供代理下载与在线预览 / 播放（Range 与 If-Range 原样透传）。
     *
     * <p>使用 JDK {@link HttpClient} 读取响应流，不整体缓冲，避免大文件一次性读入堆内存；
     * 416 越界时关闭内容流并只返回总字节数。下载与预览的差异仅在响应的
     * {@code Content-Disposition}，由 controller 决定，本方法不区分。</p>
     *
     * @param id 文件ID
     * @param rangeHeader 请求的 {@code Range} 头，可为空
     * @param ifRangeHeader 请求的 {@code If-Range} 头，可为空
     * @return 内容流与 Range 响应头
     * @throws BizException 文件不存在或下游读取失败时抛出
     */
    public RemoteContentStream openContent(Long id, String rangeHeader, String ifRangeHeader) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(contentUri(id))
                .GET()
                .timeout(Duration.ofMinutes(PREVIEW_TIMEOUT_MINUTES));
        if (StringUtils.hasText(rangeHeader)) {
            builder.header(HttpHeaders.RANGE, rangeHeader.trim());
        }
        if (StringUtils.hasText(ifRangeHeader)) {
            builder.header(HttpHeaders.IF_RANGE, ifRangeHeader.trim());
        }
        builder.header(HEADER_TENANT, tenantHeader());
        String token = internalTokenProperties.resolveToken();
        if (internalTokenProperties.isEnabled() && !token.isBlank()) {
            builder.header(internalTokenProperties.getHeader(), token);
        }
        HttpResponse<InputStream> response;
        try {
            response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw RemoteHttpSupport.failure(ACTION_PREVIEW, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw RemoteHttpSupport.failure(ACTION_PREVIEW, "读取被中断");
        }
        int status = response.statusCode();
        if (status == HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value()) {
            closeQuietly(response.body());
            return buildStream(response, status, 0L, null);
        }
        if (status != HttpStatus.OK.value() && status != HttpStatus.PARTIAL_CONTENT.value()) {
            closeQuietly(response.body());
            throw toPreviewException(status);
        }
        long contentLength = response.headers().firstValue(HttpHeaders.CONTENT_LENGTH)
                .map(FileArchiveClient::parseLong).orElse(-1L);
        return buildStream(response, status, contentLength, response.body());
    }

    /**
     * 组装流式响应对象。
     *
     * @param response 下游响应
     * @param status HTTP 状态码
     * @param contentLength 内容长度
     * @param body 内容流（416 越界时为空）
     * @return 流式响应
     */
    private static RemoteContentStream buildStream(HttpResponse<InputStream> response, int status,
                                                   long contentLength, InputStream body) {
        RemoteContentStream stream = new RemoteContentStream();
        stream.setStatus(status);
        stream.setContentLength(contentLength);
        stream.setContentRange(response.headers().firstValue(HttpHeaders.CONTENT_RANGE).orElse(null));
        stream.setContentRangeTotal(parseTotal(stream.getContentRange()));
        stream.setContentType(response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(null));
        stream.setEtag(response.headers().firstValue(HttpHeaders.ETAG).orElse(null));
        stream.setBody(body);
        return stream;
    }

    /**
     * 拼接文件内容接口地址。
     *
     * @param id 文件ID
     * @return 内容接口 URI
     */
    private URI contentUri(Long id) {
        return URI.create(fileBaseUrl + String.format(CONTENT_URI_TEMPLATE, id));
    }

    /**
     * 解析 {@code Content-Range: bytes *}{@code /size} 中的总字节数。
     *
     * @param header Content-Range 头
     * @return 总字节数；无法解析返回 0
     */
    static long parseTotal(String header) {
        if (!StringUtils.hasText(header)) {
            return 0L;
        }
        int slash = header.lastIndexOf('/');
        return slash < 0 ? 0L : parseLong(header.substring(slash + 1));
    }

    /**
     * 解析长整型，非法值返回 {@code -1}。
     *
     * @param text 文本
     * @return 长整型
     */
    private static long parseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * 静默关闭内容流。
     *
     * @param stream 内容流，可为 {@code null}
     */
    private static void closeQuietly(InputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException e) {
            log.debug("关闭档案内容流失败: {}", e.getMessage());
        }
    }

    /**
     * 将下游异常状态映射为业务异常。
     *
     * @param status HTTP 状态
     * @return 业务异常
     */
    private static BizException toPreviewException(int status) {
        if (status == HttpStatus.NOT_FOUND.value()) {
            return new BizException(ErrorCode.NOT_FOUND, "archive.file.gone", "档案文件不存在或已被删除");
        }
        return RemoteHttpSupport.failure(ACTION_PREVIEW, "HTTP " + status);
    }
}
