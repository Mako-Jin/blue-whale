package com.bluewhale.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 用章授权实体，对应表 {@code ct_seal_grant}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_seal_grant")
public class CtSealGrant extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 印章ID */
    private Long sealId;

    /** 被授权用户ID */
    private Long granteeUserId;

    /** 适用合同类别（空表示全类别） */
    private String contractCategory;

    /** 单份合同金额上限（空表示不限） */
    private BigDecimal maxAmount;

    /** 授权生效日期 */
    private LocalDate validFrom;

    /** 授权失效日期 */
    private LocalDate validTo;

    /** 状态：ENABLED/DISABLED */
    private String status;

    /** 授权操作人用户ID */
    private Long grantedBy;
}
