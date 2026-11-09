package com.bluewhale.mall.pricing.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 运费模板实体，对应表 {@code mall_freight_template}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_freight_template")
public class FreightTemplateEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 模板名称 */
    private String templateName;

    /** 计费方式 */
    private String chargeType;

    /** 固定运费金额 */
    private BigDecimal baseAmount;

    /** 包邮门槛 */
    private BigDecimal freeThreshold;

    /** 每件运费（仅 BY_PIECE） */
    private BigDecimal piecePrice;

    /** 包邮件数门槛（仅 BY_PIECE 可选） */
    private Integer pieceFreeThreshold;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
