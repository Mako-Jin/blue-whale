package com.bluewhale.crm.integration.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 商机只读投影实体，对应表 {@code crm_opportunity}。
 *
 * <p>仅用于客户 360 聚合；商机写模型归 M2 的销售上下文。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_opportunity")
public class OpportunityEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 客户 ID */
    private Long customerId;

    /** 商机名称 */
    private String name;

    /** 金额 */
    private BigDecimal amount;

    /** 阶段 */
    private String stage;

    /** 预计关闭日期 */
    private LocalDate expectedCloseDate;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 输单原因 */
    private String lostReason;

    /** 关闭时间 */
    private java.time.LocalDateTime closedAt;
}
