package com.bluewhale.erp.qc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 计量器具台账实体，对应表 {@code erp_qc_measuring_tool}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_qc_measuring_tool")
public class MeasuringToolEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 量具编号 */
    private String toolNo;

    /** 量具名称 */
    private String name;

    /** 量具类型 */
    private String toolType;

    /** 精度值描述 */
    private String precisionValue;

    /** 校准周期（月） */
    private Integer calibrationCycleMonths;

    /** 上次校准日期 */
    private LocalDate lastCalibrationDate;

    /** 下次校准日期 */
    private LocalDate nextCalibrationDate;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
