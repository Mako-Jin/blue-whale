package com.bluewhale.file.filter;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.file.config.FileProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 文件内容接口访问防护：防盗链与调用者限流。
 *
 * <p>只拦截下载与预览接口；开关默认关闭。防盗链按 {@code Referer} 主机名匹配同主机、
 * 精确主机或 {@code *.suffix} 通配后缀；限流按调用者（{@code X-User-Id}，缺失时按客户端 IP）
 * 使用进程内令牌桶。被拒绝的请求直接返回 JSON 错误，不进入业务处理。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class FileAccessGuardFilter extends OncePerRequestFilter {

    /** 下载接口路径后缀 */
    private static final String DOWNLOAD_SUFFIX = "/download";

    /** 预览接口路径后缀 */
    private static final String PREVIEW_SUFFIX = "/preview";

    /** 进程内跟踪的调用者上限，超出后清理空闲桶 */
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    /** 令牌桶空闲判定时长 */
    private static final long IDLE_NANOS = TimeUnit.MINUTES.toNanos(10);

    /** 被限流时的建议重试间隔（秒） */
    private static final String RETRY_AFTER_SECONDS = "1";

    private final FileProperties fileProperties;

    private final ObjectMapper objectMapper;

    /** 调用者到令牌桶的映射 */
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.endsWith(DOWNLOAD_SUFFIX) && !path.endsWith(PREVIEW_SUFFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        FileProperties.Access access = fileProperties.getAccess();
        if (access.getHotlink().isEnabled() && !hotlinkAllowed(request, access.getHotlink())) {
            writeError(response, HttpStatus.FORBIDDEN.value(), ErrorCode.FORBIDDEN.getCode(),
                    "来源站点不允许访问该文件");
            return;
        }
        if (access.getRateLimit().isEnabled() && !tryAcquire(request, access.getRateLimit())) {
            response.setHeader(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
            writeError(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                    ErrorCode.LOCK_ACQUIRE_FAILED.getCode(), "请求过于频繁，请稍后重试");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * 判断来源站点是否允许访问。
     *
     * @param request 当前请求
     * @param config 防盗链配置
     * @return 允许访问返回 {@code true}
     */
    private boolean hotlinkAllowed(HttpServletRequest request, FileProperties.Hotlink config) {
        String referer = request.getHeader(HttpHeaders.REFERER);
        if (!StringUtils.hasText(referer)) {
            return config.isAllowEmptyReferer();
        }
        String host = refererHost(referer);
        if (host == null) {
            return false;
        }
        if (host.equalsIgnoreCase(request.getServerName())) {
            return true;
        }
        return config.getAllowedHosts().stream().anyMatch(pattern -> matchesHost(host, pattern));
    }

    /**
     * 提取 Referer 主机名。
     *
     * @param referer Referer 头
     * @return 主机名；无法解析时返回 {@code null}
     */
    private static String refererHost(String referer) {
        try {
            return URI.create(referer.trim()).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 主机名匹配：支持精确主机与 {@code *.suffix} 通配后缀。
     *
     * @param host 实际主机名
     * @param pattern 允许的主机模式
     * @return 匹配返回 {@code true}
     */
    private static boolean matchesHost(String host, String pattern) {
        if (!StringUtils.hasText(pattern)) {
            return false;
        }
        String normalized = pattern.trim().toLowerCase(Locale.ROOT);
        String actual = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("*.")) {
            return actual.endsWith(normalized.substring(1));
        }
        return actual.equals(normalized);
    }

    /**
     * 尝试获取一个令牌。
     *
     * @param request 当前请求
     * @param config 限流配置
     * @return 获取成功返回 {@code true}
     */
    private boolean tryAcquire(HttpServletRequest request, FileProperties.RateLimit config) {
        long now = System.nanoTime();
        evictIdleBuckets(now);
        String key = clientKey(request);
        TokenBucket bucket = buckets.computeIfAbsent(key,
                ignored -> new TokenBucket(config.getBurst(), config.getRequestsPerSecond(), now));
        return bucket.tryAcquire(now);
    }

    /**
     * 调用者标识：优先用户 ID，缺失时使用客户端 IP。
     *
     * @param request 当前请求
     * @return 限流键
     */
    private static String clientKey(HttpServletRequest request) {
        String userId = request.getHeader(WebHeaders.USER_ID);
        if (StringUtils.hasText(userId)) {
            return "u:" + userId.trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    /**
     * 跟踪调用者过多时清理空闲令牌桶，避免映射无限增长。
     *
     * @param now 当前纳秒时间
     */
    private void evictIdleBuckets(long now) {
        if (buckets.size() <= MAX_TRACKED_CLIENTS) {
            return;
        }
        buckets.entrySet().removeIf(entry -> entry.getValue().idle(now));
    }

    /**
     * 写出 JSON 错误响应。
     *
     * @param response HTTP 响应
     * @param status HTTP 状态码
     * @param code 业务错误码
     * @param message 错误提示
     * @throws IOException 写响应失败
     */
    private void writeError(HttpServletResponse response, int status, int code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, message)));
    }

    /**
     * 令牌桶：按固定速率补充令牌，允许最多 {@code capacity} 的瞬时突发。
     */
    private static final class TokenBucket {

        /** 桶容量 */
        private final double capacity;

        /** 每纳秒补充的令牌数 */
        private final double refillPerNano;

        /** 当前令牌数 */
        private double tokens;

        /** 上次补充时间（纳秒） */
        private long lastRefill;

        /** 最近访问时间（纳秒），用于空闲清理 */
        private long lastAccess;

        /**
         * 构造满桶。
         *
         * @param capacity 桶容量
         * @param perSecond 每秒补充令牌数
         * @param now 当前纳秒时间
         */
        private TokenBucket(int capacity, int perSecond, long now) {
            this.capacity = Math.max(capacity, 1);
            this.tokens = this.capacity;
            this.refillPerNano = Math.max(perSecond, 1) / 1_000_000_000.0d;
            this.lastRefill = now;
            this.lastAccess = now;
        }

        /**
         * 尝试获取一个令牌。
         *
         * @param now 当前纳秒时间
         * @return 获取成功返回 {@code true}
         */
        private boolean tryAcquire(long now) {
            refill(now);
            lastAccess = now;
            if (tokens < 1.0d) {
                return false;
            }
            tokens -= 1.0d;
            return true;
        }

        /**
         * 按时间差补充令牌，不超过桶容量。
         *
         * @param now 当前纳秒时间
         */
        private void refill(long now) {
            long elapsed = now - lastRefill;
            if (elapsed <= 0) {
                return;
            }
            tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
            lastRefill = now;
        }

        /**
         * 是否空闲超过阈值。
         *
         * @param now 当前纳秒时间
         * @return 空闲返回 {@code true}
         */
        private boolean idle(long now) {
            return now - lastAccess > IDLE_NANOS;
        }
    }
}
