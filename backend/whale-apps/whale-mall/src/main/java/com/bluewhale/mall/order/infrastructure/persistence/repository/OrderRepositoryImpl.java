package com.bluewhale.mall.order.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.mall.common.enums.CurrencyType;
import com.bluewhale.mall.common.enums.MallMode;
import com.bluewhale.mall.common.enums.SettlementType;
import com.bluewhale.mall.order.domain.model.DailyReportStat;
import com.bluewhale.mall.order.domain.model.MallOrder;
import com.bluewhale.mall.order.domain.model.OrderCoupon;
import com.bluewhale.mall.order.domain.model.OrderItem;
import com.bluewhale.mall.order.domain.model.OrderLog;
import com.bluewhale.mall.order.domain.model.OrderStatus;
import com.bluewhale.mall.order.domain.repository.OrderRepository;
import com.bluewhale.mall.order.infrastructure.persistence.entity.OrderCouponEntity;
import com.bluewhale.mall.order.infrastructure.persistence.entity.OrderEntity;
import com.bluewhale.mall.order.infrastructure.persistence.entity.OrderItemEntity;
import com.bluewhale.mall.order.infrastructure.persistence.entity.OrderLogEntity;
import com.bluewhale.mall.order.infrastructure.persistence.mapper.OrderCouponMapper;
import com.bluewhale.mall.order.infrastructure.persistence.mapper.OrderItemMapper;
import com.bluewhale.mall.order.infrastructure.persistence.mapper.OrderLogMapper;
import com.bluewhale.mall.order.infrastructure.persistence.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 订单仓储实现（MyBatis-Plus）。
 *
 * <p>列表查询会一次性把明细捞回来：订单列表页每一行都要显示「首个商品名 + 共 N 件」，
 * 逐单调详情就是典型 N+1。这里用一次 {@code IN} 查询批量补齐。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    private final OrderLogMapper orderLogMapper;

    private final OrderCouponMapper orderCouponMapper;

    @Override
    public Long insert(MallOrder order) {
        OrderEntity entity = toEntity(order);
        orderMapper.insert(entity);
        order.setId(entity.getId());
        for (OrderItem item : order.getItems()) {
            OrderItemEntity itemEntity = toItemEntity(item, entity.getId());
            orderItemMapper.insert(itemEntity);
            item.setId(itemEntity.getId());
            item.setOrderId(entity.getId());
        }
        for (OrderCoupon coupon : order.getCoupons()) {
            // rule 是计价期瞬态字段，优惠结果已固化进 discountAmount，不落库
            coupon.setOrderId(entity.getId());
            OrderCouponEntity couponEntity = toCouponEntity(coupon, order.getOrderNo());
            orderCouponMapper.insert(couponEntity);
            coupon.setId(couponEntity.getId());
        }
        return entity.getId();
    }

    @Override
    public void update(MallOrder order) {
        OrderEntity entity = toEntity(order);
        orderMapper.updateById(entity);
    }

    @Override
    public void appendLog(Long orderId, OrderLog log) {
        OrderLogEntity entity = new OrderLogEntity();
        entity.setOrderId(orderId);
        entity.setFromStatus(log.getFromStatus());
        entity.setToStatus(log.getToStatus());
        entity.setAction(log.getAction());
        entity.setOperatorId(log.getOperatorId());
        entity.setOperatorName(log.getOperatorName());
        entity.setRemark(log.getRemark());
        orderLogMapper.insert(entity);
    }

    @Override
    public Optional<MallOrder> findById(Long id) {
        OrderEntity entity = orderMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        MallOrder order = toDomain(entity);
        order.setItems(loadItems(List.of(entity.getId())).getOrDefault(entity.getId(), new ArrayList<>()));
        order.setCoupons(loadCoupons(List.of(entity.getId())).getOrDefault(entity.getId(), new ArrayList<>()));
        return Optional.of(order);
    }

    @Override
    public Optional<MallOrder> findByOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return Optional.empty();
        }
        OrderEntity entity = orderMapper.selectOne(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getOrderNo, orderNo.trim()));
        if (entity == null) {
            return Optional.empty();
        }
        MallOrder order = toDomain(entity);
        order.setItems(loadItems(List.of(entity.getId())).getOrDefault(entity.getId(), new ArrayList<>()));
        order.setCoupons(loadCoupons(List.of(entity.getId())).getOrDefault(entity.getId(), new ArrayList<>()));
        return Optional.of(order);
    }

    @Override
    public Optional<MallOrder> findByIdempotency(Long buyerUserId, String idempotencyKey) {
        if (buyerUserId == null || !StringUtils.hasText(idempotencyKey)) {
            return Optional.empty();
        }
        OrderEntity entity = orderMapper.selectOne(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getBuyerUserId, buyerUserId)
                .eq(OrderEntity::getIdempotencyKey, idempotencyKey.trim()));
        if (entity == null) {
            return Optional.empty();
        }
        MallOrder order = toDomain(entity);
        order.setItems(loadItems(List.of(entity.getId())).getOrDefault(entity.getId(), new ArrayList<>()));
        order.setCoupons(loadCoupons(List.of(entity.getId())).getOrDefault(entity.getId(), new ArrayList<>()));
        return Optional.of(order);
    }

    @Override
    public List<MallOrder> page(Long buyerUserId, String status, String keyword, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<OrderEntity> page = orderMapper.selectPage(new Page<>(current, Math.max(size, 1)),
                filter(buyerUserId, status, keyword));
        List<OrderEntity> records = page.getRecords();
        if (records.isEmpty()) {
            return List.of();
        }
        List<Long> ids = records.stream().map(OrderEntity::getId).toList();
        Map<Long, List<OrderItem>> items = loadItems(ids);
        Map<Long, List<OrderCoupon>> coupons = loadCoupons(ids);
        List<MallOrder> orders = new ArrayList<>(records.size());
        for (OrderEntity entity : records) {
            MallOrder order = toDomain(entity);
            order.setItems(items.getOrDefault(entity.getId(), new ArrayList<>()));
            order.setCoupons(coupons.getOrDefault(entity.getId(), new ArrayList<>()));
            orders.add(order);
        }
        return orders;
    }

    @Override
    public long count(Long buyerUserId, String status, String keyword) {
        return orderMapper.selectCount(filter(buyerUserId, status, keyword));
    }

    @Override
    public List<OrderLog> listLogs(Long orderId) {
        return orderLogMapper.selectList(new LambdaQueryWrapper<OrderLogEntity>()
                        .eq(OrderLogEntity::getOrderId, orderId)
                        .orderByAsc(OrderLogEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 批量加载明细并按订单分组。
     *
     * @param orderIds 订单 ID 列表
     * @return 订单 ID → 明细列表
     */
    private Map<Long, List<OrderItem>> loadItems(List<Long> orderIds) {
        Map<Long, List<OrderItem>> result = new HashMap<>();
        if (orderIds == null || orderIds.isEmpty()) {
            return result;
        }
        List<OrderItemEntity> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItemEntity>()
                .in(OrderItemEntity::getOrderId, new LinkedHashSet<>(orderIds))
                .orderByAsc(OrderItemEntity::getId));
        for (OrderItemEntity item : items) {
            result.computeIfAbsent(item.getOrderId(), key -> new ArrayList<>()).add(toDomain(item));
        }
        return result;
    }

    /**
     * 批量加载用券明细并按订单分组。
     *
     * @param orderIds 订单 ID 列表
     * @return 订单 ID → 用券明细列表
     */
    private Map<Long, List<OrderCoupon>> loadCoupons(List<Long> orderIds) {
        Map<Long, List<OrderCoupon>> result = new HashMap<>();
        if (orderIds == null || orderIds.isEmpty()) {
            return result;
        }
        List<OrderCouponEntity> coupons = orderCouponMapper.selectList(new LambdaQueryWrapper<OrderCouponEntity>()
                .in(OrderCouponEntity::getOrderId, new LinkedHashSet<>(orderIds))
                .orderByAsc(OrderCouponEntity::getId));
        for (OrderCouponEntity coupon : coupons) {
            result.computeIfAbsent(coupon.getOrderId(), key -> new ArrayList<>()).add(toDomain(coupon));
        }
        return result;
    }

    @Override
    public List<DailyReportStat> dailyReport(LocalDate from, LocalDate to) {
        // to 为空视为只查 from 当天；from 晚于 to 的乱序区间直接返回空
        LocalDate effectiveFrom = from == null ? LocalDate.now().minusDays(6) : from;
        LocalDate effectiveTo = to == null ? effectiveFrom : to;
        if (effectiveFrom.isAfter(effectiveTo)) {
            return List.of();
        }
        LocalDateTime start = effectiveFrom.atStartOfDay();
        LocalDateTime end = effectiveTo.plusDays(1).atStartOfDay();
        List<Map<String, Object>> rows = orderMapper.dailyReport(start, end);
        List<DailyReportStat> stats = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            stats.add(toStat(row));
        }
        return stats;
    }

    /**
     * 聚合行转报表统计模型。
     *
     * <p>列名取自 SQL 小写别名，但也兼容驼峰键（数据源方言或下游包装
     * 可能改写键名）；数值列做 2 位小数规整，日期列兼容 java.sql.Date 与
     * LocalDateTime 两种驱动返回。</p>
     *
     * @param row 聚合行
     * @return 报表统计模型
     */
    private DailyReportStat toStat(Map<String, Object> row) {
        DailyReportStat stat = new DailyReportStat();
        Object date = firstNonNull(row, "stat_date", "statDate");
        if (date instanceof LocalDate localDate) {
            stat.setStatDate(localDate);
        } else if (date instanceof LocalDateTime localDateTime) {
            stat.setStatDate(localDateTime.toLocalDate());
        } else if (date instanceof java.sql.Date sqlDate) {
            stat.setStatDate(sqlDate.toLocalDate());
        }
        stat.setOrderCount(row.get("order_count") instanceof Number number ? number.longValue()
                : (row.get("orderCount") instanceof Number camel ? camel.longValue() : 0L));
        stat.setGoodsAmount(decimalOf(row, "goods_amount", "goodsAmount"));
        stat.setFreightAmount(decimalOf(row, "freight_amount", "freightAmount"));
        stat.setCouponDiscount(decimalOf(row, "coupon_discount", "couponDiscount"));
        stat.setPaidAmount(decimalOf(row, "paid_amount", "paidAmount"));
        stat.setRefundAmount(decimalOf(row, "refund_amount", "refundAmount"));
        return stat;
    }

    /**
     * 从聚合行取第一个非空键值（兼容下划线与驼峰列名）。
     *
     * @param row  聚合行
     * @param keys 候选键
     * @return 第一个非空值
     */
    private Object firstNonNull(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key) && row.get(key) != null) {
                return row.get(key);
            }
        }
        return null;
    }

    /**
     * 从聚合行取数值列并规整为 2 位小数。
     *
     * @param row  聚合行
     * @param keys 候选键
     * @return 金额（空行按 0）
     */
    private BigDecimal decimalOf(Map<String, Object> row, String... keys) {
        Object value = firstNonNull(row, keys);
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public boolean occupyShippedQuantity(Long orderItemId, int quantity) {
        if (orderItemId == null || quantity <= 0) {
            return false;
        }
        return orderItemMapper.occupyShippedQty(orderItemId, quantity) > 0;
    }

    @Override
    public boolean releaseShippedQuantity(Long orderItemId, int quantity) {
        if (orderItemId == null || quantity <= 0) {
            return false;
        }
        return orderItemMapper.releaseShippedQty(orderItemId, quantity) > 0;
    }

    /**
     * 组装查询条件。
     *
     * @param buyerUserId 买家用户 ID（null 不限）
     * @param status 状态
     * @param keyword 订单号关键字
     * @return 查询包装器
     */
    private LambdaQueryWrapper<OrderEntity> filter(Long buyerUserId, String status, String keyword) {
        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
        if (buyerUserId != null) {
            wrapper.eq(OrderEntity::getBuyerUserId, buyerUserId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(OrderEntity::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(OrderEntity::getOrderNo, keyword.trim().toUpperCase(Locale.ROOT));
        }
        return wrapper.orderByDesc(OrderEntity::getId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private MallOrder toDomain(OrderEntity entity) {
        MallOrder order = new MallOrder();
        order.setId(entity.getId());
        order.setOrderNo(entity.getOrderNo());
        order.setChannelId(entity.getChannelId());
        order.setChannelCode(entity.getChannelCode());
        order.setMode(MallMode.parse(entity.getMode()));
        order.setSettlementType(SettlementType.parse(entity.getSettlementType()));
        order.setCurrencyType(CurrencyType.parse(entity.getCurrencyType()));
        order.setBuyerUserId(entity.getBuyerUserId());
        order.setBuyerName(entity.getBuyerName());
        order.setStatus(OrderStatus.parse(entity.getStatus()));
        order.setTotalAmount(entity.getTotalAmount());
        order.setGoodsAmount(entity.getGoodsAmount());
        order.setDiscountAmount(entity.getDiscountAmount());
        order.setFreightAmount(entity.getFreightAmount());
        order.setCouponUserId(entity.getCouponUserId());
        order.setCouponCode(entity.getCouponCode());
        order.setTotalQuantity(entity.getTotalQuantity());
        order.setItemCount(entity.getItemCount());
        order.setIdempotencyKey(entity.getIdempotencyKey());
        order.setPayNo(entity.getPayNo());
        order.setPayTime(entity.getPayTime());
        order.setRefundAmount(entity.getRefundAmount());
        order.setRefundTime(entity.getRefundTime());
        order.setShipTime(entity.getShipTime());
        order.setCompleteTime(entity.getCompleteTime());
        order.setCancelTime(entity.getCancelTime());
        order.setCancelReason(entity.getCancelReason());
        order.setExpireTime(entity.getExpireTime());
        order.setRemark(entity.getRemark());
        order.setCreateTime(entity.getCreateTime());
        return order;
    }

    /**
     * 明细实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private OrderItem toDomain(OrderItemEntity entity) {
        OrderItem item = new OrderItem();
        item.setId(entity.getId());
        item.setOrderId(entity.getOrderId());
        item.setSpuId(entity.getSpuId());
        item.setSkuId(entity.getSkuId());
        item.setCategoryId(entity.getCategoryId());
        item.setSpuName(entity.getSpuName());
        item.setSkuCode(entity.getSkuCode());
        item.setSpecJson(entity.getSpecJson());
        item.setImageUrl(entity.getImageUrl());
        item.setPriceType(entity.getPriceType());
        item.setUnitPrice(entity.getUnitPrice());
        item.setQuantity(entity.getQuantity());
        item.setShippedQuantity(entity.getShippedQuantity());
        item.setRefundedQuantity(entity.getRefundedQuantity());
        item.setRefundedAmount(entity.getRefundedAmount());
        item.setAmount(entity.getAmount());
        item.setShareDiscount(entity.getShareDiscount());
        item.setShareFreight(entity.getShareFreight());
        return item;
    }

    /**
     * 流水实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private OrderLog toDomain(OrderLogEntity entity) {
        OrderLog log = new OrderLog();
        log.setAction(entity.getAction());
        log.setFromStatus(entity.getFromStatus());
        log.setToStatus(entity.getToStatus());
        log.setOperatorId(entity.getOperatorId());
        log.setOperatorName(entity.getOperatorName());
        log.setRemark(entity.getRemark());
        log.setCreateTime(entity.getCreateTime());
        return log;
    }

    /**
     * 领域模型转实体。
     *
     * @param order 订单
     * @return 实体
     */
    private OrderEntity toEntity(MallOrder order) {
        OrderEntity entity = new OrderEntity();
        entity.setId(order.getId());
        entity.setOrderNo(order.getOrderNo());
        entity.setChannelId(order.getChannelId());
        entity.setChannelCode(order.getChannelCode());
        entity.setMode(order.getMode() == null ? null : order.getMode().name());
        entity.setSettlementType(order.getSettlementType() == null ? null : order.getSettlementType().name());
        entity.setCurrencyType(order.getCurrencyType() == null ? null : order.getCurrencyType().name());
        entity.setBuyerUserId(order.getBuyerUserId());
        entity.setBuyerName(order.getBuyerName());
        entity.setStatus(order.getStatus() == null ? null : order.getStatus().name());
        entity.setTotalAmount(order.getTotalAmount());
        entity.setGoodsAmount(order.getGoodsAmount());
        entity.setDiscountAmount(order.getDiscountAmount());
        entity.setFreightAmount(order.getFreightAmount());
        entity.setCouponUserId(order.getCouponUserId());
        entity.setCouponCode(order.getCouponCode());
        entity.setTotalQuantity(order.getTotalQuantity());
        entity.setItemCount(order.getItemCount());
        entity.setIdempotencyKey(order.getIdempotencyKey());
        entity.setPayNo(order.getPayNo());
        entity.setPayTime(order.getPayTime());
        entity.setRefundAmount(order.getRefundAmount());
        entity.setRefundTime(order.getRefundTime());
        entity.setShipTime(order.getShipTime());
        entity.setCompleteTime(order.getCompleteTime());
        entity.setCancelTime(order.getCancelTime());
        entity.setCancelReason(order.getCancelReason());
        entity.setExpireTime(order.getExpireTime());
        entity.setRemark(order.getRemark());
        return entity;
    }

    /**
     * 领域模型转明细实体。
     *
     * @param item 明细
     * @param orderId 订单 ID
     * @return 实体
     */
    private OrderItemEntity toItemEntity(OrderItem item, Long orderId) {
        OrderItemEntity entity = new OrderItemEntity();
        entity.setId(item.getId());
        entity.setOrderId(orderId);
        entity.setSpuId(item.getSpuId());
        entity.setSkuId(item.getSkuId());
        entity.setCategoryId(item.getCategoryId());
        entity.setSpuName(item.getSpuName());
        entity.setSkuCode(item.getSkuCode());
        entity.setSpecJson(item.getSpecJson());
        entity.setImageUrl(item.getImageUrl());
        entity.setPriceType(item.getPriceType());
        entity.setUnitPrice(item.getUnitPrice());
        entity.setQuantity(item.getQuantity());
        entity.setAmount(item.getAmount());
        entity.setShareDiscount(item.getShareDiscount());
        entity.setShareFreight(item.getShareFreight());
        return entity;
    }

    /**
     * 用券明细实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private OrderCoupon toDomain(OrderCouponEntity entity) {
        OrderCoupon coupon = new OrderCoupon();
        coupon.setId(entity.getId());
        coupon.setOrderId(entity.getOrderId());
        coupon.setOrderNo(entity.getOrderNo());
        coupon.setCouponUserId(entity.getCouponUserId());
        coupon.setCouponId(entity.getCouponId());
        coupon.setCouponCode(entity.getCouponCode());
        coupon.setCouponName(entity.getCouponName());
        coupon.setCouponType(entity.getCouponType());
        coupon.setScope(entity.getScope());
        coupon.setCategoryIds(entity.getCategoryIds());
        coupon.setDiscountAmount(entity.getDiscountAmount());
        coupon.setCreateTime(entity.getCreateTime());
        return coupon;
    }

    /**
     * 领域模型转用券明细实体。
     *
     * @param coupon  用券明细
     * @param orderNo 订单号（冗余落一份，按单对账不用先查订单头）
     * @return 实体
     */
    private OrderCouponEntity toCouponEntity(OrderCoupon coupon, String orderNo) {
        OrderCouponEntity entity = new OrderCouponEntity();
        entity.setOrderId(coupon.getOrderId());
        entity.setOrderNo(orderNo);
        entity.setCouponUserId(coupon.getCouponUserId());
        entity.setCouponId(coupon.getCouponId());
        entity.setCouponCode(coupon.getCouponCode());
        entity.setCouponName(coupon.getCouponName());
        entity.setCouponType(coupon.getCouponType());
        entity.setScope(coupon.getScope());
        entity.setCategoryIds(coupon.getCategoryIds());
        entity.setDiscountAmount(coupon.getDiscountAmount() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : coupon.getDiscountAmount());
        return entity;
    }
}
