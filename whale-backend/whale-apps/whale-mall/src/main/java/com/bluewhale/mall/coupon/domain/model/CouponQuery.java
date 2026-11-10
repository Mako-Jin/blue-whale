package com.bluewhale.mall.coupon.domain.model;

import lombok.Data;

/**
 * 优惠券模板查询条件。
 *
 * @author BlueWhale
 */
@Data
public class CouponQuery {

    /** 关键字（编码或名称） */
    private String keyword;

    /** 券类型 */
    private String couponType;

    /** 状态 */
    private String status;
}
