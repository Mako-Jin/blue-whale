package com.bluewhale.crm.analytics.interfaces.overview;

import com.bluewhale.crm.analytics.application.service.AnalyticsAppService;
import com.bluewhale.crm.analytics.domain.model.DashboardData;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.OverviewProvider;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import com.bluewhale.overview.support.Overviews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 礼部（CRM）概览：内城十二坊的第三个坊——客户经营。
 *
 * <p><b>这一屏讲什么。</b>礼部掌"宾客"：本月卖出去多少、离目标还差多少、管道里还压着
 * 多少、有几个商机已经过期没关。它不与户部重复——户部看的是**采购与成本**（钱怎么花出去），
 * 这里看的是**销售与目标**（钱怎么收进来）。</p>
 *
 * <p><b>口径不另造。</b>四个数全部来自 {@link AnalyticsAppService#dashboard(CrmActor)}，
 * 与 2D「数据分析 → 销售看板」是同一次调用。目标完成率、加权管道、预测缺口这三个数背后
 * 各有一条算式（{@code metricCalculator.targetCompletion}、按阶段胜率折算、缺口取
 * {@code max(0, 目标 − 已赢单 − 加权管道)}），在这里重算一遍等于给同一件事写第二份定义，
 * 迟早与后台对不上，而没人能判断谁对。</p>
 *
 * <p><b>数据范围必须跟 2D 完全一致，所以要把角色传进去。</b>
 * {@code CrmActor#allDataScope()} 按角色判（{@code tenant_admin / crm_admin /
 * crm_sales_manager / crm_analyst} 看全租户，其余只看本人）。这条规则只认角色、
 * 不认权限码，因此 {@link OverviewContext} 从 S6 起一并快照角色码；这里直接把它交给
 * {@link CrmActor}，不自作主张放宽——普通销售站在礼部坊前看到的必须是他自己的数，
 * 与后台同一时刻一致；管理员看到的才是全租户的数。城里比后台多看到一列数据，
 * 就是一次越权。</p>
 *
 * <p><b>门禁关着就让它降级。</b>{@code whale.crm.analytics.enabled} 关闭时
 * {@code dashboard} 会抛 {@code GATE_CLOSED}，SDK 会把快照降级成 {@code degraded}，
 * 屏风上写"闭门"而不是一片 0。这里不 catch、不返回 0：接口挂了与"本月零业绩"
 * 长得一模一样，是本项目最高频的静默失效。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class CrmOverviewProvider implements OverviewProvider {

    /** 服务一级前缀，与网关路由一致 */
    private static final String SERVICE = "crm";

    /** 城内节点 key（礼部坊），与 {@code city-layout.json} 对齐 */
    private static final String NODE_KEY = "libu-ritual-hall";

    /** 权限码：复用 2D 销售看板同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = CrmPermissions.ANALYTICS_DASHBOARD;

    /** 客户台账列表接口：屏风页直接取这个路径，不再单发一套只读接口 */
    private static final String CUSTOMER_PAGE_PATH = "/crm/customer/page";

    /** 百分比口径的除数：契约要比率（0.94），2D 看板给的是百分数（94） */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 达成率"偏慢"的分界：低于它给黄灯，低于 1 给绿灯 */
    private static final BigDecimal RATE_WARN = new BigDecimal("0.6");

    /** 比率保留位 */
    private static final int RATE_SCALE = 4;

    /** 销售看板应用服务（本屏唯一数据来源） */
    private final AnalyticsAppService analyticsAppService;

    @Override
    public String service() {
        return SERVICE;
    }

    @Override
    public String nodeKey() {
        return NODE_KEY;
    }

    @Override
    public String title() {
        return "礼部";
    }

    @Override
    public String subtitle() {
        return "客户经营";
    }

    @Override
    public String permissionCode() {
        return PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        DashboardData data = analyticsAppService.dashboard(actorOf(context));
        LocalDate statDate = data.getStatDate() == null ? LocalDate.now() : data.getStatDate();
        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · " + YearMonth.from(statDate))
                .note("口径：CRM 销售看板 /crm/analytics/dashboard，与 2D 同源；"
                        + "数据范围按角色（管理员看全租户，其余看本人）");
        appendKpis(builder, data);
        appendTargetChart(builder, data);
        appendLinks(builder);
        appendAlerts(builder, data);
        return builder.build();
    }

    /**
     * 由概览上下文还原 CRM 操作人。
     *
     * <p>只带 userId / 角色 / 权限，不带用户名：销售看板不做脱敏，用户名在这里用不上。
     * 角色缺失（老调用方、内网直连）时退化为"本人范围"，宁可少算也不多给。</p>
     *
     * @param context 概览上下文，可空
     * @return 操作人
     */
    private static CrmActor actorOf(OverviewContext context) {
        if (context == null) {
            return CrmActor.anonymous();
        }
        return new CrmActor(context.userId(), null, context.roleCodes(), context.permissionCodes());
    }

    /**
     * 四张牌：本月赢单、目标达成、加权管道、风险商机。
     *
     * <p>"目标达成"用比率（0.94）而不是百分数（94）：契约的 {@code percent} 口径是比率，
     * 前端乘 100 再补一位小数。直接把 94 发过去，屏风上会显示成 9400%。</p>
     *
     * <p><b>本月没设目标时牌面必须是灰的。</b>此时达成率算出来是 0，看着像"业绩为零"，
     * 其实是"没人给它定指标"——两种情况的处置完全不同（一个要催销售，一个要催主管设目标）。
     * 所以这条牌在目标为 0 时给 {@link OverviewTone#FLAT} 并写明"本月未设目标"。</p>
     *
     * @param builder 组装器
     * @param data 销售看板
     */
    private static void appendKpis(Overviews.Builder builder, DashboardData data) {
        BigDecimal target = amount(data.getMonthTargetAmount());
        BigDecimal won = amount(data.getMonthWonAmount());
        BigDecimal pipeline = amount(data.getWeightedPipelineAmount());
        long risk = data.getRiskOpportunityCount();
        builder.kpi(Overviews.money("month-won", "本月赢单", won, OverviewTone.FLAT,
                        "目标 " + Overviews.formatValue(OverviewFormat.MONEY, target.doubleValue())))
                .kpi(Overviews.percent("target-rate", "目标达成", ratio(data.getTargetCompletionRate()),
                        rateTone(target, data.getTargetCompletionRate()),
                        target.signum() == 0 ? "本月未设目标" : "赢单 ÷ 目标"))
                .kpi(Overviews.money("pipeline", "加权管道", pipeline, OverviewTone.FLAT, "阶段胜率折算"))
                .kpi(Overviews.count("risk", "风险商机", risk,
                        risk > 0 ? OverviewTone.WARN : OverviewTone.GOOD, "已过关闭日"));
    }

    /**
     * 目标 / 赢单 / 管道三根柱：一眼看出缺口在哪。
     *
     * <p>三项单位相同（金额），才能放进同一张图；达成率不进图——比率与金额混画，
     * Y 轴就没法标度了。</p>
     *
     * @param builder 组装器
     * @param data 销售看板
     */
    private static void appendTargetChart(Overviews.Builder builder, DashboardData data) {
        builder.chart(Overviews.chart("month-target", "本月目标 / 赢单 / 管道", "bar", OverviewFormat.MONEY,
                List.of("目标", "已赢单", "加权管道"),
                Overviews.series("金额", amount(data.getMonthTargetAmount()), amount(data.getMonthWonAmount()),
                        amount(data.getWeightedPipelineAmount()))));
    }

    /**
     * 入口：客户台账。
     *
     * <p>客户列表是礼部唯一"点进去能读"的台账：客户名、等级、来源、最近跟进时间都是
     * 城里的文字字段。商机与报价单也能点，但一行里大半是金额与阶段编码，
     * 屏风上不如销售看板这张图好读，所以只挂一个入口，其余留在 2D。</p>
     *
     * @param builder 组装器
     */
    private static void appendLinks(Overviews.Builder builder) {
        builder.link(Overviews.link("customers", "客户台账", CUSTOMER_PAGE_PATH, CrmPermissions.CUSTOMER_LIST));
    }

    /**
     * 告警条：风险商机、预测缺口、本月无目标、未关闭行动项。
     *
     * <p>四条都是"数字本身不会喊"的事：风险商机数是个计数、缺口是减法结果，
     * 单看牌面读不出"该干活了"。第三条尤其重要——目标为 0 时达成率恒为 0，
     * 不说明原因的话，屏风上就是一张"本月业绩为零"的假象。</p>
     *
     * @param builder 组装器
     * @param data 销售看板
     */
    private static void appendAlerts(Overviews.Builder builder, DashboardData data) {
        long risk = data.getRiskOpportunityCount();
        if (risk > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + risk + " 个商机已过预计关闭日", (int) risk);
        }
        BigDecimal target = amount(data.getMonthTargetAmount());
        if (target.signum() == 0) {
            builder.alert(OverviewAlertLevel.INFO, "本月未设定销售目标，达成率无参考意义", null);
        } else if (amount(data.getForecastGap()).signum() > 0) {
            builder.alert(OverviewAlertLevel.INFO, "按加权管道测算，本月目标还差 "
                    + Overviews.formatValue(OverviewFormat.MONEY, amount(data.getForecastGap()).doubleValue()), null);
        }
        long open = data.getOpenActionCount();
        if (open > 0) {
            builder.alert(OverviewAlertLevel.INFO, "有 " + open + " 条行动计划未关闭", (int) open);
        }
    }

    /**
     * 金额兜底：null 不当 0 传给图表（图表里的 null 会画成 NaN，整张卡静默变白）
     *
     * @param value 金额
     * @return 非空金额，缺省 0
     */
    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 百分数转比率：2D 看板给 0–100，契约要 0–1
     *
     * @param percent 百分数，可空
     * @return 比率
     */
    private static BigDecimal ratio(BigDecimal percent) {
        if (percent == null) {
            return BigDecimal.ZERO;
        }
        return percent.divide(HUNDRED, RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 达成率语气：没目标 → 灰；≥100% → 绿；≥60% → 黄；其余 → 红
     *
     * @param target 本月目标金额
     * @param percent 达成率百分数，可空
     * @return 语气
     */
    private static OverviewTone rateTone(BigDecimal target, BigDecimal percent) {
        if (target.signum() == 0) {
            return OverviewTone.FLAT;
        }
        BigDecimal value = ratio(percent);
        if (value.compareTo(BigDecimal.ONE) >= 0) {
            return OverviewTone.GOOD;
        }
        return value.compareTo(RATE_WARN) >= 0 ? OverviewTone.WARN : OverviewTone.BAD;
    }
}
