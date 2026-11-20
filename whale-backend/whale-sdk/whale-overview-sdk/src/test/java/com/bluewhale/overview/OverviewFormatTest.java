package com.bluewhale.overview;

import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.support.Overviews;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 排版口径测试：{@link Overviews#formatValue} 必须与前端 `overview.ts#formatValue` 逐条对齐。
 *
 * <p>为什么这条要有测试：汇总类 provider（数枢殿总控）在服务端把多路快照压成一张表，
 * 表格里的数字由这里排版。一旦阈值或进位与前端不一致，同一份数据在城里（前端排）
 * 与屏风上（服务端排）会显示成两个数 —— 这类分歧没人能判断谁对。</p>
 *
 * @author BlueWhale
 */
class OverviewFormatTest {

    @Test
    @DisplayName("计数：万 / 亿折算，其余千分位")
    void shouldFormatCount() {
        assertThat(Overviews.formatValue(OverviewFormat.COUNT, 0)).isEqualTo("0");
        assertThat(Overviews.formatValue(OverviewFormat.COUNT, 999)).isEqualTo("999");
        assertThat(Overviews.formatValue(OverviewFormat.COUNT, 1234)).isEqualTo("1,234");
        assertThat(Overviews.formatValue(OverviewFormat.COUNT, 12_345)).isEqualTo("1.2万");
        assertThat(Overviews.formatValue(OverviewFormat.COUNT, 123_456_789)).isEqualTo("1.23亿");
    }

    @Test
    @DisplayName("金额：带 ￥ 前缀，万 / 亿同阈值")
    void shouldFormatMoney() {
        assertThat(Overviews.formatValue(OverviewFormat.MONEY, 860)).isEqualTo("¥860");
        assertThat(Overviews.formatValue(OverviewFormat.MONEY, 86_400)).isEqualTo("¥8.6万");
        assertThat(Overviews.formatValue(OverviewFormat.MONEY, 250_000_000)).isEqualTo("¥2.50亿");
    }

    @Test
    @DisplayName("百分比：值传比率，显示乘 100（0.82 → 82.0%）")
    void shouldFormatPercent() {
        assertThat(Overviews.formatValue(OverviewFormat.PERCENT, 0.82)).isEqualTo("82.0%");
        // 真实的 0.8%（比率 0.008）不能被显示成 80%
        assertThat(Overviews.formatValue(OverviewFormat.PERCENT, 0.008)).isEqualTo("0.8%");
    }

    @Test
    @DisplayName("时长：秒 → 天 / 小时 / 分钟")
    void shouldFormatDuration() {
        assertThat(Overviews.formatValue(OverviewFormat.DURATION, 42)).isEqualTo("42秒");
        assertThat(Overviews.formatValue(OverviewFormat.DURATION, 300)).isEqualTo("5分钟");
        assertThat(Overviews.formatValue(OverviewFormat.DURATION, 7_200)).isEqualTo("2.0小时");
        assertThat(Overviews.formatValue(OverviewFormat.DURATION, 172_800)).isEqualTo("2.0天");
    }

    @Test
    @DisplayName("文本：整数不带小数点")
    void shouldFormatText() {
        assertThat(Overviews.formatValue(OverviewFormat.TEXT, 42)).isEqualTo("42");
        assertThat(Overviews.formatValue(OverviewFormat.TEXT, 1.5)).isEqualTo("1.5");
    }

    @Test
    @DisplayName("formatKpi：只有计数类追加单位（金额再拼单位会变成 ￥12万元）")
    void shouldAppendUnitOnlyForCount() {
        OverviewKpi orders = new OverviewKpi("orders", "订单", 88, OverviewFormat.COUNT, "件", null, null, null);
        OverviewKpi revenue = new OverviewKpi("revenue", "营收", 88_000, OverviewFormat.MONEY, "元", null, null, null);

        assertThat(Overviews.formatKpi(orders)).isEqualTo("88件");
        assertThat(Overviews.formatKpi(revenue)).isEqualTo("¥8.8万");
        assertThat(Overviews.formatKpi(null)).isEqualTo("—");
    }
}
