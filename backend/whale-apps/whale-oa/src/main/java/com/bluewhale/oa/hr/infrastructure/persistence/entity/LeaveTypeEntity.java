package com.bluewhale.oa.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 假期类型实体，对应表 {@code oa_leave_type}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_leave_type")
public class LeaveTypeEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 计量单位 */
    private String unit;

    /** 年度默认额度 */
    private BigDecimal annualQuota;

    /** 最小请假单位 */
    private BigDecimal minUnit;

    /** 是否必须上传附件 */
    private Boolean requiresAttachment;

    /** 是否允许负额度 */
    private Boolean allowNegative;

    /** 是否启用 */
    private Boolean enabled;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    private String remark;
}
