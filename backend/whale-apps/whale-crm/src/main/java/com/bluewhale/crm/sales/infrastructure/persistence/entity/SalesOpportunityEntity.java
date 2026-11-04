package com.bluewhale.crm.sales.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商机实体，对应表 {@code crm_opportunity}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_opportunity")
public class SalesOpportunityEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 客户 ID */
    private Long customerId;

    /** 商机名称 */
    private String name;

    /** 预计金额 */
    private BigDecimal amount;

    /** 阶段 */
    private String stage;

    /** 赢单概率 */
    private BigDecimal probability;

    /** 来源 */
    private String source;

    /** 竞争对手 */
    private String competitor;

    /** 下次行动时间 */
    private LocalDateTime nextActionAt;

    /** 进入当前阶段时间 */
    private LocalDateTime stageEnteredAt;

    /** 预计关闭日期 */
    private LocalDate expectedCloseDate;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 输单原因 */
    private String lostReason;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 版本号（乐观锁） */
    @Version
    private Integer version;
}
