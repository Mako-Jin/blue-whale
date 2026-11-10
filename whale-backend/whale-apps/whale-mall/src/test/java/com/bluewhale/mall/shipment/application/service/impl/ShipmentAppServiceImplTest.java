package com.bluewhale.mall.shipment.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.catalog.domain.repository.SkuStockRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.common.sequence.MallSequenceService;
import com.bluewhale.mall.config.MallProperties;
import com.bluewhale.mall.order.domain.model.MallOrder;
import com.bluewhale.mall.order.domain.model.OrderItem;
import com.bluewhale.mall.order.domain.model.OrderStatus;
import com.bluewhale.mall.order.domain.repository.OrderRepository;
import com.bluewhale.mall.shipment.application.command.ShipmentCancelCommand;
import com.bluewhale.mall.shipment.application.command.ShipmentCreateCommand;
import com.bluewhale.mall.shipment.application.command.ShipmentDeliveredCommand;
import com.bluewhale.mall.shipment.application.command.ShipmentLineCommand;
import com.bluewhale.mall.shipment.application.view.ShipmentView;
import com.bluewhale.mall.shipment.domain.model.Shipment;
import com.bluewhale.mall.shipment.domain.model.ShipmentItem;
import com.bluewhale.mall.shipment.domain.model.ShipmentStatus;
import com.bluewhale.mall.shipment.domain.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发货单应用服务（Wave 5.98）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentAppServiceImplTest {

    private static final MallActor OPERATOR = new MallActor(9L, "ops", Set.of("mall_operator"), Set.of());

    private static final MallActor BUYER = new MallActor(7L, "buyer", Set.of(), Set.of());

    private static final String SHIPMENT_NO = "MS20261107000001";

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SkuStockRepository skuStockRepository;

    @Mock
    private MallSequenceService sequenceService;

    @Mock
    private MallProperties properties;

    @InjectMocks
    private ShipmentAppServiceImpl shipmentAppService;

    @BeforeEach
    void setUp() {
        when(properties.getPageLimit()).thenReturn(100);
        when(sequenceService.nextShipmentNo()).thenReturn(SHIPMENT_NO);
        when(shipmentRepository.insert(any(Shipment.class))).thenAnswer(invocation -> {
            Shipment shipment = invocation.getArgument(0);
            shipment.setId(500L);
            return 500L;
        });
        when(orderRepository.occupyShippedQuantity(anyLong(), anyInt())).thenReturn(true);
    }

    /** 首次发货：占额度、扣预占、订单转 SHIPPED */
    @Test
    @DisplayName("首次发货推进订单到已发货并扣减预占")
    void createShipsOrder() {
        MallOrder order = paidOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ShipmentView view = shipmentAppService.create(command(1L, 11L, 2), OPERATOR);

        assertThat(view.getShipmentNo()).isEqualTo(SHIPMENT_NO);
        assertThat(view.getStatus()).isEqualTo(ShipmentStatus.SHIPPED.name());
        assertThat(view.getCarrierName()).isEqualTo("顺丰速运");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getShipTime()).isNotNull();
        verify(orderRepository).occupyShippedQuantity(11L, 2);
        verify(skuStockRepository).ship(201L, 2);
    }

    /** 拆包：第二张发货单到来时订单已是 SHIPPED，不重写发货时间 */
    @Test
    @DisplayName("拆包多单发货不重写订单发货时间")
    void createKeepsShipTimeOnSecondShipment() {
        MallOrder order = paidOrder();
        order.setStatus(OrderStatus.SHIPPED);
        LocalDateTime firstShipTime = LocalDateTime.of(2026, 11, 6, 9, 0);
        order.setShipTime(firstShipTime);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        shipmentAppService.create(command(1L, 11L, 1), OPERATOR);

        assertThat(order.getShipTime()).isEqualTo(firstShipTime);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    /** 超过可发数量：内存先拦一次，给出「哪一行超了」的提示 */
    @Test
    @DisplayName("超过可发数量拒绝发货")
    void createRejectsOverShip() {
        MallOrder order = paidOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> shipmentAppService.create(command(1L, 11L, 3), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.SHIPMENT_QUANTITY_EXCEEDED));
        verify(skuStockRepository, never()).ship(anyLong(), anyInt());
    }

    /** 条件更新失败（并发占用）：整笔失败，不扣库存 */
    @Test
    @DisplayName("行额度占用失败拒绝发货")
    void createRejectsWhenOccupiedByOthers() {
        MallOrder order = paidOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.occupyShippedQuantity(11L, 2)).thenReturn(false);

        assertThatThrownBy(() -> shipmentAppService.create(command(1L, 11L, 2), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.SHIPMENT_QUANTITY_EXCEEDED));
        verify(skuStockRepository, never()).ship(anyLong(), anyInt());
    }

    /** 运单号已被占用 */
    @Test
    @DisplayName("运单号重复拒绝建单")
    void createRejectsDuplicateTrackingNo() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder()));
        when(shipmentRepository.existsTrackingNo("SF", "SF123456789")).thenReturn(true);

        assertThatThrownBy(() -> shipmentAppService.create(command(1L, 11L, 1), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.SHIPMENT_TRACKING_CONFLICT));
    }

    /** 已完成的订单不能再发货 */
    @Test
    @DisplayName("已完成订单拒绝发货")
    void createRejectsCompletedOrder() {
        MallOrder order = paidOrder();
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> shipmentAppService.create(command(1L, 11L, 1), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.SHIPMENT_ORDER_STATE_INVALID));
    }

    /** 买家不能发货 */
    @Test
    @DisplayName("非运营拒绝发货")
    void createRejectsBuyer() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder()));

        assertThatThrownBy(() -> shipmentAppService.create(command(1L, 11L, 1), BUYER))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.ORDER_FORBIDDEN));
    }

    /** 明细行不属于该订单 */
    @Test
    @DisplayName("明细行不属于订单拒绝发货")
    void createRejectsForeignItem() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder()));

        assertThatThrownBy(() -> shipmentAppService.create(command(1L, 99L, 1), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.SHIPMENT_ITEM_NOT_FOUND));
    }

    /** 重复回调：已签收直接返回，不重复写库 */
    @Test
    @DisplayName("重复签收回调幂等")
    void markDeliveredIsIdempotent() {
        Shipment shipment = shipment(ShipmentStatus.DELIVERED);
        when(shipmentRepository.findByNo(SHIPMENT_NO)).thenReturn(Optional.of(shipment));

        shipmentAppService.markDelivered(deliveredCommand());

        verify(shipmentRepository, never()).updateStatus(any(Shipment.class));
        verify(orderRepository, never()).update(any(MallOrder.class));
    }

    /** 全部发满且全部签收 → 订单完成 */
    @Test
    @DisplayName("全部签收且发满后订单完成")
    void markDeliveredCompletesOrder() {
        Shipment shipment = shipment(ShipmentStatus.SHIPPED);
        when(shipmentRepository.findByNo(SHIPMENT_NO)).thenReturn(Optional.of(shipment));
        MallOrder order = paidOrder();
        order.setStatus(OrderStatus.SHIPPED);
        order.getItems().forEach(item -> item.setShippedQuantity(item.getQuantity()));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.listByOrderId(1L)).thenReturn(List.of(shipment));

        shipmentAppService.markDelivered(deliveredCommand());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(shipmentRepository).updateStatus(shipment);
    }

    /** 还有一行没发：签收也不能完结订单，否则剩下的货永远发不出去 */
    @Test
    @DisplayName("未发满时签收不完成订单")
    void markDeliveredKeepsOrderWhenNotFullyShipped() {
        Shipment shipment = shipment(ShipmentStatus.SHIPPED);
        when(shipmentRepository.findByNo(SHIPMENT_NO)).thenReturn(Optional.of(shipment));
        MallOrder order = paidOrder();
        order.setStatus(OrderStatus.SHIPPED);
        order.getItems().get(0).setShippedQuantity(1);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.listByOrderId(1L)).thenReturn(List.of(shipment));

        shipmentAppService.markDelivered(deliveredCommand());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    /** 作废：释放额度与预占，订单退回待发货 */
    @Test
    @DisplayName("作废唯一发货单后订单退回待发货")
    void cancelRevertsOrder() {
        Shipment shipment = shipment(ShipmentStatus.SHIPPED);
        when(shipmentRepository.findById(500L)).thenReturn(Optional.of(shipment));
        MallOrder order = paidOrder();
        order.setStatus(OrderStatus.SHIPPED);
        order.setShipTime(LocalDateTime.now());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.listByOrderId(1L)).thenReturn(List.of(shipment));
        ShipmentCancelCommand cancel = new ShipmentCancelCommand();
        cancel.setReason("错发");

        shipmentAppService.cancel(500L, cancel, OPERATOR);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getShipTime()).isNull();
        verify(orderRepository).releaseShippedQuantity(11L, 2);
        verify(skuStockRepository).unship(201L, 2);
    }

    /** 还有其他有效发货单：作废一张不改变订单状态 */
    @Test
    @DisplayName("仍有其他有效发货单时订单保持已发货")
    void cancelKeepsOrderWhenOtherEffective() {
        Shipment shipment = shipment(ShipmentStatus.SHIPPED);
        Shipment other = shipment(ShipmentStatus.SHIPPED);
        other.setId(501L);
        when(shipmentRepository.findById(500L)).thenReturn(Optional.of(shipment));
        MallOrder order = paidOrder();
        order.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.listByOrderId(1L)).thenReturn(List.of(shipment, other));
        ShipmentCancelCommand cancel = new ShipmentCancelCommand();
        cancel.setReason("错发");

        shipmentAppService.cancel(500L, cancel, OPERATOR);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(orderRepository, never()).update(any(MallOrder.class));
    }

    /** 已支付订单（一行两件） */
    private MallOrder paidOrder() {
        MallOrder order = new MallOrder();
        order.setId(1L);
        order.setOrderNo("MO20261107000001");
        order.setChannelId(2L);
        order.setStatus(OrderStatus.PAID);
        order.setTotalQuantity(2);
        OrderItem item = new OrderItem();
        item.setId(11L);
        item.setOrderId(1L);
        item.setSpuId(200L);
        item.setSkuId(201L);
        item.setSpuName("测试商品");
        item.setSkuCode("SKU-201");
        item.setQuantity(2);
        item.setShippedQuantity(0);
        List<OrderItem> items = new ArrayList<>();
        items.add(item);
        order.setItems(items);
        return order;
    }

    /** 构造建单命令 */
    private ShipmentCreateCommand command(Long orderId, Long orderItemId, int quantity) {
        ShipmentLineCommand line = new ShipmentLineCommand();
        line.setOrderItemId(orderItemId);
        line.setQuantity(quantity);
        ShipmentCreateCommand command = new ShipmentCreateCommand();
        command.setOrderId(orderId);
        command.setCarrierCode("SF");
        command.setTrackingNo("SF123456789");
        List<ShipmentLineCommand> lines = new ArrayList<>();
        lines.add(line);
        command.setItems(lines);
        return command;
    }

    /** 构造签收回调 */
    private ShipmentDeliveredCommand deliveredCommand() {
        ShipmentDeliveredCommand command = new ShipmentDeliveredCommand();
        command.setShipmentNo(SHIPMENT_NO);
        return command;
    }

    /** 构造指定状态的发货单（明细 2 件） */
    private Shipment shipment(ShipmentStatus status) {
        ShipmentItem item = new ShipmentItem();
        item.setOrderItemId(11L);
        item.setSpuId(200L);
        item.setSkuId(201L);
        item.setSpuName("测试商品");
        item.setQuantity(2);
        Shipment shipment = Shipment.create(SHIPMENT_NO, 1L, "MO20261107000001", 2L,
                com.bluewhale.mall.shipment.domain.model.CarrierType.SF, "SF123456789",
                new ArrayList<>(List.of(item)), LocalDateTime.now());
        shipment.setId(500L);
        shipment.setStatus(status);
        return shipment;
    }
}
