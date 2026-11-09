package com.bluewhale.iot.service;

import com.bluewhale.iot.config.IotProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 设备实时事件推送服务（SSE，对齐 MES 看板先例）。
 *
 * <p>订阅粒度是<b>单设备</b>：{@code GET /iot/devices/{id}/stream} 建立长连接后，
 * 遥测入库、指令状态变化、事件上报都会即时推给该设备的订阅者。</p>
 *
 * <p>连接不设超时（由客户端断开或心跳失败回收）；心跳按
 * {@code whale.iot.stream-heartbeat-seconds} 发送注释帧，避免 Nginx/网关空闲掐链。
 * 推送失败即摘除该连接，不阻塞其它订阅者。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class IotStreamService {

    /** 心跳间隔下限（秒） */
    private static final int MIN_HEARTBEAT_SECONDS = 5;

    /** 事件名：连接就绪 */
    public static final String EVENT_READY = "ready";

    /** 事件名：遥测 */
    public static final String EVENT_TELEMETRY = "telemetry";

    /** 事件名：事件上报 */
    public static final String EVENT_EVENT = "event";

    /** 事件名：指令状态 */
    public static final String EVENT_COMMAND = "command";

    private final IotProperties properties;

    /** 设备ID → 订阅者列表 */
    private final Map<Long, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    /** 心跳调度线程池（单线程足够：任务为轻量注释帧写回） */
    private final ScheduledExecutorService scheduler;

    /**
     * 构造推送服务并启动心跳调度。
     *
     * @param properties IoT 配置
     */
    public IotStreamService(IotProperties properties) {
        this.properties = properties;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "iot-sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        int interval = Math.max(MIN_HEARTBEAT_SECONDS, properties.getStreamHeartbeatSeconds());
        scheduler.scheduleAtFixedRate(this::heartbeat, interval, interval, TimeUnit.SECONDS);
    }

    /**
     * 订阅单设备实时事件。
     *
     * @param deviceId 设备ID
     * @return SSE 发射器
     */
    public SseEmitter subscribe(Long deviceId) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(deviceId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeConnection(deviceId, emitter));
        emitter.onTimeout(() -> removeConnection(deviceId, emitter));
        emitter.onError(throwable -> removeConnection(deviceId, emitter));
        try {
            emitter.send(SseEmitter.event().name(EVENT_READY).data(Map.of("deviceId", deviceId.toString())));
        } catch (IOException | IllegalStateException e) {
            removeConnection(deviceId, emitter);
        }
        return emitter;
    }

    /**
     * 向某设备的所有订阅者推送事件。
     *
     * @param deviceId 设备ID
     * @param event    事件名
     * @param data     事件数据（由 MVC 序列化为 JSON）
     * @return 成功送达的订阅者数量
     */
    public int publish(Long deviceId, String event, Object data) {
        List<SseEmitter> emitters = subscribers.get(deviceId);
        if (emitters == null || emitters.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
                delivered++;
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE 推送失败，摘除连接：deviceId={}, event={}", deviceId, event);
                removeConnection(deviceId, emitter);
            }
        }
        return delivered;
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
     * 心跳：向所有连接发送注释帧并顺带清理失效连接。
     */
    void heartbeat() {
        subscribers.forEach((deviceId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    removeConnection(deviceId, emitter);
                }
            }
        });
    }

    /**
     * 摘除连接（幂等）。
     *
     * <p>正常链路由 MVC 的完成/超时/异常回调触发；包内可见以便单测直接验证摘除逻辑。</p>
     *
     * @param deviceId 设备ID
     * @param emitter  发射器
     */
    void removeConnection(Long deviceId, SseEmitter emitter) {
        List<SseEmitter> emitters = subscribers.get(deviceId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            subscribers.remove(deviceId, emitters);
        }
    }

    /**
     * 容器关闭时停止心跳线程。
     */
    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
