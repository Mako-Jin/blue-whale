package com.bluewhale.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.message.config.MessageReceiptProperties;
import com.bluewhale.message.po.MessageCallbackTaskPo;
import com.bluewhale.message.mapper.MessageCallbackTaskMapper;
import com.bluewhale.message.support.CallbackUrlGuard;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 回执回调派发器（outbox 消费者）。
 *
 * <p>按 {@code next_retry_at} 取待发任务，调用业务方登记的回调地址；成功置 {@code SENT}，
 * 失败按 2 的幂次退避重试，超过 {@code max-attempts} 置 {@code GAVE_UP} 并保留最后错误。
 * 后台线程无租户上下文，租户头取任务自身落库的 {@code tenant_id}。</p>
 *
 * <p><b>投递语义为 at-least-once</b>：HTTP 成功与任务状态落库不在同一事务内，进程崩溃可能
 * 导致同一任务被再次投递；业务方必须以 {@code taskId}（或回执 {@code receiptId}）做幂等。</p>
 *
 * <p><b>多实例安全</b>：任务派发前先按状态原子抢占
 * （{@code UPDATE ... WHERE status IN ('PENDING','FAILED')} 置为 {@code DISPATCHING}），
 * 抢到的实例才发起回调；抢占时间超过 {@code dispatch-timeout-millis} 仍停留在
 * {@code DISPATCHING} 的任务视为实例崩溃，回收为 {@code FAILED} 并立即可重试。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "whale.message.receipt", name = "callback-enabled",
        havingValue = "true", matchIfMissing = true)
public class MessageCallbackDispatcher {

    /** 待派发状态 */
    private static final String STATUS_PENDING = "PENDING";

    /** 失败待重试状态 */
    private static final String STATUS_FAILED = "FAILED";

    /** 派发成功 */
    private static final String STATUS_SENT = "SENT";

    /** 超过重试上限，放弃 */
    private static final String STATUS_GAVE_UP = "GAVE_UP";

    /** 错误摘要最大长度（列宽 512） */
    private static final int ERROR_MAX_LENGTH = 512;

    /** 退避上限（秒），避免 2^n 溢出成超长休眠 */
    private static final long MAX_BACKOFF_SECONDS = 3600L;

    /** 业务响应体状态码字段 */
    private static final String FIELD_CODE = "code";

    /** 业务响应体消息字段 */
    private static final String FIELD_MESSAGE = "message";

    /** 业务成功码 */
    private static final int CODE_SUCCESS = 0;

    private final MessageCallbackTaskMapper callbackTaskMapper;

    private final MessageReceiptProperties properties;

    /** 实例标识：多实例抢占时标记任务归属 */
    private final String instanceId = UUID.randomUUID().toString();

    /** 回调客户端（延迟到构造完成后初始化，避免字段初始化早于配置注入） */
    private RestClient callbackClient;

    /**
     * 初始化回调客户端。
     */
    @PostConstruct
    void initClient() {
        this.callbackClient = buildClient();
    }

    /**
     * 定时派发待回调任务。
     */
    @Scheduled(fixedDelayString = "${whale.message.receipt.dispatch-interval-millis:15000}",
            initialDelayString = "${whale.message.receipt.dispatch-initial-delay-millis:20000}")
    public void dispatch() {
        recoverStale();
        List<MessageCallbackTaskPo> tasks = selectDueTasks();
        if (tasks.isEmpty()) {
            return;
        }
        for (MessageCallbackTaskPo task : tasks) {
            // 原子抢占：抢不到说明已被其它实例处理或状态已变化，直接跳过
            if (callbackTaskMapper.claim(task.getId(), instanceId, LocalDateTime.now()) == 0) {
                continue;
            }
            deliver(task);
        }
    }

