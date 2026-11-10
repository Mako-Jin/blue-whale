package com.bluewhale.erp.mfg.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 工作中心实体，对应表 {@code erp_mfg_work_center}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_mfg_work_center")
public class WorkCenterEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 所属部门 */
    private String department;

    /** 日产能工时 */
    private BigDecimal dailyCapacityHours;

    /** 效率百分比 */
    private BigDecimal efficiencyRate;

    /** 工作日（ISO 1–7，逗号分隔） */
    private String workDays;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
