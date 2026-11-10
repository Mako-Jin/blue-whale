package com.bluewhale.party.union.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 节日福利发放明细实体，对应表 {@code pty_union_welfare_item}。
 *
 * <p>快照字段（工号、姓名、金额）与签收字段全部显式映射列名：
 * 它们是账务依据，列名错位会让对账数据看起来「正常」但内容错配。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_union_welfare_item")
public class UnionWelfareItemEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 福利批次 ID */
    @TableField("welfare_id")
    private Long welfareId;

    /** 工会会员 ID */
    @TableField("union_member_id")
    private Long unionMemberId;

    /** 员工编号（发放时快照） */
    @TableField("employee_no")
    private String employeeNo;

    /** 会员姓名（发放时快照） */
    @TableField("member_name")
    private String memberName;

    /** 发放金额（标准快照） */
    private BigDecimal amount;

    /** 签收状态 */
    @TableField("sign_status")
    private String signStatus;

    /** 签收时间 */
    @TableField("signed_at")
    private LocalDateTime signedAt;

    /** 签收操作人用户 ID */
    @TableField("signed_by")
    private Long signedBy;
}