    /**
     * 回收派发超时的任务：进程崩溃或网络挂起会让任务永久停留在 {@code DISPATCHING}。
     */
    private void recoverStale() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.minusNanos(Math.max(1L, properties.getDispatchTimeoutMillis()) * 1_000_000L);
        int recovered = callbackTaskMapper.recoverStale(deadline, now);
        if (recovered > 0) {
            log.warn("回收派发超时的回调任务: count={}", recovered);
        }
    }

    /**
     * 取到期任务。
     *
     * @return 待派发任务
     */
    private List<MessageCallbackTaskPo> selectDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<MessageCallbackTaskPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(MessageCallbackTaskPo::getStatus, STATUS_PENDING, STATUS_FAILED)
                .and(inner -> inner.isNull(MessageCallbackTaskPo::getNextRetryAt)
                        .or().le(MessageCallbackTaskPo::getNextRetryAt, now))
                .orderByAsc(MessageCallbackTaskPo::getId);
        Page<MessageCallbackTaskPo> page = new Page<>(1, Math.max(1, properties.getDispatchBatchSize()));
        return callbackTaskMapper.selectPage(page, wrapper).getRecords();
    }

    /**
     * 派发单条回调。
     *
     * <p>业务方统一响应体是「HTTP 200 + 业务码」，因此必须解析 {@code code}：
     * 只认 HTTP 2xx 会把「渠道非法 / 参数校验失败」当成回调成功，回执被静默丢弃。</p>
     *
     * @param task 回调任务
     */
    private void deliver(MessageCallbackTaskPo task) {
        int attempts = task.getAttempts() == null ? 0 : task.getAttempts();
        int nextAttempts = attempts + 1;
        task.setAttempts(nextAttempts);
        try {
            CallbackUrlGuard.assertAllowed(task.getCallbackUrl());
        } catch (BizException e) {
            // 历史任务可能落库了未校验的地址，派发前二次校验，命中直接放弃避免 SSRF
            task.setStatus(STATUS_GAVE_UP);
            task.setLastError(truncate(e.getMessage(), ERROR_MAX_LENGTH));
            task.setNextRetryAt(null);
            finish(task);
            log.warn("回调地址未通过安全校验，放弃派发: taskId={}, reason={}", task.getId(), e.getMessage());
            return;
        }
        try {
            Map<String, Object> response = callbackClient.post()
                    .uri(task.getCallbackUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(WebHeaders.TENANT_ID, String.valueOf(task.getTenantId()))
                    .body(StringUtils.hasText(task.getPayloadJson()) ? task.getPayloadJson() : "{}")
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            assertBusinessSuccess(response);
            task.setStatus(STATUS_SENT);
            task.setLastError(null);
            task.setNextRetryAt(null);
            finish(task);
            log.info("回执回调成功: taskId={}, messageId={}, deliveryId={}",
                    task.getId(), task.getMessageId(), task.getDeliveryId());
        } catch (RuntimeException e) {
            task.setLastError(truncate(e.getMessage(), ERROR_MAX_LENGTH));
            if (nextAttempts >= properties.getMaxAttempts()) {
                task.setStatus(STATUS_GAVE_UP);
                task.setNextRetryAt(null);
                log.warn("回执回调超过重试上限，放弃: taskId={}, attempts={}, reason={}",
                        task.getId(), nextAttempts, e.getMessage());
            } else {
                task.setStatus(STATUS_FAILED);
                task.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds(nextAttempts)));
                log.warn("回执回调失败，稍后重试: taskId={}, attempts={}, reason={}",
                        task.getId(), nextAttempts, e.getMessage());
            }
            finish(task);
        }
    }

    /**
     * 结束派发：清空抢占标记后落库，避免任务被超时回收逻辑误判为卡死。
     *
     * @param task 回调任务
     */
    private void finish(MessageCallbackTaskPo task) {
        task.setDispatchOwner(null);
        task.setDispatchAt(null);
        callbackTaskMapper.updateById(task);
    }

    /**
     * 校验业务方响应码：无 code 字段视为成功，有且非 0 视为失败。
     *
     * @param response 响应体
     */
    private static void assertBusinessSuccess(Map<String, Object> response) {
        if (response == null) {
            return;
        }
        Object code = response.get(FIELD_CODE);
        if (code instanceof Number number && number.intValue() != CODE_SUCCESS) {
            throw new IllegalStateException("业务方返回失败: code=" + number
                    + ", message=" + response.get(FIELD_MESSAGE));
        }
    }

    /**
     * 计算退避秒数：{@code base * 2^(attempts-1)}，上限 1 小时。
     *
     * @param attempts 已尝试次数
     * @return 退避秒数
     */
    private long backoffSeconds(int attempts) {
        long base = Math.max(1L, properties.getRetryBackoffSeconds());
        int shift = Math.min(Math.max(attempts - 1, 0), 10);
        return Math.min(base << shift, MAX_BACKOFF_SECONDS);
    }

    /**
     * 截断文本到列宽上限。
     *
     * @param value 原值
     * @param max   最大长度
     * @return 截断后的值
     */
    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    /**
     * 构造回调客户端（带内网令牌头与超时）。
     *
     * @return RestClient
     */
    private RestClient buildClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        return InternalTokenSupport.builder().requestFactory(factory).build();
    }
}
