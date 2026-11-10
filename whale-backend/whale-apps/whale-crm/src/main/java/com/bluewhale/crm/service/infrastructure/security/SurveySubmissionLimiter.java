package com.bluewhale.crm.service.infrastructure.security;

import com.bluewhale.crm.config.CrmSurveyProperties;
import com.bluewhale.crm.service.domain.port.SurveyRateLimitPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 满意度答卷提交限流（单节点内存滑动窗口）。
 *
 * <p>按「来源 IP + 工单」计数，默认 10 分钟内最多 5 次；不引入分布式缓存，多实例部署时
 * 以单实例维度生效。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class SurveySubmissionLimiter implements SurveyRateLimitPort {

    /** 未取得来源 IP 时的占位值 */
    private static final String UNKNOWN_IP = "unknown";

    /** 问卷配置 */
    private final CrmSurveyProperties properties;

    /** 计数窗口 */
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    /**
     * 尝试占用一次提交配额。
     *
     * @param clientIp 来源 IP
     * @param ticketId 工单 ID
     * @return 是否允许提交
     */
    public boolean tryAcquire(String clientIp, Long ticketId) {
        String key = (clientIp == null || clientIp.isBlank() ? UNKNOWN_IP : clientIp) + ":" + ticketId;
        long now = System.currentTimeMillis();
        long windowMillis = Math.max(1, properties.getRateLimitMinutes()) * 60_000L;
        int limit = Math.max(1, properties.getRateLimitCount());
        Deque<Long> timestamps = windows.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowMillis) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
