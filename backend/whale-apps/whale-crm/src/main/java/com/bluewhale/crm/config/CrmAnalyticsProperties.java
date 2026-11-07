package com.bluewhale.crm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据分析（M6 指标快照 / 看板 / 分析 / 行动计划 / 导出）配置。
 *
 * <p>D12 口径：指标快照每日 02:00 生成，保留 5 年；重跑按
 * {@code metricCode + statDate + dimensionType + dimensionId} 幂等覆盖；
 * 行动计划为规则化建议，不做 AI 预测。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.crm.analytics")
public class CrmAnalyticsProperties {

    /** 分析模块开关 */
    private boolean enabled = true;

    /** 指标快照定时任务 Cron（默认每日 02:00） */
    private String snapshotCron = "0 0 2 * * ?";

    /** 行动计划生成任务 Cron（默认每日 02:30） */
    private String actionPlanCron = "0 30 2 * * ?";

    /** 指标快照保留年数 */
    private int retentionYears = 5;

    /** 单次导出最大行数 */
    private int exportMaxRows = 5000;

    /** 看板快照最大回溯天数 */
    private int dashboardLookbackDays = 90;

    /** A/B 客户未跟进提醒天数 */
    private int keyCustomerIdleDays = 30;

    /** 商机预计关闭逾期宽限天数 */
    private int opportunityOverdueDays = 0;

    /** 合同到期提醒天数 */
    private int contractExpireDays = 90;

    /** 逾期应收提醒金额阈值 */
    private String overdueAmountThreshold = "0";
}
