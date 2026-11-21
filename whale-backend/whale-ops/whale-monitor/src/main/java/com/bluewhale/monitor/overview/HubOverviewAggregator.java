package com.bluewhale.monitor.overview;

import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewState;
import com.bluewhale.overview.model.OverviewTone;
import com.bluewhale.overview.support.Overviews;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 数枢殿总控：把在册各殿的 {@code <服务>/overview} 汇总成一份快照 + 一张表。
 *
 * <p><b>为什么总控要放在服务端而不是前端拼。</b>前端拼是 S3 的做法（八路异构接口各猜字段），
 * 它的代价不是多写几十行，而是<b>没有"没数"这个状态</b>：一路失败就少一张卡，
 * 玩家看到的是"这殿今天没业务"而不是"这殿的接口挂了"。契约化了之后，
 * 总控能给每一座殿明确标出 {@code live/stale/degraded/denied}，
 * 而这个判定必须发生在能看到全部下游结果的地方 —— 也就是这里。</p>
 *
 * <p><b>为什么聚合放在 whale-monitor。</b>它是本工程里唯一的"无库采集型"服务：
 * 只读 Nacos 与各服务的只读接口，不持有业务数据。总控要做的事与它完全同构，
 * 复用它不需要新增服务、新增库、新增编排（VM 余量本来就紧）；
 * 而新起一个"总控服务"要再占一档内存，收益只是名字更贴切。</p>
 *
 * <p><b>三条硬约束：</b></p>
 * <ol>
 *   <li><b>并行 + 独立超时</b>：8 座殿串行问一遍就是 8 个超时叠加；并行后总耗时≈最慢的那座。</li>
 *   <li><b>单殿失败不影响其它殿</b>：某一座挂了只让那一行变红，不让整页变空。</li>
 *   <li><b>转发调用者身份而不是服务身份</b>：各殿按 {@code X-Permission-Codes} 自行裁剪，
 *       总控不能拿一个"超级身份"去把所有人无权看的数都取回来再过滤 —— 那等于绕过各殿的鉴权。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Component
public class HubOverviewAggregator implements DisposableBean {

    /** 日志 */
    private static final Logger LOG = LoggerFactory.getLogger(HubOverviewAggregator.class);

    /** 服务一级前缀（网关路由 {@code /monitor/**}） */
    public static final String SERVICE = "monitor";

    /** 城内节点 key（数枢殿，与 city-layout.json 对齐） */
    public static final String NODE_KEY = "shushu-hall";

    /** 厅/牌主标题 */
    public static final String TITLE = "数枢殿";

    /** 副标题 */
    public static final String SUBTITLE = "八方宫署 · 总控";

    /** 屏风页列表接口（概览的 links 里下发它） */
    public static final String LIST_PATH = "/monitor/overview/halls";

    /** 屏风页入口 key（前端 SCREEN_PAGES 用它对上） */
    public static final String LINK_KEY = "hall-health";

    /** 线程池下限：至少让四座殿同时问出去 */
    private static final int MIN_THREADS = 4;

    /** 线程池上限：总控是 IO 等待型，再多人也快不了，只会把下游打满 */
    private static final int MAX_THREADS = 8;

    /** 告警/说明文本上限（契约：告警一句话 ≤30 字） */
    private static final int TEXT_LIMIT = 30;

    /** notes 里最多列几座殿的状态（再多要看表格） */
    private static final int NOTE_LIMIT = 6;

    /** 默认缓存时长（毫秒） */
    private static final long DEFAULT_TTL_MILLIS = 30_000L;

    /** 统一响应体成功码 */
    private static final int CODE_SUCCESS = 0;

    /** HTTP 未认证 */
    private static final int HTTP_UNAUTHORIZED = 401;

    /** HTTP 无权限 */
    private static final int HTTP_FORBIDDEN = 403;

    /** 总控配置 */
    private final HubOverviewProperties properties;

    /** 问下游用的客户端（超时按单殿的超时设，各殿并行时互不拖累） */
    private final RestClient restClient;

    /** 并行问询线程池（daemon，随进程退出） */
    private final ExecutorService executor;

