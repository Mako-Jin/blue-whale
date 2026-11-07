package com.bluewhale.mall.shipment.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.catalog.domain.repository.SkuStockRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.common.sequence.MallSequenceService;
import com.bluewhale.mall.config.MallProperties;
import com.bluewhale.mall.order.domain.model.MallOrder;
import com.bluewhale.mall.order.domain.model.OrderItem;
import com.bluewhale.mall.order.domain.model.OrderLog;
import com.bluewhale.mall.order.domain.model.OrderStatus;
import com.bluewhale.mall.order.domain.repository.OrderRepository;
import com.bluewhale.mall.shipment.application.command.ShipmentCancelCommand;
import com.bluewhale.mall.shipment.application.command.ShipmentCreateCommand;
import com.bluewhale.mall.shipment.application.command.ShipmentDeliveredCommand;
import com.bluewhale.mall.shipment.application.command.ShipmentLineCommand;
import com.bluewhale.mall.shipment.application.service.ShipmentAppService;
import com.bluewhale.mall.shipment.application.view.ShipmentItemView;
import com.bluewhale.mall.shipment.application.view.ShipmentView;
import com.bluewhale.mall.shipment.domain.model.CarrierType;
import com.bluewhale.mall.shipment.domain.model.Shipment;
import com.bluewhale.mall.shipment.domain.model.ShipmentItem;
import com.bluewhale.mall.shipment.domain.model.ShipmentQuery;
import com.bluewhale.mall.shipment.domain.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 发货单应用服务实现。
 *
 * <p>这里是履约的唯一写入点：占用已发数量、扣预占库存、推进订单状态三件事
 * 必须在同一个事务里完成。分开做就会出现「库存扣了但没有发货单」这种
 * 谁也说不清的状态。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentAppServiceImpl implements ShipmentAppService {

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 「其它承运商」留空时的兜底名称 */
    private static final String FALLBACK_CARRIER_NAME = "其它";

    private final ShipmentRepository shipmentRepository;

    private final OrderRepository orderRepository;

    private final SkuStockRepository skuStockRepository;

    private final MallSequenceService sequenceService;

    private final MallProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShipmentView create(ShipmentCreateCommand command, MallActor actor) {
        actor.requireUserId();
        requireOperator(actor);
        MallOrder order = requireOrder(command.getOrderId());
        if (!order.getStatus().canShip()) {
            throw MallErrorCode.exception(MallErrorCode.SHIPMENT_ORDER_STATE_INVALID,
                    "订单当前状态（" + order.getStatus() + "）不允许发货");
        }
        CarrierType carrier = parseCarrier(command.getCarrierCode());
        String trackingNo = command.getTrackingNo() == null ? null : command.getTrackingNo().trim();
        if (shipmentRepository.existsTrackingNo(carrier.name(), trackingNo)) {
            throw MallErrorCode.exception(MallErrorCode.SHIPMENT_TRACKING_CONFLICT,
                    "运单号 " + trackingNo + " 已被其他发货单使用");
        }
        List<ShipmentItem> items = toItems(order, command.getItems());
        Shipment shipment = Shipment.create(sequenceService.nextShipmentNo(), order.getId(),
                order.getOrderNo(), order.getChannelId(), carrier, trackingNo, items, LocalDateTime.now());
        shipment.setCarrierName(resolveCarrierName(carrier, command.getCarrierName()));
        shipment.setRemark(command.getRemark() == null ? null : command.getRemark().trim());
        shipmentRepository.insert(shipment);
        occupy(order, shipment);
        String from = order.getStatus().name();
        order.ship(shipment.getShippedTime());
        orderRepository.update(order);
        appendLog(order, "SHIP", from, OrderStatus.SHIPPED.name(), actor,
                "发货单 " + shipment.getShipmentNo() + " · " + shipment.getCarrierName()
                        + " · " + shipment.getTrackingNo());
        return toView(shipment);
    }

    @Override
    public PageResult<ShipmentView> page(ShipmentQuery query, long current, long size) {
        long pageSize = size < 1 ? Math.min(DEFAULT_PAGE_SIZE, properties.getPageLimit())
                : Math.min(size, properties.getPageLimit());
        long pageNo = current < 1 ? 1L : current;
        ShipmentQuery condition = query == null ? new ShipmentQuery() : query;
        List<Shipment> shipments = shipmentRepository.page(
                condition, (pageNo - 1L) * pageSize, (int) pageSize);
        List<ShipmentView> views = new ArrayList<>(shipments.size());
        for (Shipment shipment : shipments) {
            views.add(toView(shipment));
        }
        return PageResult.of(views, shipmentRepository.count(condition), pageNo, pageSize);
    }

    @Override
    public ShipmentView detail(Long id, MallActor actor) {
        Shipment shipment = requireShipment(id);
        assertVisible(shipment, actor);
        return toView(shipment);
    }

    @Override
    public List<ShipmentView> listByOrderNo(String orderNo, MallActor actor) {
        actor.requireUserId();
        MallOrder order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.ORDER_NOT_FOUND,
                        "订单不存在：" + orderNo));
        if (!actor.isOperator() && !order.belongsTo(actor.getUserId())) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_FORBIDDEN, "无权查看他人订单的物流");
        }
        List<Shipment> shipments = shipmentRepository.listByOrderId(order.getId());
        List<ShipmentView> views = new ArrayList<>(shipments.size());
        for (Shipment shipment : shipments) {
            views.add(toView(shipment));
        }
        return views;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deliver(Long id, MallActor actor) {
        actor.requireUserId();
        requireOperator(actor);
        Shipment shipment = requireShipment(id);
        if (!shipment.markDelivered(LocalDateTime.now())) {
            return;
        }
        shipmentRepository.updateStatus(shipment);
        completeIfAllDelivered(shipment.getOrderId(), actor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markDelivered(ShipmentDeliveredCommand command) {
        Shipment shipment = shipmentRepository.findByNo(command.getShipmentNo())
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.SHIPMENT_NOT_FOUND,
                        "发货单不存在：" + command.getShipmentNo()));
        if (!shipment.markDelivered(command.getDeliveredTime())) {
            // 重复回调是常态：物流平台会重试、运营也可能手点两下，
            // 已签收直接返回成功，不重写签收时间
            return;
        }
        shipmentRepository.updateStatus(shipment);
        completeIfAllDelivered(shipment.getOrderId(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, ShipmentCancelCommand command, MallActor actor) {
        actor.requireUserId();
        requireOperator(actor);
        Shipment shipment = requireShipment(id);
        shipment.cancel(command == null ? null : command.getReason(), LocalDateTime.now());
        shipmentRepository.updateStatus(shipment);
        release(shipment);
        revertOrderIfNoEffectiveShipment(shipment, actor);
    }

    /**
     * 占用行级已发额度并扣预占库存。
     *
     * <p>先在内存里比一次可发数量是为了给出「哪一行超了」的提示；
     * 真正的并发防线是条件更新 {@code quantity - shipped_quantity >= ?}，
     * 影响 0 行即整笔回滚。</p>
     *
     * @param order 订单
     * @param shipment 发货单
     */
    private void occupy(MallOrder order, Shipment shipment) {
        for (ShipmentItem item : shipment.getItems()) {
            if (!orderRepository.occupyShippedQuantity(item.getOrderItemId(), item.quantityOrZero())) {
                throw MallErrorCode.exception(MallErrorCode.SHIPMENT_QUANTITY_EXCEEDED,
                        "商品「" + item.getSpuName() + "」可发数量不足，可能有其他发货单正在处理");
            }
            skuStockRepository.ship(item.getSkuId(), item.quantityOrZero());
        }
    }

    /**
     * 释放行级已发额度并回补预占库存（作废时）。
     *
     * @param shipment 发货单
     */
    private void release(Shipment shipment) {
        for (ShipmentItem item : shipment.getItems()) {
            orderRepository.releaseShippedQuantity(item.getOrderItemId(), item.quantityOrZero());
            skuStockRepository.unship(item.getSkuId(), item.quantityOrZero());
        }
    }

    /**
     * 全部发货单签收且全部发满时完成订单。
     *
     * <p>只签收一个包裹、还有一行没发就把订单置为已完成，剩下的货永远发不出去
     * （{@code COMPLETED} 不能再发货）。两个条件缺一不可。</p>
     *
     * @param orderId 订单 ID
     * @param actor 操作人（内网回调时为空）
     */
    private void completeIfAllDelivered(Long orderId, MallActor actor) {
        MallOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.SHIPPED) {
            return;
        }
        List<Shipment> shipments = shipmentRepository.listByOrderId(orderId);
        for (Shipment other : shipments) {
            if (other.effective() && !other.delivered()) {
                return;
            }
        }
        if (!order.fullyShipped()) {
            return;
        }
        String from = order.getStatus().name();
        order.complete(LocalDateTime.now());
        orderRepository.update(order);
        appendLog(order, "COMPLETE", from, OrderStatus.COMPLETED.name(), actor, "全部发货单已签收");
    }

    /**
     * 发货单全部作废后把订单退回待发货。
     *
     * @param shipment 被作废的发货单
     * @param actor 操作人
     */
    private void revertOrderIfNoEffectiveShipment(Shipment shipment, MallActor actor) {
        MallOrder order = orderRepository.findById(shipment.getOrderId())
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.SHIPPED) {
            appendLog(order, "SHIPMENT_CANCEL", order.getStatus().name(), order.getStatus().name(), actor,
                    "作废发货单 " + shipment.getShipmentNo());
            return;
        }
        for (Shipment other : shipmentRepository.listByOrderId(order.getId())) {
            if (other.effective()) {
                appendLog(order, "SHIPMENT_CANCEL", order.getStatus().name(), order.getStatus().name(), actor,
                        "作废发货单 " + shipment.getShipmentNo());
                return;
            }
        }
        order.unShip();
        orderRepository.update(order);
        appendLog(order, "UNSHIP", OrderStatus.SHIPPED.name(), OrderStatus.PAID.name(), actor,
                "发货单 " + shipment.getShipmentNo() + " 全部作废，订单退回待发货");
    }

    /**
     * 组装发货明细（从订单行取快照）。
     *
     * @param order 订单
     * @param lines 请求明细
     * @return 发货明细
     */
    private List<ShipmentItem> toItems(MallOrder order, List<ShipmentLineCommand> lines) {
        List<ShipmentItem> items = new ArrayList<>(lines.size());
        for (ShipmentLineCommand line : lines) {
            OrderItem orderItem = findItem(order, line.getOrderItemId());
            int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
            if (quantity > orderItem.shippableQuantity()) {
                throw MallErrorCode.exception(MallErrorCode.SHIPMENT_QUANTITY_EXCEEDED,
                        "商品「" + orderItem.getSpuName() + "」本次发货 " + quantity
                                + " 件，可发仅 " + orderItem.shippableQuantity() + " 件");
            }
            ShipmentItem item = new ShipmentItem();
            item.setOrderItemId(orderItem.getId());
            item.setSpuId(orderItem.getSpuId());
            item.setSkuId(orderItem.getSkuId());
            item.setSpuName(orderItem.getSpuName());
            item.setSkuCode(orderItem.getSkuCode());
            item.setSpecJson(orderItem.getSpecJson());
            item.setQuantity(quantity);
            items.add(item);
        }
        return items;
    }

    /**
     * 在订单里定位一行。
     *
     * @param order 订单
     * @param orderItemId 订单明细 ID
     * @return 订单行
     */
    private OrderItem findItem(MallOrder order, Long orderItemId) {
        for (OrderItem item : order.getItems()) {
            if (item.getId() != null && item.getId().equals(orderItemId)) {
                return item;
            }
        }
        throw MallErrorCode.exception(MallErrorCode.SHIPMENT_ITEM_NOT_FOUND,
                "发货明细行不属于该订单：" + orderItemId);
    }

    /**
     * 解析承运商编码。
     *
     * @param carrierCode 编码
     * @return 承运商
     */
    private CarrierType parseCarrier(String carrierCode) {
        if (!StringUtils.hasText(carrierCode)) {
            throw MallErrorCode.exception(MallErrorCode.SHIPMENT_CARRIER_INVALID, "请选择承运商");
        }
        try {
            return CarrierType.valueOf(carrierCode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw MallErrorCode.exception(MallErrorCode.SHIPMENT_CARRIER_INVALID,
                    "未知的承运商编码：" + carrierCode);
        }
    }

    /**
     * 承运商展示名称。
     *
     * @param carrier 承运商枚举
     * @param customName 请求里带的自定名称
     * @return 名称
     */
    private String resolveCarrierName(CarrierType carrier, String customName) {
        if (StringUtils.hasText(customName)) {
            String name = customName.trim();
            return name.length() > Shipment.CARRIER_NAME_MAX_LENGTH
                    ? name.substring(0, Shipment.CARRIER_NAME_MAX_LENGTH) : name;
        }
        return carrier == CarrierType.OTHER ? FALLBACK_CARRIER_NAME : carrier.label();
    }

    /**
     * 校验操作人是否为商城运营。
     *
     * @param actor 操作人
     */
    private void requireOperator(MallActor actor) {
        if (!actor.isOperator()) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_FORBIDDEN, "只有商城运营可以操作发货单");
        }
    }

    /**
     * 加载订单。
     *
     * @param orderId 订单 ID
     * @return 订单
     */
    private MallOrder requireOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.ORDER_NOT_FOUND,
                        "订单不存在：" + orderId));
    }

    /**
     * 加载发货单。
     *
     * @param id 主键
     * @return 发货单
     */
    private Shipment requireShipment(Long id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.SHIPMENT_NOT_FOUND,
                        "发货单不存在：" + id));
    }

    /**
     * 买家只能看自己订单的发货单，运营可看全部。
     *
     * @param shipment 发货单
     * @param actor 操作人
     */
    private void assertVisible(Shipment shipment, MallActor actor) {
        if (actor == null || actor.isOperator()) {
            return;
        }
        MallOrder order = requireOrder(shipment.getOrderId());
        if (!order.belongsTo(actor.getUserId())) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_FORBIDDEN, "无权查看他人订单的物流");
        }
    }

    /**
     * 追加订单状态流水。
     *
     * @param order 订单
     * @param action 动作
     * @param from 变更前状态
     * @param to 变更后状态
     * @param actor 操作人（内网回调时为空）
     * @param remark 说明
     */
    private void appendLog(MallOrder order, String action, String from, String to,
                           MallActor actor, String remark) {
        OrderLog entry = new OrderLog();
        entry.setAction(action);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        if (actor != null) {
            entry.setOperatorId(actor.getUserId());
            entry.setOperatorName(actor.getUsername());
        }
        entry.setRemark(remark);
        orderRepository.appendLog(order.getId(), entry);
    }

    /**
     * 发货单转视图。
     *
     * @param shipment 发货单
     * @return 视图
     */
    private ShipmentView toView(Shipment shipment) {
        ShipmentView view = new ShipmentView();
        view.setId(shipment.getId());
        view.setShipmentNo(shipment.getShipmentNo());
        view.setOrderId(shipment.getOrderId());
        view.setOrderNo(shipment.getOrderNo());
        view.setChannelId(shipment.getChannelId());
        view.setCarrierCode(shipment.getCarrierCode());
        view.setCarrierName(shipment.getCarrierName());
        view.setTrackingNo(shipment.getTrackingNo());
        view.setStatus(shipment.getStatus() == null ? null : shipment.getStatus().name());
        view.setItemCount(shipment.getItemCount());
        view.setTotalQuantity(shipment.getTotalQuantity());
        view.setShippedTime(shipment.getShippedTime());
        view.setDeliveredTime(shipment.getDeliveredTime());
        view.setCancelTime(shipment.getCancelTime());
        view.setCancelReason(shipment.getCancelReason());
        view.setRemark(shipment.getRemark());
        view.setCreateTime(shipment.getCreateTime());
        if (shipment.getItems() != null) {
            for (ShipmentItem item : shipment.getItems()) {
                view.getItems().add(toItemView(item));
            }
        }
        return view;
    }

    /**
     * 发货明细转视图。
     *
     * @param item 明细
     * @return 视图
     */
    private ShipmentItemView toItemView(ShipmentItem item) {
        ShipmentItemView view = new ShipmentItemView();
        view.setOrderItemId(item.getOrderItemId());
        view.setSpuId(item.getSpuId());
        view.setSkuId(item.getSkuId());
        view.setSpuName(item.getSpuName());
        view.setSkuCode(item.getSkuCode());
        view.setSpecJson(item.getSpecJson());
        view.setQuantity(item.getQuantity());
        return view;
    }
}
