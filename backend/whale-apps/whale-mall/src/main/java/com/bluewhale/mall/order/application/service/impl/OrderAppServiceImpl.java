package com.bluewhale.mall.order.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.aftersale.application.service.AfterSaleAppService;
import com.bluewhale.mall.cart.domain.model.CartItem;
import com.bluewhale.mall.cart.domain.repository.CartRepository;
import com.bluewhale.mall.catalog.application.model.SkuOffering;
import com.bluewhale.mall.catalog.application.service.SkuOfferingService;
import com.bluewhale.mall.catalog.domain.repository.SkuStockRepository;
import com.bluewhale.mall.channel.domain.model.MallChannel;
import com.bluewhale.mall.channel.domain.repository.ChannelRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.common.enums.CurrencyType;
import com.bluewhale.mall.common.sequence.MallSequenceService;
import com.bluewhale.mall.config.MallProperties;
import com.bluewhale.mall.order.application.command.OrderCreateCommand;
import com.bluewhale.mall.order.application.command.OrderPaidCommand;
import com.bluewhale.mall.order.application.command.OrderPreviewCommand;
import com.bluewhale.mall.order.application.command.OrderRefundedCommand;
import com.bluewhale.mall.order.application.service.OrderAppService;
import com.bluewhale.mall.order.application.view.OrderLineView;
import com.bluewhale.mall.order.application.view.OrderPreviewView;
import com.bluewhale.mall.order.application.view.OrderView;
import com.bluewhale.mall.order.domain.model.MallOrder;
import com.bluewhale.mall.order.domain.model.OrderItem;
import com.bluewhale.mall.order.domain.model.OrderLog;
import com.bluewhale.mall.order.domain.model.OrderStatus;
import com.bluewhale.mall.order.domain.repository.OrderRepository;
import com.bluewhale.mall.order.infrastructure.client.PayInternalClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 订单应用服务实现。
 *
 * <p>下单是一个事务里的五步，顺序不能换：<b>解析价格 → 校验可售 → 预占库存 → 写订单 → 清购物车</b>。
 * 预占放在写订单之前，是因为「单已建、库存没了」这种半成品必须回滚掉，
 * 而不是留在库里等人工处理；而预占失败时事务回滚会把前面几条已预占的库存一起还原。</p>
 *
 * <p>幂等键落唯一索引（不落 Redis）：Redis 过期后重复提交照样建单，唯一索引永远有效。
 * 命中已有订单时返回该订单而不是报 409——用户双击「提交订单」不该看到一个错误。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class OrderAppServiceImpl implements OrderAppService {

    /** 支付回调的操作人名称 */
    private static final String SYSTEM_OPERATOR = "SYSTEM";

    private final OrderRepository orderRepository;

    private final CartRepository cartRepository;

    private final ChannelRepository channelRepository;

    private final SkuOfferingService skuOfferingService;

    private final SkuStockRepository skuStockRepository;

    private final MallSequenceService sequenceService;

    /** whale-pay 内网客户端（服务间直连） */
    private final PayInternalClient payInternalClient;

    /** 售后应用服务（退款唯一入口） */
    private final AfterSaleAppService afterSaleAppService;

    private final MallProperties properties;

    @Override
    public OrderPreviewView preview(OrderPreviewCommand command, MallActor actor) {
        Long userId = actor.requireUserId();
        MallChannel channel = requireChannel(command.getChannelId());
        List<CartItem> items = loadSelectedItems(channel.getId(), command.getItemIds(), userId);
        assertLineLimit(items.size());
        Map<Long, SkuOffering> offerings = skuOfferingService.resolve(skuIds(items), channel.getMode());
        OrderPreviewView view = new OrderPreviewView();
        view.setChannelId(channel.getId());
        view.setMode(channel.getMode() == null ? null : channel.getMode().name());
        view.setSettlementType(channel.getSettlementType() == null ? null : channel.getSettlementType().name());
        view.setCurrencyType(channel.getCurrencyType() == null
                ? CurrencyType.CASH.name() : channel.getCurrencyType().name());
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int quantity = 0;
        for (CartItem item : items) {
            SkuOffering offering = offerings.get(item.getSkuId());
            BigDecimal unitPrice = skuOfferingService.requireSellable(offering, item.getQuantity());
            OrderLineView line = toLine(offering, unitPrice, item.getQuantity());
            view.getLines().add(line);
            total = total.add(line.getAmount());
            quantity += item.getQuantity();
        }
        view.setTotalAmount(total);
        view.setTotalQuantity(quantity);
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderView create(OrderCreateCommand command, String idempotencyKey, MallActor actor) {
        Long userId = actor.requireUserId();
        String key = normalizeIdempotencyKey(idempotencyKey);
        Optional<MallOrder> existing = orderRepository.findByIdempotency(userId, key);
        if (existing.isPresent()) {
            return toView(existing.get());
        }
        MallChannel channel = requireChannel(command.getChannelId());
        if (!channel.isActive()) {
            throw MallErrorCode.exception(MallErrorCode.CHANNEL_STATE_CONFLICT, "该商城渠道已停业");
        }
        List<CartItem> items = loadSelectedItems(channel.getId(), command.getItemIds(), userId);
        assertLineLimit(items.size());
        MallOrder order = newOrder(channel, userId, key, command.getRemark());
        fillItems(order, items, channel);
        order.recalculate();
        reserveStock(order);
        order.setOrderNo(sequenceService.nextOrderNo());
        orderRepository.insert(order);
        cartRepository.removeByIds(items.stream().map(CartItem::getId).toList());
        orderRepository.appendLog(order.getId(), log("CREATE", null, order.getStatus().name(), actor, "创建订单"));
        return toView(order);
    }

    @Override
    public PageResult<OrderView> pageMine(String status, Long current, Long size, MallActor actor) {
        Long userId = actor.requireUserId();
        return page(userId, status, null, current, size);
    }

    @Override
    public PageResult<OrderView> pageAll(String status, String keyword, Long current, Long size, MallActor actor) {
        actor.requireUserId();
        return page(null, status, keyword, current, size);
    }

    @Override
    public OrderView detail(Long id, MallActor actor) {
        MallOrder order = requireOrder(id);
        assertVisible(order, actor);
        return toView(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String reason, MallActor actor) {
        MallOrder order = requireOrder(id);
        assertVisible(order, actor);
        String from = statusName(order);
        order.cancel(reason, LocalDateTime.now());
        releaseStock(order);
        orderRepository.update(order);
        orderRepository.appendLog(order.getId(), log("CANCEL", from, statusName(order), actor,
                StringUtils.hasText(reason) ? reason : "买家取消"));
        // 已创建过支付单则联动取消；支付单不存在（未发起支付）或已取消均不阻塞订单取消
        if (StringUtils.hasText(order.getPayNo())) {
            payInternalClient.cancel(order.getPayNo());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String pay(Long id, MallActor actor) {
        MallOrder order = requireOrder(id);
        if (!order.belongsTo(actor.requireUserId())) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_FORBIDDEN, "无权支付他人订单");
        }
        if (!order.getStatus().canPay()) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_STATE_CONFLICT,
                    "订单当前状态（" + order.getStatus() + "）不允许支付");
        }
        String payNo = order.getPayNo();
        if (!StringUtils.hasText(payNo)) {
            // 幂等创建：同单号重复创建 whale-pay 直接返回原单
            payNo = payInternalClient.createPayOrder(order.getOrderNo(), order.getTotalAmount(),
                    order.getCurrencyType() == null ? "CNY" : order.getCurrencyType().name(),
                    properties.getPayNotifyUrl());
            order.setPayNo(payNo);
            orderRepository.update(order);
        }
        payInternalClient.pay(payNo);
        return payNo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(Long id, String reason, MallActor actor) {
        // 整单退款不再是独立路径：生成覆盖全部可退行的售后单并直接通过审核，
        // 行额度占用、退款幂等键、回调结单与买家申请走同一段代码
        afterSaleAppService.applyFullRefund(requireOrder(id), reason, actor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markPaid(OrderPaidCommand command) {
        MallOrder order = orderRepository.findByOrderNo(command.getOrderNo())
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            // 支付方会重试；已支付/发货/完成/退款中/已退款均幂等返回成功，
            // 重复置位反而会覆盖支付时间或把退款单打回已支付
            return;
        }
        assertPaidAmount(order, command.getPaidAmount());
        String from = statusName(order);
        order.pay(command.getPayNo(), LocalDateTime.now());
        orderRepository.update(order);
        orderRepository.appendLog(order.getId(), systemLog("PAY", from, statusName(order), "支付回调"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRefunded(OrderRefundedCommand command) {
        // 结单与订单退款额累计都在售后侧：回调载荷里的 refundNo 就是售后单号，
        // 找不到售后单时按 M1 历史退款单兼容处理，不抛错
        afterSaleAppService.markRefunded(command.getOrderNo(), command.getRefundNo(), command.getRefundAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long id, MallActor actor) {
        actor.requireUserId();
        if (!actor.isOperator()) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_FORBIDDEN, "只有商城运营可以发货");
        }
        MallOrder order = requireOrder(id);
        String from = statusName(order);
        order.ship(LocalDateTime.now());
        for (OrderItem item : order.getItems()) {
            skuStockRepository.ship(item.getSkuId(), item.getQuantity());
        }
        orderRepository.update(order);
        orderRepository.appendLog(order.getId(), log("SHIP", from, statusName(order), actor, "已发货"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, MallActor actor) {
        MallOrder order = requireOrder(id);
        assertVisible(order, actor);
        String from = statusName(order);
        order.complete(LocalDateTime.now());
        orderRepository.update(order);
        orderRepository.appendLog(order.getId(), log("COMPLETE", from, statusName(order), actor, "订单完成"));
    }

    /**
     * 构造订单头（不含明细）。
     *
     * @param channel 渠道
     * @param userId 买家用户 ID
     * @param idempotencyKey 幂等键
     * @param remark 备注
     * @return 订单
     */
    private MallOrder newOrder(MallChannel channel, Long userId, String idempotencyKey, String remark) {
        MallOrder order = new MallOrder();
        order.setChannelId(channel.getId());
        order.setChannelCode(channel.getChannelCode());
        order.setMode(channel.getMode());
        order.setSettlementType(channel.getSettlementType());
        order.setCurrencyType(channel.getCurrencyType());
        order.setBuyerUserId(userId);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setIdempotencyKey(idempotencyKey);
        order.setRemark(remark);
        if (properties.getOrderExpireMinutes() > 0) {
            order.setExpireTime(LocalDateTime.now().plusMinutes(properties.getOrderExpireMinutes()));
        }
        return order;
    }

    /**
     * 按购物车条目填充订单明细（价格实时解析并落快照）。
     *
     * @param order 订单
     * @param items 购物车条目
     * @param channel 渠道
     */
    private void fillItems(MallOrder order, List<CartItem> items, MallChannel channel) {
        Map<Long, SkuOffering> offerings = skuOfferingService.resolve(skuIds(items), channel.getMode());
        for (CartItem item : items) {
            SkuOffering offering = offerings.get(item.getSkuId());
            BigDecimal unitPrice = skuOfferingService.requireSellable(offering, item.getQuantity());
            order.getItems().add(toOrderItem(offering, unitPrice, item.getQuantity()));
        }
    }

    /**
     * 预占库存（任一失败即整单回滚）。
     *
     * @param order 订单
     */
    private void reserveStock(MallOrder order) {
        for (OrderItem item : order.getItems()) {
            if (!skuStockRepository.reserve(item.getSkuId(), item.getQuantity())) {
                throw MallErrorCode.exception(MallErrorCode.STOCK_INSUFFICIENT,
                        "SKU " + item.getSkuCode() + " 库存不足，请调整数量后重试");
            }
        }
    }

    /**
     * 释放库存预占（取消订单）。
     *
     * @param order 订单
     */
    private void releaseStock(MallOrder order) {
        for (OrderItem item : order.getItems()) {
            skuStockRepository.release(item.getSkuId(), item.getQuantity());
        }
    }

    /**
     * 校验支付金额与订单金额一致。
     *
     * @param order 订单
     * @param paidAmount 实付金额（可为空）
     */
    private void assertPaidAmount(MallOrder order, BigDecimal paidAmount) {
        if (paidAmount == null) {
            return;
        }
        if (paidAmount.compareTo(order.getTotalAmount()) != 0) {
            throw MallErrorCode.exception(MallErrorCode.PAY_CALLBACK_INVALID,
                    "支付金额与订单金额不一致（应付 " + order.getTotalAmount() + "，实付 " + paidAmount + "）");
        }
    }

    /**
     * 加载本次要结算的购物车条目。
     *
     * @param channelId 渠道 ID
     * @param itemIds 条目 ID（空表示已选中全部）
     * @param userId 用户 ID
     * @return 条目列表
     */
    private List<CartItem> loadSelectedItems(Long channelId, List<Long> itemIds, Long userId) {
        List<CartItem> items = itemIds == null || itemIds.isEmpty()
                ? cartRepository.list(channelId, userId)
                : cartRepository.findByIds(itemIds);
        List<CartItem> result = new ArrayList<>();
        for (CartItem item : items) {
            if (!userId.equals(item.getUserId()) || !channelId.equals(item.getChannelId())) {
                throw MallErrorCode.exception(MallErrorCode.CART_ITEM_NOT_FOUND);
            }
            if (itemIds == null || itemIds.isEmpty()) {
                if (Boolean.TRUE.equals(item.getSelected())) {
                    result.add(item);
                }
            } else {
                result.add(item);
            }
        }
        if (result.isEmpty()) {
            throw MallErrorCode.exception(MallErrorCode.CART_EMPTY);
        }
        return result;
    }

    /**
     * 分页查询。
     *
     * @param buyerUserId 买家用户 ID（null 不限）
     * @param status 状态
     * @param keyword 订单号关键字
     * @param current 页码
     * @param size 每页条数
     * @return 分页结果
     */
    private PageResult<OrderView> page(Long buyerUserId, String status, String keyword, Long current, Long size) {
        long pageSize = size == null || size < 1
                ? Math.min(20, properties.getPageLimit()) : Math.min(size, properties.getPageLimit());
        long pageNo = current == null || current < 1 ? 1L : current;
        List<MallOrder> orders = orderRepository.page(buyerUserId, status, keyword,
                (pageNo - 1L) * pageSize, (int) pageSize);
        List<OrderView> views = new ArrayList<>(orders.size());
        for (MallOrder order : orders) {
            views.add(toView(order));
        }
        return PageResult.of(views, orderRepository.count(buyerUserId, status, keyword), pageNo, pageSize);
    }

    /**
     * 校验下单行数上限。
     *
     * @param lines 行数
     */
    private void assertLineLimit(int lines) {
        if (lines > properties.getOrderMaxLines()) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_LINES_EXCEEDED,
                    "单笔订单最多 " + properties.getOrderMaxLines() + " 种商品");
        }
    }

    /**
     * 归一幂等键。
     *
     * @param idempotencyKey 原始幂等键
     * @return 可用幂等键
     */
    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            if (properties.isIdempotencyRequired()) {
                throw MallErrorCode.exception(MallErrorCode.ORDER_IDEMPOTENCY_REQUIRED);
            }
            return UUID.randomUUID().toString();
        }
        String key = idempotencyKey.trim();
        if (key.length() > MallOrder.IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_IDEMPOTENCY_REQUIRED, "幂等键过长");
        }
        return key;
    }

    /**
     * 查询订单，不存在即失败。
     *
     * @param id 订单 ID
     * @return 订单
     */
    private MallOrder requireOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.ORDER_NOT_FOUND));
    }

    /**
     * 查询渠道，不存在即失败。
     *
     * @param channelId 渠道 ID
     * @return 渠道
     */
    private MallChannel requireChannel(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.CHANNEL_NOT_FOUND));
    }

    /**
     * 校验订单可见性（本人或运营）。
     *
     * @param order 订单
     * @param actor 操作人
     */
    private void assertVisible(MallOrder order, MallActor actor) {
        if (!order.belongsTo(actor.getUserId()) && !actor.isOperator()) {
            throw MallErrorCode.exception(MallErrorCode.ORDER_FORBIDDEN);
        }
    }

    /**
     * 提取 SKU ID。
     *
     * @param items 购物车条目
     * @return SKU ID 列表
     */
    private List<Long> skuIds(List<CartItem> items) {
        return items.stream().map(CartItem::getSkuId).toList();
    }

    /**
     * 状态名（空安全）。
     *
     * @param order 订单
     * @return 状态名
     */
    private String statusName(MallOrder order) {
        return order.getStatus() == null ? null : order.getStatus().name();
    }

    /**
     * 构造操作人流水。
     *
     * @param action 动作
     * @param from 原状态
     * @param to 新状态
     * @param actor 操作人
     * @param remark 说明
     * @return 流水
     */
    private OrderLog log(String action, String from, String to, MallActor actor, String remark) {
        OrderLog log = new OrderLog();
        log.setAction(action);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorId(actor.getUserId());
        log.setOperatorName(actor.getUsername());
        log.setRemark(remark);
        return log;
    }

    /**
     * 构造系统流水。
     *
     * @param action 动作
     * @param from 原状态
     * @param to 新状态
     * @param remark 说明
     * @return 流水
     */
    private OrderLog systemLog(String action, String from, String to, String remark) {
        OrderLog log = new OrderLog();
        log.setAction(action);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorName(SYSTEM_OPERATOR);
        log.setRemark(remark);
        return log;
    }

    /**
     * 可售单元转订单明细。
     *
     * @param offering 可售单元
     * @param unitPrice 成交单价
     * @param quantity 数量
     * @return 明细
     */
    private OrderItem toOrderItem(SkuOffering offering, BigDecimal unitPrice, Integer quantity) {
        OrderItem item = new OrderItem();
        item.setSpuId(offering.getSpuId());
        item.setSkuId(offering.getSku().getId());
        item.setSpuName(offering.getSpuName());
        item.setSkuCode(offering.getSku().getSkuCode());
        item.setSpecJson(offering.getSku().getSpecJson());
        item.setImageUrl(offering.getSku().getImageUrl() == null
                ? offering.getMainImage() : offering.getSku().getImageUrl());
        item.setPriceType(offering.getPriceType());
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        return item;
    }

    /**
     * 可售单元转订单行视图。
     *
     * @param offering 可售单元
     * @param unitPrice 成交单价
     * @param quantity 数量
     * @return 行视图
     */
    private OrderLineView toLine(SkuOffering offering, BigDecimal unitPrice, Integer quantity) {
        OrderLineView line = new OrderLineView();
        line.setSkuId(offering.getSku().getId());
        line.setSpuId(offering.getSpuId());
        line.setSpuName(offering.getSpuName());
        line.setSkuCode(offering.getSku().getSkuCode());
        line.setSpecJson(offering.getSku().getSpecJson());
        line.setImageUrl(offering.getSku().getImageUrl() == null
                ? offering.getMainImage() : offering.getSku().getImageUrl());
        line.setPriceType(offering.getPriceType());
        line.setUnitPrice(unitPrice);
        line.setQuantity(quantity);
        line.setAmount(unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP));
        return line;
    }

    /**
     * 订单转视图。
     *
     * @param order 订单
     * @return 视图
     */
    private OrderView toView(MallOrder order) {
        OrderView view = new OrderView();
        view.setId(order.getId());
        view.setOrderNo(order.getOrderNo());
        view.setChannelId(order.getChannelId());
        view.setChannelCode(order.getChannelCode());
        view.setMode(order.getMode() == null ? null : order.getMode().name());
        view.setSettlementType(order.getSettlementType() == null ? null : order.getSettlementType().name());
        view.setCurrencyType(order.getCurrencyType() == null ? null : order.getCurrencyType().name());
        view.setBuyerUserId(order.getBuyerUserId());
        view.setBuyerName(order.getBuyerName());
        view.setStatus(statusName(order));
        view.setTotalAmount(order.getTotalAmount());
        view.setTotalQuantity(order.getTotalQuantity());
        view.setItemCount(order.getItemCount());
        view.setPayNo(order.getPayNo());
        view.setPayTime(order.getPayTime());
        view.setRefundAmount(order.getRefundAmount());
        view.setRefundTime(order.getRefundTime());
        view.setShipTime(order.getShipTime());
        view.setCompleteTime(order.getCompleteTime());
        view.setCancelTime(order.getCancelTime());
        view.setCancelReason(order.getCancelReason());
        view.setRemark(order.getRemark());
        view.setCreateTime(order.getCreateTime());
        for (OrderItem item : order.getItems()) {
            OrderLineView line = new OrderLineView();
            line.setId(item.getId());
            line.setSkuId(item.getSkuId());
            line.setSpuId(item.getSpuId());
            line.setSpuName(item.getSpuName());
            line.setSkuCode(item.getSkuCode());
            line.setSpecJson(item.getSpecJson());
            line.setImageUrl(item.getImageUrl());
            line.setPriceType(item.getPriceType());
            line.setUnitPrice(item.getUnitPrice());
            line.setQuantity(item.getQuantity());
            line.setRefundedQuantity(item.getRefundedQuantity());
            line.setRefundableQuantity(item.refundableQuantity());
            line.setAmount(item.getAmount());
            view.getItems().add(line);
        }
        return view;
    }
}
