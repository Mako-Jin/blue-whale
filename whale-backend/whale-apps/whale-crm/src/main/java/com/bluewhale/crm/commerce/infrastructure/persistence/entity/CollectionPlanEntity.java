package com.bluewhale.crm.commerce.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 回款计划分期实体，对应表 {@code crm_collection_plan}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_collection_plan")
public class CollectionPlanEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** CRM 合同台账 ID */
    private Long contractId;

    /** CRM 客户 ID */
    private Long customerId;

    /** 分期序号 */
    private Integer installmentNo;

    /** 计划回款金额 */
    private BigDecimal plannedAmount;

    /** 计划回款日期 */
    private LocalDate plannedDate;

    /** 状态 */
    private String status;

    /** 催收优先级覆盖 */
    private String priorityOverride;

    /** 承诺还款日期 */
    private LocalDate promiseDate;

    /** 承诺还款金额 */
    private BigDecimal promiseAmount;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer lockVersion;
}
