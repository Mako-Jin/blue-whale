package com.bluewhale.common.log.filter;

import com.bluewhale.common.audit.support.SensitiveDataMasker;
import com.bluewhale.common.log.entity.ApiLogRecord;
import com.bluewhale.common.log.mapper.ApiLogMapper;
import com.bluewhale.common.log.properties.ApiLogProperties;
import com.bluewhale.common.log.sink.LogPersistSupport;
import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 后端接口日志过滤器（REQ-SEC-006 A4）。
 *
 * <p>为每个同步 HTTP 请求采集 traceId/用户/IP/URI/入参/出参/耗时/结果并异步落库 {@code api_log}。
 * 请求与响应体经 {@code LimitedCaching*} 包装器 tee 透传，仅在 Content-Type 为 JSON 或表单时采集，
 * 且经 {@link SensitiveDataMasker#maskJson(String)} 脱敏。</p>
 *
 * <p>隔离性：<b>异步派发同样采集且只落库一次</b>——首次派发若已启动异步处理，
 * 仅缓存采样决策、开始时间与请求体，待 ASYNC 派发完成后以最终响应落库；
 * 采样决策跨派发复用，落库前写入幂等标记，避免重复采集。组装/落库任何异常仅告警，
 * 绝不冒泡影响业务主流程。排除路径由 {@code whale.log.api.exclude-paths}
 * 控制，默认含前端上报接口以防回环。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class ApiLogFilter extends OncePerRequestFilter {

    /** 落库通道标识 */
    private static final String CHANNEL = "api";
    /** 采样决策属性（首次派发判定，异步派发沿用） */
    private static final String ATTR_SAMPLED = ApiLogFilter.class.getName() + ".sampled";
    /** 请求开始时间属性（异步派发计算总耗时） */
    private static final String ATTR_START_MILLIS = ApiLogFilter.class.getName() + ".startMillis";
    /** 首次派发捕获的请求体属性（异步派发复用） */
    private static final String ATTR_REQUEST_BODY = ApiLogFilter.class.getName() + ".requestBody";
    /** 已落库幂等标记属性 */
    private static final String ATTR_RECORDED = ApiLogFilter.class.getName() + ".recorded";
    /** 代理转发IP头 */
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    /** 业务成功标记 */
    private static final int BIZ_SUCCESS = 1;
    /** 业务失败标记 */
    private static final int BIZ_FAILURE = 0;
    /** HTTP 错误状态阈值 */
    private static final int HTTP_ERROR_THRESHOLD = 400;
    /** 异常消息落库长度上限 */
    private static final int MAX_ERROR_MSG_LENGTH = 512;
    /** 全采样率下限（含） */
    private static final double SAMPLE_FULL = 1.0D;
    /** 不采样率上限（含） */
    private static final double SAMPLE_NONE = 0.0D;

    private final ApiLogProperties properties;
    private final LogPersistSupport persistSupport;
    private final ApiLogMapper apiLogMapper;
    private final SensitiveDataMasker sensitiveDataMasker;

    /** Ant 路径匹配器（线程安全，复用单例） */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 构造接口日志过滤器
     *
     * @param properties 接口日志配置
     * @param persistSupport 落库支撑
     * @param apiLogMapper 接口日志 Mapper
     * @param sensitiveDataMasker 敏感数据脱敏器
     */
    public ApiLogFilter(ApiLogProperties properties,
                        LogPersistSupport persistSupport,
                        ApiLogMapper apiLogMapper,
                        SensitiveDataMasker sensitiveDataMasker) {
        this.properties = properties;
        this.persistSupport = persistSupport;
        this.apiLogMapper = apiLogMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        List<String> excludePaths = properties.getExcludePaths();
        if (excludePaths == null) {
            return false;
        }
        for (String pattern : excludePaths) {
            if (pattern != null && pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (Boolean.TRUE.equals(request.getAttribute(ATTR_RECORDED))) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean asyncDispatch = isAsyncDispatch(request);
        if (!resolveSampled(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        long startMillis = resolveStartMillis(request, asyncDispatch);
        LimitedCachingRequestWrapper requestWrapper = asyncDispatch
                ? null : new LimitedCachingRequestWrapper(request, properties.getMaxBodyLength());
        LimitedCachingResponseWrapper responseWrapper =
                new LimitedCachingResponseWrapper(response, properties.getMaxBodyLength());
        boolean captureRequest = !asyncDispatch && isCapturable(request.getContentType());
        String initialRequestBody = asyncDispatch
                ? (String) request.getAttribute(ATTR_REQUEST_BODY) : null;
        Exception failure = null;
        try {
            filterChain.doFilter(requestWrapper == null ? request : requestWrapper, responseWrapper);
        } catch (Exception e) {
            failure = e;
            throw e;
        } finally {
            if (!asyncDispatch && request.isAsyncStarted()) {
                // 异步处理尚未完成：仅暂存上下文，待 ASYNC 派发拿到最终响应后再采集
                request.setAttribute(ATTR_START_MILLIS, startMillis);
                request.setAttribute(ATTR_REQUEST_BODY,
                        captureRequest ? requestWrapper.getCapturedBody() : null);
            } else {
                String capturedRequestBody = asyncDispatch
                        ? initialRequestBody
                        : (captureRequest ? requestWrapper.getCapturedBody() : null);
                record(request, responseWrapper, failure, startMillis, capturedRequestBody);
            }
        }
    }

    /**
     * 采集并投递接口日志：组装异常仅告警，先置幂等标记避免重复采集
     *
     * @param request 当前请求
     * @param responseWrapper 响应包装器
     * @param failure 链路异常；成功时为 {@code null}
     * @param startMillis 请求开始时间（毫秒）
     * @param capturedRequestBody 请求体镜像（可能来自首次派发暂存）
     */
    private void record(HttpServletRequest request, LimitedCachingResponseWrapper responseWrapper,
                        Exception failure, long startMillis, String capturedRequestBody) {
        try {
            ApiLogRecord record = new ApiLogRecord();
            fillRequest(record, request, capturedRequestBody);
            fillResponse(record, responseWrapper, failure);
            fillResult(record, failure, startMillis);
            request.setAttribute(ATTR_RECORDED, Boolean.TRUE);
            persistSupport.submit(() -> persist(record), CHANNEL);
        } catch (Exception e) {
            log.warn("接口日志组装/投递失败，不影响业务主流程: uri={}", request.getRequestURI(), e);
        }
    }

    /**
     * 解析采样决策：首次派发判定并暂存，异步派发沿用同一结果，避免两次随机导致漏采
     *
     * @param request 当前请求
     * @return 是否采样
     */
    private boolean resolveSampled(HttpServletRequest request) {
        Object cached = request.getAttribute(ATTR_SAMPLED);
        if (cached instanceof Boolean decision) {
            return decision;
        }
        boolean decision = sampled();
        request.setAttribute(ATTR_SAMPLED, decision);
        return decision;
    }

    /**
     * 解析请求开始时间：异步派发复用首次派发暂存值，保证耗时为端到端总耗时
     *
     * @param request 当前请求
     * @param asyncDispatch 是否为异步派发
     * @return 请求开始时间（毫秒）
     */
    private long resolveStartMillis(HttpServletRequest request, boolean asyncDispatch) {
        if (asyncDispatch) {
            Object cached = request.getAttribute(ATTR_START_MILLIS);
            if (cached instanceof Long millis) {
                return millis;
            }
        }
        return System.currentTimeMillis();
    }

    /**
     * 采样判定：{@code >=1} 全采、{@code <=0} 全不采，其余按概率采样
     *
     * @return 是否采样
     */
    private boolean sampled() {
        double rate = properties.getSampleRate();
        if (rate >= SAMPLE_FULL) {
            return true;
        }
        if (rate <= SAMPLE_NONE) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < rate;
    }

    /**
     * 提交落库；异常仅告警
     *
     * @param record 待落库记录
     */
    private void persist(ApiLogRecord record) {
        try {
            apiLogMapper.insert(record);
        } catch (Exception e) {
            log.warn("接口日志落库失败，不影响业务主流程: traceId={}", record.getTraceId(), e);
        }
    }

    /**
     * 填充请求侧字段：模块名 / traceId / 用户 / URI / 方法 / 查询串 / 入参（白名单且脱敏）/ IP / UA
     *
     * @param record 待填充记录
     * @param request 原始请求
     * @param capturedRequestBody 请求体镜像（异步派发来自首次派发暂存）；无采集时为 {@code null}
     */
    private void fillRequest(ApiLogRecord record, HttpServletRequest request, String capturedRequestBody) {
        record.setModule(properties.getModule());
        record.setTraceId(WebHeaders.traceId(request));
        record.setUserId(parseUserId(WebHeaders.userId(request)));
        record.setUsername(WebHeaders.username(request));
        record.setRequestUri(request.getRequestURI());
        record.setHttpMethod(request.getMethod());
        record.setQueryString(sensitiveDataMasker.maskQueryString(request.getQueryString()));
        record.setRequestParams(capturedRequestBody == null
                ? null : sensitiveDataMasker.maskJson(capturedRequestBody));
        record.setIp(resolveIp(request));
        record.setUserAgent(WebHeaders.header(request, HttpHeaders.USER_AGENT));
    }

    /**
     * 填充响应侧字段：出参（白名单且脱敏）/ 响应码 / 业务结果
     *
     * @param record 待填充记录
     * @param responseWrapper 响应包装器（提供限长镜像的响应体与状态码）
     * @param failure 链路抛出的异常，成功时为 {@code null}
     */
    private void fillResponse(ApiLogRecord record, LimitedCachingResponseWrapper responseWrapper, Exception failure) {
        record.setResponseBody(isCapturable(responseWrapper.getContentType())
                ? sensitiveDataMasker.maskJson(responseWrapper.getCapturedBody()) : null);
        record.setResponseCode(responseWrapper.getStatus());
        record.setBizStatus(resolveBizStatus(failure, responseWrapper.getStatus()));
    }

    /**
     * 填充结果侧字段：失败原因 / 耗时 / 落库时间
     *
     * @param record 待填充记录
     * @param failure 链路抛出的异常，成功时为 {@code null}
     * @param startMillis 请求开始时间（毫秒）
     */
    private void fillResult(ApiLogRecord record, Exception failure, long startMillis) {
        record.setErrorMsg(resolveErrorMsg(failure));
        record.setCostMs(System.currentTimeMillis() - startMillis);
        record.setCreateTime(LocalDateTime.now());
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
     * 解析业务结果：无异常且 HTTP 状态小于 400 视为成功
     *
     * @param failure 链路异常，成功时为 {@code null}
     * @param status HTTP 状态码
     * @return 1 成功 / 0 失败
     */
    private static int resolveBizStatus(Exception failure, int status) {
        return failure == null && status < HTTP_ERROR_THRESHOLD ? BIZ_SUCCESS : BIZ_FAILURE;
    }

    /**
     * 解析失败原因（异常类名 + 消息）并按字段长度截断
     *
     * @param failure 链路异常，成功时为 {@code null}
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
     * 解析调用方IP：{@code X-Forwarded-For} 首段 → 远程地址
     *
     * @param request 当前请求
     * @return 调用方IP
     */
    private static String resolveIp(HttpServletRequest request) {
        String forwarded = WebHeaders.header(request, FORWARDED_FOR_HEADER);
        if (forwarded != null) {
            int commaIndex = forwarded.indexOf(',');
            return (commaIndex > 0 ? forwarded.substring(0, commaIndex) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 解析网关注入的用户ID，非数字视为未提供
     *
     * @param userId 用户ID文本
     * @return 用户ID；缺失或非法时返回 {@code null}
     */
    private static Long parseUserId(String userId) {
        if (userId == null) {
            return null;
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
