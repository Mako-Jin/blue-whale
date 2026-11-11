package com.bluewhale.twin.service;

import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.dto.BindingAlertVO;
import com.bluewhale.twin.entity.TwinAlertDispatch;
import com.bluewhale.twin.support.TwinAlertNotifyFilter;
import com.bluewhale.twin.support.TwinAlertRecipientResolver;
import com.bluewhale.twin.support.TwinAlertWebhookSender;
import com.bluewhale.twin.support.TwinMessageClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * 告警外发服务（Wave 6.10）：级别变化后把事件推到外部 webhook 与站内信。
 *
 * <p>调用点在 {@code TwinBindingRuntimeService.publishAlerts}——前台 tick 与无头通道
 * 共用的唯一告警出生地，且排在落库之后：外发失败不影响落库与页面可见性。</p>
 *
 * <p>两个通道共用同一套「级别门槛 + 冷却去重」过滤：一次级别变化最多触发一轮外发，
 * 不会因为配了两个通道而打扰两次不同频次的通知。投递走虚拟线程异步执行，
 * tick 不会被外部 HTTP 拖慢；失败不重试，等下一次级别变化。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class TwinAlertNotifyService {

    /** 告警标题前缀 */
    private static final String TITLE_PREFIX = "【孪生告警·";

    /** 幂等键前缀（whale-message 侧按租户内幂等） */
    private static final String IDEMPOTENCY_PREFIX = "twin-alert-";

    /** 标题列宽（whale-message 上限 128） */
    private static final int TITLE_MAX_LENGTH = 120;

    /** 级别：严重 */
    private static final String LEVEL_CRITICAL = "critical";

    /** 级别：恢复（不落告警行，只回填恢复时间） */
    private static final String LEVEL_NORMAL = "normal";

    /** 恢复通知标题前缀（Wave 6.11 M0） */
    private static final String RECOVERY_PREFIX = "【孪生恢复】";

    /** 事件：新告警（与 SSE 帧同名） */
    private static final String EVENT_ALERT = "twin.alert";

    /** 事件：恢复（Wave 6.11 M0） */
    private static final String EVENT_RECOVERED = "twin.alert.recovered";

    private final TwinProperties properties;

    private final TwinAlertWebhookSender sender;

    private final TwinMessageClient messageClient;

    private final TwinAlertRecipientResolver recipientResolver;

    private final TwinAlertDispatchService dispatchService;

    private final TwinAlertNotifySettingsService settingsService;

    private final TwinAlertNotifyFilter filter;

    private final Executor executor;

    /**
     * 生产用构造：虚拟线程执行器，关闭时统一回收。
     *
     * <p>类里有测试用的第二个构造器，必须显式标注 {@code @Autowired}，
     * 否则 Spring 会去找无参构造而启动失败。</p>
     *
     * @param properties        孪生配置
     * @param sender            webhook 投递器
     * @param messageClient     whale-message 内网客户端
     * @param recipientResolver 接收人解析
     * @param dispatchService   投递记录服务
     * @param settingsService   场景级通知设置
     */
    @Autowired
    public TwinAlertNotifyService(TwinProperties properties, TwinAlertWebhookSender sender,
                                  TwinMessageClient messageClient,
                                  TwinAlertRecipientResolver recipientResolver,
                                  TwinAlertDispatchService dispatchService,
                                  TwinAlertNotifySettingsService settingsService) {
        this(properties, sender, messageClient, recipientResolver, dispatchService, settingsService,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * 测试用构造：可注入同步执行器，避免断言与异步的竞态。
     *
     * @param properties        孪生配置
     * @param sender            webhook 投递器
     * @param messageClient     whale-message 内网客户端
     * @param recipientResolver 接收人解析
     * @param dispatchService   投递记录服务
     * @param settingsService   场景级通知设置
     * @param executor          任务执行器
     */
    TwinAlertNotifyService(TwinProperties properties, TwinAlertWebhookSender sender,
                           TwinMessageClient messageClient,
                           TwinAlertRecipientResolver recipientResolver,
                           TwinAlertDispatchService dispatchService,
                           TwinAlertNotifySettingsService settingsService,
                           Executor executor) {
        this.properties = properties;
        this.sender = sender;
        this.messageClient = messageClient;
        this.recipientResolver = recipientResolver;
        this.dispatchService = dispatchService;
        this.settingsService = settingsService;
        this.executor = executor;
        TwinProperties.AlertNotify config = properties.getAlertNotify();
        this.filter = new TwinAlertNotifyFilter(config.getMinLevel(), config.getCooldownSeconds());
    }

    /**
     * 告警级别变化后的外发入口（同级重复、低于门槛、通道全关、静默期内均跳过）。
     *
     * <p>Wave 6.11 起 {@code level=normal} 走恢复通道：只有<b>当初真的通知过</b>的告警
     * 才回一条「已恢复」，且静默判定按被恢复的级别做（严重告警的恢复照样穿透静默）。</p>
     *
     * @param sceneId  场景ID
     * @param tenantId 租户ID
     * @param alertId  告警ID（已落库行的主键；恢复事件为空）
     * @param alert    告警内容（与 SSE 帧同源）
     */
    public void notify(Long sceneId, Long tenantId, Long alertId, BindingAlertVO alert) {
        if (alert == null) {
            return;
        }
        TwinProperties.AlertNotify config = properties.getAlertNotify();
        // 场景级设置只能收紧：关通道 / 抬高门槛，突破不了全局开关
        TwinAlertNotifySettingsService.SceneSettings sceneSettings = settingsService.current(sceneId);
        List<String> urls = config.getWebhookUrls();
        boolean webhookReady = config.isEnabled() && urls != null && !urls.isEmpty()
                && settingsService.webhookAllowed(sceneSettings);
        boolean inappReady = config.isInappEnabled() && settingsService.inappAllowed(sceneSettings);
        if (!webhookReady && !inappReady) {
            return;
        }
        if (LEVEL_NORMAL.equals(alert.getLevel())) {
            notifyRecovery(sceneId, tenantId, alert, sceneSettings,
                    webhookReady ? List.copyOf(urls) : List.of(), inappReady);
            return;
        }
        if (!settingsService.reachesMinLevel(alert.getLevel(), sceneSettings)) {
            return;
        }
        if (settingsService.quietActive(sceneSettings, alert.getLevel(), LocalTime.now())) {
            log.debug("静默时段跳过外发：sceneId={}, stateKey={}, level={}",
                    sceneId, alert.getStateKey(), alert.getLevel());
            return;
        }
        if (!filter.accept(sceneId, alert.getStateKey(), alert.getLevel(), System.currentTimeMillis())) {
            return;
        }
        Map<String, Object> payload = buildPayload(sceneId, tenantId, alert);
        List<String> targets = webhookReady ? List.copyOf(urls) : List.of();
        execute(sceneId, alert, () -> dispatchAll(sceneId, tenantId, alertId, alert, payload,
                targets, inappReady, EVENT_ALERT));
    }

    /**
     * 恢复通知（Wave 6.11 M0）：级别回落到 normal 时补一条「已恢复」。
     *
     * <p>三个前提缺一不可：全局与场景都允许恢复通知、这次告警当初确实外发过、
     * 被恢复的级别没被静默拦下。投递记录沿用同一张表，{@code level} 记 {@code normal}
     * 以便与告警本身区分。</p>
     *
     * @param sceneId       场景ID
     * @param tenantId      租户ID
     * @param alert         恢复事件（level=normal，previous 为被恢复的级别）
     * @param sceneSettings 场景设置
     * @param urls          webhook 地址（空表示该通道未启用）
     * @param inapp         站内信通道是否生效
     */
    private void notifyRecovery(Long sceneId, Long tenantId, BindingAlertVO alert,
                                TwinAlertNotifySettingsService.SceneSettings sceneSettings,
                                List<String> urls, boolean inapp) {
        if (!settingsService.recoveryAllowed(sceneSettings)) {
            return;
        }
        String previous = filter.acceptRecovery(sceneId, alert.getStateKey());
        if (previous == null) {
            // 当初没通知出去（低于门槛或被静默拦下），恢复也不该凭空发一条
            return;
        }
        if (settingsService.quietActive(sceneSettings, previous, LocalTime.now())) {
            log.debug("静默时段跳过恢复通知：sceneId={}, stateKey={}, previous={}",
                    sceneId, alert.getStateKey(), previous);
            return;
        }
        Map<String, Object> payload = buildRecoveryPayload(sceneId, tenantId, alert, previous);
        execute(sceneId, alert, () -> dispatchAll(sceneId, tenantId, null, alert, payload,
                urls, inapp, EVENT_RECOVERED));
    }

    /**
     * 提交到虚拟线程执行器（关停中直接丢弃，不影响 tick）。
     *
     * @param sceneId 场景ID
     * @param alert   告警内容（仅用于日志）
     * @param task    投递任务
     */
    private void execute(Long sceneId, BindingAlertVO alert, Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException error) {
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
     * 按通道逐个投递：webhook 逐地址、站内信一次多人；每次尝试都落投递记录。
     *
     * @param sceneId  场景ID
     * @param tenantId 租户ID
     * @param alertId  告警ID
     * @param alert    告警内容
     * @param payload  事件载荷
     * @param urls     webhook 地址列表（空表示该通道未启用）
     * @param inapp    站内信通道是否生效（已叠加场景开关）
     */
    private void dispatchAll(Long sceneId, Long tenantId, Long alertId, BindingAlertVO alert,
                             Map<String, Object> payload, List<String> urls, boolean inapp,
                             String event) {
        for (String url : urls) {
            boolean sent = sender.send(url, payload);
            recordDispatch(tenantId, sceneId, alertId, alert, event,
                    TwinAlertDispatchService.CHANNEL_WEBHOOK, url,
                    sent ? TwinAlertDispatchService.STATUS_SUCCESS
                            : TwinAlertDispatchService.STATUS_FAILED,
                    null, sent ? null : "webhook 投递失败（HTTP 非 2xx 或连接异常）");
        }
        if (inapp) {
            sendInapp(sceneId, tenantId, alertId, alert, payload, event);
        }
    }

    /**
     * 站内信通道：解析接收人 → 调 whale-message 内网发送 → 落投递记录。
     *
     * @param sceneId  场景ID
     * @param tenantId 租户ID
     * @param alertId  告警ID
     * @param alert    告警内容
     * @param payload  事件载荷
     */
    private void sendInapp(Long sceneId, Long tenantId, Long alertId, BindingAlertVO alert,
                           Map<String, Object> payload, String event) {
        TwinAlertRecipientResolver.Recipients recipients = recipientResolver.resolve(sceneId);
        if (recipients.userIds().isEmpty()) {
            recordDispatch(tenantId, sceneId, alertId, alert, event,
                    TwinAlertDispatchService.CHANNEL_INAPP,
                    "无接收人", TwinAlertDispatchService.STATUS_SKIPPED, null,
                    "场景未配置 alertNotify.userIds 且无创建人");
            return;
        }
        // Wave 6.17c：站内信接收人按用户偏好过滤（偏好数据在 auth、语义解释在 message）。
        // 解析失败返回 null → 全部放行（fail-open）；偏好只能收紧接收人集合，铁律不动。
        List<Long> userIds = filterByPreference(tenantId, recipients.userIds(), alert.getLevel());
        if (userIds.isEmpty()) {
            recordDispatch(tenantId, sceneId, alertId, alert, event,
                    TwinAlertDispatchService.CHANNEL_INAPP,
                    "userIds=0", TwinAlertDispatchService.STATUS_SKIPPED, null,
                    "全部接收人被用户偏好过滤（渠道关闭或静默窗口）");
            return;
        }
        TwinMessageClient.SendOutcome outcome = messageClient.sendInapp(tenantId,
                idempotencyKey(sceneId, alertId, alert, event),
                title(recipients.sceneName(), alert, event),
                content(recipients.sceneName(), alert, event),
                payload, userIds);
        recordDispatch(tenantId, sceneId, alertId, alert, event,
                TwinAlertDispatchService.CHANNEL_INAPP,
                "userIds=" + userIds.size(),
                outcome.ok() ? TwinAlertDispatchService.STATUS_SUCCESS
                        : TwinAlertDispatchService.STATUS_FAILED,
                outcome.messageId(), outcome.error());
    }

    /**
     * 按用户偏好过滤站内信接收人（Wave 6.17c）。
     *
     * <p>开关关闭或解析失败（{@code null}）时全部放行；critical 穿透由全局开关
     * {@code quietHoursCriticalBypass} 转成 message 侧的静默豁免级别。</p>
     *
     * @param tenantId 租户ID
     * @param userIds  候选接收人
     * @param level    本次告警级别
     * @return 过滤后仍可投的用户列表
     */
    private List<Long> filterByPreference(Long tenantId, List<Long> userIds, String level) {
        if (!properties.getAlertNotify().isPreferenceEnabled()) {
            return userIds;
        }
        List<Long> allowed = messageClient.resolveInappRecipients(tenantId, userIds, level,
                properties.getAlertNotify().isQuietHoursCriticalBypass());
        return allowed != null ? allowed : userIds;
    }

    /**
     * 落一条投递记录（失败只记 WARN，旁路不影响主链路）。
     *
     * @param tenantId  租户ID
     * @param sceneId   场景ID
     * @param alertId   告警ID
     * @param alert     告警内容
     * @param channel   通道
     * @param target    投递目标
     * @param status    结果
     * @param messageId 站内信消息ID（webhook 为空）
     * @param error     失败原因（成功为空）
     */
    private void recordDispatch(Long tenantId, Long sceneId, Long alertId, BindingAlertVO alert,
                                String event, String channel, String target, String status,
                                Long messageId, String error) {
        TwinAlertDispatch dispatch = new TwinAlertDispatch();
        dispatch.setTenantId(tenantId);
        dispatch.setAlertId(alertId);
        dispatch.setSceneId(sceneId);
        dispatch.setStateKey(alert.getStateKey());
        // 恢复投递记 normal（区别于告警本身），前端按级别标签渲染成「已恢复」
        dispatch.setLevel(EVENT_RECOVERED.equals(event) ? LEVEL_NORMAL : alert.getLevel());
        dispatch.setChannel(channel);
        dispatch.setTarget(target);
        dispatch.setStatus(status);
        dispatch.setMessageId(messageId);
        dispatch.setErrorMsg(error);
        dispatchService.record(dispatch);
    }

    /**
     * 幂等键：同一告警行固定不变，重复投递由 whale-message 去重。
     *
     * @param sceneId 场景ID
     * @param alertId 告警ID
     * @param alert   告警内容
     * @return 幂等键（不超过 96 字符）
     */
    private String idempotencyKey(Long sceneId, Long alertId, BindingAlertVO alert, String event) {
        if (alertId != null) {
            return IDEMPOTENCY_PREFIX + alertId;
        }
        String prefix = EVENT_RECOVERED.equals(event) ? IDEMPOTENCY_PREFIX + "recovery-" : IDEMPOTENCY_PREFIX;
        return prefix + sceneId + "-" + alert.getStateKey() + "-" + alert.getTs();
    }

    /**
     * 通知标题：级别 + 场景名 + 点位（恢复通知改前缀，不带级别）。
     *
     * @param sceneName 场景名称（可空）
     * @param alert     告警内容
     * @param event     事件类型
     * @return 标题（超长截断）
     */
    private String title(String sceneName, BindingAlertVO alert, String event) {
        StringBuilder title;
        if (EVENT_RECOVERED.equals(event)) {
            title = new StringBuilder(RECOVERY_PREFIX);
            if (StringUtils.hasText(sceneName)) {
                title.append(sceneName).append(" / ");
            }
            title.append(point(alert));
        } else {
            title = new StringBuilder(TITLE_PREFIX)
                    .append(levelLabel(alert.getLevel())).append("】");
            if (StringUtils.hasText(sceneName)) {
                title.append(sceneName).append(" / ");
            }
            title.append(point(alert));
        }
        String text = title.toString();
        return text.length() <= TITLE_MAX_LENGTH ? text : text.substring(0, TITLE_MAX_LENGTH);
    }

    /**
     * 通知正文：纯文本多行，接收端无需渲染即可阅读。
     *
     * @param sceneName 场景名称（可空）
     * @param alert     告警内容
     * @param event     事件类型
     * @return 正文
     */
    private String content(String sceneName, BindingAlertVO alert, String event) {
        if (EVENT_RECOVERED.equals(event)) {
            return new StringBuilder()
                    .append("场景：").append(StringUtils.hasText(sceneName) ? sceneName : "-").append('\n')
                    .append("点位：").append(point(alert)).append('\n')
                    .append("状态：已恢复（原级别：").append(levelLabel(alert.getPrevious())).append("）\n")
                    .append("当前值：").append(alert.getValue()).append('\n')
                    .append("时间：").append(alert.getTs())
                    .toString();
        }
        StringBuilder content = new StringBuilder()
                .append("场景：").append(StringUtils.hasText(sceneName) ? sceneName : "-").append('\n')
                .append("点位：").append(point(alert)).append('\n')
                .append("级别：").append(levelLabel(alert.getLevel()));
        if (StringUtils.hasText(alert.getPrevious())) {
            content.append("（上一级别：").append(levelLabel(alert.getPrevious())).append('）');
        }
        content.append('\n').append("当前值：").append(alert.getValue()).append('\n');
        if (alert.getThresholds() != null && !alert.getThresholds().isEmpty()) {
            content.append("阈值：").append(alert.getThresholds()).append('\n');
        }
        content.append("时间：").append(alert.getTs());
        return content.toString();
    }

    /**
     * 点位展示：{@code 实体编码.状态键}（实体编码缺失时只显示状态键）。
     *
     * @param alert 告警内容
     * @return 点位文本
     */
    private String point(BindingAlertVO alert) {
        return StringUtils.hasText(alert.getEntityCode())
                ? alert.getEntityCode() + "." + alert.getStateKey()
                : alert.getStateKey();
    }

    /**
     * 级别中文名。
     *
     * @param level 级别
     * @return 中文标签
     */
    private String levelLabel(String level) {
        return LEVEL_CRITICAL.equals(level) ? "严重" : "预警";
    }

    /**
     * 组装事件载荷：字段与 SSE {@code alert} 帧保持一致，接收端可用同一套解析。
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

    /**
     * 组装恢复事件载荷：字段与告警帧同源，另加 {@code recoveredFrom}。
     *
     * @param sceneId  场景ID
     * @param tenantId 租户ID
     * @param alert    恢复事件
     * @param previous 被恢复的级别
     * @return 载荷
     */
    private Map<String, Object> buildRecoveryPayload(Long sceneId, Long tenantId,
                                                     BindingAlertVO alert, String previous) {
        Map<String, Object> payload = buildPayload(sceneId, tenantId, alert);
        payload.put("event", EVENT_RECOVERED);
        payload.put("recoveredFrom", previous);
        return payload;
    }
}
