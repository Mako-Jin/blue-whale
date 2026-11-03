package com.bluewhale.video.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenProperties;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.model.VideoStream;
import com.bluewhale.video.domain.repository.ContentStreamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * 视频内容客户端：直连 whale-file 内网内容接口 {@code /file/internal/{id}/content}。
 *
 * <p>使用 JDK {@link HttpClient} 而非 RestClient，保证响应体以流式形态返回、
 * 不被整体缓冲；Range / If-Range 原样透传，响应头（Content-Length / Content-Range /
 * ETag / Content-Type）解析为领域模型，文件 ID 不出本服务。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileVideoContentClient implements ContentStreamRepository {

    /**
     * 进程内共享 HTTP 客户端（流式响应，无整体超时）。
     *
     * <p>显式固定 HTTP/1.1：whale-file 内容接口由 Tomcat HTTP/1.1 连接器提供，
     * JDK 客户端默认 HTTP/2 会为每条新建连接先发 h2c {@code Upgrade} 探测，
     * 多付一次往返；高并发开流时这部分排队会直接体现在首字节时延上。</p>
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 内容接口模板 */
    private static final String CONTENT_PATH = "/file/internal/%d/content";

    /** 文件头读取的 Range 模板 */
    private static final String HEAD_RANGE = "bytes=0-%d";

    /** HTTP 全量响应状态码 */
    private static final int STATUS_OK = 200;

    /** HTTP 分段响应状态码 */
    private static final int STATUS_PARTIAL = 206;

    private final DiscoveryClient discoveryClient;

    private final InternalTokenProperties internalTokenProperties;

    private final VideoProperties videoProperties;

    @Override
    public VideoStream open(Long fileId, Long tenantId, String rangeHeader, String ifRangeHeader) {
        return execute(fileId, tenantId, rangeHeader, ifRangeHeader, false);
    }

    @Override
    public VideoStream openImage(Long fileId, Long tenantId, String rangeHeader, String ifRangeHeader) {
        return execute(fileId, tenantId, rangeHeader, ifRangeHeader, true);
    }

    @Override
    public byte[] readHead(Long fileId, Long tenantId, int length) {
        int size = Math.max(length, 1);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(fileId))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .header(HttpHeaders.RANGE, String.format(HEAD_RANGE, size - 1));
        applyTenant(builder, tenantId);
        try {
            HttpResponse<byte[]> response = HTTP_CLIENT.send(builder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status != STATUS_OK && status != STATUS_PARTIAL) {
                return new byte[0];
            }
            return response.body() == null ? new byte[0] : response.body();
        } catch (IOException ex) {
            log.warn("[VIDEO] 文件头读取失败: fileId={}, error={}", fileId, ex.getMessage());
            return new byte[0];
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new byte[0];
        }
    }

    /**
     * 执行内容读取：流式响应或图片代理共用同一 Range 透传逻辑。
     *
     * @param fileId 文件 ID
     * @param tenantId 租户 ID
     * @param rangeHeader 请求的 {@code Range} 头
     * @param ifRangeHeader 请求的 {@code If-Range} 头
     * @param image 是否为图片代理（仅用于异常文案）
     * @return 内容流
     */
    private VideoStream execute(Long fileId, Long tenantId, String rangeHeader, String ifRangeHeader, boolean image) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(fileId))
                .GET()
                .timeout(Duration.ofMinutes(5));
        if (StringUtils.hasText(rangeHeader)) {
            builder.header(HttpHeaders.RANGE, rangeHeader.trim());
        }
        if (StringUtils.hasText(ifRangeHeader)) {
            builder.header(HttpHeaders.IF_RANGE, ifRangeHeader.trim());
        }
        applyTenant(builder, tenantId);
        HttpResponse<InputStream> response;
        try {
            response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, readFailureMessage(image));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.SYSTEM_ERROR, "视频内容读取被中断");
        }
        int status = response.statusCode();
        if (status == HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value()) {
            closeQuietly(response.body());
            long total = parseTotal(response.headers().firstValue(HttpHeaders.CONTENT_RANGE).orElse(null));
            return new VideoStream(status, 0L, null, total,
                    response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(null),
                    response.headers().firstValue(HttpHeaders.ETAG).orElse(null), null);
        }
        if (status != STATUS_OK && status != STATUS_PARTIAL) {
            closeQuietly(response.body());
            throw toException(status, image);
        }
        long contentLength = response.headers().firstValue(HttpHeaders.CONTENT_LENGTH)
                .map(FileVideoContentClient::parseLong).orElse(-1L);
        return new VideoStream(status, contentLength,
                response.headers().firstValue(HttpHeaders.CONTENT_RANGE).orElse(null),
                contentLength,
                response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(null),
                response.headers().firstValue(HttpHeaders.ETAG).orElse(null),
                response.body());
    }

    /**
     * 注入租户头与内网令牌。
     *
     * @param builder 请求构造器
     * @param tenantId 租户 ID
     */
    private void applyTenant(HttpRequest.Builder builder, Long tenantId) {
        if (tenantId != null) {
            builder.header("X-Tenant-Id", String.valueOf(tenantId));
        }
        String token = internalTokenProperties.resolveToken();
        if (internalTokenProperties.isEnabled() && !token.isBlank()) {
            builder.header(internalTokenProperties.getHeader(), token);
        }
    }

    /**
     * 解析文件服务实例地址。
     *
     * @param fileId 文件 ID
     * @return 内容接口 URI
     */
    private URI uri(Long fileId) {
        List<ServiceInstance> instances = discoveryClient.getInstances(videoProperties.getFileServiceName());
        if (instances.isEmpty()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件服务不可用");
        }
        return URI.create(instances.get(0).getUri() + String.format(CONTENT_PATH, fileId));
    }

    /**
     * 解析 {@code Content-Range: bytes *}{@code /size} 中的总字节数。
     *
     * @param header Content-Range 头
     * @return 总字节数；无法解析返回 0
     */
    private static long parseTotal(String header) {
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
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    /**
     * 静默关闭流。
     *
     * @param stream 内容流，可为 {@code null}
     */
    private static void closeQuietly(InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ex) {
                log.debug("关闭文件内容流失败: {}", ex.getMessage());
            }
        }
    }

    /**
     * 构造读取失败提示。
     *
     * @param image 是否为图片代理
     * @return 提示文案
     */
    private static String readFailureMessage(boolean image) {
        return image ? "图片内容读取失败" : "视频内容读取失败";
    }

    /**
     * 将下游 HTTP 状态映射为业务异常。
     *
     * @param status HTTP 状态
     * @param image 是否为图片代理
     * @return 业务异常
     */
    private static BizException toException(int status, boolean image) {
        String kind = image ? "图片" : "视频";
        if (status == HttpStatus.NOT_FOUND.value()) {
            return new BizException(ErrorCode.NOT_FOUND, kind + "文件不存在");
        }
        return new BizException(ErrorCode.SYSTEM_ERROR, kind + "内容读取失败（HTTP " + status + "）");
    }
}
