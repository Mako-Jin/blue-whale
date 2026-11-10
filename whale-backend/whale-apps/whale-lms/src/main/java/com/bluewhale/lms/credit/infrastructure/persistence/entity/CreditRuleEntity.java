package com.bluewhale.lms.credit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 学分规则实体，对应表 {@code lr_credit_rule}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lr_credit_rule")
public class CreditRuleEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 规则编码（租户内唯一） */
    private String ruleCode;

    /** 规则名称 */
    private String name;

    /** 来源类型 */
    private String sourceType;

    /** 匹配对象 ID（0 为该来源全部） */
    private Long matchId;

    /** 单次学分 */
    private BigDecimal credit;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
