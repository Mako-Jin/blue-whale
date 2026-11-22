package com.bluewhale.overview;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 概览装载服务：缓存、超时、降级、指标四件事的唯一实现。
 *
 * <p><b>1. TTL 缓存。</b>键为 {@code service|nodeKey|tenantId|权限签名} —— 租户必须进键，
 * 否则 A 租户的数会被发给 B 租户（这类越权不会报错，只会"数据看起来怪"）。
 * <b>权限同样必须进键</b>：契约要求 provider 按权限做块级裁剪（没权限的块根本不下发），
 * 同一租户里权限不同的两个用户看到的快照本来就不是一份东西，共用缓存等于把
 * 有权限的人的数据发给没权限的人。</p>
 *
 * <p><b>2. 超时。</b>provider 在独立线程池执行，超时即 {@code cancel(true)} 并按失败处理。
 * 直接在请求线程里跑 provider，一旦 provider 里有个没设超时的下游调用，
 * 会把 Tomcat 的工作线程也一起拖住 —— 概览是"看一眼"的接口，不该有这个权力。</p>
 *
 * <p><b>3. 降级。</b>失败时若存在上次成功的快照，返回它并置 {@code stale}；
 * 否则返回 {@code degraded} 空态。<b>失败结果不写缓存</b>：这样下一次轮询会自动重试，
 * 下游恢复后最多晚一个轮询周期。</p>
 *
 * <p><b>4. 指标。</b>每次装载都回调 {@link OverviewOutcomeListener}，
 * 让"降级了三周没人发现"变成可告警的时序。</p>
 *
 * @author BlueWhale
 */
public class OverviewService implements DisposableBean {

    /** 日志 */
    private static final Logger LOG = LoggerFactory.getLogger(OverviewService.class);

    /** ISO-8601，带时区 */
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /** 线程池下限：再小就会让两个厅互相排队 */
    private static final int MIN_POOL_SIZE = 2;

    /** 线程池上限：概览是 IO 等待型，够用即可，不要按 CPU 数
     * （容器里 CPU 限额常常写成 0.5，按它算会得到 1 个线程） */
    private static final int MAX_POOL_SIZE = 8;

    /** 全部 provider，按注册顺序 */
    private final List<OverviewProvider> providers;

    /** 配置 */
    private final OverviewProperties properties;

    /** 装载结果监听器 */
    private final OverviewOutcomeListener listener;

    /** provider 执行线程池（daemon，容器关闭时随进程退出） */
    private final ExecutorService executor;

