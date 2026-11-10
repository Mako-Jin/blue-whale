package com.bluewhale.mall.pricing.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 区域运费费率实体，对应表 {@code mall_freight_region}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_freight_region")
public class FreightRegionRateEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 运费模板 ID */
    private Long templateId;

    /** 区域（省/直辖市名，模板内唯一） */
    private String region;

    /** 该区域运费 */
    private BigDecimal amount;

    /** 该区域满额包邮门槛（可空） */
    private BigDecimal freeThreshold;
}
