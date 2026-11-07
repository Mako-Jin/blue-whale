package com.bluewhale.mall.order.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.cart.domain.model.CartItem;
import com.bluewhale.mall.cart.domain.repository.CartRepository;
import com.bluewhale.mall.catalog.application.model.SkuOffering;
import com.bluewhale.mall.catalog.application.service.SkuOfferingService;
import com.bluewhale.mall.catalog.domain.model.Sku;
import com.bluewhale.mall.catalog.domain.repository.SkuStockRepository;
import com.bluewhale.mall.channel.domain.model.MallChannel;
import com.bluewhale.mall.channel.domain.repository.ChannelRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.common.enums.MallMode;
import com.bluewhale.mall.common.enums.SettlementType;
import com.bluewhale.mall.common.sequence.MallSequenceService;
import com.bluewhale.mall.config.MallProperties;
import com.bluewhale.mall.order.application.command.OrderCreateCommand;
import com.bluewhale.mall.order.application.view.OrderView;
import com.bluewhale.mall.order.domain.model.MallOrder;
import com.bluewhale.mall.order.domain.model.OrderItem;
import com.bluewhale.mall.order.domain.model.OrderStatus;
import com.bluewhale.mall.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下单主链路测试（幂等、预占失败、取消释放）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class OrderAppServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private SkuOfferingService skuOfferingService;

    @Mock
    private SkuStockRepository skuStockRepository;

    @Mock
    private MallSequenceService sequenceService;

    @Mock
    private MallProperties properties;

    @InjectMocks
    private OrderAppServiceImpl orderAppService;

    private MallActor actor;

    @BeforeEach
    void setUp() {
        actor = new MallActor(7L, "buyer", java.util.Set.of("mall_operator"), java.util.Set.of());
        // 三条阈值只在部分用例里被读到，用 lenient 声明避免严格模式误判
        lenient().when(properties.getOrderMaxLines()).thenReturn(50);
        lenient().when(properties.getOrderExpireMinutes()).thenReturn(30);
        lenient().when(properties.isIdempotencyRequired()).thenReturn(true);
    }

    /**
     * 命中已有幂等键时直接返回原订单，不重复扣库存、不重复建单。
     */
    @Test
    @DisplayName("重复提交返回首次订单，不再预占库存")
    void createIsIdempotent() {
        MallOrder existing = existingOrder();
        when(orderRepository.findByIdempotency(7L, "K-1")).thenReturn(Optional.of(existing));

        OrderView view = orderAppService.create(command(), "K-1", actor);

        assertThat(view.getOrderNo()).isEqualTo("MO20261106000001");
        verify(skuStockRepository, never()).reserve(anyLong(), anyInt());
        verify(orderRepository, never()).insert(any(MallOrder.class));
    }

    /**
     * 正常下单：预占库存、建单、清购物车。
     */
    @Test
    @DisplayName("正常下单预占库存并清空已购购物车条目")
    void createReservesStockAndClearsCart() {
        when(orderRepository.findByIdempotency(anyLong(), anyString())).thenReturn(Optional.empty());
        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel()));
        when(cartRepository.list(1L, 7L)).thenReturn(List.of(cartItem(11L, 100L, 2)));
        when(skuOfferingService.resolve(anyCollection(), any())).thenReturn(Map.of(100L, offering()));
        when(skuOfferingService.requireSellable(any(SkuOffering.class), anyInt()))
                .thenReturn(new BigDecimal("19.90"));
        when(skuStockRepository.reserve(100L, 2)).thenReturn(true);
        when(sequenceService.nextOrderNo()).thenReturn("MO20261106000002");
        when(orderRepository.insert(any(MallOrder.class))).thenAnswer(invocation -> {
            MallOrder order = invocation.getArgument(0);
            order.setId(99L);
            return 99L;
        });

        OrderView view = orderAppService.create(command(), "K-2", actor);

        assertThat(view.getTotalAmount()).isEqualByComparingTo("39.80");
        assertThat(view.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT.name());
        verify(skuStockRepository).reserve(100L, 2);
        verify(cartRepository).removeByIds(List.of(11L));
        verify(orderRepository).appendLog(any(), any());
    }

    /**
     * 预占失败必须整单失败，且不写订单。
     */
    @Test
    @DisplayName("库存不足时不建单")
    void createFailsWhenStockNotEnough() {
        when(orderRepository.findByIdempotency(anyLong(), anyString())).thenReturn(Optional.empty());
        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel()));
        when(cartRepository.list(1L, 7L)).thenReturn(List.of(cartItem(11L, 100L, 2)));
        when(skuOfferingService.resolve(anyCollection(), any())).thenReturn(Map.of(100L, offering()));
        when(skuOfferingService.requireSellable(any(SkuOffering.class), anyInt()))
                .thenReturn(new BigDecimal("19.90"));
        when(skuStockRepository.reserve(100L, 2)).thenReturn(false);

        assertThatThrownBy(() -> orderAppService.create(command(), "K-3", actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.STOCK_INSUFFICIENT));
        verify(orderRepository, never()).insert(any(MallOrder.class));
    }

    /**
     * 取消待支付订单释放预占。
     */
    @Test
    @DisplayName("取消订单释放库存预占")
    void cancelReleasesStock() {
        when(orderRepository.findById(5L)).thenReturn(Optional.of(existingOrder()));

        orderAppService.cancel(5L, "不想要了", actor);

        verify(skuStockRepository).release(100L, 2);
        verify(orderRepository).update(any(MallOrder.class));
    }

    /**
     * 构造下单命令。
     *
     * @return 命令
     */
    private OrderCreateCommand command() {
        OrderCreateCommand command = new OrderCreateCommand();
        command.setChannelId(1L);
        command.setItemIds(new ArrayList<>());
        return command;
    }

    /**
     * 构造渠道。
     *
     * @return 渠道
     */
    private MallChannel channel() {
        MallChannel channel = new MallChannel();
        channel.setId(1L);
        channel.setChannelCode("RETAIL-1");
        channel.setChannelName("官方商城");
        channel.setMode(MallMode.RETAIL);
        channel.setSettlementType(SettlementType.ONLINE);
        channel.setCurrencyType(com.bluewhale.mall.common.enums.CurrencyType.CASH);
        return channel;
    }

    /**
     * 构造购物车条目。
     *
     * @param id 条目 ID
     * @param skuId SKU ID
     * @param quantity 数量
     * @return 条目
     */
    private CartItem cartItem(Long id, Long skuId, int quantity) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setChannelId(1L);
        item.setUserId(7L);
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        item.setSelected(true);
        return item;
    }

    /**
     * 构造可售单元。
     *
     * @return 可售单元
     */
    private SkuOffering offering() {
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
        offering.setAvailable(true);
        return offering;
    }

    /**
     * 构造已有订单。
     *
     * @return 订单
     */
    private MallOrder existingOrder() {
        MallOrder order = new MallOrder();
        order.setId(5L);
        order.setOrderNo("MO20261106000001");
        order.setBuyerUserId(7L);
        order.setChannelId(1L);
        order.setChannelCode("RETAIL-1");
        order.setMode(MallMode.RETAIL);
        order.setSettlementType(SettlementType.ONLINE);
        order.setCurrencyType(com.bluewhale.mall.common.enums.CurrencyType.CASH);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setIdempotencyKey("K-1");
        order.setTotalAmount(new BigDecimal("39.80"));
        order.setTotalQuantity(2);
        order.setItemCount(1);
        OrderItem item = new OrderItem();
        item.setSkuId(100L);
        item.setSkuCode("SKU-100");
        item.setSpuName("测试商品");
        item.setUnitPrice(new BigDecimal("19.90"));
        item.setQuantity(2);
        item.setAmount(new BigDecimal("39.80"));
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }
}
