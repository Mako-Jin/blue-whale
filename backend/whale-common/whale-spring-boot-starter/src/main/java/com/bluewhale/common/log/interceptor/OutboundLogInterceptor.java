package com.bluewhale.common.log.interceptor;

import com.bluewhale.common.audit.support.SensitiveDataMasker;
import com.bluewhale.common.log.entity.OutboundLogRecord;
import com.bluewhale.common.log.mapper.OutboundLogMapper;
import com.bluewhale.common.log.sink.LogPersistSupport;
import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * 出站三方调用日志拦截器（REQ-SEC-006 A5）。
 *
 * <p>采集出站 HTTP 调用的 method/uri/host/耗时/状态/结果与请求、响应体摘要，异步落库
 * {@code outbound_log}。traceId 从 {@code RequestContextHolder} 取网关注入的 {@code X-Trace-Id}，
 * 缺失时生成 32 位无横线 UUID；请求/响应体经 {@link SensitiveDataMasker#maskJson(String)} 脱敏。</p>
 *
 * <p>响应体读取采用自建 buffering 响应包装，避免消费掉调用方随后要读的体；
 * 超时/连接异常记 {@code biz_status=0} 后<b>原样抛出</b>，组装/落库异常仅告警，绝不影响业务主流程。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class OutboundLogInterceptor implements ClientHttpRequestInterceptor {

    /** 落库通道标识 */
    private static final String CHANNEL = "outbound";
    /** 业务成功标记 */
    private static final int BIZ_SUCCESS = 1;
    /** 业务失败标记 */
    private static final int BIZ_FAILURE = 0;
    /** HTTP 错误状态阈值 */
    private static final int HTTP_ERROR_THRESHOLD = 400;
    /** 异常消息落库长度上限 */
    private static final int MAX_ERROR_MSG_LENGTH = 512;
    /** 请求/响应体摘要字节上限（超限只取前段，避免大 body 撑爆内存） */
    private static final int MAX_BODY_BYTES = 8192;

    private final OutboundLogMapper outboundLogMapper;
    private final SensitiveDataMasker sensitiveDataMasker;
    private final LogPersistSupport persistSupport;

    /**
     * 构造出站日志拦截器
     *
     * @param outboundLogMapper 出站日志 Mapper
     * @param sensitiveDataMasker 敏感数据脱敏器
     * @param persistSupport 落库支撑
     */
    public OutboundLogInterceptor(OutboundLogMapper outboundLogMapper,
                                  SensitiveDataMasker sensitiveDataMasker,
                                  LogPersistSupport persistSupport) {
        this.outboundLogMapper = outboundLogMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.persistSupport = persistSupport;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        long startMillis = System.currentTimeMillis();
        ClientHttpResponse response = null;
        byte[] responseBytes = null;
        String responseContentType = null;
        Exception failure = null;
        try {
            response = execution.execute(request, body);
            responseContentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            responseBytes = readBody(response);
            return new BufferingClientHttpResponse(response, responseBytes);
        } catch (Exception e) {
            failure = e;
            throw e;
        } finally {
            publish(request, body, response, responseContentType, responseBytes, failure, startMillis);
        }
    }

    /**
     * 组装并投递出站日志记录；任何异常仅告警，绝不冒泡影响业务
     *
     * @param request 出站请求
     * @param requestBody 出站请求体字节
     * @param response 出站响应，失败时为 {@code null}
     * @param responseContentType 响应内容类型
     * @param responseBytes 响应体字节
     * @param failure 调用异常，成功时为 {@code null}
     * @param startMillis 调用开始时间（毫秒）
     */
    private void publish(HttpRequest request, byte[] requestBody, ClientHttpResponse response,
                         String responseContentType, byte[] responseBytes, Exception failure, long startMillis) {
        try {
            OutboundLogRecord record = buildRecord(request, requestBody, response, responseContentType,
                    responseBytes, failure, startMillis);
            persistSupport.submit(() -> persist(record), CHANNEL);
        } catch (Exception e) {
            log.warn("出站日志组装/投递失败，不影响业务主流程: uri={}", request.getURI(), e);
        }
    }

    /**
     * 提交落库；异常仅告警
     *
     * @param record 待落库记录
     */
    private void persist(OutboundLogRecord record) {
        try {
            outboundLogMapper.insert(record);
        } catch (Exception e) {
            log.warn("出站日志落库失败，不影响业务主流程: traceId={}", record.getTraceId(), e);
        }
    }

    /**
     * 组装出站日志记录
     *
     * @param request 出站请求
     * @param requestBody 出站请求体字节
     * @param response 出站响应，失败时为 {@code null}
     * @param responseContentType 响应内容类型
     * @param responseBytes 响应体字节
     * @param failure 调用异常，成功时为 {@code null}
     * @param startMillis 调用开始时间（毫秒）
     * @return 组装完成的出站日志记录
     */
    private OutboundLogRecord buildRecord(HttpRequest request, byte[] requestBody, ClientHttpResponse response,
                                          String responseContentType, byte[] responseBytes, Exception failure,
                                          long startMillis) {
        URI uri = request.getURI();
        OutboundLogRecord record = new OutboundLogRecord();
        record.setTraceId(resolveTraceId());
        record.setUserId(resolveUserId());
        record.setUsername(resolveUsername());
        record.setTargetHost(uri.getAuthority());
        record.setTargetUri(buildTargetUri(uri));
        record.setHttpMethod(request.getMethod().name());
        record.setRequestParams(isCapturable(request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                ? sensitiveDataMasker.maskJson(toBodyText(requestBody)) : null);
        record.setResponseBody(isCapturable(responseContentType)
                ? sensitiveDataMasker.maskJson(toBodyText(responseBytes)) : null);
        record.setResponseCode(resolveStatusCode(response));
        record.setBizStatus(resolveBizStatus(failure, response));
        record.setErrorMsg(resolveErrorMsg(failure));
        record.setCostMs(System.currentTimeMillis() - startMillis);
        record.setCreateTime(LocalDateTime.now());
        return record;
    }

    /**
     * 读取响应体全部字节
     *
     * @param response 出站响应
     * @return 响应体字节
     * @throws IOException 读取失败
     */
    private static byte[] readBody(ClientHttpResponse response) throws IOException {
        try (InputStream in = response.getBody()) {
            return in.readAllBytes();
        }
    }

    /**
     * 把字节按键截断转为文本，超限只取前段
     *
     * @param bytes 原始字节
     * @return UTF-8 文本；无内容时返回 {@code null}
     */
    private static String toBodyText(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return new String(bytes, 0, Math.min(bytes.length, MAX_BODY_BYTES), StandardCharsets.UTF_8);
    }

    /**
     * 判定 Content-Type 是否在采集白名单（JSON / 表单）
     *
     * @param contentType 内容类型
     * @return 是否采集
     */
    private static boolean isCapturable(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.contains("json") || lower.contains("x-www-form-urlencoded");
    }

    /**
     * 拼接目标 URI（路径 + 脱敏后的查询串）
     *
     * @param uri 请求 URI
     * @return 目标 URI
     */
    private String buildTargetUri(URI uri) {
        String path = uri.getPath();
        String query = sensitiveDataMasker.maskQueryString(uri.getRawQuery());
        if (query == null || query.isEmpty()) {
            return path;
        }
        return path + "?" + query;
    }

    /**
     * 解析响应状态码
     *
     * @param response 出站响应，可为 {@code null}
     * @return HTTP 状态码；响应为空或读取失败时返回 {@code null}
     */
    private static Integer resolveStatusCode(ClientHttpResponse response) {
        if (response == null) {
            return null;
        }
        try {
            HttpStatusCode statusCode = response.getStatusCode();
            return statusCode == null ? null : statusCode.value();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析业务结果：无异常且 HTTP 状态小于 400 视为成功
     *
     * @param failure 调用异常，成功时为 {@code null}
     * @param response 出站响应，可为 {@code null}
     * @return 1 成功 / 0 失败
     */
    private static int resolveBizStatus(Exception failure, ClientHttpResponse response) {
        if (failure != null) {
            return BIZ_FAILURE;
        }
        Integer status = resolveStatusCode(response);
        return status != null && status < HTTP_ERROR_THRESHOLD ? BIZ_SUCCESS : BIZ_FAILURE;
    }

    /**
     * 解析失败原因（异常类名 + 消息）并按字段长度截断
     *
     * @param failure 调用异常，成功时为 {@code null}
     * @return 失败原因；无异常时返回 {@code null}
     */
    private static String resolveErrorMsg(Exception failure) {
        if (failure == null) {
            return null;
        }
        String message = failure.getClass().getSimpleName()
                + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
        return message.length() <= MAX_ERROR_MSG_LENGTH ? message : message.substring(0, MAX_ERROR_MSG_LENGTH);
    }

    /**
     * 解析 traceId：请求头优先，缺失则生成 32 位无横线 UUID（与网关同规格）
     *
     * @return 链路追踪ID
     */
    private static String resolveTraceId() {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            String traceId = WebHeaders.traceId(request);
            if (traceId != null) {
                return traceId;
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 解析发起调用的用户ID，非数字视为未提供
     *
     * @return 用户ID；缺失或非法时返回 {@code null}
     */
    private static Long resolveUserId() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String userId = WebHeaders.userId(request);
        if (userId == null) {
            return null;
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析发起调用的用户名
     *
     * @return 用户名；无请求上下文时返回 {@code null}
     */
    private static String resolveUsername() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : WebHeaders.username(request);
    }

    /**
     * 获取当前线程绑定的 HTTP 请求
     *
     * @return 当前请求；无请求上下文时返回 {@code null}
     */
    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /**
     * 可重复读取的出站响应包装：缓存响应字节，供调用方按需重复读取
     *
     * @author BlueWhale
     */
    private static final class BufferingClientHttpResponse implements ClientHttpResponse {

        /** 原始响应 */
        private final ClientHttpResponse delegate;

        /** 缓存后的响应体字节 */
        private final byte[] body;

        /**
         * 构造可重复读取的响应包装
         *
         * @param delegate 原始响应
         * @param body 缓存后的响应体字节
         */
        BufferingClientHttpResponse(ClientHttpResponse delegate, byte[] body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
