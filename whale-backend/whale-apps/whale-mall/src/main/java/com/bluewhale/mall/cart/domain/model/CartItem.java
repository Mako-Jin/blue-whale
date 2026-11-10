package com.bluewhale.mall.cart.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车条目。
 *
 * <p><b>不存价格</b>：加购只记 SKU 与数量，价格在下单试算时实时解析。
 * 存价会出现「加购时 9.9，结算还是 9.9」而商品早已调价的口径漏洞，
 * 而且没有任何一处能说清「这个 9.9 是哪天的价」。</p>
 *
 * @author BlueWhale
 */
@Data
public class CartItem {

    /** 默认数量 */
    public static final int DEFAULT_QUANTITY = 1;

    /** 主键 */
    private Long id;

    /** 渠道 ID */
    private Long channelId;

    /** 买家用户 ID */
    private Long userId;

    /** SKU ID */
    private Long skuId;

    /** 数量 */
    private Integer quantity;

    /** 是否选中 */
    private Boolean selected;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
