package com.bluewhale.alert.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 告警统计视图对象（运维看板）。
 *
 * @author BlueWhale
 */
@Data
public class AlertRecordStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前活跃告警数（状态为 FIRING） */
    private long activeCount;

    /** 记录总数（按查询时间范围过滤） */
    private long totalCount;

    /** 已恢复数 */
    private long recoveredCount;

    /** 已静默数 */
    private long silencedCount;

    /** 按级别统计 */
    private Map<String, Long> byLevel;

    /** 触发次数 TOP N 规则 */
    private List<RuleCount> topRules;

    /** 平均恢复时长（秒），无恢复记录时为 0 */
    private double mttrSeconds;

    /**
     * 规则触发次数。
     *
     * @author BlueWhale
     */
    @Data
    public static class RuleCount implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 规则编码 */
        private String ruleCode;

        /** 触发次数 */
        private long count;

        /**
         * 构造规则计数
         *
         * @param ruleCode 规则编码
         * @param count    触发次数
         */
        public RuleCount(String ruleCode, long count) {
            this.ruleCode = ruleCode;
            this.count = count;
        }
    }
}
