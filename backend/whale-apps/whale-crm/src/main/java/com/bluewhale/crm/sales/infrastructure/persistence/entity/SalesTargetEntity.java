package com.bluewhale.crm.sales.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售目标实体，对应表 {@code crm_sales_target}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_sales_target")
public class SalesTargetEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 周期 */
    private String period;

    /** 周期类型 */
    private String periodType;

    /** 范围类型 */
    private String scopeType;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 目标金额 */
    private BigDecimal targetAmount;

    /** 备注 */
    private String remark;

    /** 创建人 */
    private Long createdBy;

    /** 乐观锁版本号 */
    @Version
    private Integer lockVersion;
}
