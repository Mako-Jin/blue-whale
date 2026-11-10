package com.bluewhale.mes.quality.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SPC 控制图表点（每个子组一个点）
 *
 * @author BlueWhale
 */
@Data
public class MesSpcChartPointVO {

    /** 子组号 */
    private Integer subgroupNo;

    /** 子组样本数 */
    private Integer sampleCount;

    /** 子组均值 */
    private BigDecimal subgroupXbar;

    /** 子组极差 */
    private BigDecimal subgroupRange;

    /** 是否命中规则 */
    private Integer alertFlag;

    /** 命中规则说明 */
    private String alertRule;

    /** 最近采样时间 */
    private LocalDateTime sampledAt;
}
