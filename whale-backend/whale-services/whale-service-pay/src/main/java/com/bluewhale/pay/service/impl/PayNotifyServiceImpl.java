package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pay.config.PayNotifyProperties;
import com.bluewhale.pay.enums.PayNotifyEvent;
import com.bluewhale.pay.enums.PayNotifyRetryStatus;
import com.bluewhale.pay.mapper.PayNotifyTaskMapper;
import com.bluewhale.pay.po.PayNotifyTaskPo;
import com.bluewhale.pay.po.PayOrderPo;
import com.bluewhale.pay.service.PayNotifyService;
import com.bluewhale.pay.vo.PayNotifyRetryBatchItemVO;
import com.bluewhale.pay.vo.PayNotifyRetryResultVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务通知服务实现。
 *
 * <p>回调地址格式为 {@code <服务名>/<路径>}（如
 * {@code whale-mall/mall/internal/order/paid}），投递时经 Nacos
 * 解析服务实例，与部署端口解耦。载荷携带 {@code tenantId}，投递线程
 * 以 {@code X-Tenant-Id} 头还原租户上下文。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class PayNotifyServiceImpl implements PayNotifyService {

    /** 成功业务码（Result.ok 的 code） */
    private static final int BIZ_SUCCESS_CODE = 0;

    /** 单轮重试最多处理的任务数 */
    private static final int RETRY_BATCH = 100;

    /** 事件字段 */
    private static final String FIELD_EVENT = "event";

    /** 状态：待投递 */
    private static final String STATUS_PENDING = "PENDING";

    /** 状态：投递成功 */
    private static final String STATUS_SUCCESS = "SUCCESS";

    /** 状态：死信 */
    private static final String STATUS_DEAD = "DEAD";

    /** 实例轮询游标：避免所有回调固定打到第一个实例 */
    private static final AtomicInteger INSTANCE_CURSOR = new AtomicInteger();

    /** 载荷中链路追踪字段 */
    private static final String FIELD_TRACE_ID = "traceId";

    /** 指标标签兜底值 */
    private static final String UNKNOWN = "UNKNOWN";

    private final PayNotifyTaskMapper taskMapper;

    private final ObjectMapper objectMapper;

    private final DiscoveryClient discoveryClient;

    private final PayNotifyProperties properties;

    private final TaskExecutor notifyExecutor;

    private final MeterRegistry meterRegistry;

    private final RestClient restClient;

    /**
     * 构造通知服务。
     *
     * @param taskMapper     任务 Mapper
     * @param objectMapper   JSON
     * @param discoveryClient 服务发现
     * @param properties     通知配置
     * @param notifyExecutor 通知线程池
     * @param meterRegistry  指标注册表
     */
    public PayNotifyServiceImpl(PayNotifyTaskMapper taskMapper, ObjectMapper objectMapper,
            DiscoveryClient discoveryClient, PayNotifyProperties properties,
            @Qualifier("payNotifyExecutor") TaskExecutor notifyExecutor, MeterRegistry meterRegistry) {
        this.taskMapper = taskMapper;
        this.objectMapper = objectMapper;
        this.discoveryClient = discoveryClient;
        this.properties = properties;
        this.notifyExecutor = notifyExecutor;
        this.meterRegistry = meterRegistry;
        this.restClient = InternalTokenSupport.builder()
                .requestFactory(buildRequestFactory())
                .build();
        registerGauges();
    }

    /**
     * 注册通知积压指标（Gauge 每次抓取时按库内实时值计算）。
     */
    private void registerGauges() {
        Gauge.builder("pay.notify.pending", taskMapper, PayNotifyTaskMapper::countPending)
                .description("待投递通知任务数")
                .register(meterRegistry);
        Gauge.builder("pay.notify.dead", taskMapper, PayNotifyTaskMapper::countDead)
                .description("死信通知任务数")
                .register(meterRegistry);
        Gauge.builder("pay.notify.oldest_pending_age_seconds", taskMapper, mapper -> {
            LocalDateTime oldest = mapper.selectOldestPendingCreateTime();
            return oldest == null ? 0d : Duration.between(oldest, LocalDateTime.now()).toSeconds();
        }).description("最早待投递任务已积压秒数").register(meterRegistry);
    }

    /**
     * 构建回调 HTTP 工厂（带读超时）。
     *
     * @return 请求工厂
     */
    private SimpleClientHttpRequestFactory buildRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        return factory;
    }

    @Override
    public PayNotifyTaskPo enqueue(PayNotifyEvent event, PayOrderPo order, String refundNo,
                                   BigDecimal refundAmount) {
        if (!StringUtils.hasText(order.getNotifyUrl())) {
            log.warn("支付单未配置回调地址，跳过通知: orderNo={}", order.getOrderNo());
            return null;
        }
        PayNotifyTaskPo task = new PayNotifyTaskPo();
        task.setBizType(order.getBizType());
        task.setBizOrderNo(order.getOrderNo());
        task.setEvent(event.code());
        task.setNotifyUrl(order.getNotifyUrl());
        task.setPayload(buildPayload(event, order, refundNo, refundAmount));
        task.setStatus(STATUS_PENDING);
        task.setRetryCount(0);
        task.setNextRetryAt(LocalDateTime.now());
        taskMapper.insert(task);
        return task;
    }

    @Override
    public void dispatchAfterCommit(Long taskId) {
        if (taskId == null) {
            return;
        }
        Runnable dispatch = () -> {
            try {
                deliverTask(taskId);
            } catch (RuntimeException ex) {
                log.error("通知任务异步投递异常: taskId={}", taskId, ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyExecutor.execute(dispatch);
                }
            });
        } else {
            notifyExecutor.execute(dispatch);
        }
    }

    @Override
    public boolean deliverTask(Long taskId) {
        return deliver(taskId).success();
    }

    /**
     * 投递一次并返回细分结果。
     *
     * <p>{@code claimed=false} 表示本次没有领取到任务（其他实例正在投递、任务已完成或状态已变更），
     * 与「领取后投递失败」区分开，供手工重试返回三态。</p>
     *
     * @param taskId 任务 ID
     * @return 投递结果
     */
    private DeliveryOutcome deliver(Long taskId) {
        PayNotifyTaskPo task = taskMapper.selectById(taskId);
        if (task == null) {
            return new DeliveryOutcome(false, false, "任务不存在");
        }
        if (!STATUS_PENDING.equals(task.getStatus())) {
            return new DeliveryOutcome(STATUS_SUCCESS.equals(task.getStatus()), false, null);
        }
        if (taskMapper.claimForDelivery(taskId, task.getRetryCount()) == 0) {
            log.debug("通知任务已被其他实例领取: taskId={}", taskId);
            return new DeliveryOutcome(false, false, "任务正在投递中");
        }
        long startNanos = System.nanoTime();
        String error = doNotify(task);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        if (error == null) {
            taskMapper.markSuccess(taskId);
            recordDelivery(task, "SUCCESS", elapsedMs);
            return new DeliveryOutcome(true, true, null);
        }
        LocalDateTime nextRetryAt = LocalDateTime.now().plus(backoff(task.getRetryCount()));
        int updated = taskMapper.markFailure(taskId, properties.getMaxRetries(), nextRetryAt, trimError(error));
        boolean dead = task.getRetryCount() + 1 >= properties.getMaxRetries();
        recordDelivery(task, dead ? STATUS_DEAD : "FAILED", elapsedMs);
        if (updated > 0 && log.isWarnEnabled()) {
            log.warn("通知投递失败: taskId={} event={} retry={} error={}",
                    taskId, task.getEvent(), task.getRetryCount() + 1, error);
        }
        return new DeliveryOutcome(false, true, error);
    }

    @Override
    @Scheduled(fixedDelayString = "${whale.pay.notify.retry-interval-ms:30000}")
    @SchedulerLock(name = "pay-notify-retry", lockAtMostFor = "5m")
    public int retryDueTasks() {
        // 全局跨租户扫描：显式忽略租户拦截（见 PayNotifyTaskMapper#selectDueForRetry）
        List<PayNotifyTaskPo> due = taskMapper.selectDueForRetry(LocalDateTime.now(), RETRY_BATCH);
        int success = 0;
        for (PayNotifyTaskPo task : due) {
            if (deliverTask(task.getId())) {
                success++;
            }
        }
        if (!due.isEmpty() && log.isInfoEnabled()) {
            log.info("通知重试轮次完成: due={} success={}", due.size(), success);
        }
        return success;
    }

    @Override
    public PayNotifyRetryResultVO retryManually(Long taskId) {
        PayNotifyTaskPo task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (STATUS_SUCCESS.equals(task.getStatus())) {
            return PayNotifyRetryResultVO.of(PayNotifyRetryStatus.SUCCESS, "通知任务已投递成功，无需重试");
        }
        if (STATUS_DEAD.equals(task.getStatus())) {
            LambdaUpdateWrapper<PayNotifyTaskPo> reset = new LambdaUpdateWrapper<>();
            reset.eq(PayNotifyTaskPo::getId, taskId)
                    .eq(PayNotifyTaskPo::getStatus, STATUS_DEAD)
                    .set(PayNotifyTaskPo::getStatus, STATUS_PENDING)
                    .set(PayNotifyTaskPo::getRetryCount, 0)
                    .set(PayNotifyTaskPo::getNextRetryAt, LocalDateTime.now());
            taskMapper.update(null, reset);
        }
        DeliveryOutcome outcome = deliver(taskId);
        if (outcome.success()) {
            return PayNotifyRetryResultVO.of(PayNotifyRetryStatus.SUCCESS, "通知投递成功");
        }
        if (!outcome.claimed()) {
            // 未领取到任务：其他实例正在投递，或任务状态已被并发修改
            return PayNotifyRetryResultVO.of(PayNotifyRetryStatus.IN_PROGRESS,
                    "任务正在投递中或状态已变更，请稍后刷新");
        }
        return PayNotifyRetryResultVO.of(PayNotifyRetryStatus.RETRY_PENDING,
                "投递失败，已安排下次重试: " + outcome.error());
    }

    @Override
    public List<PayNotifyRetryBatchItemVO> retryBatch(List<Long> taskIds) {
        List<PayNotifyRetryBatchItemVO> results = new ArrayList<>();
        if (taskIds == null || taskIds.isEmpty()) {
            return results;
        }
        for (Long taskId : new LinkedHashSet<>(taskIds)) {
            if (taskId == null) {
                continue;
            }
            try {
                PayNotifyRetryResultVO result = retryManually(taskId);
                results.add(PayNotifyRetryBatchItemVO.of(taskId,
                        PayNotifyRetryStatus.valueOf(result.getStatus()), result.getMessage()));
            } catch (BizException ex) {
                results.add(PayNotifyRetryBatchItemVO.of(taskId, PayNotifyRetryStatus.NOT_FOUND,
                        ex.getMessage()));
            }
        }
        return results;
    }

    @Override
    public PageResult<PayNotifyTaskPo> pageTasks(long current, long size, String status, String bizType) {
        // 管理端请求带租户上下文，租户插件自动追加 tenant_id 条件
        LambdaQueryWrapper<PayNotifyTaskPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(status), PayNotifyTaskPo::getStatus, status);
        wrapper.eq(StringUtils.hasText(bizType), PayNotifyTaskPo::getBizType, bizType);
        wrapper.orderByDesc(PayNotifyTaskPo::getCreateTime);
        long safeSize = Math.min(Math.max(size, 1L), 200L);
        Page<PayNotifyTaskPo> page = taskMapper.selectPage(new Page<>(current, safeSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PayNotifyTaskPo getTask(Long taskId) {
        PayNotifyTaskPo task = taskId == null ? null : taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return task;
    }

    /**
     * 执行一次 HTTP 回调。
     *
     * @param task 任务
     * @return 失败原因；成功返回 null
     */
    private String doNotify(PayNotifyTaskPo task) {
        String[] target = splitNotifyUrl(task.getNotifyUrl());
        if (target == null) {
            return "回调地址格式非法: " + task.getNotifyUrl();
        }
        List<ServiceInstance> instances = discoveryClient.getInstances(target[0]);
        if (instances == null || instances.isEmpty()) {
            return "服务实例不可用: " + target[0];
        }
        ServiceInstance instance = instances.get(Math.floorMod(INSTANCE_CURSOR.getAndIncrement(), instances.size()));
        String endpoint = instance.getUri() + target[1];
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Tenant-Id", String.valueOf(extractTenantId(task)));
            String traceId = extractTraceId(task);
            if (StringUtils.hasText(traceId)) {
                // 异步线程没有请求上下文，透传 enqueue 时固化的 trace，避免回调链路断在这里
                request.header("X-Trace-Id", traceId);
            }
            String body = request.body(task.getPayload())
                    .retrieve()
                    .body(String.class);
            return parseBizCode(body);
        } catch (RuntimeException ex) {
            return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        }
    }

    /**
     * 记录一次投递的计数与耗时指标。
     *
     * @param task       任务
     * @param result     结果：SUCCESS / FAILED / DEAD
     * @param durationMs 本次投递耗时（毫秒）
     */
    private void recordDelivery(PayNotifyTaskPo task, String result, long durationMs) {
        String event = StringUtils.hasText(task.getEvent()) ? task.getEvent() : UNKNOWN;
        String bizType = StringUtils.hasText(task.getBizType()) ? task.getBizType() : UNKNOWN;
        String targetService = targetService(task);
        meterRegistry.counter("pay.notify.delivery",
                "event", event, "bizType", bizType, "targetService", targetService,
                "result", result).increment();
        Timer.builder("pay.notify.delivery_duration")
                .tags("event", event, "bizType", bizType, "targetService", targetService,
                        "result", result)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));
    }

    /**
     * 回调目标服务名（指标标签）。
     *
     * @param task 任务
     * @return 服务名；地址非法时为 {@code UNKNOWN}
     */
    private static String targetService(PayNotifyTaskPo task) {
        String[] target = splitNotifyUrl(task.getNotifyUrl());
        return target == null ? UNKNOWN : target[0];
    }

    /**
     * 从载荷读取 enqueue 时固化的 traceId。
     *
     * @param task 任务
     * @return traceId；缺失或解析失败返回 {@code null}
     */
    private String extractTraceId(PayNotifyTaskPo task) {
        try {
            JsonNode node = objectMapper.readTree(task.getPayload());
            JsonNode traceId = node == null ? null : node.get(FIELD_TRACE_ID);
            return traceId == null || traceId.isNull() ? null : traceId.asText();
        } catch (RuntimeException | JsonProcessingException ex) {
            return null;
        }
    }

    /**
     * 解析业务响应码：HTTP 成功但业务码非 0 视为失败（业务方拒绝）。
     *
     * @param body 响应体
     * @return 失败原因；成功返回 null
     */
    private String parseBizCode(String body) {
        if (!StringUtils.hasText(body)) {
            return "回调响应为空";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode code = node.get("code");
            if (code != null && code.asInt() != BIZ_SUCCESS_CODE) {
                return "业务码非成功: " + body;
            }
            return null;
        } catch (RuntimeException | JsonProcessingException ex) {
            return "回调响应解析失败: " + body;
        }
    }

    /**
     * 构建回调载荷。
     *
     * @param event        事件
     * @param order        支付单
     * @param refundNo     退款单号
     * @param refundAmount 累计退款金额
     * @return JSON
     */
    private String buildPayload(PayNotifyEvent event, PayOrderPo order, String refundNo,
                                BigDecimal refundAmount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", TenantContext.get());
        payload.put(FIELD_EVENT, event.code());
        payload.put("bizType", order.getBizType());
        payload.put("orderNo", order.getOrderNo());
        payload.put("payNo", order.getOrderNo());
        payload.put("amount", order.getAmount());
        payload.put("currency", order.getCurrency());
        payload.put("refundNo", refundNo);
        payload.put("refundAmount", refundAmount == null ? order.getRefundAmount() : refundAmount);
        payload.put("payTime", order.getPayTime() == null ? null : order.getPayTime().toString());
        // 固化当前 trace：异步投递线程没有请求上下文，投递时据此透传 X-Trace-Id
        payload.put(FIELD_TRACE_ID, RequestContextHolder.getTraceId());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("回调载荷序列化失败", ex);
        }
    }

    /**
     * 从载荷提取租户 ID（无则返回未知租户）。
     *
     * @param task 任务
     * @return 租户 ID
     */
    private Long extractTenantId(PayNotifyTaskPo task) {
        try {
            JsonNode node = objectMapper.readTree(task.getPayload());
            JsonNode tenantId = node.get("tenantId");
            if (tenantId != null && !tenantId.isNull()) {
                return tenantId.asLong();
            }
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("通知载荷租户解析失败，按未知租户投递: taskId={}", task.getId());
        }
        return TenantContext.UNKNOWN_TENANT_ID;
    }

    /**
     * 拆分回调地址为服务名与路径。
     *
     * @param notifyUrl 服务名/路径
     * @return [服务名, 路径]；非法返回 null
     */
    static String[] splitNotifyUrl(String notifyUrl) {
        if (!StringUtils.hasText(notifyUrl)) {
            return null;
        }
        int index = notifyUrl.indexOf('/');
        if (index <= 0 || index == notifyUrl.length() - 1) {
            return null;
        }
        return new String[] {notifyUrl.substring(0, index), notifyUrl.substring(index)};
    }

    /**
     * 计算下次退避时长：30s × 2^retryCount。
     *
     * @param retryCount 已重试次数
     * @return 时长
     */
    private Duration backoff(int retryCount) {
        int seconds = properties.getBackoffSeconds() << Math.min(retryCount, 8);
        return Duration.ofSeconds(seconds);
    }

    /**
     * 截断错误信息到库列长度。
     *
     * @param error 原始错误
     * @return 截断后错误
     */
    private static String trimError(String error) {
        return error == null ? null
                : (error.length() <= 500 ? error : error.substring(0, 500));
    }

    /**
     * 单次投递的细分结果。
     *
     * @param success 是否投递成功
     * @param claimed 是否由本次调用领取到任务（false 表示其他实例正在投递或状态已变更）
     * @param error   失败原因（成功时为空）
     */
    private record DeliveryOutcome(boolean success, boolean claimed, String error) {
    }
}
