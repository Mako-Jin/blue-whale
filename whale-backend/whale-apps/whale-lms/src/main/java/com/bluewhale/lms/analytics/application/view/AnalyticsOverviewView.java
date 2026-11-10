package com.bluewhale.lms.analytics.application.view;

import com.bluewhale.lms.credit.domain.model.CreditBucket;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 学习看板总览。
 *
 * @author BlueWhale
 */
@Data
public class AnalyticsOverviewView {

    /** 统计日 */
    private LocalDate statDate;

    /** 统计范围 */
    private String scopeType;

    /** 范围名称 */
    private String scopeName;

    /** 在职人数 */
    private long memberTotal;

    /** 学习人数 */
    private long learnerTotal;

    /** 学习覆盖率（%） */
    private BigDecimal coverageRate;

    /** 完成人数 */
    private long learnerCompleted;

    /** 学习完成率（%） */
    private BigDecimal completionRate;

    /** 参考人数 */
    private long examTakerTotal;

    /** 通过人数 */
    private long examPassedTotal;

    /** 考试通过率（%） */
    private BigDecimal passRate;

    /** 学分总量 */
    private BigDecimal creditTotal;

    /** 获学分人数 */
    private long creditEarners;

    /** 学分分布（按人合计分档） */
    private List<CreditBucket> creditDistribution = new ArrayList<>();

    /**
     * 本次数据是否为实时计算。
     *
     * <p>{@code true} 表示当天还没有快照，页面展示的是即时算出来的数——
     * 此时不写库：看板是读接口，被刷新几次就往库里落几次快照会把「每日一快照」
     * 变成「每次访问一快照」，历史趋势立刻失真。</p>
     */
    private boolean live;

    /** 最近快照趋势（按统计日升序） */
    private List<MetricSnapshotView> trend = new ArrayList<>();
}
