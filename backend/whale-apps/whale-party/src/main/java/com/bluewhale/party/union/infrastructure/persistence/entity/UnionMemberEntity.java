package com.bluewhale.party.union.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工会会员实体，对应表 {@code pty_union_member}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_union_member")
public class UnionMemberEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 会员编号（租户内唯一） */
    private String memberNo;

    /** 员工编号 */
    private String employeeNo;

    /** 平台用户 ID */
    private Long userId;

    /** 会员姓名 */
    private String memberName;

    /** 所属部门名称 */
    private String deptName;

    /** 入会日期 */
    private LocalDate joinDate;

    /** 会籍状态 */
    private String status;

    /** 退会日期 */
    private LocalDate withdrawDate;

    /** 会费基数 */
    private BigDecimal duesBase;

    /** 会费缴至月份 */
    private String duesPaidTo;

    /** 备注 */
    private String remark;
}
