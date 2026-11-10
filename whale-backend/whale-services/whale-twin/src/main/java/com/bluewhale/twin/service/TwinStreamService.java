package com.bluewhale.twin.service;

import com.bluewhale.twin.config.TwinProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 场景实时事件推送服务（SSE，按<b>场景</b>订阅，对齐 whale-iot 设备流先例）。
 *
 * <p>订阅粒度是场景：{@code GET /twin/scenes/{id}/stream} 建立长连接后，
 * 该场景下所有绑定的实时态变化（含设备上下线）都会推给订阅者。
 * 连接不设超时；心跳按 {@code whale.twin.stream-heartbeat-seconds} 发注释帧；
 * 推送失败即摘除该连接，不阻塞其它订阅者。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class TwinStreamService {

    /** 心跳间隔下限（秒） */
    private static final int MIN_HEARTBEAT_SECONDS = 5;

    /** 事件名：连接就绪 */
    public static final String EVENT_READY = "ready";

    /** 事件名：绑定实时态批次 */
    public static final String EVENT_BINDING = "binding";

    private final TwinProperties properties;

    /** 场景ID → 订阅者列表 */
    private final Map<Long, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    /** 心跳调度线程池（单线程足够：任务为轻量注释帧写回） */
    private final ScheduledExecutorService scheduler;

    /**
     * 构造推送服务并启动心跳调度。
     *
     * @param properties 孪生配置
     */
    public TwinStreamService(TwinProperties properties) {
        this.properties = properties;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "twin-sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        int interval = Math.max(MIN_HEARTBEAT_SECONDS, properties.getStreamHeartbeatSeconds());
        scheduler.scheduleAtFixedRate(this::heartbeat, interval, interval, TimeUnit.SECONDS);
    }

    /**
     * 订阅场景实时事件。
     *
     * @param sceneId 场景ID
     * @return SSE 发射器
     */
    public SseEmitter subscribe(Long sceneId) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(sceneId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeConnection(sceneId, emitter));
        emitter.onTimeout(() -> removeConnection(sceneId, emitter));
        emitter.onError(throwable -> removeConnection(sceneId, emitter));
        try {
            emitter.send(SseEmitter.event().name(EVENT_READY)
                    .data(Map.of("sceneId", sceneId.toString())));
        } catch (IOException | IllegalStateException e) {
            removeConnection(sceneId, emitter);
        }
        return emitter;
    }

    /**
     * 向某场景的所有订阅者推送事件。
     *
     * @param sceneId 场景ID
     * @param event   事件名
     * @param data    事件数据（由 MVC 序列化为 JSON）
     * @return 成功送达的订阅者数量
     */
    public int publish(Long sceneId, String event, Object data) {
        List<SseEmitter> emitters = subscribers.get(sceneId);
        if (emitters == null || emitters.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
                delivered++;
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE 推送失败，摘除连接：sceneId={}, event={}", sceneId, event);
                removeConnection(sceneId, emitter);
            }
        }
        return delivered;
    }

    /**
     * 当前有活跃订阅者的场景集合（运行时轮询只刷这些场景，省 whale-iot 配额）。
     *
     * @return 场景ID集合
     */
    public Set<Long> activeScenes() {
        Set<Long> result = ConcurrentHashMap.newKeySet();
        for (Map.Entry<Long, List<SseEmitter>> entry : subscribers.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * 当前活跃订阅连接总数（运维观测用）。
     *
     * @return 连接数
     */
    public int activeConnections() {
        return subscribers.values().stream().mapToInt(List::size).sum();
    }

    /**
     * 摘除连接（幂等；包内可见便于单测直测清理逻辑）。
     *
     * @param sceneId 场景ID
     * @param emitter 发射器
     */
    void removeConnection(Long sceneId, SseEmitter emitter) {
        List<SseEmitter> emitters = subscribers.get(sceneId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                subscribers.remove(sceneId, emitters);
            }
        }
    }

    /**
     * 心跳：向所有连接发送注释帧并顺带清理失效连接。
     */
    private void heartbeat() {
        for (Map.Entry<Long, List<SseEmitter>> entry : subscribers.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("keepalive"));
                } catch (IOException | IllegalStateException e) {
                    removeConnection(entry.getKey(), emitter);
                }
            }
        }
    }

    /**
     * 停服时关掉心跳线程池。
     */
    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
