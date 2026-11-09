package com.bluewhale.mall.pricing.domain.model;

/**
 * 运费计费方式。
 *
 * <p>渠道级规则六种：全免、固定、满额包邮、按件、按重（首重续重）、区域。
 * 按重与区域都在渠道级模板上落地——首重续重取订单行重量快照合计，
 * 区域费率按省/区域一行覆盖模板基准；商品级多模板累加仍属另一波工作量。</p>
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
    BY_PIECE,

    /** 按重计费：首重收基础运费，超出部分按续重步长累加（不足一步按一步） */
    BY_WEIGHT,

    /** 区域运费：按省/区域费率计收，可选区域满额包邮；未命中区域回落基础运费 */
    REGIONAL
}
