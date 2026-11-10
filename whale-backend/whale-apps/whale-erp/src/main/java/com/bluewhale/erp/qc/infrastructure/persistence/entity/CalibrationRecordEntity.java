package com.bluewhale.erp.qc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 量具校准记录实体，对应表 {@code erp_qc_calibration_record}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_qc_calibration_record")
public class CalibrationRecordEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 校准记录号 */
    private String recordNo;

    /** 量具编号 */
    private String toolNo;

    /** 校准计划主键 */
    private Long planId;

    /** 校准日期 */
    private LocalDate calibrationDate;

    /** 校准结论 */
    private String result;

    /** 校准证书号 */
    private String certificateNo;

    /** 备注 */
    private String remark;
}
