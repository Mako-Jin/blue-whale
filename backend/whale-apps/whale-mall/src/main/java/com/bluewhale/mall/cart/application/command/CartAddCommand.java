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
    @NotNull(message = "必须指定商城渠道")
    private Long channelId;

    /** SKU ID */
    @NotNull(message = "必须指定商品规格")
    private Long skuId;

    /** 数量 */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于 0")
    private Integer quantity;
}
