package com.bluewhale.twin.service;

import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.dto.BindingAlertVO;
import com.bluewhale.twin.support.TwinAlertNotifyFilter;
import com.bluewhale.twin.support.TwinAlertWebhookSender;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * 告警外发服务（Wave 6.10 M0）：级别变化后把事件推到外部 webhook。
 *
 * <p>调用点在 {@code TwinBindingRuntimeService.publishAlerts}——前台 tick 与无头通道
 * 共用的唯一告警出生地，且排在落库之后：外发失败不影响落库与页面可见性。</p>
 *
 * <p>投递走虚拟线程异步执行（对齐 {@code TwinInsightService} 既有做法），
 * tick 不会被外部 HTTP 拖慢；失败不重试，等下一次级别变化。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class TwinAlertNotifyService {

    private final TwinProperties properties;

    private final TwinAlertWebhookSender sender;

    private final TwinAlertNotifyFilter filter;

    private final Executor executor;

    /**
     * 生产用构造：虚拟线程执行器，关闭时统一回收。
     *
     * <p>类里有测试用的第二个构造器，必须显式标注 {@code @Autowired}，
     * 否则 Spring 会去找无参构造而启动失败。</p>
     *
     * @param properties 孪生配置
     * @param sender     webhook 投递器
     */
    @Autowired
    public TwinAlertNotifyService(TwinProperties properties, TwinAlertWebhookSender sender) {
        this(properties, sender, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * 测试用构造：可注入同步执行器，避免断言与异步的竞态。
     *
     * @param properties 孪生配置
     * @param sender     webhook 投递器
     * @param executor   任务执行器
     */
    TwinAlertNotifyService(TwinProperties properties, TwinAlertWebhookSender sender, Executor executor) {
        this.properties = properties;
        this.sender = sender;
        this.executor = executor;
        TwinProperties.AlertNotify config = properties.getAlertNotify();
        this.filter = new TwinAlertNotifyFilter(config.getMinLevel(), config.getCooldownSeconds());
    }

    /**
     * 告警级别变化后的外发入口（同级重复、恢复、低于门槛、未开开关均静默跳过）。
     *
     * @param sceneId  场景ID
     * @param tenantId 租户ID
     * @param alert    告警内容（与 SSE 帧同源）
     */
    public void notify(Long sceneId, Long tenantId, BindingAlertVO alert) {
        if (alert == null) {
            return;
        }
        TwinProperties.AlertNotify config = properties.getAlertNotify();
        List<String> urls = config.getWebhookUrls();
        if (!config.isEnabled() || urls == null || urls.isEmpty()) {
            return;
        }
        if (!filter.accept(sceneId, alert.getStateKey(), alert.getLevel(), System.currentTimeMillis())) {
            return;
        }
        Map<String, Object> payload = buildPayload(sceneId, tenantId, alert);
        try {
            executor.execute(() -> {
                for (String url : urls) {
                    sender.send(url, payload);
                }
            });
        } catch (RejectedExecutionException error) {
            // 关停中：丢弃即可，落库与页面可见性不受影响
            log.debug("告警外发执行器已关闭，丢弃本次投递：sceneId={}, stateKey={}",
                    sceneId, alert.getStateKey());
        }
    }

    /**
     * 关闭执行器（仅自有线程池才需要回收）。
     */
    @PreDestroy
    void shutdown() {
        if (executor instanceof ExecutorService service) {
            service.shutdown();
        }
    }

    /**
     * 组装 webhook 载荷：字段与 SSE {@code alert} 帧保持一致，接收端可用同一套解析。
     *
     * @param sceneId  场景ID
     * @param tenantId 租户ID
     * @param alert    告警内容
     * @return 载荷
     */
    private Map<String, Object> buildPayload(Long sceneId, Long tenantId, BindingAlertVO alert) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "twin.alert");
        payload.put("sceneId", sceneId == null ? null : sceneId.toString());
        payload.put("tenantId", tenantId == null ? null : tenantId.toString());
        payload.put("bindingId", alert.getBindingId());
        payload.put("entityId", alert.getEntityId());
        payload.put("entityCode", alert.getEntityCode());
        payload.put("stateKey", alert.getStateKey());
        payload.put("level", alert.getLevel());
        payload.put("previous", alert.getPrevious());
        payload.put("value", alert.getValue());
        payload.put("thresholds", alert.getThresholds());
        payload.put("ts", alert.getTs());
        return payload;
    }
}
