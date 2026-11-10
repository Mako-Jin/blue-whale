package com.bluewhale.contract.esign.reconcile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.contract.config.ContractEsignProperties;
import com.bluewhale.contract.esign.provider.EsignCallbackSigner;
import com.bluewhale.contract.esign.provider.EsignProvider;
import com.bluewhale.contract.esign.provider.EsignProviderRegistry;
import com.bluewhale.contract.esign.provider.EsignTaskStatus;
import com.bluewhale.contract.entity.CtSignTask;
import com.bluewhale.contract.mapper.CtSignTaskMapper;
import com.bluewhale.contract.service.EsignCallbackService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 三方签署回调补偿任务：兜底乱序、丢失或延迟的回调。
 *
 * <p>扫描停留超过 {@code stale-seconds} 且仍为 {@code SIGNING} 的非 local 任务，
 * 调用渠道 {@code queryTask} 查询真实状态；发现终态时按回调协议自签 HMAC 请求并交由
 * {@link EsignCallbackService#handle} 处理——与真实回调共用验签、唯一键幂等、证据落库
 * 与工作流推进链路，因此重复执行天然安全。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "whale.contract.esign.reconcile", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SignTaskReconcileJob {

    /** 待签署任务状态 */
    private static final String TASK_SIGNING = "SIGNING";

    /** 本地渠道（不参与轮询） */
    private static final String PROVIDER_LOCAL = "LOCAL";

    /** 签署动作 */
    private static final String ACTION_SIGN = "SIGN";

    /** 拒签动作 */
    private static final String ACTION_REFUSE = "REFUSE";

    /** 三方状态：已签署 */
    private static final String RESULT_SIGNED = "SIGNED";

    /** 三方状态：已完成 */
    private static final String RESULT_FINISHED = "FINISHED";

    /** 三方状态：已完成（部分平台命名） */
    private static final String RESULT_COMPLETED = "COMPLETED";

    /** 三方状态：已拒签 */
    private static final String RESULT_REFUSED = "REFUSED";

    /** 三方状态：已驳回 */
    private static final String RESULT_REJECTED = "REJECTED";

    /** 默认拒签原因 */
    private static final String DEFAULT_REFUSE_REASON = "三方平台返回拒签";

    /** JSON 序列化（Jackson 2，兼容 Spring Boot 4） */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CtSignTaskMapper signTaskMapper;

    private final EsignProviderRegistry providerRegistry;

    private final EsignCallbackService callbackService;

    private final ContractEsignProperties properties;

    /**
     * 轮询补偿入口，默认 60 秒一轮、启动后 30 秒首跑（均可环境变量覆盖）。
     */
    @Scheduled(fixedDelayString = "${whale.contract.esign.reconcile.interval-ms:60000}",
            initialDelayString = "${whale.contract.esign.reconcile.initial-delay-ms:30000}")
    public void reconcile() {
        ContractEsignProperties.Reconcile config = properties.getReconcile();
        if (!config.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(properties.getCallbackSecret())) {
            log.warn("签署回调补偿跳过：未配置 callback-secret，无法生成内部签名回调");
            return;
        }
        List<CtSignTask> tasks = signTaskMapper.selectList(staleQuery(config));
        if (tasks.isEmpty()) {
            return;
        }
        for (CtSignTask task : tasks) {
            try {
                reconcileOne(task);
            } catch (Exception ex) {
                log.warn("签署回调补偿失败：taskId={}，原因={}", task.getId(), ex.getMessage());
            }
        }
    }

    /**
     * 构造待补偿任务查询：仅非 local 且已拿到三方任务号的超时 {@code SIGNING} 任务。
     *
     * @param config 补偿配置
     * @return 查询条件
     */
    private LambdaQueryWrapper<CtSignTask> staleQuery(ContractEsignProperties.Reconcile config) {
        int limit = Math.max(1, config.getBatchSize());
        return new LambdaQueryWrapper<CtSignTask>()
                .eq(CtSignTask::getStatus, TASK_SIGNING)
                .isNotNull(CtSignTask::getProviderTaskId)
                .ne(CtSignTask::getProvider, PROVIDER_LOCAL)
                .le(CtSignTask::getUpdateTime, LocalDateTime.now().minusSeconds(config.getStaleSeconds()))
                .orderByAsc(CtSignTask::getUpdateTime)
                .last("LIMIT " + limit);
    }

    /**
     * 补偿单个任务。
     *
     * @param task 签署任务
     */
    private void reconcileOne(CtSignTask task) {
        String providerName = task.getProvider().toLowerCase(Locale.ROOT);
        EsignProvider provider = providerRegistry.require(providerName);
        EsignTaskStatus status = provider.queryTask(task.getProviderTaskId());
        String action = mapAction(status);
        if (action == null) {
            log.debug("签署任务 {} 三方状态 {}，暂不推进", task.getId(), status == null ? null : status.status());
            return;
        }
        deliver(provider.name(), task, action, status == null ? null : status.message());
        log.info("签署回调补偿推进任务 {}（provider={}, action={}）", task.getId(), provider.name(), action);
    }

    /**
     * 将三方状态映射为回调动作。
     *
     * @param status 三方任务状态
     * @return {@code SIGN}/{@code REFUSE}；非终态返回 {@code null}
     */
    private String mapAction(EsignTaskStatus status) {
        if (status == null || !StringUtils.hasText(status.status())) {
            return null;
        }
        String normalized = status.status().trim().toUpperCase(Locale.ROOT);
        if (RESULT_SIGNED.equals(normalized) || RESULT_FINISHED.equals(normalized)
                || RESULT_COMPLETED.equals(normalized)) {
            return ACTION_SIGN;
        }
        if (RESULT_REFUSED.equals(normalized) || RESULT_REJECTED.equals(normalized)) {
            return ACTION_REFUSE;
        }
        return null;
    }

    /**
     * 生成内部签名回调并交由回调服务处理（幂等键为 {@code RECONCILE-<taskId>-<action>}）。
     *
     * @param providerName 渠道名（小写）
     * @param task         签署任务
     * @param action       回调动作
     * @param reason       三方返回说明（拒签原因）
     */
    private void deliver(String providerName, CtSignTask task, String action, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("providerEventId", "RECONCILE-" + task.getId() + "-" + action);
        payload.put("action", action);
        payload.put("signTaskId", String.valueOf(task.getId()));
        payload.put("providerTaskId", task.getProviderTaskId());
        if (ACTION_REFUSE.equals(action)) {
            payload.put("refuseReason", StringUtils.hasText(reason) ? reason : DEFAULT_REFUSE_REASON);
        }
        String body = toJson(payload);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> headers = new HashMap<>();
        headers.put(EsignCallbackSigner.HEADER_TIMESTAMP, timestamp);
        headers.put(EsignCallbackSigner.HEADER_NONCE, nonce);
        headers.put(EsignCallbackSigner.HEADER_SIGNATURE,
                EsignCallbackSigner.sign(properties.getCallbackSecret(), timestamp, nonce, body));
        callbackService.handle(providerName, body, headers);
    }

    /**
     * JSON 序列化回调体。
     *
     * @param payload 回调负载
     * @return JSON 字符串
     */
    private String toJson(Map<String, Object> payload) {
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("签署回调补偿负载序列化失败", ex);
        }
    }
}
