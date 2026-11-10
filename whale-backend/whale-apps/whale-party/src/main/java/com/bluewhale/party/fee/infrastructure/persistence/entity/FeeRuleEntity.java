package com.bluewhale.party.fee.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 党费标准实体，对应表 {@code pty_fee_rule}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pty_fee_rule")
public class FeeRuleEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 档位名称 */
    private String ruleName;

    /** 基数下限（含） */
    private BigDecimal minBase;

    /** 基数上限（不含），空表示无上限 */
    private BigDecimal maxBase;

    /** 缴费比例 */
    private BigDecimal rate;

    /** 状态 */
    private String status;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    private String remark;
}
