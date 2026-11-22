package com.bluewhale.monitor.overview;

import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数枢殿总控聚合器测试：并行、单殿失败隔离、权限转发、超时、缓存与明细映射。
 *
 * <p>用 JDK 自带的 {@link HttpServer} 当"下游各殿"，不引第三方 mock 服务：
 * 这里要验的恰恰是"真的走了一次 HTTP、真的转发了我给的请求头"，
 * 用内存桩会把这两件事一起省掉。</p>
 *
 * @author BlueWhale
 */
class HubOverviewAggregatorTest {

    /** 下游返回的实时快照（营收 1234 → 期望排成「营收 ¥1,234」） */
    private static final String LIVE_BODY = """
            {"code":0,"message":"ok","data":{"service":"erp","nodeKey":"hubu-hall","title":"户部",
            "subtitle":"经营驾驶舱 · 本月","generatedAt":"2026-11-21T03:00:00+08:00","state":"live",
            "kpis":[{"key":"revenue","label":"营收","value":1234,"format":"money","tone":"good"},
            {"key":"orders","label":"订单","value":88,"format":"count","unit":"件","tone":"flat"}],
            "charts":[],"alerts":[],"links":[],"notes":["口径：演示"]}}
            """;

    /** 下游自己说"这是上次的数" */
    private static final String STALE_BODY = """
            {"code":0,"message":"ok","data":{"service":"pay","nodeKey":"duzhi-hall","title":"度支殿",
            "generatedAt":"2026-11-21T02:00:00+08:00","state":"stale",
            "kpis":[{"key":"amount","label":"交易额","value":86000,"format":"money","tone":"flat"}],
            "charts":[],"alerts":[],"links":[],"notes":["沿用上次 02:00"]}}
            """;

    private HttpServer server;

    private HubOverviewAggregator aggregator;

