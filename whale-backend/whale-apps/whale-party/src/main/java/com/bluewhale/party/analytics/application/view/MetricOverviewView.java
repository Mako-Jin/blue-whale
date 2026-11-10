package com.bluewhale.party.analytics.application.view;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 看板总览。
 *
 * <p>{@link #live} 为 true 表示该日没有快照、数据是实时计算的：看板据此提示
 * 「尚未生成当日快照」，避免把实时值当成归档值用于审计。</p>
 *
 * @author BlueWhale
 */
@Data
public class MetricOverviewView {

    /** 统计日 */
    private LocalDate statDate;

    /** 范围：ALL / BRANCH */
    private String scopeType;

    /** 范围 ID */
    private Long scopeId;

    /** 范围名称 */
    private String scopeName;

    /** 是否实时计算（无快照） */
    private boolean live;

    /** 指标列表 */
    private List<MetricItemView> items = new ArrayList<>();

    /** 近 N 日趋势（党务看收缴率，工会看覆盖率） */
    private List<MetricTrendPointView> trend = new ArrayList<>();
}