    /** 聚合结果缓存：键含租户与权限签名，见 {@link #cacheKey(OverviewContext)} */
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    /** 每键一把锁，避免同一租户同时进来打穿下游 */
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    /**
     * 构造聚合器
     *
     * @param properties 总控配置（已绑定）
     */
    public HubOverviewAggregator(HubOverviewProperties properties) {
        this.properties = properties;
        int timeoutMillis = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, properties.getTimeout().toMillis()));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        this.restClient = RestClient.builder().requestFactory(factory).build();
        int size = properties.getTargets() == null ? MIN_THREADS : properties.getTargets().size();
        int threads = Math.max(MIN_THREADS, Math.min(MAX_THREADS, size));
        this.executor = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "hub-overview");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 取一次聚合结果（命中缓存直接返回）
     *
     * @param context 调用者身份与权限码
     * @return 聚合结果，永不抛业务异常
     */
    public HubAggregate aggregate(OverviewContext context) {
        String key = cacheKey(context);
        HubAggregate cached = fresh(key);
        if (cached != null) {
            return cached;
        }
        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
            HubAggregate again = fresh(key);
            if (again != null) {
                return again;
            }
            HubAggregate built = build(context);
            store(key, built);
            return built;
        }
    }

    /**
     * 现算一次聚合：并行问在册的每一座殿
     *
     * @param context 调用者身份与权限码
     * @return 聚合结果
     */
    private HubAggregate build(OverviewContext context) {
        List<HubOverviewProperties.Target> targets = properties.getTargets() == null
                ? List.of()
                : List.copyOf(properties.getTargets());
        if (targets.isEmpty()) {
            /* 空配置也要给一个诚实的快照：KPI 全 0 加一句说明，比"接口 404"好排查得多 */
            return new HubAggregate(Overviews.builder(SERVICE, NODE_KEY, TITLE)
                    .subtitle(SUBTITLE)
                    .note("未登记下游服务（whale.overview.aggregate.targets 为空）")
                    .build(), List.of());
        }
        List<CompletableFuture<HubProbe>> futures = new ArrayList<>(targets.size());
        for (HubOverviewProperties.Target target : targets) {
            futures.add(CompletableFuture.supplyAsync(() -> probe(target, context), executor));
        }
        List<HubProbe> probes = new ArrayList<>(targets.size());
        for (int index = 0; index < futures.size(); index++) {
            probes.add(await(targets.get(index), futures.get(index)));
        }
        return new HubAggregate(snapshotOf(probes), List.copyOf(probes));
    }

    /**
     * 等一路问询结果；线程池异常等极端情况折成"未通"而不是让整页炸掉
     *
     * @param target 目标殿
     * @param future 问询任务
     * @return 问询结果
     */
    private HubProbe await(HubOverviewProperties.Target target, CompletableFuture<HubProbe> future) {
        try {
            return future.join();
        } catch (CompletionException | CancellationException e) {
            LOG.warn("总控问询异常：node={}, error={}", target.getNodeKey(), e.toString());
            return new HubProbe(orElse(target.getNodeKey(), target.getName()), orElse(target.getName(), "未知殿"),
                    HubHallState.DEGRADED, "问询中断", null);
        }
    }

    /**
     * 问一座殿
     *
     * @param target 目标殿配置
     * @param context 调用者身份
     * @return 问询结果（失败也返回结果对象，不抛异常）
     */
    private HubProbe probe(HubOverviewProperties.Target target, OverviewContext context) {
        String nodeKey = orElse(target.getNodeKey(), target.getName());
        String name = orElse(target.getName(), nodeKey);
        if (isBlank(target.getUrl())) {
            return new HubProbe(nodeKey, name, HubHallState.DEGRADED, "未配置地址", null);
        }
        if (!context.hasPermission(target.getPermission())) {
            /* 明知缺码就不要发这一次请求：省一次 403 往返，也给出一句更准的话 */
            return new HubProbe(nodeKey, name, HubHallState.DENIED, "无此殿凭据", null);
        }
        try {
            Envelope envelope = restClient.get()
                    .uri(target.getUrl())
                    .headers(headers -> forward(headers, context))
                    .retrieve()
                    .body(Envelope.class);
            return interpret(nodeKey, name, envelope);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == HTTP_UNAUTHORIZED || status == HTTP_FORBIDDEN) {
                return new HubProbe(nodeKey, name, HubHallState.DENIED, "无此殿凭据", null);
            }
            return new HubProbe(nodeKey, name, HubHallState.DEGRADED, "HTTP " + status, null);
        } catch (RuntimeException e) {
            return new HubProbe(nodeKey, name, HubHallState.DEGRADED, reasonOf(e), null);
        }
    }

    /**
     * 解释下游响应：信封成功码 → 快照；否则折成未通
     *
     * @param nodeKey 节点 key
     * @param name 殿名
     * @param envelope 下游响应
     * @return 问询结果
     */
    private HubProbe interpret(String nodeKey, String name, Envelope envelope) {
        if (envelope == null || envelope.data() == null) {
            return new HubProbe(nodeKey, name, HubHallState.DEGRADED, "空响应", null);
        }
        if (envelope.code() != CODE_SUCCESS) {
            return new HubProbe(nodeKey, name, HubHallState.DEGRADED, shorten(orElse(envelope.message(), "业务拒绝")), null);
        }
        OverviewSnapshot snapshot = envelope.data();
        HubHallState state = snapshot.state() == OverviewState.STALE ? HubHallState.STALE : HubHallState.LIVE;
        return new HubProbe(nodeKey, name, state, noteOf(snapshot, state), snapshot);
    }

    /**
     * 逐殿结果 → 汇总结论（厅与牌画的东西）
     *
     * @param probes 逐殿结果
     * @return 汇总快照
     */
    private OverviewSnapshot snapshotOf(List<HubProbe> probes) {
        int total = probes.size();
        long live = count(probes, HubHallState.LIVE);
        long stale = count(probes, HubHallState.STALE);
        long denied = count(probes, HubHallState.DENIED);
        long down = total - live - stale - denied;
        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, TITLE)
                .subtitle(SUBTITLE + " · " + total + " 殿在册")
                .kpi(Overviews.count("halls", "接入殿", total, OverviewTone.FLAT, "在册"))
                .kpi(Overviews.count("live", "通畅", live, live == total ? OverviewTone.GOOD : OverviewTone.FLAT, null))
                .kpi(Overviews.count("stale", "陈旧", stale, stale > 0 ? OverviewTone.WARN : OverviewTone.FLAT, "沿用上次"))
                .kpi(Overviews.count("down", "未通", down + denied,
                        down + denied > 0 ? OverviewTone.BAD : OverviewTone.GOOD, null))
                /* 环形图只表达一件事：这几座殿各自是什么状态。
                   总量太小（个位数）不适合画折线——三个点连不出一条趋势，只会显得空。 */
                .chart(Overviews.chart("health", "各殿连通", "donut", OverviewFormat.COUNT,
                        List.of("通畅", "陈旧", "未通"),
                        Overviews.series("殿", live, stale, down + denied)))
                .link(Overviews.link(LINK_KEY, "八方宫署", LIST_PATH, null));
        if (down > 0) {
            builder.alert(OverviewAlertLevel.CRITICAL, "未通：" + names(probes, HubHallState.DEGRADED), (int) down);
        }
        if (denied > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "无凭据：" + names(probes, HubHallState.DENIED), (int) denied);
        }
        if (stale > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "沿用上次快照 " + stale + " 座", (int) stale);
        }
        String notes = notesOf(probes);
        if (!notes.isEmpty()) {
            builder.note(notes);
        }
        return builder.build();
    }

    /**
     * 某一状态下的殿名列表（告警文案用）
     *
     * @param probes 逐殿结果
     * @param state 目标状态
     * @return 例如「户部、工部」
     */
    private String names(List<HubProbe> probes, HubHallState state) {
        List<String> names = probes.stream()
                .filter(probe -> probe.state() == state)
                .map(HubProbe::name)
                .toList();
        return shorten(String.join("、", names));
    }

    /**
     * 非通畅的殿各写一行说明（最多 {@link #NOTE_LIMIT} 行）
     *
     * @param probes 逐殿结果
     * @return 说明文本
     */
    private String notesOf(List<HubProbe> probes) {
        List<String> lines = probes.stream()
                .filter(probe -> probe.state() != HubHallState.LIVE)
                .limit(NOTE_LIMIT)
                .map(probe -> probe.name() + " " + probe.state().value() + " · " + probe.note())
                .toList();
        return String.join("；", lines);
    }

    /**
     * 统计某一状态的殿数
     *
     * @param probes 逐殿结果
     * @param state 目标状态
     * @return 殿数
     */
    private long count(List<HubProbe> probes, HubHallState state) {
        return probes.stream().filter(probe -> probe.state() == state).count();
    }

    /**
     * 转发调用者身份：每个下游都按自己的权限码裁剪，总控不代替它们做鉴权
     *
     * @param headers 待发的请求头
     * @param context 调用者身份
     */
    private void forward(HttpHeaders headers, OverviewContext context) {
        if (context.userId() != null) {
            headers.set(WebHeaders.USER_ID, String.valueOf(context.userId()));
        }
        if (context.tenantId() != null) {
            headers.set(WebHeaders.TENANT_ID, String.valueOf(context.tenantId()));
        }
        Set<String> codes = context.permissionCodes();
        if (!codes.isEmpty()) {
            headers.set(WebHeaders.PERMISSION_CODES, String.join(",", codes));
        }
        /* 角色也要转：礼部坊的数据范围按 CRM 的 CrmActor 角色口径判（管理员看全量、
           其余看本人）。只转权限不转角色，总控里那座坊会按"本人范围"算，
           与直接进坊看到的数不一样——同一座城两块屏报两个数，最难查。 */
        Set<String> roles = context.roleCodes();
        if (!roles.isEmpty()) {
            headers.set(WebHeaders.USER_ROLES, String.join(",", roles));
        }
    }

    /**
     * 把异常折成一句人话（超时 / 未接通 / 其它）
     *
     * @param error 异常
     * @return 说明，≤{@link #TEXT_LIMIT} 字
     */
    private String reasonOf(RuntimeException error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 6; depth++) {
            if (cause instanceof SocketTimeoutException) {
                return "问询超时";
            }
            if (cause instanceof ConnectException) {
                return "未接通";
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        return shorten(orElse(error.getMessage(), "未接通"));
    }

    /**
     * 单殿说明：状态 + 下游自己的口径/降级说明
     *
     * @param snapshot 下游快照
     * @param state 连通状态
     * @return 说明
     */
    private String noteOf(OverviewSnapshot snapshot, HubHallState state) {
        String base = state == HubHallState.LIVE ? "实时" : "陈旧";
        List<String> notes = snapshot.notes();
        if (notes == null || notes.isEmpty()) {
            return base;
        }
        return base + " · " + notes.get(0);
    }

    /**
     * 缓存键：租户 + 权限签名
     *
     * <p>权限必须进键——每座殿的内容都按调用者的权限码裁剪过，
     * 同一租户里权限不同的两个人本来就该看到不同的总控。</p>
     *
     * @param context 调用者上下文
     * @return 缓存键
     */
    private String cacheKey(OverviewContext context) {
        Long tenantId = context == null ? null : context.tenantId();
        Set<String> codes = context == null ? Set.of() : context.permissionCodes();
        String signature = codes.isEmpty() ? "none" : codes.size() + ":" + Integer.toHexString(codes.hashCode());
        String tenant = tenantId == null ? "-" : String.valueOf(tenantId);
        return tenant + '|' + signature;
    }

    /**
     * 取未过期的缓存
     *
     * @param key 缓存键
     * @return 聚合结果；无缓存或已过期时返回 {@code null}
     */
    private HubAggregate fresh(String key) {
        Cached cached = cache.get(key);
        if (cached == null) {
            return null;
        }
        return System.currentTimeMillis() - cached.storedAt() < ttlMillis() ? cached.aggregate() : null;
    }

    /**
     * 写缓存（TTL ≤ 0 表示不缓存，排障时用得上）
     *
     * @param key 缓存键
     * @param aggregate 聚合结果
     */
    private void store(String key, HubAggregate aggregate) {
        if (ttlMillis() <= 0) {
            return;
        }
        cache.put(key, new Cached(aggregate, System.currentTimeMillis()));
    }

    /**
     * 缓存时长；负值或空按默认 30 秒
     *
     * @return 毫秒
     */
    private long ttlMillis() {
        if (properties.getCacheTtl() == null) {
            return DEFAULT_TTL_MILLIS;
        }
        long millis = properties.getCacheTtl().toMillis();
        return millis < 0 ? DEFAULT_TTL_MILLIS : millis;
    }

    /**
     * 截断到一句话的长度（告警条在 3D 厅里只有一行）
     *
     * @param text 原文
     * @return 截断后的文本
     */
    private String shorten(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= TEXT_LIMIT ? text : text.substring(0, TEXT_LIMIT - 1) + "…";
    }

    /**
     * 空串兜底
     *
     * @param value 原值
     * @param fallback 兜底值
     * @return 非空值
     */
    private String orElse(String value, String fallback) {
        return isBlank(value) ? (fallback == null ? "" : fallback) : value;
    }

    /**
     * 判空
     *
     * @param value 原值
     * @return 空白时 {@code true}
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
        cache.clear();
        locks.clear();
    }

    /**
     * 下游统一响应体（只关心 code / message / data 三个字段）。
     *
     * <p>用 record 直接反序列化，而不是先取 JsonNode 再转换：字段少、结构固定，
     * 少一次树遍历，也让"响应形状变了"在编译期就暴露。</p>
     *
     * @param code 业务码，0 为成功
     * @param message 业务消息
     * @param data 概览快照
     * @author BlueWhale
     */
    record Envelope(int code, String message, OverviewSnapshot data) {
    }

    /**
     * 一条缓存记录
     *
     * @param aggregate 聚合结果
     * @param storedAt 写入时刻（毫秒）
     * @author BlueWhale
     */
    private record Cached(HubAggregate aggregate, long storedAt) {
    }
}