    private final Map<String, String> seenHeaders = new ConcurrentHashMap<>();

    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (aggregator != null) {
            aggregator.destroy();
        }
        server.stop(0);
    }

    /**
     * 注册一个下游殿
     *
     * @param path 路径
     * @param status HTTP 状态码
     * @param body 响应体
     * @param delayMillis 响应前延迟（模拟慢殿）
     */
    private void stub(String path, int status, String body, long delayMillis) {
        server.createContext(path, exchange -> {
            hits.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
            exchange.getRequestHeaders().forEach((name, values) -> {
                if (name.toLowerCase().startsWith("x-")) {
                    // HttpServer 会统一首字母大写（X-user-id），这里归一成小写再比对
                    seenHeaders.put(name.toLowerCase() + "@" + path, String.join(",", values));
                }
            });
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            respond(exchange, status, body);
        });
    }

    /**
     * 回一个 JSON 响应
     *
     * @param exchange 请求
     * @param status 状态码
     * @param body 响应体
     * @throws IOException 写响应失败
     */
    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /**
     * 造一个指向本机桩服务的殿配置
     *
     * @param nodeKey 节点 key
     * @param name 殿名
     * @param path 路径
     * @param permission 权限码，可空
     * @return 配置
     */
    private HubOverviewProperties.Target target(String nodeKey, String name, String path, String permission) {
        HubOverviewProperties.Target target = new HubOverviewProperties.Target();
        target.setNodeKey(nodeKey);
        target.setName(name);
        target.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + path);
        target.setPermission(permission);
        return target;
    }

    /**
     * 按给定配置启动聚合器
     *
     * @param timeout 单殿超时
     * @param ttl 缓存时长
     * @param targets 殿配置
     * @return 聚合器
     */
    private HubOverviewAggregator start(Duration timeout, Duration ttl, HubOverviewProperties.Target... targets) {
        HubOverviewProperties properties = new HubOverviewProperties();
        properties.setTimeout(timeout);
        properties.setCacheTtl(ttl);
        properties.setTargets(new ArrayList<>(List.of(targets)));
        aggregator = new HubOverviewAggregator(properties);
        return aggregator;
    }

    /**
     * 按原始配置启动聚合器（允许 targets / cacheTtl 为 null，覆盖配置缺省分支）
     *
     * @param timeout 单殿超时
     * @param ttl 缓存时长，可为 null
     * @param targets 殿配置，可为 null
     * @return 聚合器
     */
    private HubOverviewAggregator startCustom(Duration timeout, Duration ttl,
                                              List<HubOverviewProperties.Target> targets) {
        HubOverviewProperties properties = new HubOverviewProperties();
        properties.setTimeout(timeout);
        properties.setCacheTtl(ttl);
        properties.setTargets(targets);
        aggregator = new HubOverviewAggregator(properties);
        return aggregator;
    }

    /**
     * 调用者上下文：有户部权限
     *
     * @return 上下文
     */
    private static OverviewContext ctx() {
        return OverviewContext.of(11L, 7L, Set.of("sys:erp:bi:cockpit:list"));
    }

    /**
     * 按 key 取 KPI 数值
     *
     * @param snapshot 快照
     * @param key 指标 key
     * @return 数值
     */
    private static double kpi(OverviewSnapshot snapshot, String key) {
        return snapshot.kpis().stream()
                .filter(item -> key.equals(item.key()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少指标 " + key))
                .value();
    }

    @Test
    @DisplayName("三殿（实时/陈旧/500）→ 计数正确、单殿失败不拖累其它殿")
    void shouldAggregateHealthyAndFailingHalls() {
        stub("/erp/overview", 200, LIVE_BODY, 0);
        stub("/pay/overview", 200, STALE_BODY, 0);
        stub("/mes/overview", 500, "{\"code\":500}", 0);
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ofSeconds(30),
                target("hubu-hall", "户部", "/erp/overview", "sys:erp:bi:cockpit:list"),
                target("duzhi-hall", "度支殿", "/pay/overview", null),
                target("gongbu-hall", "工部", "/mes/overview", null));

        HubAggregate aggregate = subject.aggregate(ctx());

        assertThat(kpi(aggregate.snapshot(), "halls")).isEqualTo(3d);
        assertThat(kpi(aggregate.snapshot(), "live")).isEqualTo(1d);
        assertThat(kpi(aggregate.snapshot(), "stale")).isEqualTo(1d);
        assertThat(kpi(aggregate.snapshot(), "down")).isEqualTo(1d);
        assertThat(aggregate.snapshot().alerts()).anyMatch(alert -> alert.text().contains("未通"));
        assertThat(aggregate.rows()).extracting(row -> row.name() + ":" + row.state())
                .containsExactly("户部:live", "度支殿:stale", "工部:degraded");
        // 主指标在服务端排好版：金额走 ¥ + 千分位
        assertThat(aggregate.rows().get(0).headline()).isEqualTo("营收 ¥1,234");
        assertThat(aggregate.rows().get(2).headline()).isEqualTo("—");
        // 数据时刻压成值班要看的那一段（ISO 放进 3D 屏风里长到没人读）
        assertThat(aggregate.rows().get(0).updatedLabel()).isEqualTo("11-21 03:00");
        assertThat(aggregate.rows().get(2).updatedLabel()).isEmpty();
    }

    @Test
    @DisplayName("转发调用者身份：下游拿到的是这个人的用户/租户/权限，不是服务身份")
    void shouldForwardCallerIdentity() {
        stub("/erp/overview", 200, LIVE_BODY, 0);
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ZERO,
                target("hubu-hall", "户部", "/erp/overview", null));

        subject.aggregate(ctx());

        assertThat(seenHeaders).containsEntry("x-user-id@/erp/overview", "11");
        assertThat(seenHeaders).containsEntry("x-tenant-id@/erp/overview", "7");
        assertThat(seenHeaders.get("x-permission-codes@/erp/overview")).contains("sys:erp:bi:cockpit:list");
    }

    @Test
    @DisplayName("调用者缺该殿权限码：不发请求，直接标 denied")
    void shouldSkipRequestWhenPermissionMissing() {
        stub("/erp/overview", 200, LIVE_BODY, 0);
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ZERO,
                target("hubu-hall", "户部", "/erp/overview", "sys:erp:finance:view"));

        HubAggregate aggregate = subject.aggregate(OverviewContext.of(11L, 7L, Set.of("sys:erp:bi:cockpit:list")));

        assertThat(hits.getOrDefault("/erp/overview", new AtomicInteger()).get()).isZero();
        assertThat(aggregate.rows()).extracting(row -> row.state()).containsExactly("denied");
        assertThat(aggregate.rows().get(0).note()).isEqualTo("无此殿凭据");
    }

    @Test
    @DisplayName("慢殿只影响自己：2s 超时下 500ms 的殿仍算未通，其它殿照常返回")
    void shouldTimeOutSlowHall() {
        stub("/slow/overview", 200, LIVE_BODY, 600);
        stub("/erp/overview", 200, LIVE_BODY, 0);
        HubOverviewAggregator subject = start(Duration.ofMillis(200), Duration.ZERO,
                target("slow-hall", "慢殿", "/slow/overview", null),
                target("hubu-hall", "户部", "/erp/overview", null));

        long start = System.currentTimeMillis();
        HubAggregate aggregate = subject.aggregate(ctx());
        long elapsed = System.currentTimeMillis() - start;

        assertThat(aggregate.rows()).extracting(row -> row.state())
                .containsExactly("degraded", "live");
        assertThat(aggregate.rows().get(0).note()).isEqualTo("问询超时");
        // 串行会等 600ms+；并行 + 单殿超时应当早点回来
        assertThat(elapsed).isLessThan(1_200L);
    }

    @Test
    @DisplayName("缓存：TTL 内不重复打下游，各殿只被问一次")
    void shouldReuseAggregateWithinTtl() {
        stub("/erp/overview", 200, LIVE_BODY, 0);
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ofSeconds(30),
                target("hubu-hall", "户部", "/erp/overview", null));

        subject.aggregate(ctx());
        subject.aggregate(ctx());

        assertThat(hits.get("/erp/overview").get()).isEqualTo(1);
    }

    @Test
    @DisplayName("没登记下游：给一句可诊断的说明，而不是空指针")
    void shouldExplainWhenNoTargets() {
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ZERO);

        HubAggregate aggregate = subject.aggregate(ctx());

        assertThat(aggregate.probes()).isEmpty();
        assertThat(aggregate.snapshot().notes()).anyMatch(note -> note.contains("未登记下游服务"));
    }

    @Test
    @DisplayName("详情：指标全部列出并按契约排版，点数不存在的殿返回 null")
    void shouldExposeDetailLines() {
        stub("/erp/overview", 200, LIVE_BODY, 0);
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ZERO,
                target("hubu-hall", "户部", "/erp/overview", null));

        HubAggregate aggregate = subject.aggregate(ctx());
        var detail = aggregate.detail("hubu-hall");

        assertThat(detail).isNotNull();
        assertThat(detail.lines()).extracting(line -> line.label() + "=" + line.value())
                .containsExactly("营收=¥1,234", "订单=88件");
        assertThat(detail.note()).contains("口径：演示");
        assertThat(aggregate.detail("nope")).isNull();
    }

    @Test
    @DisplayName("配置为 null：当作空殿表处理，给出可诊断说明而不是空指针")
    void shouldHandleNullTargets() {
        HubOverviewAggregator subject = startCustom(Duration.ofSeconds(2), Duration.ZERO, null);

        HubAggregate aggregate = subject.aggregate(ctx());

        assertThat(aggregate.probes()).isEmpty();
        assertThat(aggregate.snapshot().notes()).anyMatch(note -> note.contains("未登记下游服务"));
    }

    @Test
    @DisplayName("未配置地址 / 空节点 key / 空殿名：按诊断文本降级，不抛异常")
    void shouldDegradeWhenUrlOrNamesBlank() {
        HubOverviewProperties.Target blankNode = target(null, null, "/erp/overview", null);
        blankNode.setUrl("  ");
        HubOverviewProperties.Target blankName = target("gongbu-hall", "  ", "/erp/overview", null);
        stub("/erp/overview", 200, LIVE_BODY, 0);
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ZERO, blankNode, blankName);

        HubAggregate aggregate = subject.aggregate(ctx());

        assertThat(aggregate.rows().get(0).nodeKey()).isEmpty();
        assertThat(aggregate.rows().get(0).name()).isEmpty();
        assertThat(aggregate.rows().get(0).state()).isEqualTo("degraded");
        assertThat(aggregate.rows().get(0).note()).isEqualTo("未配置地址");
        assertThat(aggregate.rows().get(1).name()).isEqualTo("gongbu-hall");
        assertThat(aggregate.rows().get(1).state()).isEqualTo("live");
    }

    @Test
    @DisplayName("下游回 401/403：标无凭据，不笼统算成未通")
    void shouldMarkDeniedOnAuthFailures() {
        stub("/a/overview", 401, "{}", 0);
        stub("/b/overview", 403, "{}", 0);
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ZERO,
                target("a-hall", "甲殿", "/a/overview", null),
                target("b-hall", "乙殿", "/b/overview", null));

        HubAggregate aggregate = subject.aggregate(ctx());

        assertThat(aggregate.rows()).extracting(row -> row.state()).containsExactly("denied", "denied");
        assertThat(aggregate.rows()).extracting(row -> row.note()).containsExactly("无此殿凭据", "无此殿凭据");
    }

    @Test
    @DisplayName("下游空响应：无响应体与缺失 data 都折成空响应降级")
    void shouldDegradeOnEmptyResponses() {
        server.createContext("/empty/overview", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        stub("/nodata/overview", 200, "{\"code\":0,\"message\":\"ok\"}", 0);
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ZERO,
                target("empty-hall", "空殿", "/empty/overview", null),
                target("nodata-hall", "无数殿", "/nodata/overview", null));

        HubAggregate aggregate = subject.aggregate(ctx());

        assertThat(aggregate.rows()).extracting(row -> row.note()).containsExactly("空响应", "空响应");
    }

    @Test
    @DisplayName("下游业务拒绝：长消息截断成一句，不把整段文案塞进告警条")
    void shouldDegradeOnBusinessErrorWithLongMessage() {
        String longMessage = "业务拒绝".repeat(10);
        // 必须带 data，否则先被“空响应”分支截住，走不到业务码判定
        String body = "{\"code\":500,\"message\":\"" + longMessage + "\",\"data\":{}}";
        stub("/biz/overview", 200, body, 0);
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ZERO,
                target("biz-hall", "业务殿", "/biz/overview", null));

        HubAggregate aggregate = subject.aggregate(ctx());

        assertThat(aggregate.rows().get(0).state()).isEqualTo("degraded");
        assertThat(aggregate.rows().get(0).note()).hasSize(30).endsWith("…");
    }

    @Test
    @DisplayName("身份转发：空身份不写头，角色码单独转发")
    void shouldForwardRolesAndSkipNullIdentity() {
        stub("/erp/overview", 200, LIVE_BODY, 0);
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ZERO,
                target("hubu-hall", "户部", "/erp/overview", null));

        subject.aggregate(OverviewContext.of(null, null, Set.of(), Set.of("crm_admin", "crm_analyst")));

        assertThat(seenHeaders).doesNotContainKey("x-user-id@/erp/overview");
        assertThat(seenHeaders).doesNotContainKey("x-tenant-id@/erp/overview");
        assertThat(seenHeaders).doesNotContainKey("x-permission-codes@/erp/overview");
        assertThat(seenHeaders.get("x-user-roles@/erp/overview")).contains("crm_admin", "crm_analyst");
    }

    @Test
    @DisplayName("下游快照无 notes：说明只写实时，不追加空壳后缀")
    void shouldHandleSnapshotWithoutNotes() {
        String nullNotes = """
                {"code":0,"message":"ok","data":{"service":"erp","nodeKey":"hubu-hall","title":"户部",
                "generatedAt":"2026-11-21T03:00:00+08:00","state":"live",
                "kpis":[{"key":"revenue","label":"营收","value":1234,"format":"money","tone":"flat"}],
                "charts":[],"alerts":[],"links":[],"notes":null}}
                """;
        String emptyNotes = nullNotes.replace("\"notes\":null", "\"notes\":[]");
        stub("/nullnotes/overview", 200, nullNotes, 0);
        stub("/emptynotes/overview", 200, emptyNotes, 0);
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ZERO,
                target("a-hall", "甲殿", "/nullnotes/overview", null),
                target("b-hall", "乙殿", "/emptynotes/overview", null));

        HubAggregate aggregate = subject.aggregate(ctx());

        assertThat(aggregate.rows()).extracting(row -> row.note()).containsExactly("实时", "实时");
    }

    @Test
    @DisplayName("空上下文：缓存键按匿名租户处理，空殿表仍返回诚实快照")
    void shouldHandleNullContext() {
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ZERO);

        HubAggregate aggregate = subject.aggregate(null);

        assertThat(aggregate.probes()).isEmpty();
        assertThat(aggregate.snapshot().notes()).anyMatch(note -> note.contains("未登记下游服务"));
    }

    @Test
    @DisplayName("TTL 过期：重新问下游，而不是一直返回旧快照")
    void shouldRebuildAfterTtlExpires() throws InterruptedException {
        stub("/erp/overview", 200, LIVE_BODY, 0);
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ofMillis(1),
                target("hubu-hall", "户部", "/erp/overview", null));

        subject.aggregate(ctx());
        Thread.sleep(20L);
        subject.aggregate(ctx());

        assertThat(hits.get("/erp/overview").get()).isEqualTo(2);
    }

    @Test
    @DisplayName("缓存时长为 null：回落默认 TTL")
    void shouldUseDefaultTtlWhenNull() {
        stub("/erp/overview", 200, LIVE_BODY, 0);
        HubOverviewAggregator subject = startCustom(Duration.ofSeconds(2), null,
                new ArrayList<>(List.of(target("hubu-hall", "户部", "/erp/overview", null))));

        subject.aggregate(ctx());
        subject.aggregate(ctx());

        assertThat(hits.get("/erp/overview").get()).isEqualTo(1);
    }

    @Test
    @DisplayName("缓存时长为负数：同样回落默认 TTL")
    void shouldUseDefaultTtlWhenNegative() {
        stub("/erp/overview", 200, LIVE_BODY, 0);
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ofMillis(-1),
                target("hubu-hall", "户部", "/erp/overview", null));

        subject.aggregate(ctx());
        subject.aggregate(ctx());

        assertThat(hits.get("/erp/overview").get()).isEqualTo(1);
    }

    @Test
    @DisplayName("连接被拒：折成未接通，不把堆栈写到表格里")
    void shouldReportConnectionRefused() throws IOException {
        ServerSocket closed = new ServerSocket(0);
        int port = closed.getLocalPort();
        closed.close();
        HubOverviewProperties.Target unreachable = new HubOverviewProperties.Target();
        unreachable.setNodeKey("down-hall");
        unreachable.setName("断联殿");
        unreachable.setUrl("http://127.0.0.1:" + port + "/overview");
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ZERO, unreachable);

        HubAggregate aggregate = subject.aggregate(ctx());

        assertThat(aggregate.rows().get(0).state()).isEqualTo("degraded");
        assertThat(aggregate.rows().get(0).note()).isEqualTo("未接通");
    }

    @Test
    @DisplayName("响应体坏掉：按 RuntimeException 降级，说明截断为一句")
    void shouldDegradeOnUnreadableBody() {
        stub("/bad/overview", 200, "not-a-json", 0);
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ZERO,
                target("bad-hall", "坏殿", "/bad/overview", null));

        HubAggregate aggregate = subject.aggregate(ctx());

        assertThat(aggregate.rows().get(0).state()).isEqualTo("degraded");
        assertThat(aggregate.rows().get(0).note()).isNotBlank().hasSizeLessThanOrEqualTo(30);
    }

    @Test
    @DisplayName("问询任务异常：await 兜住 CompletionException，单殿降级不影响整页")
    void shouldDegradeWhenProbeTaskFails() {
        Set<String> explodingCodes = new AbstractSet<>() {
            @Override
            public Iterator<String> iterator() {
                return List.<String>of().iterator();
            }

            @Override
            public int size() {
                return 0;
            }

            @Override
            public boolean contains(Object value) {
                throw new IllegalStateException("模拟权限读取失败");
            }
        };
        OverviewContext broken = new OverviewContext(11L, 7L, explodingCodes, Set.of());
        HubOverviewAggregator subject = start(Duration.ofSeconds(2), Duration.ZERO,
                target("a-hall", "甲殿", "/a/overview", "sys:a:view"));

        HubAggregate aggregate = subject.aggregate(broken);

        assertThat(aggregate.rows().get(0).state()).isEqualTo("degraded");
        assertThat(aggregate.rows().get(0).note()).isEqualTo("问询中断");
    }
}
