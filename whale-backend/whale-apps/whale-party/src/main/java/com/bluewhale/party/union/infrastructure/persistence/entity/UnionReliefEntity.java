package com.bluewhale.party.union.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 慰问帮扶实体，对应表 {@code pty_union_relief}。
 *
 * <p>{@code applyYear} 需要显式映射到 {@code apply_year}：字段名是业务口径的「申请年度」，
 * 而列名是数据库规范的年月命名，靠默认驼峰转换会得到 {@code apply_year} 以外的结果。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_union_relief")
public class UnionReliefEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 申请编号（租户内唯一） */
    private String reliefNo;

    /** 工会会员 ID */
    private Long unionMemberId;

    /** 申请人用户 ID */
    private Long applicantUserId;

    /** 慰问类型 */
    private String reliefType;

    /** 申请年度（计上限） */
    @TableField("apply_year")
    private Integer applyYear;

    /** 申请金额 */
    private BigDecimal amount;

    /** 申请事由 */
    private String reason;

    /** 状态 */
    private String status;

    /** 工作流实例 ID */
    private Long approvalInstanceId;

    /** 工作流状态快照 */
    private String approvalStatus;

    /** 审批降级 / 同步提示 */
    private String approvalHint;

    /** 提交审批时间 */
    private LocalDateTime submitTime;

    /** 审批通过时间 */
    private LocalDateTime approvedAt;

    /** 发放时间 */
    private LocalDateTime paidAt;

    /** 备注 */
    private String remark;
}
