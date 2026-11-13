package com.bluewhale.music.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenProperties;
import com.bluewhale.music.domain.model.AudioStream;
import com.bluewhale.music.domain.repository.AudioContentRepository;
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
 * 音频内容客户端：直连 whale-file 内网内容接口 {@code /file/v1/internal/{id}/content}。
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
public class FileAudioContentClient implements AudioContentRepository {

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
    private static final String CONTENT_PATH = "/file/v1/internal/%d/content";

    private final DiscoveryClient discoveryClient;

    private final InternalTokenProperties internalTokenProperties;

    @Override
    public AudioStream open(Long fileId, Long tenantId, String rangeHeader, String ifRangeHeader) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(fileId))
                .GET()
                .timeout(Duration.ofMinutes(5));
        if (StringUtils.hasText(rangeHeader)) {
            builder.header(HttpHeaders.RANGE, rangeHeader.trim());
        }
        if (StringUtils.hasText(ifRangeHeader)) {
            builder.header(HttpHeaders.IF_RANGE, ifRangeHeader.trim());
        }
        if (tenantId != null) {
            builder.header("X-Tenant-Id", String.valueOf(tenantId));
        }
        String token = internalTokenProperties.resolveToken();
        if (internalTokenProperties.isEnabled() && !token.isBlank()) {
            builder.header(internalTokenProperties.getHeader(), token);
        }
        HttpResponse<InputStream> response;
        try {
            response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "音频内容读取失败");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.SYSTEM_ERROR, "音频内容读取被中断");
        }
        int status = response.statusCode();
        if (status == HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value()) {
            closeQuietly(response.body());
            long total = parseTotal(response.headers().firstValue(HttpHeaders.CONTENT_RANGE).orElse(null));
            return new AudioStream(status, 0L, null, total,
                    response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(null),
                    response.headers().firstValue(HttpHeaders.ETAG).orElse(null), null);
        }
        if (status != HttpStatus.OK.value() && status != HttpStatus.PARTIAL_CONTENT.value()) {
            closeQuietly(response.body());
            throw toException(status);
        }
        long contentLength = response.headers().firstValue(HttpHeaders.CONTENT_LENGTH)
                .map(FileAudioContentClient::parseLong).orElse(-1L);
        return new AudioStream(status, contentLength,
                response.headers().firstValue(HttpHeaders.CONTENT_RANGE).orElse(null),
                contentLength,
                response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(null),
                response.headers().firstValue(HttpHeaders.ETAG).orElse(null),
                response.body());
    }

    /**
     * 解析文件服务实例地址。
     *
     * @param fileId 文件 ID
     * @return 内容接口 URI
     */
    private URI uri(Long fileId) {
        List<ServiceInstance> instances = discoveryClient.getInstances("whale-file");
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
     * 将下游 HTTP 状态映射为业务异常。
     *
     * @param status HTTP 状态
     * @return 业务异常
     */
    private static BizException toException(int status) {
        if (status == HttpStatus.NOT_FOUND.value()) {
            return new BizException(ErrorCode.NOT_FOUND, "音频文件不存在");
        }
        return new BizException(ErrorCode.SYSTEM_ERROR, "音频内容读取失败（HTTP " + status + "）");
    }
}
