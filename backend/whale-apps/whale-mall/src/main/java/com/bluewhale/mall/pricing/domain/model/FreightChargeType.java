package com.bluewhale.mall.pricing.domain.model;

/**
 * 运费计费方式。
 *
 * <p>保留四种能一眼算清的规则。按重量、首重续重、多模板累加
 * 需要商品级模板与叠加计算，是独立一波的工作量；渠道级规则
 * 已经覆盖「零售满 99 包邮」「B2B 固定 30 元」「小件按件 5 元」
 * 这几个最常见的场景。</p>
 *
 * @author BlueWhale
 */
public enum FreightChargeType {

    /** 全免运费 */
    FREE,

    /** 固定运费 */
    FIXED,

    /** 满额包邮，未达门槛收固定运费 */
    THRESHOLD,

    /** 按件计费，每件收固定单价，可选「满 N 件包邮」 */
    BY_PIECE
}
