package com.bluewhale.mall.cart.application.service.impl;

import com.bluewhale.mall.cart.application.command.CartAddCommand;
import com.bluewhale.mall.cart.application.command.CartSelectCommand;
import com.bluewhale.mall.cart.application.service.CartAppService;
import com.bluewhale.mall.cart.application.view.CartItemView;
import com.bluewhale.mall.cart.application.view.CartView;
import com.bluewhale.mall.cart.domain.model.CartItem;
import com.bluewhale.mall.cart.domain.repository.CartRepository;
import com.bluewhale.mall.catalog.application.model.SkuOffering;
import com.bluewhale.mall.catalog.application.service.SkuOfferingService;
import com.bluewhale.mall.channel.domain.model.MallChannel;
import com.bluewhale.mall.channel.domain.repository.ChannelRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.common.enums.CurrencyType;
import com.bluewhale.mall.config.MallProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 购物车应用服务实现。
 *
 * <p>加购时允许「库存不足」的商品进车（用户可以等补货后再买），但<b>不允许</b>
 * 下架、停用规格、该渠道无价的商品进车——它们放进购物车只会让结算页整单失败，
 * 用户却不知道是哪一件的问题。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CartAppServiceImpl implements CartAppService {

    /** 库存不足的中文原因（允许进车的唯一「不可售」情形） */
    private static final String REASON_OUT_OF_STOCK = "库存不足";

    private final CartRepository cartRepository;

    private final ChannelRepository channelRepository;

    private final SkuOfferingService skuOfferingService;

    private final MallProperties properties;

    @Override
    public CartView view(Long channelId, MallActor actor) {
        Long userId = actor.requireUserId();
        MallChannel channel = requireChannel(channelId);
        return buildView(channel, cartRepository.list(channelId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartView add(CartAddCommand command, MallActor actor) {
        Long userId = actor.requireUserId();
        MallChannel channel = requireChannel(command.getChannelId());
        if (!channel.isActive()) {
            throw MallErrorCode.exception(MallErrorCode.CHANNEL_STATE_CONFLICT, "该商城渠道已停业");
        }
        int quantity = normalizeQuantity(command.getQuantity());
        SkuOffering offering = skuOfferingService
                .resolve(List.of(command.getSkuId()), channel.getMode()).get(command.getSkuId());
        if (offering == null) {
            throw MallErrorCode.exception(MallErrorCode.SKU_NOT_FOUND);
        }
        if (!offering.isAvailable() && !REASON_OUT_OF_STOCK.equals(offering.getReason())) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_PRODUCT_UNAVAILABLE,
                    "该商品当前不可购买：" + offering.getReason());
        }
        CartItem item = cartRepository.findBySku(channel.getId(), userId, command.getSkuId())
                .orElseGet(() -> newItem(channel.getId(), userId, command.getSkuId()));
        int merged = (item.getQuantity() == null ? 0 : item.getQuantity()) + quantity;
        if (merged > properties.getCartMaxQuantity()) {
            throw MallErrorCode.exception(MallErrorCode.CART_QUANTITY_INVALID,
                    "单件商品最多购买 " + properties.getCartMaxQuantity() + " 件");
        }
        item.setQuantity(merged);
        item.setSelected(true);
        cartRepository.save(item);
        return buildView(channel, cartRepository.list(channel.getId(), userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartView updateQuantity(Long itemId, Integer quantity, MallActor actor) {
        Long userId = actor.requireUserId();
        CartItem item = requireOwnedItem(itemId, userId);
        item.setQuantity(normalizeQuantity(quantity));
        cartRepository.save(item);
        MallChannel channel = requireChannel(item.getChannelId());
        return buildView(channel, cartRepository.list(item.getChannelId(), userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartView remove(Long itemId, MallActor actor) {
        Long userId = actor.requireUserId();
        CartItem item = requireOwnedItem(itemId, userId);
        cartRepository.remove(itemId);
        MallChannel channel = requireChannel(item.getChannelId());
        return buildView(channel, cartRepository.list(item.getChannelId(), userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartView select(CartSelectCommand command, MallActor actor) {
        Long userId = actor.requireUserId();
        MallChannel channel = requireChannel(command.getChannelId());
        boolean selected = command.getSelected() == null || command.getSelected();
        List<CartItem> items = command.getItemIds() == null || command.getItemIds().isEmpty()
                ? cartRepository.list(channel.getId(), userId)
                : cartRepository.findByIds(command.getItemIds());
        for (CartItem item : items) {
            if (!userId.equals(item.getUserId()) || !channel.getId().equals(item.getChannelId())) {
                throw MallErrorCode.exception(MallErrorCode.CART_ITEM_NOT_FOUND);
            }
            item.setSelected(selected);
            cartRepository.save(item);
        }
        return buildView(channel, cartRepository.list(channel.getId(), userId));
    }

    /**
     * 组装购物车视图（实时解析价格与可售状态）。
     *
     * @param channel 渠道
     * @param items 条目列表
     * @return 购物车视图
     */
    private CartView buildView(MallChannel channel, List<CartItem> items) {
        CartView view = new CartView();
        view.setChannelId(channel.getId());
        view.setChannelName(channel.getChannelName());
        view.setCurrencyType(channel.getCurrencyType() == null
                ? CurrencyType.CASH.name() : channel.getCurrencyType().name());
        Map<Long, SkuOffering> offerings = skuOfferingService.resolve(
                items.stream().map(CartItem::getSkuId).toList(), channel.getMode());
        int totalQuantity = 0;
        int selectedQuantity = 0;
        BigDecimal selectedAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<CartItemView> views = new ArrayList<>(items.size());
        for (CartItem item : items) {
            SkuOffering offering = offerings.get(item.getSkuId());
            CartItemView line = toLine(item, offering);
            views.add(line);
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            totalQuantity += quantity;
            boolean counted = Boolean.TRUE.equals(item.getSelected()) && Boolean.TRUE.equals(line.getAvailable());
            if (counted) {
                selectedQuantity += quantity;
                selectedAmount = selectedAmount.add(line.getAmount());
            }
        }
        view.setItems(views);
        view.setTotalQuantity(totalQuantity);
        view.setSelectedQuantity(selectedQuantity);
        view.setSelectedAmount(selectedAmount);
        return view;
    }

    /**
     * 条目转视图。
     *
     * @param item 条目
     * @param offering 可售单元，可为 null
     * @return 视图
     */
    private CartItemView toLine(CartItem item, SkuOffering offering) {
        CartItemView line = new CartItemView();
        line.setId(item.getId());
        line.setSkuId(item.getSkuId());
        line.setQuantity(item.getQuantity());
        line.setSelected(item.getSelected());
        if (offering == null) {
            line.setAvailable(false);
            line.setUnavailableReason("商品或规格已不存在");
            line.setAvailableQty(0);
            return line;
        }
        line.setSkuCode(offering.getSku().getSkuCode());
        line.setSpuId(offering.getSpuId());
        line.setSpuName(offering.getSpuName());
        line.setSpecJson(offering.getSku().getSpecJson());
        line.setImageUrl(offering.getSku().getImageUrl() == null
                ? offering.getMainImage() : offering.getSku().getImageUrl());
        line.setUnitPrice(offering.getUnitPrice());
        line.setPriceType(offering.getPriceType());
        line.setAvailableQty(offering.getAvailableQty());
        line.setAvailable(offering.isAvailable());
        line.setUnavailableReason(offering.getReason());
        if (offering.getUnitPrice() != null && item.getQuantity() != null) {
            line.setAmount(offering.getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP));
        }
        return line;
    }

    /**
     * 构造新条目。
     *
     * @param channelId 渠道 ID
     * @param userId 用户 ID
     * @param skuId SKU ID
     * @return 条目
     */
    private CartItem newItem(Long channelId, Long userId, Long skuId) {
        CartItem item = new CartItem();
        item.setChannelId(channelId);
        item.setUserId(userId);
        item.setSkuId(skuId);
        item.setQuantity(0);
        item.setSelected(true);
        return item;
    }

    /**
     * 校验数量。
     *
     * @param quantity 数量
     * @return 合法数量
     */
    private int normalizeQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw MallErrorCode.exception(MallErrorCode.CART_QUANTITY_INVALID, "数量必须大于 0");
        }
        if (quantity > properties.getCartMaxQuantity()) {
            throw MallErrorCode.exception(MallErrorCode.CART_QUANTITY_INVALID,
                    "单件商品最多购买 " + properties.getCartMaxQuantity() + " 件");
        }
        return quantity;
    }

    /**
     * 查询属于自己的条目。
     *
     * @param itemId 条目 ID
     * @param userId 用户 ID
     * @return 条目
     */
    private CartItem requireOwnedItem(Long itemId, Long userId) {
        CartItem item = cartRepository.findById(itemId)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.CART_ITEM_NOT_FOUND));
        if (!userId.equals(item.getUserId())) {
            throw MallErrorCode.exception(MallErrorCode.CART_ITEM_NOT_FOUND);
        }
        return item;
    }

    /**
     * 查询渠道。
     *
     * @param channelId 渠道 ID
     * @return 渠道
     */
    private MallChannel requireChannel(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.CHANNEL_NOT_FOUND));
    }
}