    /** 上次成功快照；失败时从这里取 stale 数据 */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /** 每键一把锁：避免同一节点被并发打穿（8 个厅同时刷新时尤其明显） */
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    /**
     * 构造概览服务
     *
     * @param providers 全部 provider，可为空列表（此时任何请求都返回"未实现"）
     * @param properties 配置
     * @param listener 装载结果监听器，不得为 null
     */
    public OverviewService(List<OverviewProvider> providers, OverviewProperties properties, OverviewOutcomeListener listener) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
        this.properties = properties;
        this.listener = listener;
        int poolSize = Math.max(MIN_POOL_SIZE, Math.min(MAX_POOL_SIZE, this.providers.size() + 1));
        this.executor = Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "overview-loader");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 全部 provider（运维排查用：哪些节点接进来了）
     *
     * @return 只读列表
     */
    public List<OverviewProvider> providers() {
        return providers;
    }

    /**
     * 按节点 key 选择 provider
     *
     * <p>只有一个 provider 时允许不传 node；多个时必须显式指定 ——
     * "随便挑一个"会让同一个 URL 在不同部署上返回不同业务的数据，这类问题极难排查。</p>
     *
     * @param nodeKey 节点 key，可空
     * @return 命中的 provider
     * @throws BizException 本服务未实现概览、未指定节点但存在多个、或节点不存在
     */
    public OverviewProvider resolve(String nodeKey) {
        if (providers.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.overview.overview-service.service-integrated-overview-overviewprovider-implemented",
                          "本服务尚未接入概览（未实现 OverviewProvider）");
        }
        if (nodeKey == null || nodeKey.isBlank()) {
            if (providers.size() == 1) {
                return providers.get(0);
            }
            String candidates = providers.stream().map(OverviewProvider::nodeKey).collect(Collectors.joining(", "));
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.overview.overview-service.service-provides-multiple-overview-nodes-specify",
                          "本服务提供多个概览节点，请用 node 参数指定：{0}", candidates);
        }
        return providers.stream()
                .filter(provider -> nodeKey.equals(provider.nodeKey()))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "biz.overview.overview-service.unknown-overview-node", "未知概览节点：{0}",
                                           nodeKey));
    }

    /**
     * 装载概览：命中缓存直接返回，否则调 provider 并处理超时/降级
     *
     * @param context 调用者上下文
     * @param provider 目标 provider
     * @return 规范化后的快照，永不抛业务异常（除 provider 不可用外）
     */
    public OverviewSnapshot load(OverviewContext context, OverviewProvider provider) {
        String key = cacheKey(provider, context);
        long ttlMillis = ttlMillis();
        CacheEntry cached = cache.get(key);
        if (isFresh(cached, ttlMillis)) {
            return cached.snapshot();
        }
        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
            CacheEntry current = cache.get(key);
            if (isFresh(current, ttlMillis)) {
                return current.snapshot();
            }
            return fetch(provider, context, key, current);
        }
    }

    /**
     * 调 provider 并结算缓存/降级
     *
     * @param provider 目标 provider
     * @param context 调用者上下文
     * @param key 缓存键
     * @param previous 上次成功快照，可空
     * @return 本次对外的快照
     */
    private OverviewSnapshot fetch(OverviewProvider provider, OverviewContext context, String key, CacheEntry previous) {
        OverviewSnapshot built;
        try {
            built = invoke(provider, context);
        } catch (TimeoutException e) {
            return degrade(provider, previous, "取数超时（>" + properties.getTimeout().toMillis() + "ms）");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return degrade(provider, previous, "取数被中断");
        } catch (ExecutionException e) {
            return degrade(provider, previous, "取数失败：" + rootMessage(e));
        }
        OverviewSnapshot stamped = stamp(built, provider);
        cache.put(key, new CacheEntry(stamped, System.currentTimeMillis()));
        notifyListener(provider.service(), "live");
        return stamped;
    }

    /**
     * 在独立线程里执行 provider，带超时
     *
     * @param provider 目标 provider
     * @param context 调用者上下文
     * @return 原始快照
     * @throws TimeoutException provider 超时（已尝试中断）
     * @throws InterruptedException 当前线程被中断
     * @throws ExecutionException provider 抛出的异常
     */
    private OverviewSnapshot invoke(OverviewProvider provider, OverviewContext context)
            throws TimeoutException, InterruptedException, ExecutionException {
        Future<OverviewSnapshot> future = executor.submit(() -> provider.build(context));
        try {
            return future.get(properties.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        }
    }

    /**
     * 降级：优先返回上次成功快照并置 stale，否则返回 degraded 空态
     *
     * @param provider 目标 provider
     * @param previous 上次成功快照，可空
     * @param note 降级原因
     * @return 降级快照
     */
    private OverviewSnapshot degrade(OverviewProvider provider, CacheEntry previous, String note) {
        String service = orElse(provider.service(), "unknown");
        if (properties.isServeStaleOnError() && previous != null) {
            notifyListener(service, "stale");
            LOG.warn("概览降级为上次成功快照：service={}, node={}, reason={}", service, provider.nodeKey(), note);
            return previous.snapshot().asStale(note + " · 沿用上次 " + previous.snapshot().generatedAt());
        }
        notifyListener(service, "degraded");
        LOG.warn("概览不可用：service={}, node={}, reason={}", service, provider.nodeKey(), note);
        return OverviewSnapshot.empty(service, provider.nodeKey(), provider.title(), provider.subtitle(), note, now());
    }

    /**
     * 补齐 provider 不必自己填的元字段：服务名、节点、标题、生成时刻、状态
     *
     * @param source provider 返回的快照
     * @param provider 目标 provider
     * @return 补全后的快照
     */
    private OverviewSnapshot stamp(OverviewSnapshot source, OverviewProvider provider) {
        if (source == null) {
            return OverviewSnapshot.empty(orElse(provider.service(), "unknown"), provider.nodeKey(), provider.title(),
                    provider.subtitle(), "provider 返回空快照", now());
        }
        OverviewSnapshot normalized = source.normalized();
        return new OverviewSnapshot(
                orElse(normalized.service(), orElse(provider.service(), "unknown")),
                orElse(normalized.nodeKey(), provider.nodeKey()),
                orElse(normalized.title(), provider.title()),
                orElse(normalized.subtitle(), provider.subtitle()),
                orElse(normalized.generatedAt(), now()),
                OverviewState.LIVE,
                normalized.kpis(),
                normalized.charts(),
                normalized.alerts(),
                normalized.links(),
                normalized.notes());
    }

    /**
     * 判断缓存是否仍在 TTL 内
     *
     * @param entry 缓存项，可空
     * @param ttlMillis TTL 毫秒
     * @return 可复用返回 {@code true}
     */
    private boolean isFresh(CacheEntry entry, long ttlMillis) {
        return entry != null && System.currentTimeMillis() - entry.storedAt() < ttlMillis;
    }

    /**
     * 缓存键：服务 + 节点 + 租户 + 权限签名
     *
     * @param provider 目标 provider
     * @param context 调用者上下文
     * @return 缓存键
     */
    private String cacheKey(OverviewProvider provider, OverviewContext context) {
        Long tenantId = context == null ? null : context.tenantId();
        return orElse(provider.service(), "unknown") + '|' + provider.nodeKey() + '|'
                + (tenantId == null ? "-" : tenantId) + '|' + permissionSignature(context);
    }

    /**
     * 权限签名：把调用者的权限集合压成定长短串，用于缓存键
     *
     * <p>用 {@code Set#hashCode}（元素哈希之和，与顺序无关）+ 集合大小：同一套权限无论
     * 网关以什么顺序拼 {@code X-Permission-Codes} 都会落到同一个键上；大小参与签名是为了
     * 降低不同集合撞同一哈希的概率。签名只影响缓存命中，不承担鉴权职责。</p>
     *
     * @param context 调用者上下文，可空
     * @return 签名，例如 {@code 3:a1b2c3d4}；无上下文时为 {@code none}
     */
    private String permissionSignature(OverviewContext context) {
        if (context == null || context.permissionCodes().isEmpty()) {
            return "none";
        }
        Set<String> codes = context.permissionCodes();
        return codes.size() + ":" + Integer.toHexString(codes.hashCode());
    }

    /**
     * 取 TTL 毫秒。
     *
     * <p>只把 {@code null} 与负值当非法配置（回落 30 秒）；<b>0 是合法值</b>，
     * 表示"不缓存、每次穿透"——排障时用得上，测试也靠它把缓存边界打出来。</p>
     *
     * @return 毫秒
     */
    private long ttlMillis() {
        Duration ttl = properties.getTtl();
        return ttl == null || ttl.isNegative() ? 30_000L : ttl.toMillis();
    }

    /**
     * 回调监听器，任何异常都吞掉（指标失败不能影响取数）
     *
     * @param service 服务前缀
     * @param outcome 结果
     */
    private void notifyListener(String service, String outcome) {
        try {
            listener.onLoad(service, outcome);
        } catch (RuntimeException e) {
            LOG.debug("概览指标回调失败：{}", e.getMessage());
        }
    }

    /**
     * 取异常的根因消息，便于写进 notes
     *
     * @param error 执行异常
     * @return 根因消息，可空
     */
    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        return message.length() > 60 ? message.substring(0, 60) : message;
    }

    /**
     * 空值兜底
     *
     * @param value 原值
     * @param fallback 兜底值
     * @return 非空值
     */
    private String orElse(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 当前时刻
     *
     * @return ISO-8601 带时区
     */
    private String now() {
        return OffsetDateTime.now().format(ISO);
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
        cache.clear();
        locks.clear();
    }

    /**
     * 一条缓存记录
     *
     * @param snapshot 上次成功的快照
     * @param storedAt 写入时刻（毫秒）
     * @author BlueWhale
     */
    private record CacheEntry(OverviewSnapshot snapshot, long storedAt) {
    }
}
