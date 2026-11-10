package com.bluewhale.mes.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 综合看板 - 设备域聚合视图
 *
 * <p>状态计数来自设备台账当前状态快照；停机数为当日新增停机单；
 * OEE 均值取当日全部 OEE 快照的算术平均，与设备 OEE 页面同源。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesEquipmentBoardVO {

    /** 设备总数 */
    private Long totalEquipmentCount;

    /** 启用设备数 */
    private Long enabledEquipmentCount;

    /** 按当前状态分组的设备数（RUNNING/IDLE/FAULT/...） */
    private Map<String, Long> statusCounts;

    /** 故障设备数 */
    private Long faultEquipmentCount;

    /** 当日新增停机单数 */
    private Long todayDowntimeCount;

    /** 当日 OEE 快照数 */
    private Long oeeSnapshotCount;

    /** 当日平均可用率（0–1，4 位小数，无快照为空） */
    private BigDecimal avgAvailability;

    /** 当日平均性能（0–1，4 位小数，无快照为空） */
    private BigDecimal avgPerformance;

    /** 当日平均质量（0–1，4 位小数，无快照为空） */
    private BigDecimal avgQuality;

    /** 当日平均 OEE（0–1，4 位小数，无快照为空） */
    private BigDecimal avgOee;
}
