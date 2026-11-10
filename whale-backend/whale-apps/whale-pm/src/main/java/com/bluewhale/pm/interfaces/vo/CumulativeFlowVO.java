package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 累积流图（按状态分类统计每日工作项数量）
 *
 * @author BlueWhale
 */
@Data
public class CumulativeFlowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 统计开始日期 */
    private LocalDate startDate;

    /** 统计结束日期 */
    private LocalDate endDate;

    /** 日期轴 */
    private List<LocalDate> dates;

    /** 状态分类序列 */
    private List<CumulativeFlowSeriesVO> series;
}
