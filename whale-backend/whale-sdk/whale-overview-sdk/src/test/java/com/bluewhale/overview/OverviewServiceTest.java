package com.bluewhale.overview;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.overview.model.OverviewAlert;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewChart;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewState;
import com.bluewhale.overview.support.Overviews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OverviewService} 单元测试：缓存、超时、降级、租户隔离、节点解析。
 *
 * @author BlueWhale
 */
class OverviewServiceTest {

    private OverviewProperties properties;

    private final List<String> outcomes = new CopyOnWriteArrayList<>();

    private OverviewService service;

    @BeforeEach
    void setUp() {
        properties = new OverviewProperties();
        properties.setTtl(Duration.ofSeconds(30));
        properties.setTimeout(Duration.ofMillis(400));
        outcomes.clear();
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.destroy();
        }
    }

    private OverviewService serviceOf(OverviewProvider... providers) {
        service = new OverviewService(List.of(providers), properties, (name, outcome) -> outcomes.add(outcome));
        return service;
    }

    private static OverviewContext ctx(Long tenantId) {
        return OverviewContext.of(7L, tenantId, Set.of("sys:erp:bi:cockpit:list"));
    }

    @Test
    @DisplayName("TTL 内复用上次快照，不重复调 provider")
    void shouldReuseSnapshotWithinTtl() {
        StubProvider provider = new StubProvider(context -> Overviews.builder("erp", "hubu-hall", "户部")
                .kpi(Overviews.money("revenue", "营收", 100))
                .build());
        OverviewService subject = serviceOf(provider);

        OverviewSnapshot first = subject.load(ctx(1L), provider);
        OverviewSnapshot second = subject.load(ctx(1L), provider);

        assertThat(provider.calls.get()).isEqualTo(1);
        assertThat(second).isEqualTo(first);
        assertThat(outcomes).containsExactly("live");
    }

    @Test
    @DisplayName("租户进缓存键：不同租户各自取数，不串数据")
    void shouldIsolateTenants() {
        StubProvider provider = new StubProvider(context -> Overviews.builder("erp", "hubu-hall", "户部")
                .kpi(Overviews.count("tenant", "租户", context.tenantId() == null ? 0 : context.tenantId()))
                .build());
        OverviewService subject = serviceOf(provider);

        OverviewSnapshot a = subject.load(ctx(11L), provider);
        OverviewSnapshot b = subject.load(ctx(22L), provider);

        assertThat(provider.calls.get()).isEqualTo(2);
        assertThat(a.kpis().get(0).value()).isEqualTo(11d);
        assertThat(b.kpis().get(0).value()).isEqualTo(22d);
    }

    @Test
    @DisplayName("权限进缓存键：同租户内权限不同的两个人不共用快照")
    void shouldIsolatePermissions() {
        StubProvider provider = new StubProvider(context -> Overviews.builder("erp", "hubu-hall", "户部")
                .kpi(Overviews.count("blocks", "可见块", context.hasPermission("sys:erp:purchase:list") ? 2 : 1))
                .build());
        OverviewService subject = serviceOf(provider);

        OverviewSnapshot limited = subject.load(OverviewContext.of(7L, 1L, Set.of("sys:erp:bi:cockpit:list")), provider);
        OverviewSnapshot full = subject.load(
                OverviewContext.of(7L, 1L, Set.of("sys:erp:bi:cockpit:list", "sys:erp:purchase:list")), provider);

        assertThat(provider.calls.get()).isEqualTo(2);
        assertThat(limited.kpis().get(0).value()).isEqualTo(1d);
        assertThat(full.kpis().get(0).value()).isEqualTo(2d);
    }

    @Test
    @DisplayName("成功过一次后失败 → 返回上次快照并置 stale，带降级说明")
    void shouldServeStaleAfterFailure() {
        AtomicInteger round = new AtomicInteger();
        StubProvider provider = new StubProvider(context -> {
            if (round.incrementAndGet() > 1) {
                throw new IllegalStateException("数据库连接失败");
            }
            return Overviews.builder("erp", "hubu-hall", "户部")
                    .kpi(Overviews.count("orders", "订单", 8))
                    .build();
        });
        OverviewService subject = serviceOf(provider);

        subject.load(ctx(1L), provider);
        // 让 TTL 过期，第二次必然穿透到 provider
        properties.setTtl(Duration.ZERO);
        OverviewSnapshot stale = subject.load(ctx(1L), provider);

        assertThat(stale.state()).isEqualTo(OverviewState.STALE);
        assertThat(stale.kpis()).hasSize(1);
        assertThat(stale.notes()).anyMatch(note -> note.contains("数据库连接失败"));
        assertThat(outcomes).containsExactly("live", "stale");
    }

    @Test
    @DisplayName("从未成功过 → degraded 空态，不抛异常")
    void shouldReturnDegradedWhenNeverSucceeded() {
        StubProvider provider = new StubProvider(context -> {
            throw new IllegalStateException("服务未启动");
        });
        OverviewService subject = serviceOf(provider);

        OverviewSnapshot snapshot = subject.load(ctx(1L), provider);

        assertThat(snapshot.state()).isEqualTo(OverviewState.DEGRADED);
        assertThat(snapshot.kpis()).isEmpty();
        assertThat(snapshot.notes()).anyMatch(note -> note.contains("服务未启动"));
        assertThat(snapshot.generatedAt()).isNotBlank();
        assertThat(outcomes).containsExactly("degraded");
    }

    @Test
    @DisplayName("provider 超时被中断并按失败降级，不挂住调用方")
    void shouldTimeoutAndDegrade() {
        properties.setTimeout(Duration.ofMillis(120));
        StubProvider provider = new StubProvider(context -> {
            try {
                Thread.sleep(3_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted");
            }
            return Overviews.builder("erp", "hubu-hall", "户部").build();
        });
        OverviewService subject = serviceOf(provider);

        long start = System.currentTimeMillis();
        OverviewSnapshot snapshot = subject.load(ctx(1L), provider);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(snapshot.state()).isEqualTo(OverviewState.DEGRADED);
        assertThat(snapshot.notes()).anyMatch(note -> note.contains("超时"));
        assertThat(elapsed).isLessThan(2_000L);
    }

    @Test
    @DisplayName("serveStaleOnError=false 时不返回旧值，直接 degraded")
    void shouldNotServeStaleWhenDisabled() {
        AtomicInteger round = new AtomicInteger();
        properties.setServeStaleOnError(false);
        StubProvider provider = new StubProvider(context -> {
            if (round.incrementAndGet() > 1) {
                throw new IllegalStateException("boom");
            }
            return Overviews.builder("erp", "hubu-hall", "户部")
                    .kpi(Overviews.count("orders", "订单", 8))
                    .build();
        });
        OverviewService subject = serviceOf(provider);
        subject.load(ctx(1L), provider);
        properties.setTtl(Duration.ZERO);

        OverviewSnapshot snapshot = subject.load(ctx(1L), provider);

        assertThat(snapshot.state()).isEqualTo(OverviewState.DEGRADED);
        assertThat(snapshot.kpis()).isEmpty();
    }

    @Test
    @DisplayName("单节点可省略 node；多节点必须指定；未知节点报 NOT_FOUND")
    void shouldResolveProvider() {
        OverviewProvider first = new StubProvider(context -> Overviews.builder("erp", "hubu-hall", "户部").build());
        OverviewProvider second = new StubProvider("erp", "cangshu-hall", "藏书殿",
                context -> Overviews.builder("erp", "cangshu-hall", "藏书殿").build());
        OverviewService single = new OverviewService(List.of(first), properties, (name, outcome) -> { });
        assertThat(single.resolve(null)).isSameAs(first);
        single.destroy();

        OverviewService multi = serviceOf(first, second);
        assertThat(multi.resolve("cangshu-hall")).isSameAs(second);
        assertThatThrownBy(() -> multi.resolve(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("node 参数");
        assertThatThrownBy(() -> multi.resolve("nope"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未知概览节点");
    }

    @Test
    @DisplayName("未实现 provider 时给出可诊断的 404 文案")
    void shouldRejectWhenNoProvider() {
        OverviewService subject = serviceOf();

        assertThatThrownBy(() -> subject.resolve(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("尚未接入概览");
    }

    @Test
    @DisplayName("规范化：长度不一致的图表被剔除，告警按严重度降序")
    void shouldDropMisalignedChartAndSortAlerts() {
        OverviewChart broken = new OverviewChart("g", "错位", "line", OverviewFormat.COUNT,
                List.of("一月", "二月"), List.of(Overviews.series("营收", 1, 2, 3)));
        OverviewChart fine = Overviews.chart("ok", "正常", "line", OverviewFormat.MONEY,
                List.of("一月", "二月"), Overviews.series("营收", 1, 2));
        StubProvider provider = new StubProvider(context -> Overviews.builder("erp", "hubu-hall", "户部")
                .chart(broken)
                .chart(fine)
                .alert(new OverviewAlert(OverviewAlertLevel.INFO, "提示", 1, null))
                .alert(new OverviewAlert(OverviewAlertLevel.CRITICAL, "严重", 2, null))
                .build());
        OverviewService subject = serviceOf(provider);

        OverviewSnapshot snapshot = subject.load(ctx(1L), provider);

        assertThat(snapshot.charts()).extracting(OverviewChart::key).containsExactly("ok");
        assertThat(snapshot.alerts()).extracting(OverviewAlert::level)
                .containsExactly(OverviewAlertLevel.CRITICAL, OverviewAlertLevel.INFO);
    }

    @Test
    @DisplayName("provider 少填元字段时由 SDK 补齐（服务名/标题/时刻/状态）")
    void shouldStampMetadata() {
        StubProvider provider = new StubProvider(context -> new OverviewSnapshot(null, null, null, null, null,
                null, List.of(), List.of(), List.of(), List.of(), List.of()));
        OverviewService subject = serviceOf(provider);

        OverviewSnapshot snapshot = subject.load(ctx(1L), provider);

        assertThat(snapshot.service()).isEqualTo("erp");
        assertThat(snapshot.nodeKey()).isEqualTo("hubu-hall");
        assertThat(snapshot.title()).isEqualTo("户部");
        assertThat(snapshot.generatedAt()).isNotBlank();
        assertThat(snapshot.state()).isEqualTo(OverviewState.LIVE);
    }

    @Test
    @DisplayName("监听器异常不影响取数")
    void shouldIgnoreListenerFailure() {
        service = new OverviewService(List.of(new StubProvider(context -> Overviews.builder("erp", "hubu-hall", "户部").build())),
                properties, (name, outcome) -> {
                    throw new IllegalStateException("指标注册失败");
                });
        OverviewProvider provider = service.providers().get(0);

        OverviewSnapshot snapshot = service.load(ctx(1L), provider);

        assertThat(snapshot.state()).isEqualTo(OverviewState.LIVE);
    }

    @Test
    @DisplayName("provider 在独立线程执行时也拿得到租户上下文（租户插件靠它加条件）")
    void shouldPropagateTenantContextToProviderThread() {
        AtomicReference<Long> seen = new AtomicReference<>();
        StubProvider provider = new StubProvider(context -> {
            seen.set(TenantContext.get());
            return Overviews.builder("erp", "hubu-hall", "户部").build();
        });
        OverviewService subject = serviceOf(provider);

        subject.load(ctx(9001L), provider);

        /* 不传过去的话 MyBatis-Plus 租户插件会整表跳过租户条件（查全平台），
           而且不报错——所以这条必须钉死，不能只靠"统计服务校验 tenantId 非空"。 */
        assertThat(seen.get()).isEqualTo(9001L);
    }

    @Test
    @DisplayName("无租户的请求清空上下文：线程池复用不把上一个租户带过去")
    void shouldClearTenantContextForAnonymizedLoad() {
        AtomicReference<Long> seen = new AtomicReference<>(1L);
        StubProvider provider = new StubProvider(context -> {
            seen.set(TenantContext.get());
            return Overviews.builder("erp", "hubu-hall", "户部").build();
        });
        OverviewService subject = serviceOf(provider);

        subject.load(ctx(9001L), provider);
        subject.load(ctx(null), provider);

        assertThat(seen.get()).isNull();
        assertThat(TenantContext.get()).isNull();
    }

    /**
     * 可控 stub：记录调用次数，按给定函数返回或抛错。
     */
    private static final class StubProvider implements OverviewProvider {

        private final String service;

        private final String nodeKey;

        private final String title;

        private final Function<OverviewContext, OverviewSnapshot> body;

        private final AtomicInteger calls = new AtomicInteger();

        StubProvider(Function<OverviewContext, OverviewSnapshot> body) {
            this("erp", "hubu-hall", "户部", body);
        }

        StubProvider(String service, String nodeKey, String title, Function<OverviewContext, OverviewSnapshot> body) {
            this.service = service;
            this.nodeKey = nodeKey;
            this.title = title;
            this.body = body;
        }

        @Override
        public String service() {
            return service;
        }

        @Override
        public String nodeKey() {
            return nodeKey;
        }

        @Override
        public String title() {
            return title;
        }

        @Override
        public OverviewSnapshot build(OverviewContext context) {
            calls.incrementAndGet();
            return body.apply(context);
        }
    }
}
