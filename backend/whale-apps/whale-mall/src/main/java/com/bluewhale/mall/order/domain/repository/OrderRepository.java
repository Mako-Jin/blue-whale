package com.bluewhale.mall.order.domain.repository;

import com.bluewhale.mall.order.domain.model.MallOrder;
import com.bluewhale.mall.order.domain.model.OrderLog;

import java.util.List;
import java.util.Optional;

/**
 * 订单仓储。
 *
 * @author BlueWhale
 */
public interface OrderRepository {

    /**
     * 新建订单（含明细）。
     *
     * @param order 订单
     * @return 订单 ID
     */
    Long insert(MallOrder order);

    /**
     * 更新订单状态与时间字段（不触碰明细）。
     *
     * @param order 订单
     */
    void update(MallOrder order);

    /**
     * 追加状态流水。
     *
     * @param orderId 订单 ID
     * @param log 流水
     */
    void appendLog(Long orderId, OrderLog log);

    /**
     * 按 ID 查询（含明细）。
     *
     * @param id 订单 ID
     * @return 订单
     */
    Optional<MallOrder> findById(Long id);

    /**
     * 按订单号查询（含明细）。
     *
     * @param orderNo 订单号
     * @return 订单
     */
    Optional<MallOrder> findByOrderNo(String orderNo);

    /**
     * 按幂等键查询（含明细）。
     *
     * @param buyerUserId 买家用户 ID
     * @param idempotencyKey 幂等键
     * @return 订单
     */
    Optional<MallOrder> findByIdempotency(Long buyerUserId, String idempotencyKey);

    /**
     * 分页查询。
     *
     * @param buyerUserId 买家用户 ID（null 表示不限，用于运营列表）
     * @param status 状态过滤
     * @param keyword 订单号关键字
     * @param offset 偏移量
     * @param size 条数
     * @return 订单列表（含明细）
     */
    List<MallOrder> page(Long buyerUserId, String status, String keyword, long offset, int size);

    /**
     * 统计数量。
     *
     * @param buyerUserId 买家用户 ID（null 表示不限）
     * @param status 状态过滤
     * @param keyword 订单号关键字
     * @return 总数
     */
    long count(Long buyerUserId, String status, String keyword);

    /**
     * 查询订单状态流水。
     *
     * @param orderId 订单 ID
     * @return 流水列表（按时间正序）
     */
    List<OrderLog> listLogs(Long orderId);

    /**
     * 占用行级发货额度（建发货单时）。
     *
     * <p>与退款额度同一套写法：条件更新 {@code quantity - shipped_quantity >= ?}，
     * 并发下后到的直接失败，而不是两张发货单都成功、拣货时才发现只够一份。</p>
     *
     * @param orderItemId 订单明细 ID
     * @param quantity 本次发货数量
     * @return 占用成功返回 true
     */
    boolean occupyShippedQuantity(Long orderItemId, int quantity);

    /**
     * 释放行级发货额度（发货单作废时）。
     *
     * @param orderItemId 订单明细 ID
     * @param quantity 释放数量
     * @return 释放成功返回 true
     */
    boolean releaseShippedQuantity(Long orderItemId, int quantity);
}
