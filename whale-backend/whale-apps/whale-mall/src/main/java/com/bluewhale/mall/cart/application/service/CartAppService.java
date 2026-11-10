package com.bluewhale.mall.cart.application.service;

import com.bluewhale.mall.cart.application.command.CartAddCommand;
import com.bluewhale.mall.cart.application.command.CartSelectCommand;
import com.bluewhale.mall.cart.application.view.CartView;
import com.bluewhale.mall.common.MallActor;

/**
 * 购物车应用服务。
 *
 * @author BlueWhale
 */
public interface CartAppService {

    /**
     * 查看购物车。
     *
     * @param channelId 渠道 ID
     * @param actor 操作人
     * @return 购物车视图
     */
    CartView view(Long channelId, MallActor actor);

    /**
     * 加入购物车（同 SKU 数量累加）。
     *
     * @param command 命令
     * @param actor 操作人
     * @return 购物车视图
     */
    CartView add(CartAddCommand command, MallActor actor);

    /**
     * 修改数量。
     *
     * @param itemId 条目 ID
     * @param quantity 数量
     * @param actor 操作人
     * @return 购物车视图
     */
    CartView updateQuantity(Long itemId, Integer quantity, MallActor actor);

    /**
     * 删除条目。
     *
     * @param itemId 条目 ID
     * @param actor 操作人
     * @return 购物车视图
     */
    CartView remove(Long itemId, MallActor actor);

    /**
     * 批量修改选中态。
     *
     * @param command 命令
     * @param actor 操作人
     * @return 购物车视图
     */
    CartView select(CartSelectCommand command, MallActor actor);
}
