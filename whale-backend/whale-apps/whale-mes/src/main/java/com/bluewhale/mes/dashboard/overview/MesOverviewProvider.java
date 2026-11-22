package com.bluewhale.mes.dashboard.overview;

import com.bluewhale.mes.dashboard.service.MesDashboardService;
import com.bluewhale.mes.dashboard.vo.MesDashboardVO;
import com.bluewhale.mes.dashboard.vo.MesEquipmentBoardVO;
import com.bluewhale.mes.dispatch.vo.MesBoardVO;
import com.bluewhale.mes.quality.vo.MesQualityBoardVO;
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
import java.util.List;

/**
 * 工部（MES）概览：三维王城内城十二坊的第一个坊。
 *
 * <p><b>口径不另造。</b>四个指标全部取自 {@link MesDashboardService#dashboard} ——
 * 与 2D 综合看板、SSE 推送、CSV 导出**同一个方法**。生产/质量两域在 MES 内部又分别
 * 复用生产看板与质量看板的服务，所以城里看到的"今日完成"与车间大屏上的数字是同一个数。
 * 若这里另写一条 SQL，迟早会出现"城里的产量和看板对不上"，而没人能判断谁对。</p>
 *
 * <p><b>百分比必须换算成比率。</b>MES 的 `achievementRate` 是 0–100 的百分数
 * （`88.50` 表示 88.5%），而概览契约规定 {@link OverviewFormat#PERCENT} 一律传比率
 * （`0.885`）。S3 曾因前端"小于 1.5 就当比率"的兜底在真实 0.8% 上显示成 80%，
 * 这里必须显式除以 100，不交给渲染层猜。</p>
 *
 * <p><b>不在这里 catch 异常。</b>数据库挂了就让它抛，由 SDK 降级成 `stale` / `degraded`。
 * provider 自己吞异常返回空 KPI，会让"接口挂了"与"今天没生产"长得一模一样。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class MesOverviewProvider implements OverviewProvider {

    /** 权限码：与 2D 综合看板同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = "sys:mes:dashboard:view";

    /** 屏风页（生产工单）的权限码：与 2D 工单列表同一码 */
    private static final String WORK_ORDER_PERMISSION = "sys:mes:workorder:list";

    /** 生产工单列表接口：屏风页直接取这个路径，不再单发一套只读接口 */
    private static final String WORK_ORDER_PAGE_PATH = "/mes/v1/work-order/page";

    /** 城内节点 key（工部坊），与 city-layout.json 对齐 */
    private static final String NODE_KEY = "gongbu-hall";

    /** 百分之百：达成率从百分数换算成比率用 */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** 比率保留位数（与契约渲染的一位小数相比多留几位，避免二次舍入漂移） */
    private static final int RATIO_SCALE = 4;

    /** 达成率良好阈值：≥95% 算正常 */
    private static final double RATE_GOOD = 0.95;

    /** 达成率告警阈值：<80% 必须有人看 */
    private static final double RATE_WARN = 0.8;

    /** 综合看板应用服务 */
    private final MesDashboardService dashboardService;

    @Override
    public String service() {
        return "mes";
    }

    @Override
    public String nodeKey() {
        return NODE_KEY;
    }

    @Override
    public String title() {
        return "工部";
    }

    @Override
    public String subtitle() {
        return "制造执行";
    }

    @Override
    public String permissionCode() {
        return PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        /* 不传筛选条件：3D 城看的是全厂今日，而不是某条产线/某个班组。
           传 lineId 就等于在城里默认一个"业务口径"，而这个默认值没人会在后台看到。 */
        MesDashboardVO dashboard = dashboardService.dashboard(null, null, null);
        Overviews.Builder builder = Overviews.builder(service(), nodeKey(), title())
                .subtitle(subtitle() + " · " + safeBoardDate(dashboard))
                .note("口径：MES 综合看板快照");
        appendKpis(builder, dashboard);
        appendOutputChart(builder, dashboard.getProduction());
        /* 入口按权限裁剪：没有工单列表权限的人，连"能点进去"这件事都不该知道。
           截到前端再隐藏等于把入口清单白送，契约 §2.5 明确要求 provider 裁。 */
        if (context != null && context.hasPermission(WORK_ORDER_PERMISSION)) {
            builder.link(Overviews.link("work-orders", "生产工单", WORK_ORDER_PAGE_PATH, WORK_ORDER_PERMISSION));
        }
        appendAlerts(builder, dashboard);
        return builder.build();
    }

    /**
     * 四张牌：完成 / 达成率 / 未关闭 / 故障设备。
     *
     * <p>拆成独立方法不是为了好看，而是 {@code build()} 里每个三元判空都算一个分支，
     * 全挤在一个方法里会撞上圈复杂度上限（15）。拆开之后每个方法只讲一件事，
     * 读的人也不必在一屏里同时理解"取哪四个数"和"四个数怎么措辞"。</p>
     *
     * @param builder 组装器
     * @param dashboard 综合看板快照
     */
    private static void appendKpis(Overviews.Builder builder, MesDashboardVO dashboard) {
        MesBoardVO production = dashboard.getProduction();
        MesEquipmentBoardVO equipment = dashboard.getEquipment();
        long exceptions = count(qualityOf(dashboard).getNcrOpenCount()) + count(productionOf(dashboard).getExceptionCount());
        long faults = count(equipmentOf(dashboard).getFaultEquipmentCount());
        builder.kpi(Overviews.count("completed", "今日完成", quantity(productionOf(dashboard).getCompletedQuantity())))
                .kpi(Overviews.percent("achievement", "达成率",
                        ratio(productionOf(dashboard).getAchievementRate()),
                        rateTone(production), rateHint(production)))
                /* 「异常」把两处未关闭的事合成一个数：生产异常（现场）与 NCR（质量）。
                   分两个 KPI 会让牌面变成六个数字，而值班的人只关心"还有几件事没关"。 */
                .kpi(Overviews.count("exceptions", "未关闭", exceptions,
                        exceptions > 0 ? OverviewTone.WARN : OverviewTone.GOOD, "异常 / NCR"))
                .kpi(Overviews.count("faults", "故障设备", faults,
                        faults > 0 ? OverviewTone.BAD : OverviewTone.GOOD,
                        equipment == null ? "无台账" : "共 " + count(equipment.getTotalEquipmentCount()) + " 台"));
    }

    /**
     * 产量柱状图：目标 / 完成 / 不良三项取自同一份生产看板快照。
     *
     * <p>一张图就能看出"计划多少、做出多少、废了多少"，比三个孤立数字更有信息量；
     * 数值缺省补 0，因为图表里的 null 会画成 NaN 柱，整张卡静默变白。</p>
     *
     * @param builder 组装器
     * @param production 生产看板
     */
    private static void appendOutputChart(Overviews.Builder builder, MesBoardVO production) {
        builder.chart(Overviews.chart("output", "今日 目标 / 完成 / 不良", "bar", OverviewFormat.COUNT,
                List.of("目标", "完成", "不良"),
                Overviews.series("数量",
                        quantity(production == null ? null : production.getTargetQuantity()),
                        quantity(production == null ? null : production.getCompletedQuantity()),
                        quantity(production == null ? null : production.getDefectQuantity()))));
    }

    /**
     * 告警条：异常 / NCR / 故障设备 / SPC。
     *
     * <p>告警条的数量字段是 int（契约里它只是"这条告警涉及几件事"，不是统计量），
     * 所以在这里收敛类型，KPI 那边保持 long 不受影响。</p>
     *
     * @param builder 组装器
     * @param dashboard 综合看板快照
     */
    private static void appendAlerts(Overviews.Builder builder, MesDashboardVO dashboard) {
        int productionExceptions = (int) count(productionOf(dashboard).getExceptionCount());
        int openNcr = (int) count(qualityOf(dashboard).getNcrOpenCount());
        int spcAlerts = (int) count(qualityOf(dashboard).getSpcAlertCount());
        int faults = (int) count(equipmentOf(dashboard).getFaultEquipmentCount());
        if (productionExceptions > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + productionExceptions + " 条未关闭生产异常",
                    productionExceptions);
        }
        if (openNcr > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + openNcr + " 张 NCR 未关闭", openNcr);
        }
        if (faults > 0) {
            builder.alert(OverviewAlertLevel.CRITICAL, faults + " 台设备处于故障状态", faults);
        }
        if (spcAlerts > 0) {
            builder.alert(OverviewAlertLevel.INFO, spcAlerts + " 个样本命中 SPC 规则", spcAlerts);
        }
    }

    /**
     * 生产域取数（缺域时给空对象，避免每个调用点各写一遍三元）
     *
     * @param dashboard 综合看板
     * @return 生产域视图，永不为 null
     */
    private static MesBoardVO productionOf(MesDashboardVO dashboard) {
        MesBoardVO production = dashboard.getProduction();
        return production == null ? new MesBoardVO() : production;
    }

    /**
     * 质量域取数（缺域时给空对象）
     *
     * @param dashboard 综合看板
     * @return 质量域视图，永不为 null
     */
    private static MesQualityBoardVO qualityOf(MesDashboardVO dashboard) {
        MesQualityBoardVO quality = dashboard.getQuality();
        return quality == null ? new MesQualityBoardVO() : quality;
    }

    /**
     * 设备域取数（缺域时给空对象）
     *
     * @param dashboard 综合看板
     * @return 设备域视图，永不为 null
     */
    private static MesEquipmentBoardVO equipmentOf(MesDashboardVO dashboard) {
        MesEquipmentBoardVO equipment = dashboard.getEquipment();
        return equipment == null ? new MesEquipmentBoardVO() : equipment;
    }

    /**
     * 计数兜底：null 不当 0 传播到渲染层以外的地方
     *
     * @param value 计数
     * @return 非空计数，缺省 0
     */
    private static long count(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 数量兜底：数量为 null 时图表会画出 NaN 柱，整张卡静默变白
     *
     * @param value 数量
     * @return 非空数量，缺省 0
     */
    private static double quantity(BigDecimal value) {
        return value == null ? 0d : value.doubleValue();
    }

    /**
     * 达成率换算：百分数（0–100）→ 比率（0–1）
     *
     * <p>只在**有分母**时才有意义：MES 在"今日无计划"时返回 null，
     * 此时给 0 会让牌面显示"达成率 0.0%"，看着像全线停产。
     * 所以无计划时同样返回 0，但语气与提示语由 {@link #rateTone}/{@link #rateHint} 说明。</p>
     *
     * @param percent 百分数
     * @return 比率，缺省 0
     */
    private static double ratio(BigDecimal percent) {
        if (percent == null) {
            return 0d;
        }
        return percent.divide(HUNDRED, RATIO_SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 达成率语气：无计划时中性，不判好坏
     *
     * @param production 生产看板
     * @return 语气
     */
    private static OverviewTone rateTone(MesBoardVO production) {
        if (production == null || production.getAchievementRate() == null) {
            return OverviewTone.FLAT;
        }
        double rate = production.getAchievementRate().doubleValue() / 100d;
        if (rate >= RATE_GOOD) {
            return OverviewTone.GOOD;
        }
        return rate < RATE_WARN ? OverviewTone.WARN : OverviewTone.FLAT;
    }

    /**
     * 达成率提示语：说清分母是多少，避免"99%" 与 "99/100" 被混为一谈
     *
     * @param production 生产看板
     * @return 提示语
     */
    private static String rateHint(MesBoardVO production) {
        if (production == null || production.getAchievementRate() == null) {
            return "今日无计划";
        }
        return "目标 " + plain(production.getTargetQuantity());
    }

    /**
     * 数量转文本：不要 "1200.0"。
     *
     * <p>这是给人的口径说明（"目标 1200 件"），不是统计值；直接拼 {@code double}
     * 会得到 "1200.0"，在牌面上就像个精度声明不全的估算值。</p>
     *
     * @param value 数量
     * @return 无多余小数位的文本，缺省 "0"
     */
    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    /**
     * 统计日期兜底：为空时不要往下发 null（前端拼副标题会得到 "null"）
     *
     * @param dashboard 综合看板
     * @return 日期文本
     */
    private static String safeBoardDate(MesDashboardVO dashboard) {
        LocalDate date = dashboard == null ? null : dashboard.getBoardDate();
        return date == null ? "今日" : date.toString();
    }
}
