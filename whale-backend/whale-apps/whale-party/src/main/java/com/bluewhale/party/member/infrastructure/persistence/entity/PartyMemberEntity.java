package com.bluewhale.party.member.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 党员档案实体，对应表 {@code pty_member}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_member")
public class PartyMemberEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 党员编号（租户内唯一） */
    private String memberNo;

    /** 员工编号 */
    private String employeeNo;

    /** 平台用户 ID */
    private Long userId;

    /** 党员姓名 */
    private String memberName;

    /** 所属支部 ID */
    private Long branchId;

    /** 政治面貌 */
    private String politicalStatus;

    /** 出生日期 */
    private LocalDate birthDate;

    /** 学历编码 */
    private String education;

    /** 入党日期 */
    private LocalDate joinPartyDate;

    /** 转正日期 */
    private LocalDate fullMemberDate;

    /** 党费计算基数 */
    private BigDecimal duesBase;

    /** 联系电话 */
    private String phone;

    /** 状态 */
    private String status;

    /** 组织关系转出日期 */
    private LocalDate transferOutDate;

    /** 备注 */
    private String remark;
}
