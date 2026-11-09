package com.bluewhale.mall.pricing.domain.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 区域运费费率。
 *
 * <p>{@link FreightChargeType#REGIONAL} 模板下按省/区域一行：区域运费覆盖
 * 模板基准运费，可选「该区域满额包邮」门槛。未命中任何区域时回落模板
 * {@code baseAmount}，让新建模板在补齐区域费率前也能安全计价。</p>
 *
 * @author BlueWhale
 */
@Data
public class FreightRegionRate {

    /** 主键（模板内新建行时为 null） */
    private Long id;

    /** 所属运费模板 ID */
    private Long templateId;

    /** 区域（省/直辖市名，模板内唯一） */
    private String region;

    /** 该区域运费 */
    private BigDecimal amount;

    /** 该区域满额包邮门槛（空 = 该区域不设金额包邮） */
    private BigDecimal freeThreshold;
}
