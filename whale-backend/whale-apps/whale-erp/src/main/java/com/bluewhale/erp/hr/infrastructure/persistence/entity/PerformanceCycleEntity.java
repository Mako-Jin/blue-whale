package com.bluewhale.erp.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 绩效周期实体，对应表 {@code erp_hr_performance_cycle}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_hr_performance_cycle")
public class PerformanceCycleEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 周期编号 */
    private String cycleNo;

    /** 周期名称 */
    private String name;

    /** 考核期间 */
    private String period;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
