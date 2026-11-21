package com.bluewhale.pdm.overview;

import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.OverviewProvider;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewSeries;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import com.bluewhale.overview.support.Overviews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 将作（PDM）概览：内城十二坊的第九个坊——器物图册。
 *
 * <p><b>这一屏讲什么。</b>将作监掌营造法式：城里造什么器物、用多少料、
 * 图纸改了几版还没定稿、有多少单变更还压在流程里。它不重复任何一殿：
 * 工部看产线在造什么，将作看的是<b>造的东西本身长什么样</b>——
 * 物料、BOM、图纸、变更这四本账，在 2D 后台要切四个页面才看得全。</p>
 *
 * <p><b>口径不另造。</b>所有数字来自 {@link PdmOverviewStatsService}，
 * 与 2D 物料页 / BOM 页 / 变更页同一批表、同一套租户隔离插件；
 * "未关闭"的判定（驳回与取消不算欠账）写在统计服务一处。</p>
 *
 * <p><b>取不到数就让它抛。</b>这里不 catch、不返回 0：库连不上与"一个物料都没录"
 * 长得一模一样。SDK 会把异常降级成 stale/degraded，屏风上写"未接通"。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class PdmOverviewProvider implements OverviewProvider {

    /** 服务一级前缀，与网关路由一致 */
    private static final String SERVICE = "pdm";

    /** 城内节点 key（将作坊），与 {@code city-layout.json} 对齐 */
    private static final String NODE_KEY = "jiangzuo-bureau";

    /** 权限码：复用 2D 物料台账同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = "sys:pdm:part:list";

    /** 物料台账列表接口：屏风页直接取这个路径，不再单发一套只读接口 */
    private static final String PART_PAGE_PATH = "/pdm/part/page";

    /** 将作统计口径 */
    private final PdmOverviewStatsService statsService;

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
        return "将作";
    }

    @Override
    public String subtitle() {
        return "产品数据";
    }

    @Override
    public String permissionCode() {
        return PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        PdmOverviewStats stats = statsService.snapshot();
        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · 四本账")
                .note("口径：在册物料 = pdm_product ENABLED；生效 BOM = RELEASED 版本；"
                        + "未关闭变更 = 未进 完成/关闭/驳回/取消 的变更单；借出 = 图纸被检出未还");
        appendKpis(builder, stats);
        appendTypeChart(builder, stats);
        appendLinks(builder);
        appendAlerts(builder, stats);
        return builder.build();
    }

    /**
     * 四张牌：在册物料、生效 BOM、未关闭变更、图纸文档。
     *
     * <p>"未关闭变更"是这一屏唯一带语气的牌：变更单压着不批，BOM 和图纸就一直在改
     * 或者一直没人敢改——这是将作要盯的头等事。其余三张牌只报数，语气留给告警条。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendKpis(Overviews.Builder builder, PdmOverviewStats stats) {
        builder.kpi(Overviews.count("parts", "在册物料", stats.partEnabled(), OverviewTone.FLAT,
                        partHint(stats)))
                .kpi(Overviews.count("boms", "生效 BOM", stats.bomReleased(), OverviewTone.FLAT,
                        bomHint(stats)))
                .kpi(Overviews.count("chg-open", "未关闭变更", stats.chgOpen(),
                        chgTone(stats), chgHint(stats)))
                .kpi(Overviews.count("docs", "图纸文档", stats.docReleased(), OverviewTone.FLAT,
                        docHint(stats)));
    }

    /**
     * 物料牌的提示语：停用件是沉淀在库里的历史包袱，值得说一句。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String partHint(PdmOverviewStats stats) {
        if (stats.partEnabled() <= 0 && stats.partDisabled() <= 0) {
            return "还没有物料主数据";
        }
        return "停用 " + stats.partDisabled() + " 件";
    }

    /**
     * BOM 牌的提示语：写出版本结构，避免"生效 3 份"看不出总量。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String bomHint(PdmOverviewStats stats) {
        return "草稿 " + stats.bomDraft() + " · 共 " + stats.bomTotal() + " 版";
    }

    /**
     * 未关闭变更牌的语气：有单压着就黄，没有就绿。
     *
     * @param stats 统计快照
     * @return 语气
     */
    private static OverviewTone chgTone(PdmOverviewStats stats) {
        return stats.chgOpen() > 0 ? OverviewTone.WARN : OverviewTone.GOOD;
    }

    /**
     * 未关闭变更牌的提示语：拆出"卡在评估/审批"与"执行中"两段——
     * 前者要人去催，后者只能等执行人做完，两种积压的处理方式不同。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String chgHint(PdmOverviewStats stats) {
        if (stats.chgOpen() <= 0) {
            return "没有压着的变更单";
        }
        return "评估/审批 " + stats.chgAssessing() + " · 执行中 " + stats.chgExecuting();
    }

    /**
     * 图纸牌的提示语：借出未还的单列——图纸被一人检出，全组的变更就得排队。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String docHint(PdmOverviewStats stats) {
        return "草稿 " + stats.docDraft() + " · 借出 " + stats.docCheckedOut();
    }

    /**
     * 物料类型分布柱图：成品 / 部件 / 零件 / 原材料。
     *
     * <p><b>为什么是分布不是趋势。</b>将作的账是存量不是流量：物料今天比昨天多几个
     * 没有多少信息量，"城里 40 个零件里 30 个是标准件"这种结构才是造一台机器前
     * 要先看的东西。类型码值固定四种，柱图比折线少一根轴的歧义。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendTypeChart(Overviews.Builder builder, PdmOverviewStats stats) {
        List<PdmOverviewStats.TypeCount> counts = stats.partByType();
        if (counts == null || counts.isEmpty()) {
            return;
        }
        List<String> categories = counts.stream().map(PdmOverviewStats.TypeCount::label).toList();
        List<Double> values = counts.stream().map(item -> (double) item.count()).toList();
        builder.chart(Overviews.chart("part-types", "在册物料类型分布", "bar", OverviewFormat.COUNT,
                categories, new OverviewSeries("在册件数", values)));
    }

    /**
     * 入口：物料台账。
     *
     * <p>只挂物料一个入口：BOM 与变更在 2D 里才有发布、审批等操作按钮，
     * 城里点进去也只能看；物料台账是纯只读的清单，正适合摆在屏风上。</p>
     *
     * @param builder 组装器
     */
    private static void appendLinks(Overviews.Builder builder) {
        builder.link(Overviews.link("parts", "物料台账", PART_PAGE_PATH, PERMISSION));
    }

    /**
     * 告警条：变更积压、执行中变更、BOM 草稿、图纸借出、无生效 BOM、无物料。
     *
     * <p>这些事"数字本身不会喊"：{@code 5} 张待批变更单不会自己变红，
     * {@code 0} 份生效 BOM 与"还没建 BOM"看起来一样。
     * "有物料但没有任何生效 BOM"单独成条——只有物料清单造不出一台机器，
     * 没有生效 BOM 时车间拿到的料单是没人核对过的。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendAlerts(Overviews.Builder builder, PdmOverviewStats stats) {
        if (stats.chgAssessing() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.chgAssessing()
                    + " 张变更单卡在评估或审批，需要人去催", alertCount(stats.chgAssessing()));
        }
        if (stats.chgExecuting() > 0) {
            builder.alert(OverviewAlertLevel.INFO, "有 " + stats.chgExecuting()
                    + " 张变更单正在执行，完成前以旧版 BOM / 图纸为准", null);
        }
        if (stats.bomDraft() > 0) {
            builder.alert(OverviewAlertLevel.INFO, "有 " + stats.bomDraft()
                    + " 份 BOM 草稿还没发布，发布前车间看不到", null);
        }
        if (stats.docCheckedOut() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.docCheckedOut()
                    + " 份图纸被检出未还，相关的变更与修订要排队", alertCount(stats.docCheckedOut()));
        }
        if (stats.partEnabled() > 0 && stats.bomReleased() == 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有物料在册但没有任何生效 BOM，料单无人核对", null);
        }
        if (stats.partEnabled() <= 0 && stats.partDisabled() <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "还没有录入物料主数据，将作暂时无账可理", null);
        }
    }

    /**
     * 告警计数：契约只收 {@code Integer}，超长时截顶而不是抛异常。
     *
     * @param value 计数
     * @return 截顶后的整数
     */
    private static Integer alertCount(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
