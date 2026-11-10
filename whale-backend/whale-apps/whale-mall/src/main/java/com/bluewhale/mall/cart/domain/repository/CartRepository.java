package com.bluewhale.mall.cart.domain.repository;

import com.bluewhale.mall.cart.domain.model.CartItem;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 购物车仓储。
 *
 * <p>删除是<b>物理删除</b>：购物车条目的正确删除语义就是「不在了」。
 * 挂逻辑删除会让 {@code (channel, user, sku)} 唯一键在「删了再加」时撞键。
 * 下单成功后清空已购条目也走同一套删除。</p>
 *
 * @author BlueWhale
 */
public interface CartRepository {

    /**
     * 查询某人在某渠道的购物车。
     *
     * @param channelId 渠道 ID
     * @param userId 用户 ID
     * @return 条目列表
     */
    List<CartItem> list(Long channelId, Long userId);

    /**
     * 按 ID 查询。
     *
     * @param id 条目 ID
     * @return 条目
     */
    Optional<CartItem> findById(Long id);

    /**
     * 按 (渠道, 用户, SKU) 查询。
     *
     * @param channelId 渠道 ID
     * @param userId 用户 ID
     * @param skuId SKU ID
     * @return 条目
     */
    Optional<CartItem> findBySku(Long channelId, Long userId, Long skuId);

    /**
     * 按 ID 批量查询。
     *
     * @param ids 条目 ID 集合
     * @return 条目列表
     */
    List<CartItem> findByIds(Collection<Long> ids);

    /**
     * 保存（新建或更新数量/选中态）。
     *
     * @param item 条目
     * @return 条目 ID
     */
    Long save(CartItem item);

    /**
     * 物理删除。
     *
     * @param id 条目 ID
     */
    void remove(Long id);

    /**
     * 物理批量删除。
     *
     * @param ids 条目 ID 集合
     */
    void removeByIds(Collection<Long> ids);
}
