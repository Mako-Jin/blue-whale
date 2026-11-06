package com.bluewhale.lms.credit.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 学分年度汇总视图。
 *
 * <p>汇总从台账聚合而来，不单独存快照表：台账是事实，汇总是视图，
 * 存两份就一定会有对不上的那一天。</p>
 *
 * @author BlueWhale
 */
@Data
public class CreditSummaryView {

    /** 学员用户 ID */
    private Long userId;

    /** 归属年度（null 表示全部年度） */
    private Integer periodYear;

    /** 合计学分 */
    private BigDecimal totalCredit = BigDecimal.ZERO;

    /** 按来源分组的学分 */
    private List<SourceCredit> items = new ArrayList<>();

    /**
     * 按来源分组的学分小计。
     *
     * @author BlueWhale
     */
    @Data
    public static class SourceCredit {

        /** 来源类型 */
        private String sourceType;

        /** 该来源的学分合计 */
        private BigDecimal credit;

        /** 该来源的记账笔数 */
        private int count;
    }
}
