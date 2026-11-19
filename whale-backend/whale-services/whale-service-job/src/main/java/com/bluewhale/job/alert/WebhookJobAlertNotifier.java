package com.bluewhale.job.alert;

import com.bluewhale.job.config.JobProperties;
import com.bluewhale.job.po.JobAlertPo;
import com.bluewhale.job.po.JobInfoPo;
import com.bluewhale.job.po.JobLogPo;
import com.bluewhale.job.mapper.JobAlertMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 Webhook 的失败告警通知器。
 *
 * <p>每次任务失败终态都会：</p>
 * <ol>
 *   <li>写 {@code log.error} 告警日志（jobId / jobName / runId / logId / retryCount / errorMsg）；</li>
 *   <li>落库一条 {@code job_alert}（webhook 未配置时为 {@code LOG} 通道，配置时为 {@code WEBHOOK}）；</li>
 *   <li>webhook 已配置且不在同任务冷却期内时，异步 POST JSON 载荷，推送结果回写告警状态。</li>
 * </ol>
 *
 * <p>推送使用单线程命名线程池，异常仅记录且绝不外抛；同一 {@code jobId} 在冷却期内只外发一次，
 * 但每次失败仍会落库，保证告警流水完整。</p>
 *
 * <p>冷却资格在多实例间通过 Redis {@code SETNX + TTL} 共享，避免同一任务在各实例各外发一次；
 * Redis 不可用时降级为实例内冷却，并在本地缓存超阈值时清理已过期条目。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class WebhookJobAlertNotifier implements JobAlertNotifier {

    /** 告警通道：仅本地日志与落库 */
    private static final String CHANNEL_LOG = "LOG";

    /** 通道类型：通用 Webhook（自定义 JSON 载荷） */
    private static final String TYPE_WEBHOOK = "WEBHOOK";

    /** 通道类型：钉钉机器人 */
    private static final String TYPE_DINGTALK = "DINGTALK";

    /** 通道类型：飞书机器人 */
    private static final String TYPE_FEISHU = "FEISHU";

    /** 告警级别：错误 */
    private static final String LEVEL_ERROR = "ERROR";

    /** 告警级别：警告 */
    private static final String LEVEL_WARN = "WARN";

    /** 告警级别：信息（恢复通知） */
    private static final String LEVEL_INFO = "INFO";

    /** 告警摘要前缀：任务执行失败 */
    private static final String PREFIX_FAILURE = "任务执行失败";

    /** 告警摘要前缀：任务执行超时 */
    private static final String PREFIX_TIMEOUT = "任务执行超时";

    /** 告警摘要前缀：连续失败自动暂停 */
    private static final String PREFIX_AUTO_PAUSED = "任务连续失败已自动暂停";

    /** 告警摘要前缀：任务执行恢复 */
    private static final String PREFIX_RECOVERED = "任务执行已恢复";

    /** HMAC 算法 */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** 投递状态：待发 */
    private static final int STATUS_PENDING = 0;

    /** 投递状态：成功 */
    private static final int STATUS_SUCCESS = 1;

    /** 投递状态：失败 */
    private static final int STATUS_FAILURE = -1;

    /** 告警摘要落库长度上限（VARCHAR(512)） */
    private static final int MESSAGE_MAX_LENGTH = 512;

    /** Webhook 线程名前缀 */
    private static final String THREAD_NAME_PREFIX = "job-alert-webhook-";

    /** 时间格式化器 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 毫秒换算基数 */
    private static final long MILLIS_PER_SECOND = 1000L;

    /** 冷却键前缀：按租户与任务隔离，任务ID为雪花算法全局唯一 */
    private static final String COOLDOWN_KEY_PREFIX = "job:alert:webhook:cooldown:";

    /** 本地降级冷却缓存的清理阈值：超过后清理已过期条目，避免随历史任务数单调增长 */
    private static final int LOCAL_COOLDOWN_PRUNE_THRESHOLD = 1024;

    /** 关闭时等待在途 webhook 推送的最大秒数 */
    private static final int AWAIT_TERMINATION_SECONDS = 5;

    private final JobProperties jobProperties;

    private final JobAlertMapper jobAlertMapper;

    private final RestClient restClient;

    private final Executor webhookExecutor;

    /** Redis 客户端（可为空）：优先用于跨实例冷却，缺失时降级为实例内冷却 */
    private final ObjectProvider<RedissonClient> redissonClientProvider;

    /** jobId 到最近一次 webhook 外发时间的映射（Redis 不可用时的降级状态） */
    private final Map<Long, Long> localWebhookTimes = new ConcurrentHashMap<>();

    /**
     * 构造失败告警通知器（Spring 容器使用）
     *
     * @param jobProperties 任务服务配置
     * @param jobAlertMapper 告警记录 Mapper
     * @param restClientBuilder RestClient 构建器（由 Spring Boot 自动配置提供）
     * @param redissonClientProvider Redis 客户端提供者（可为空）
     */
    @Autowired
    public WebhookJobAlertNotifier(JobProperties jobProperties, JobAlertMapper jobAlertMapper,
                                   RestClient.Builder restClientBuilder,
                                   ObjectProvider<RedissonClient> redissonClientProvider) {
        this(jobProperties, jobAlertMapper, buildRestClient(jobProperties, restClientBuilder),
                newWebhookExecutor(), redissonClientProvider);
    }

    /**
     * 构造失败告警通知器（单元测试可直接注入 RestClient 与执行器，无 Redis）
     *
     * @param jobProperties 任务服务配置
     * @param jobAlertMapper 告警记录 Mapper
     * @param restClient Webhook 调用客户端
     * @param webhookExecutor webhook 异步执行器
     */
    WebhookJobAlertNotifier(JobProperties jobProperties, JobAlertMapper jobAlertMapper, RestClient restClient,
                            Executor webhookExecutor) {
        this(jobProperties, jobAlertMapper, restClient, webhookExecutor, null);
    }

    /**
     * 构造失败告警通知器（单元测试可注入 Redis 提供者）
     *
     * @param jobProperties 任务服务配置
     * @param jobAlertMapper 告警记录 Mapper
     * @param restClient Webhook 调用客户端
     * @param webhookExecutor webhook 异步执行器
     * @param redissonClientProvider Redis 客户端提供者
     */
    WebhookJobAlertNotifier(JobProperties jobProperties, JobAlertMapper jobAlertMapper, RestClient restClient,
                            Executor webhookExecutor, ObjectProvider<RedissonClient> redissonClientProvider) {
        this.jobProperties = jobProperties;
        this.jobAlertMapper = jobAlertMapper;
        this.restClient = restClient;
        this.webhookExecutor = webhookExecutor;
        this.redissonClientProvider = redissonClientProvider;
    }

    /**
     * 关闭 webhook 线程池：先等待在途推送完成，超时后再中断，避免丢失告警
     */
    @PreDestroy
    public void destroy() {
        if (!(webhookExecutor instanceof ExecutorService executorService)) {
            return;
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(AWAIT_TERMINATION_SECONDS, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }

    @Override
    public void notifyFailure(JobInfoPo job, JobLogPo jobLog) {
        notify(job, jobLog, LEVEL_ERROR, PREFIX_FAILURE);
    }

    @Override
    public void notifyTimeout(JobInfoPo job, JobLogPo jobLog) {
        notify(job, jobLog, LEVEL_ERROR, PREFIX_TIMEOUT);
    }

    @Override
    public void notifyAutoPaused(JobInfoPo job, int failCount) {
        String summary = "连续失败 " + failCount + " 次，已自动暂停";
        log.error("任务连续失败自动暂停告警: jobId={}, jobName={}, failCount={}",
                job.getId(), job.getJobName(), failCount);
        List<JobProperties.Channel> channels = resolveChannels();
        if (channels.isEmpty()) {
            insertAlert(job, null, CHANNEL_LOG, STATUS_SUCCESS, summary, LEVEL_WARN, PREFIX_AUTO_PAUSED);
            return;
        }
        if (!tryAcquireWebhookSlot(job)) {
            log.info("自动暂停告警外发处于冷却期，仅落库不外发: jobId={}", job.getId());
            insertAlert(job, null, CHANNEL_LOG, STATUS_SUCCESS, summary, LEVEL_WARN, PREFIX_AUTO_PAUSED);
            return;
        }
        JobAlertPo alert = insertAlert(job, null, channelNames(channels), STATUS_PENDING, summary,
                LEVEL_WARN, PREFIX_AUTO_PAUSED);
        webhookExecutor.execute(() -> pushWebhook(alert, channels, job, null, summary,
                LEVEL_WARN, PREFIX_AUTO_PAUSED));
    }

    @Override
    public void notifyRecovered(JobInfoPo job) {
        if (!jobProperties.getAlert().isRecoveryEnabled()) {
            return;
        }
        log.info("任务执行恢复通知: jobId={}, jobName={}", job.getId(), job.getJobName());
        List<JobProperties.Channel> channels = resolveChannels();
        if (channels.isEmpty()) {
            insertAlert(job, null, CHANNEL_LOG, STATUS_SUCCESS, null, LEVEL_INFO, PREFIX_RECOVERED);
            return;
        }
        // 恢复通知是「全部正常」信号，不受冷却限制：失败告警刚发过也应把恢复送达
        JobAlertPo alert = insertAlert(job, null, channelNames(channels), STATUS_PENDING, null,
                LEVEL_INFO, PREFIX_RECOVERED);
        webhookExecutor.execute(() -> pushWebhook(alert, channels, job, null, null,
                LEVEL_INFO, PREFIX_RECOVERED));
    }

    /**
     * 发送一条告警：本地日志 + 落库 + 按需外发全部已启用通道
     *
     * @param job 任务定义
     * @param jobLog 执行日志（自动暂停场景可为空）
     * @param level 告警级别
     * @param messagePrefix 摘要前缀（区分失败 / 超时 / 自动暂停）
     */
    private void notify(JobInfoPo job, JobLogPo jobLog, String level, String messagePrefix) {
        String errorMsg = truncateError(jobLog.getErrorMsg());
        if (LEVEL_WARN.equals(level)) {
            log.warn("{}告警: jobId={}, jobName={}, runId={}, logId={}, errorMsg={}",
                    messagePrefix, job.getId(), job.getJobName(), jobLog.getRunId(), jobLog.getId(), errorMsg);
        } else {
            log.error("{}告警: jobId={}, jobName={}, runId={}, logId={}, retryCount={}, errorMsg={}",
                    messagePrefix, job.getId(), job.getJobName(), jobLog.getRunId(), jobLog.getId(),
                    jobLog.getRetryCount(), errorMsg);
        }
        List<JobProperties.Channel> channels = resolveChannels();
        if (channels.isEmpty()) {
            insertAlert(job, jobLog, CHANNEL_LOG, STATUS_SUCCESS, errorMsg, level, messagePrefix);
            return;
        }
        if (!tryAcquireWebhookSlot(job)) {
            log.info("告警外发处于冷却期，仅落库不外发: jobId={}", job.getId());
            insertAlert(job, jobLog, CHANNEL_LOG, STATUS_SUCCESS, errorMsg, level, messagePrefix);
            return;
        }
        JobAlertPo alert = insertAlert(job, jobLog, channelNames(channels), STATUS_PENDING, errorMsg, level,
                messagePrefix);
        webhookExecutor.execute(() -> pushWebhook(alert, channels, job, jobLog, errorMsg, level, messagePrefix));
    }

    /**
     * 解析本次外发使用的通道列表。
     *
     * <p>{@code channels} 未配置时回退为「单个 WEBHOOK 通道 + {@code webhookUrl}」，
     * 保持既有部署的配置无需改动；告警总开关关闭时返回空列表，仅落本地流水。</p>
     *
     * @return 可外发的通道列表（可能为空）
     */
    private List<JobProperties.Channel> resolveChannels() {
        JobProperties.Alert alert = jobProperties.getAlert();
        if (!alert.isEnabled()) {
            return List.of();
        }
        List<JobProperties.Channel> configured = alert.getChannels();
        if (configured == null || configured.isEmpty()) {
            if (!StringUtils.hasText(alert.getWebhookUrl())) {
                return List.of();
            }
            JobProperties.Channel fallback = new JobProperties.Channel();
            fallback.setType(TYPE_WEBHOOK);
            fallback.setUrl(alert.getWebhookUrl());
            return List.of(fallback);
        }
        return configured.stream()
                .filter(channel -> channel != null && channel.isEnabled() && StringUtils.hasText(channel.getUrl()))
                .toList();
    }

    /**
     * 告警通道名称：按配置顺序去重后拼接，落库到 {@code job_alert.channel}
     *
     * @param channels 通道列表
     * @return 通道名称（如 {@code WEBHOOK,DINGTALK}）
     */
    private static String channelNames(List<JobProperties.Channel> channels) {
        return channels.stream()
                .map(WebhookJobAlertNotifier::channelType)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse(CHANNEL_LOG);
    }

    /**
     * 归一化通道类型：空值按通用 Webhook 处理
     *
     * @param channel 通道配置
     * @return 大写通道类型
     */
    private static String channelType(JobProperties.Channel channel) {
        return channel.getType() == null ? TYPE_WEBHOOK : channel.getType().trim().toUpperCase();
    }

    /**
     * 解析通道实际请求地址：钉钉配置密钥时把 {@code timestamp + sign} 追加到查询参数
     *
     * @param channel 通道配置
     * @return 实际请求地址
     */
    static String resolveChannelUrl(JobProperties.Channel channel) {
        return resolveChannelUrl(channel, System.currentTimeMillis());
    }

    /**
     * 解析通道实际请求地址（指定时间戳，便于测试固定签名）
     *
     * @param channel 通道配置
     * @param timestamp 毫秒时间戳
     * @return 实际请求地址
     */
    static String resolveChannelUrl(JobProperties.Channel channel, long timestamp) {
        String url = channel.getUrl();
        if (!TYPE_DINGTALK.equals(channelType(channel)) || !StringUtils.hasText(channel.getSecret())) {
            return url;
        }
        String secret = channel.getSecret();
        String sign = hmacSha256Base64(secret, timestamp + "\n" + secret);
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "timestamp=" + timestamp + "&sign="
                + URLEncoder.encode(sign, StandardCharsets.UTF_8);
    }

    /**
     * 计算飞书机器人加签：密钥为 {@code timestamp + "\n" + secret}，消息体为空
     *
     * @param timestamp 秒级时间戳
     * @param secret 加签密钥
     * @return Base64 编码的签名
     */
    static String feishuSign(long timestamp, String secret) {
        return hmacSha256Base64(timestamp + "\n" + secret, "");
    }

    /**
     * HMAC-SHA256 后 Base64 编码
     *
     * @param key 密钥
     * @param content 待签名内容
     * @return Base64 签名
     */
    private static String hmacSha256Base64(String key, String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("告警加签失败", e);
        }
    }

    /**
     * 以原子方式获取 webhook 外发资格：冷却期内同一个任务只外发一次。
     *
     * <p>优先使用 Redis {@code SETNX + TTL}，多实例共享同一冷却窗口；Redis 不可用或未配置时
     * 降级为实例内冷却，保证告警通道不因中间件故障而中断。</p>
     *
     * @param job 任务定义
     * @return 获取成功返回 {@code true}；冷却期内返回 {@code false}
     */
    private boolean tryAcquireWebhookSlot(JobInfoPo job) {
        long cooldownMillis = Math.max(jobProperties.getAlert().getCooldownSeconds(), 0) * MILLIS_PER_SECOND;
        if (cooldownMillis <= 0) {
            return true;
        }
        RedissonClient client = redissonClientProvider == null ? null : redissonClientProvider.getIfAvailable();
        if (client != null) {
            try {
                RBucket<Long> bucket = client.getBucket(cooldownKey(job));
                return bucket.setIfAbsent(System.currentTimeMillis(), Duration.ofMillis(cooldownMillis));
            } catch (RuntimeException e) {
                log.warn("告警冷却 Redis 不可用，降级为实例内冷却: jobId={}", job.getId(), e);
            }
        }
        return tryAcquireLocalSlot(job.getId(), cooldownMillis);
    }

    /**
     * 冷却键：租户 + 任务，避免跨租户复用同一键
     *
     * @param job 任务定义
     * @return Redis 键
     */
    private static String cooldownKey(JobInfoPo job) {
        long tenantId = job.getTenantId() == null ? 0L : job.getTenantId();
        return COOLDOWN_KEY_PREFIX + tenantId + ":" + job.getId();
    }

    /**
     * 实例内冷却资格判定（Redis 降级路径）
     *
     * @param jobId 任务ID
     * @param cooldownMillis 冷却毫秒数
     * @return 获取成功返回 {@code true}
     */
    private boolean tryAcquireLocalSlot(Long jobId, long cooldownMillis) {
        long now = System.currentTimeMillis();
        pruneLocalSlots(now, cooldownMillis);
        AtomicBoolean acquired = new AtomicBoolean(false);
        localWebhookTimes.compute(jobId, (key, lastTime) -> {
            if (lastTime == null || now - lastTime >= cooldownMillis) {
                acquired.set(true);
                return now;
            }
            return lastTime;
        });
        return acquired.get();
    }

    /**
     * 本地降级缓存超过阈值时清理已过期条目
     *
     * @param now 当前时间
     * @param cooldownMillis 冷却毫秒数
     */
    private void pruneLocalSlots(long now, long cooldownMillis) {
        if (localWebhookTimes.size() < LOCAL_COOLDOWN_PRUNE_THRESHOLD) {
            return;
        }
        localWebhookTimes.entrySet().removeIf(entry -> now - entry.getValue() >= cooldownMillis);
    }

    /**
     * 落库一条告警记录
     *
     * @param job 任务定义
     * @param jobLog 执行日志（自动暂停场景可为空）
     * @param channel 告警通道
     * @param status 投递状态
     * @param errorMsg 失败原因
     * @param level 告警级别
     * @param messagePrefix 摘要前缀
     * @return 已落库的告警实体（主键已回填）
     */
    private JobAlertPo insertAlert(JobInfoPo job, JobLogPo jobLog, String channel, int status, String errorMsg,
                                   String level, String messagePrefix) {
        JobAlertPo alert = new JobAlertPo();
        alert.setJobId(job.getId());
        alert.setJobName(job.getJobName());
        alert.setLogId(jobLog == null ? null : jobLog.getId());
        alert.setRunId(jobLog == null ? null : jobLog.getRunId());
        alert.setLevel(level);
        alert.setChannel(channel);
        alert.setStatus(status);
        alert.setMessage(truncate(messagePrefix + "：" + Objects.toString(job.getJobName(), ""), MESSAGE_MAX_LENGTH));
        alert.setErrorMsg(errorMsg);
        jobAlertMapper.insert(alert);
        return alert;
    }

    /**
     * 异步推送全部已启用通道：任何异常只记录并回写失败状态，绝不外抛
     *
     * @param alert 告警记录
     * @param channels 本次外发的通道列表
     * @param job 任务定义
     * @param jobLog 执行日志（自动暂停 / 恢复场景可为空）
     * @param errorMsg 失败原因
     * @param level 告警级别
     * @param messagePrefix 摘要前缀
     */
    private void pushWebhook(JobAlertPo alert, List<JobProperties.Channel> channels, JobInfoPo job,
                             JobLogPo jobLog, String errorMsg, String level, String messagePrefix) {
        int failed = 0;
        String lastError = null;
        for (JobProperties.Channel channel : channels) {
            try {
                restClient.post()
                        .uri(resolveChannelUrl(channel))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(buildBody(channel, job, jobLog, errorMsg, level, messagePrefix))
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                failed++;
                lastError = describe(e);
                log.warn("告警通道推送失败，继续尝试其余通道: jobId={}, channel={}", job.getId(),
                        channelType(channel), e);
            }
        }
        if (failed == 0) {
            updateAlertStatus(alert, STATUS_SUCCESS, "告警推送成功");
            return;
        }
        updateAlertStatus(alert, STATUS_FAILURE,
                "告警推送失败 " + failed + "/" + channels.size() + "：" + lastError);
    }

    /**
     * 按通道类型构建请求体：钉钉/飞书使用各自的文本消息协议，其余按通用 JSON
     *
     * @param channel 通道配置
     * @param job 任务定义
     * @param jobLog 执行日志（可为空）
     * @param errorMsg 失败原因
     * @param level 告警级别
     * @param messagePrefix 摘要前缀
     * @return 请求体（交由 RestClient 的 JSON 消息转换器序列化）
     */
    static Object buildBody(JobProperties.Channel channel, JobInfoPo job, JobLogPo jobLog,
                            String errorMsg, String level, String messagePrefix) {
        String type = channelType(channel);
        if (TYPE_DINGTALK.equals(type)) {
            Map<String, Object> text = new LinkedHashMap<>();
            text.put("content", buildText(job, jobLog, errorMsg, level, messagePrefix));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("msgtype", "text");
            body.put("text", text);
            return body;
        }
        if (TYPE_FEISHU.equals(type)) {
            Map<String, Object> text = new LinkedHashMap<>();
            text.put("text", buildText(job, jobLog, errorMsg, level, messagePrefix));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("msg_type", "text");
            body.put("content", text);
            if (StringUtils.hasText(channel.getSecret())) {
                // 飞书加签：时间戳为秒级字符串，签名与消息放在同一请求体
                long timestamp = System.currentTimeMillis() / MILLIS_PER_SECOND;
                body.put("timestamp", String.valueOf(timestamp));
                body.put("sign", feishuSign(timestamp, channel.getSecret()));
            }
            return body;
        }
        return buildPayload(job, jobLog, errorMsg, level, messagePrefix);
    }

    /**
     * 构建机器人文本消息内容（钉钉 / 飞书共用）
     *
     * @param job 任务定义
     * @param jobLog 执行日志（可为空）
     * @param errorMsg 失败原因
     * @param level 告警级别
     * @param messagePrefix 摘要前缀
     * @return 多行文本
     */
    private static String buildText(JobInfoPo job, JobLogPo jobLog, String errorMsg,
                                    String level, String messagePrefix) {
        StringBuilder text = new StringBuilder()
                .append('[').append(level).append("] ").append(messagePrefix)
                .append('：').append(Objects.toString(job.getJobName(), ""))
                .append('\n').append("任务ID：").append(job.getId())
                .append('\n').append("时间：").append(LocalDateTime.now().format(TIME_FORMATTER));
        if (jobLog != null) {
            text.append('\n').append("runId：").append(Objects.toString(jobLog.getRunId(), ""));
        }
        if (errorMsg != null) {
            text.append('\n').append("原因：").append(errorMsg);
        }
        return text.toString();
    }

    /**
     * 构建 webhook JSON 载荷
     *
     * @param job 任务定义
     * @param jobLog 执行日志（自动暂停场景可为空）
     * @param errorMsg 失败原因
     * @param level 告警级别
     * @param messagePrefix 摘要前缀
     * @return 有序键值载荷（交由 RestClient 的 JSON 消息转换器序列化）
     */
    private static Map<String, Object> buildPayload(JobInfoPo job, JobLogPo jobLog, String errorMsg,
                                                    String level, String messagePrefix) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", job.getId());
        payload.put("jobName", job.getJobName());
        payload.put("level", level);
        payload.put("message", messagePrefix);
        payload.put("runId", jobLog == null ? null : jobLog.getRunId());
        payload.put("logId", jobLog == null ? null : jobLog.getId());
        payload.put("retryCount", jobLog == null ? null : jobLog.getRetryCount());
        payload.put("errorMsg", errorMsg);
        payload.put("occurTime", LocalDateTime.now().format(TIME_FORMATTER));
        return payload;
    }

    /**
     * 回写告警状态与摘要（更新失败仅告警）
     *
     * @param alert 告警记录
     * @param status 投递状态
     * @param message 摘要
     */
    private void updateAlertStatus(JobAlertPo alert, int status, String message) {
        alert.setStatus(status);
        alert.setMessage(truncate(message, MESSAGE_MAX_LENGTH));
        try {
            jobAlertMapper.updateById(alert);
        } catch (Exception e) {
            log.warn("更新告警记录状态失败，已忽略: alertId={}", alert.getId(), e);
        }
    }

    /**
     * 按配置上限截断失败原因
     *
     * @param errorMsg 原始失败原因
     * @return 截断后的失败原因（入参为空返回 {@code null}）
     */
    private String truncateError(String errorMsg) {
        if (errorMsg == null) {
            return null;
        }
        return truncate(errorMsg, Math.max(jobProperties.getAlert().getMaxErrorLength(), 1));
    }

    /**
     * 按上限截断文本
     *
     * @param text 原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本（入参为空返回 {@code null}）
     */
    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    /**
     * 生成异常摘要文本
     *
     * @param e 异常
     * @return 异常摘要（优先消息，缺失时取简单类名）
     */
    private static String describe(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /**
     * 构建单线程 webhook 推送客户端：连接/读取超时取自配置
     *
     * @param jobProperties 任务服务配置
     * @param restClientBuilder RestClient 构建器
     * @return 配置了超时的 RestClient
     */
    private static RestClient buildRestClient(JobProperties jobProperties, RestClient.Builder restClientBuilder) {
        JobProperties.Alert alert = jobProperties.getAlert();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(Math.max(alert.getConnectTimeoutMillis(), 0)));
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(alert.getReadTimeoutMillis(), 0)));
        return restClientBuilder.requestFactory(requestFactory).build();
    }

    /**
     * 创建单线程命名的 webhook 执行器
     *
     * @return 单线程执行器
     */
    private static ExecutorService newWebhookExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME_PREFIX + "worker");
            thread.setDaemon(true);
            return thread;
        });
    }
}
