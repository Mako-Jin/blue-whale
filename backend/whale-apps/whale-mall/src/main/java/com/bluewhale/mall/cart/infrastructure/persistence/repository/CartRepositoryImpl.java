package com.bluewhale.mall.cart.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.mall.cart.domain.model.CartItem;
import com.bluewhale.mall.cart.domain.repository.CartRepository;
import com.bluewhale.mall.cart.infrastructure.persistence.entity.CartItemEntity;
import com.bluewhale.mall.cart.infrastructure.persistence.mapper.CartItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 购物车仓储实现（MyBatis-Plus，物理删除）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CartRepositoryImpl implements CartRepository {

    private final CartItemMapper cartItemMapper;

    @Override
    public List<CartItem> list(Long channelId, Long userId) {
        return cartItemMapper.selectList(new LambdaQueryWrapper<CartItemEntity>()
                        .eq(CartItemEntity::getChannelId, channelId)
                        .eq(CartItemEntity::getUserId, userId)
                        .orderByDesc(CartItemEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<CartItem> findById(Long id) {
        return Optional.ofNullable(cartItemMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<CartItem> findBySku(Long channelId, Long userId, Long skuId) {
        return Optional.ofNullable(cartItemMapper.selectOne(new LambdaQueryWrapper<CartItemEntity>()
                .eq(CartItemEntity::getChannelId, channelId)
                .eq(CartItemEntity::getUserId, userId)
                .eq(CartItemEntity::getSkuId, skuId))).map(this::toDomain);
    }

    @Override
    public List<CartItem> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return cartItemMapper.selectList(new LambdaQueryWrapper<CartItemEntity>()
                        .in(CartItemEntity::getId, ids))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Long save(CartItem item) {
        CartItemEntity entity = toEntity(item);
        if (entity.getId() == null) {
            cartItemMapper.insert(entity);
        } else {
            cartItemMapper.updateById(entity);
        }
        item.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public void remove(Long id) {
        cartItemMapper.deleteById(id);
    }

    @Override
    public void removeByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        cartItemMapper.delete(new LambdaQueryWrapper<CartItemEntity>()
                .in(CartItemEntity::getId, ids));
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private CartItem toDomain(CartItemEntity entity) {
        CartItem item = new CartItem();
        item.setId(entity.getId());
        item.setChannelId(entity.getChannelId());
        item.setUserId(entity.getUserId());
        item.setSkuId(entity.getSkuId());
        item.setQuantity(entity.getQuantity());
        item.setSelected(entity.getSelected() == null || entity.getSelected() == 1);
        item.setCreateTime(entity.getCreateTime());
        item.setUpdateTime(entity.getUpdateTime());
        return item;
    }

    /**
     * 领域模型转实体。
     *
     * @param item 领域模型
     * @return 实体
     */
    private CartItemEntity toEntity(CartItem item) {
        CartItemEntity entity = new CartItemEntity();
        entity.setId(item.getId());
        entity.setChannelId(item.getChannelId());
        entity.setUserId(item.getUserId());
        entity.setSkuId(item.getSkuId());
        entity.setQuantity(item.getQuantity());
        entity.setSelected(Boolean.FALSE.equals(item.getSelected()) ? 0 : 1);
        return entity;
    }
}
