package com.bluewhale.mall.cart.application.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 加入购物车命令。
 *
 * @author BlueWhale
 */
@Data
public class CartAddCommand {

    /** 渠道 ID */
    @NotNull(message = "{validation.mall.cart-add.channel-id-required}")
    private Long channelId;

    /** SKU ID */
    @NotNull(message = "{validation.mall.cart-add.sku-id-required}")
    private Long skuId;

    /** 数量 */
    @NotNull(message = "{validation.mall.cart-add.quantity-required}")
    @Min(value = 1, message = "{validation.mall.cart-add.quantity-min}")
    private Integer quantity;
}
