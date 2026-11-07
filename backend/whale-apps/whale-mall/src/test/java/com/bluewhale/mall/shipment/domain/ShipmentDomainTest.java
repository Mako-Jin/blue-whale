package com.bluewhale.mall.shipment.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.shipment.domain.model.CarrierType;
import com.bluewhale.mall.shipment.domain.model.Shipment;
import com.bluewhale.mall.shipment.domain.model.ShipmentItem;
import com.bluewhale.mall.shipment.domain.model.ShipmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 发货单领域规则（Wave 5.98）。
 *
 * @author BlueWhale
 */
class ShipmentDomainTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 10, 0);

    /** 正常建单：状态在途、件数为明细汇总 */
    @Test
    @DisplayName("建单成功：状态在途且件数按明细汇总")
    void createSucceeds() {
        Shipment shipment = Shipment.create("MS20261107000001", 1L, "MO20261107000001", 2L,
                CarrierType.SF, "SF123456789", lines(item(11L, 2), item(12L, 1)), NOW);

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
        assertThat(shipment.getCarrierName()).isEqualTo("顺丰速运");
        assertThat(shipment.getTotalQuantity()).isEqualTo(3);
        assertThat(shipment.getItemCount()).isEqualTo(2);
        assertThat(shipment.getShippedTime()).isEqualTo(NOW);
        assertThat(shipment.getItems()).allSatisfy(line -> assertThat(line.getOrderId()).isEqualTo(1L));
    }

    /** 承运商缺失直接拒绝 */
    @Test
    @DisplayName("缺少承运商拒绝建单")
    void createRejectsMissingCarrier() {
        assertThatThrownBy(() -> Shipment.create("MS1", 1L, "MO1", 2L, null, "SF1",
                lines(item(11L, 1)), NOW))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.SHIPMENT_CARRIER_INVALID));
    }

    /** 运单号为空：没有运单号的发货单等于没发 */
    @Test
    @DisplayName("运单号为空拒绝建单")
    void createRejectsBlankTrackingNo() {
        assertThatThrownBy(() -> Shipment.create("MS1", 1L, "MO1", 2L, CarrierType.SF, "  ",
                lines(item(11L, 1)), NOW))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.SHIPMENT_INVALID));
    }

    /** 明细为空 */
    @Test
    @DisplayName("发货明细为空拒绝建单")
    void createRejectsEmptyItems() {
        assertThatThrownBy(() -> Shipment.create("MS1", 1L, "MO1", 2L, CarrierType.SF, "SF1",
                new ArrayList<>(), NOW))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getMessage())
                        .contains("明细"));
    }

    /** 数量必须为正 */
    @Test
    @DisplayName("发货数量非正拒绝建单")
    void createRejectsNonPositiveQuantity() {
        ShipmentItem item = item(11L, 1);
        item.setQuantity(0);
        assertThatThrownBy(() -> Shipment.create("MS1", 1L, "MO1", 2L, CarrierType.SF, "SF1",
                lines(item), NOW))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getMessage())
                        .contains("大于 0"));
    }

    /** 同一订单行不能在一张单里拆成两行：那等于绕过可发数量校验 */
    @Test
    @DisplayName("同一订单行重复出现拒绝建单")
    void createRejectsDuplicateOrderItem() {
        assertThatThrownBy(() -> Shipment.create("MS1", 1L, "MO1", 2L, CarrierType.SF, "SF1",
                lines(item(11L, 1), item(11L, 2)), NOW))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getMessage())
                        .contains("两次"));
    }

    /** 签收幂等：第二次返回 false 且不改时间 */
    @Test
    @DisplayName("重复签收幂等且不重写时间")
    void markDeliveredIsIdempotent() {
        Shipment shipment = deliveredShipment();
        boolean first = shipment.markDelivered(NOW.plusHours(2));
        boolean second = shipment.markDelivered(NOW.plusHours(5));

        assertThat(first).isFalse();
        assertThat(second).isFalse();
        assertThat(shipment.getDeliveredTime()).isEqualTo(NOW.plusHours(2));
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
    }

    /** 已作废不能签收：包裹都作废了还签收，说明数据错乱 */
    @Test
    @DisplayName("已作废发货单不可签收")
    void markDeliveredRejectsCancelled() {
        Shipment shipment = Shipment.create("MS1", 1L, "MO1", 2L, CarrierType.SF, "SF1",
                lines(item(11L, 1)), NOW);
        shipment.cancel("填错单号", NOW.plusHours(1));

        assertThatThrownBy(() -> shipment.markDelivered(NOW.plusHours(2)))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.SHIPMENT_STATE_INVALID));
    }

    /** 已签收不能作废：货已在买家手里，该走售后 */
    @Test
    @DisplayName("已签收发货单不可作废")
    void cancelRejectsDelivered() {
        Shipment shipment = deliveredShipment();
        assertThatThrownBy(() -> shipment.cancel("想撤销", NOW.plusHours(3)))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.SHIPMENT_STATE_INVALID));
    }

    /** 作废后不再计入有效单 */
    @Test
    @DisplayName("作废后不再是有效单")
    void cancelMakesShipmentIneffective() {
        Shipment shipment = Shipment.create("MS1", 1L, "MO1", 2L, CarrierType.SF, "SF1",
                lines(item(11L, 1)), NOW);
        assertThat(shipment.effective()).isTrue();

        shipment.cancel(" 错发 ", NOW.plusHours(1));

        assertThat(shipment.effective()).isFalse();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        assertThat(shipment.getCancelReason()).isEqualTo("错发");
    }

    /** 判定辅助方法 */
    private Shipment deliveredShipment() {
        Shipment shipment = Shipment.create("MS1", 1L, "MO1", 2L, CarrierType.SF, "SF1",
                lines(item(11L, 1)), NOW);
        assertThat(shipment.markDelivered(NOW.plusHours(2))).isTrue();
        return shipment;
    }

    /** 构造一行发货明细 */
    private ShipmentItem item(Long orderItemId, int quantity) {
        ShipmentItem item = new ShipmentItem();
        item.setOrderItemId(orderItemId);
        item.setSpuId(100L);
        item.setSkuId(200L);
        item.setSpuName("测试商品");
        item.setSkuCode("SKU-200");
        item.setQuantity(quantity);
        return item;
    }

    /** 构造明细列表 */
    private List<ShipmentItem> lines(ShipmentItem... items) {
        List<ShipmentItem> list = new ArrayList<>();
        for (ShipmentItem item : items) {
            list.add(item);
        }
        return list;
    }
}
