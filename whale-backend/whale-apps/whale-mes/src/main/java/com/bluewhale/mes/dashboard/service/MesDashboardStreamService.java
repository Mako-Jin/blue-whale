package com.bluewhale.mes.dashboard.service;

import com.bluewhale.mes.dashboard.dto.MesDashboardStreamRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MES 综合看板 SSE 推送服务
 *
 * <p>M4 遗留的采集/看板推送通道收口：客户端经
 * {@code POST /mes/dashboard/stream} 建立连接后，服务端按固定间隔推送综合看板快照。
 * 推送间隔取入参（钳制 5–60 秒），缺省 {@code mes.dashboard.push-interval-seconds}（默认 10 秒，D14 口径）。
 * 连接不设超时；客户端断开或推送失败时自动清理调度任务，避免句柄泄漏。</p>
 *
 * @author BlueWhale
 */
@Service
public class MesDashboardStreamService {

    private static final Logger LOG = LoggerFactory.getLogger(MesDashboardStreamService.class);

    /** 推送间隔下限（秒） */
    private static final long MIN_INTERVAL_SECONDS = 5;

    /** 推送间隔上限（秒） */
    private static final long MAX_INTERVAL_SECONDS = 60;

    /** 默认推送间隔（秒） */
    private static final long DEFAULT_INTERVAL_SECONDS = 10;

    private final MesDashboardService dashboardService;

    /** 推送调度线程池（单线程足够：任务为轻量只读聚合 + 事件写回） */
    private final ScheduledExecutorService scheduler;

    /** 连接 ID → 调度任务，用于断开时取消推送 */
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    /** 连接 ID 序列 */
    private final AtomicLong sequence = new AtomicLong();

    /** 服务端默认推送间隔（秒） */
    private final long defaultIntervalSeconds;

    /**
     * 构造推送服务
     *
     * @param dashboardService      综合看板聚合服务
     * @param defaultIntervalSeconds 服务端默认推送间隔（秒，配置 {@code mes.dashboard.push-interval-seconds}）
     */
    public MesDashboardStreamService(MesDashboardService dashboardService,
                                     @Value("${mes.dashboard.push-interval-seconds:10}") long defaultIntervalSeconds) {
        this.dashboardService = dashboardService;
        this.defaultIntervalSeconds = clampInterval(defaultIntervalSeconds <= 0 ? DEFAULT_INTERVAL_SECONDS : defaultIntervalSeconds);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mes-dashboard-sse");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 建立 SSE 推送连接
     *
     * <p>建立后立即推送一次快照，此后按间隔推送；事件名为 {@code dashboard}，
     * 数据为综合看板 JSON 快照。</p>
     *
     * @param request 订阅筛选条件与推送间隔
     * @return SSE 发射器
     */
    public SseEmitter subscribe(MesDashboardStreamRequest request) {
        long interval = resolveInterval(request);
        SseEmitter emitter = new SseEmitter(0L);
        String connectionId = "sse-" + sequence.incrementAndGet();

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> pushOnce(connectionId, emitter, request), 0, interval, TimeUnit.SECONDS);
        tasks.put(connectionId, future);

        Runnable cleanup = () -> {
            future.cancel(false);
            tasks.remove(connectionId);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(throwable -> cleanup.run());
        return emitter;
    }

    /** 当前活跃推送连接数（运维观测用） */
    public int activeConnections() {
        return tasks.size();
    }

    /** 解析推送间隔：入参缺省或非法取服务端默认，并钳制 5–60 秒 */
    private long resolveInterval(MesDashboardStreamRequest request) {
        Long requested = request == null ? null : request.getIntervalSeconds();
        if (requested == null || requested <= 0) {
            return defaultIntervalSeconds;
        }
        return clampInterval(requested);
    }

    /** 钳制推送间隔到 5–60 秒 */
    private long clampInterval(long seconds) {
        return Math.max(MIN_INTERVAL_SECONDS, Math.min(MAX_INTERVAL_SECONDS, seconds));
    }

    /** 推送一轮快照：失败时终止连接并清理（包私有便于单测同步验证） */
    void pushOnce(String connectionId, SseEmitter emitter, MesDashboardStreamRequest request) {
        try {
            emitter.send(SseEmitter.event()
                    .id(connectionId)
                    .name("dashboard")
                    .data(dashboardService.dashboard(request.getLineId(), request.getTeamId(), request.getBoardDate())));
        } catch (IOException | IllegalStateException ex) {
            // 客户端断开或写回失败：完成连接并触发清理回调
            try {
                emitter.completeWithError(ex);
            } catch (IllegalStateException ignored) {
                // 连接已终止，清理回调会移除调度任务
            }
        } catch (RuntimeException ex) {
            // 聚合查询瞬时异常不应终止长连接：记录后跳过本轮推送
            LOG.warn("看板快照推送失败，跳过本轮: connectionId={}, reason={}", connectionId, ex.getMessage());
        }
    }
}
