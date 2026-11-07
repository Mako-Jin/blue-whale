package com.bluewhale.mall.cart.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.cart.application.command.CartAddCommand;
import com.bluewhale.mall.cart.application.command.CartSelectCommand;
import com.bluewhale.mall.cart.application.view.CartView;
import com.bluewhale.mall.cart.domain.model.CartItem;
import com.bluewhale.mall.cart.domain.repository.CartRepository;
import com.bluewhale.mall.catalog.application.model.SkuOffering;
import com.bluewhale.mall.catalog.application.service.SkuOfferingService;
import com.bluewhale.mall.catalog.domain.model.Sku;
import com.bluewhale.mall.channel.domain.model.ChannelStatus;
import com.bluewhale.mall.channel.domain.model.MallChannel;
import com.bluewhale.mall.channel.domain.repository.ChannelRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.common.enums.CurrencyType;
import com.bluewhale.mall.common.enums.MallMode;
import com.bluewhale.mall.common.enums.SettlementType;
import com.bluewhale.mall.config.MallProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 购物车应用服务：数量累加、不可售拦截、归属校验、金额统计。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class CartAppServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private SkuOfferingService skuOfferingService;

    @Mock
    private MallProperties properties;

    @InjectMocks
    private CartAppServiceImpl service;

    private MallActor actor;

    @BeforeEach
    void setUp() {
        actor = new MallActor(7L, "buyer", Set.of(), Set.of());
        lenient().when(properties.getCartMaxQuantity()).thenReturn(999);
        lenient().when(channelRepository.findById(1L)).thenReturn(Optional.of(channel()));
    }

    @Test
    @DisplayName("同一 SKU 再次加购数量累加，不新增行")
    void addMergesQuantity() {
        when(cartRepository.findBySku(1L, 7L, 100L)).thenReturn(Optional.of(cartItem(11L, 1)));
        when(skuOfferingService.resolve(anyCollection(), any())).thenReturn(Map.of(100L, offering(true, null)));
        when(cartRepository.list(1L, 7L)).thenReturn(List.of());

        service.add(command(2), actor);

        verify(cartRepository).save(org.mockito.ArgumentMatchers.argThat(item -> item.getQuantity() == 3));
    }

    @Test
    @DisplayName("规格已停用的商品不允许加购")
    void addRejectsDisabledSku() {
        when(skuOfferingService.resolve(anyCollection(), any())).thenReturn(Map.of(100L, offering(false, "规格已停用")));

        assertThatThrownBy(() -> service.add(command(1), actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.ORDER_PRODUCT_UNAVAILABLE));

        verify(cartRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("库存不足仍可加购（等补货）")
    void addAllowsOutOfStock() {
        when(cartRepository.findBySku(1L, 7L, 100L)).thenReturn(Optional.empty());
        when(skuOfferingService.resolve(anyCollection(), any())).thenReturn(Map.of(100L, offering(false, "库存不足")));
        when(cartRepository.list(1L, 7L)).thenReturn(List.of());

        service.add(command(1), actor);

        verify(cartRepository).save(any(CartItem.class));
    }

    @Test
    @DisplayName("超出数量上限被拒绝")
    void addRejectsTooMany() {
        assertThatThrownBy(() -> service.add(command(1000), actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.CART_QUANTITY_INVALID));

        verify(cartRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("停业渠道不允许加购")
    void addRejectsInactiveChannel() {
        MallChannel channel = channel();
        channel.setStatus(ChannelStatus.DISABLED);
        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));

        assertThatThrownBy(() -> service.add(command(1), actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.CHANNEL_STATE_CONFLICT));
    }

    @Test
    @DisplayName("不能改别人的购物车条目")
    void updateRejectsForeignItem() {
        CartItem item = cartItem(11L, 2);
        item.setUserId(99L);
        when(cartRepository.findById(11L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.updateQuantity(11L, 3, actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.CART_ITEM_NOT_FOUND));
    }

    @Test
    @DisplayName("修改数量后返回最新购物车")
    void updateQuantityReturnsCart() {
        when(cartRepository.findById(11L)).thenReturn(Optional.of(cartItem(11L, 2)));
        when(cartRepository.list(1L, 7L)).thenReturn(List.of(cartItem(11L, 5)));
        when(skuOfferingService.resolve(anyCollection(), any())).thenReturn(Map.of(100L, offering(true, null)));

        CartView view = service.updateQuantity(11L, 5, actor);

        assertThat(view.getItems()).hasSize(1);
        assertThat(view.getTotalQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("删除条目")
    void removeItem() {
        when(cartRepository.findById(11L)).thenReturn(Optional.of(cartItem(11L, 2)));
        when(cartRepository.list(1L, 7L)).thenReturn(List.of());
        when(skuOfferingService.resolve(anyCollection(), any())).thenReturn(Map.of());

        CartView view = service.remove(11L, actor);

        verify(cartRepository).remove(11L);
        assertThat(view.getItems()).isEmpty();
    }

    @Test
    @DisplayName("金额只统计已选且可购买的行")
    void viewSumsSelectedOnly() {
        CartItem selected = cartItem(11L, 2);
        CartItem unselected = cartItem(12L, 3);
        unselected.setSelected(false);
        when(cartRepository.list(1L, 7L)).thenReturn(List.of(selected, unselected));
        when(skuOfferingService.resolve(anyCollection(), any()))
                .thenReturn(Map.of(100L, offering(true, null)));

        CartView view = service.view(1L, actor);

        assertThat(view.getTotalQuantity()).isEqualTo(5);
        assertThat(view.getSelectedQuantity()).isEqualTo(2);
        assertThat(view.getSelectedAmount()).isEqualByComparingTo("39.80");
        assertThat(view.getCurrencyType()).isEqualTo(CurrencyType.CASH.name());
    }

    @Test
    @DisplayName("全选某渠道购物车")
    void selectAll() {
        CartSelectCommand command = new CartSelectCommand();
        command.setChannelId(1L);
        command.setSelected(false);
        when(cartRepository.list(1L, 7L)).thenReturn(List.of(cartItem(11L, 1)));
        when(skuOfferingService.resolve(anyCollection(), any())).thenReturn(Map.of(100L, offering(true, null)));

        service.select(command, actor);

        verify(cartRepository).save(org.mockito.ArgumentMatchers.argThat(item -> !item.getSelected()));
    }

    /**
     * 断言加购被拒（封装成方法以便断言不写 lambda 里的重复代码）。
     *
     * @param command 命令
     */
    private void addAttempt(CartAddCommand command) {
        assertThatThrownBy(() -> service.add(command, actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.ORDER_PRODUCT_UNAVAILABLE));
    }

    private CartAddCommand command(int quantity) {
        CartAddCommand command = new CartAddCommand();
        command.setChannelId(1L);
        command.setSkuId(100L);
        command.setQuantity(quantity);
        return command;
    }

    private MallChannel channel() {
        MallChannel channel = new MallChannel();
        channel.setId(1L);
        channel.setChannelCode("RETAIL-1");
        channel.setChannelName("官方商城");
        channel.setMode(MallMode.RETAIL);
        channel.setSettlementType(SettlementType.ONLINE);
        channel.setCurrencyType(CurrencyType.CASH);
        channel.setStatus(ChannelStatus.ACTIVE);
        return channel;
    }

    private CartItem cartItem(Long id, int quantity) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setChannelId(1L);
        item.setUserId(7L);
        item.setSkuId(100L);
        item.setQuantity(quantity);
        item.setSelected(true);
        return item;
    }

    private SkuOffering offering(boolean available, String reason) {
        Sku sku = new Sku();
        sku.setId(100L);
        sku.setSpuId(20L);
        sku.setSkuCode("SKU-100");
        SkuOffering offering = new SkuOffering();
        offering.setSku(sku);
        offering.setSpuId(20L);
        offering.setSpuName("测试商品");
        offering.setPriceType("RETAIL");
        offering.setUnitPrice(new BigDecimal("19.90"));
        offering.setAvailableQty(available ? 10 : 0);
        offering.setAvailable(available);
        offering.setReason(reason);
        return offering;
    }
}
