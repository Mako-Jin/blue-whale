package com.bluewhale.erp.qc.application.view;

import com.bluewhale.erp.qc.domain.model.OeeRecord;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * OEE 记录视图。
 *
 * @author BlueWhale
 */
@Data
public class OeeRecordView {

    /** 主键 */
    private Long id;

    /** 设备编码 */
    private String equipmentNo;

    /** 统计日期 */
    private LocalDate recordDate;

    /** 计划运行分钟 */
    private BigDecimal plannedMinutes;

    /** 实际运行分钟 */
    private BigDecimal runMinutes;

    /** 停机分钟 */
    private BigDecimal downMinutes;

    /** 理论产量 */
    private BigDecimal idealQty;

    /** 实际产量 */
    private BigDecimal outputQty;

    /** 合格产量 */
    private BigDecimal goodQty;

    /** 时间开动率 */
    private BigDecimal availabilityRate;

    /** 性能开动率 */
    private BigDecimal performanceRate;

    /** 合格率 */
    private BigDecimal qualityRate;

    /** OEE */
    private BigDecimal oeeRate;

    /**
     * 由领域对象构建视图。
     *
     * @param record OEE 记录
     * @return 视图
     */
    public static OeeRecordView of(OeeRecord record) {
        OeeRecordView view = new OeeRecordView();
        view.setId(record.getId());
        view.setEquipmentNo(record.getEquipmentNo());
        view.setRecordDate(record.getRecordDate());
        view.setPlannedMinutes(record.getPlannedMinutes());
        view.setRunMinutes(record.getRunMinutes());
        view.setDownMinutes(record.getDownMinutes());
        view.setIdealQty(record.getIdealQty());
        view.setOutputQty(record.getOutputQty());
        view.setGoodQty(record.getGoodQty());
        view.setAvailabilityRate(record.getAvailabilityRate());
        view.setPerformanceRate(record.getPerformanceRate());
        view.setQualityRate(record.getQualityRate());
        view.setOeeRate(record.getOeeRate());
        return view;
    }
}
