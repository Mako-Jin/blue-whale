package com.bluewhale.overview;

import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewState;
import com.bluewhale.overview.model.OverviewTone;
import com.bluewhale.overview.support.Overviews;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约测试：序列化结果必须与 {@code docs/specs/overview-contract/overview.schema.json} 对齐。
 *
 * <p>这组断言是"前后端字段漂移"的闸门：schema 是前端类型的唯一来源，
 * 后端一旦改了字段名/枚举字面量而没改 schema，这里先红，而不是等 3D 厅里画不出来才发现。
 * 负向验证方法：把 {@code OverviewFormat.PERCENT} 的字面量改成 {@code "pct"}，本用例应失败。</p>
 *
 * @author BlueWhale
 */
class OverviewContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Path SCHEMA =
            Path.of("..", "..", "..", "docs", "specs", "overview-contract", "overview.schema.json");

    private static OverviewSnapshot sample() {
        return Overviews.builder("erp", "hubu-hall", "户部")
                .subtitle("经营驾驶舱 · 本月")
                .kpi(Overviews.money("revenue", "营收", 1234.5))
                .kpi(Overviews.count("orders", "订单", 88, OverviewTone.GOOD, "已付款"))
                .kpi(Overviews.percent("rate", "达成率", 0.82))
                .chart(Overviews.chart("trend", "近三月营收", "line", OverviewFormat.MONEY,
                        List.of("九月", "十月", "十一月"), Overviews.series("营收", 100, 120, 130)))
                .alert(OverviewAlertLevel.WARNING, "3 个订单超期未发货", 3)
                .link(Overviews.link("orders", "订单明细", "/erp/overview/orders", null))
                .note("口径：已付款订单")
                .build();
    }

    @Test
    @DisplayName("顶层字段与 schema 的 required 完全一致")
    void topLevelFieldsMatchSchema() throws Exception {
        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(sample()));
        JsonNode schema = MAPPER.readTree(Files.readString(SCHEMA, StandardCharsets.UTF_8));
        List<String> required = new ArrayList<>();
        schema.get("required").forEach(node -> required.add(node.asText()));

        assertThat(required).isNotEmpty();
        assertThat(json.fieldNames()).toIterable().containsAll(required);
        Set<String> declared = new java.util.HashSet<>();
        schema.get("properties").fieldNames().forEachRemaining(declared::add);
        List<String> serialized = new ArrayList<>();
        json.fieldNames().forEachRemaining(serialized::add);
        assertThat(declared).containsAll(serialized);
    }

    @Test
    @DisplayName("枚举字面量与 schema enum 一致（小写）")
    void enumLiteralsMatchSchema() {
        assertThat(OverviewState.LIVE.getValue()).isEqualTo("live");
        assertThat(OverviewState.STALE.getValue()).isEqualTo("stale");
        assertThat(OverviewState.DEGRADED.getValue()).isEqualTo("degraded");
        assertThat(OverviewFormat.COUNT.getValue()).isEqualTo("count");
        assertThat(OverviewFormat.MONEY.getValue()).isEqualTo("money");
        assertThat(OverviewFormat.PERCENT.getValue()).isEqualTo("percent");
        assertThat(OverviewFormat.DURATION.getValue()).isEqualTo("duration");
        assertThat(OverviewFormat.TEXT.getValue()).isEqualTo("text");
        assertThat(OverviewTone.GOOD.getValue()).isEqualTo("good");
        assertThat(OverviewTone.WARN.getValue()).isEqualTo("warn");
        assertThat(OverviewTone.BAD.getValue()).isEqualTo("bad");
        assertThat(OverviewTone.FLAT.getValue()).isEqualTo("flat");
        assertThat(OverviewAlertLevel.INFO.getValue()).isEqualTo("info");
        assertThat(OverviewAlertLevel.WARNING.getValue()).isEqualTo("warning");
        assertThat(OverviewAlertLevel.CRITICAL.getValue()).isEqualTo("critical");
    }

    @Test
    @DisplayName("空字段不下发（NON_NULL），前端拿不到 undefined 与 null 两种缺失")
    void omitsNullFields() throws Exception {
        String json = MAPPER.writeValueAsString(sample());

        assertThat(json).doesNotContain("null");
        assertThat(json).contains("\"state\":\"live\"");
        assertThat(json).contains("\"format\":\"percent\"");
    }

    @Test
    @DisplayName("idempotent：normalized 两次结果相同（避免每次刷新都触发重绘）")
    void normalizationIsIdempotent() {
        OverviewSnapshot once = sample().normalized();
        OverviewSnapshot twice = once.normalized();

        assertThat(twice).isEqualTo(once);
    }
}
