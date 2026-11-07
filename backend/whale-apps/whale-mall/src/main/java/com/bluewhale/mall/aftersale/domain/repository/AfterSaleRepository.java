package com.bluewhale.mall.aftersale.domain.repository;

import com.bluewhale.mall.aftersale.domain.model.MallAfterSale;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 售后单仓储。
 *
 * <p>除了自身表的读写，还承担两处跨表的额度操作：行级退款额度占用
 * （{@code mall_order_item}）与订单退款金额累计（{@code mall_order}）。
 * 这两步都是条件更新——「先查再改」在并发下必然超额，
 * 必须让数据库在一条语句里同时判断并写入。</p>
 *
 * @author BlueWhale
 */
public interface AfterSaleRepository {

    /**
     * 新建售后单（含明细）。
     *
     * @param afterSale 售后单
     * @return 售后单 ID
     */
    Long insert(MallAfterSale afterSale);

    /**
     * 更新售后单（不触碰明细）。
     *
     * @param afterSale 售后单
     */
    void update(MallAfterSale afterSale);

    /**
     * 按 ID 查询（含明细）。
     *
     * @param id 售后单 ID
     * @return 售后单
     */
    Optional<MallAfterSale> findById(Long id);

    /**
     * 按售后单号查询（含明细）。
     *
     * @param afterSaleNo 售后单号
     * @return 售后单
     */
    Optional<MallAfterSale> findByAfterSaleNo(String afterSaleNo);

    /**
     * 分页查询（含明细）。
     *
     * @param userId  买家用户 ID（null 表示不限，用于运营列表）
     * @param status  状态过滤
     * @param keyword 售后单号/订单号关键字
     * @param offset  偏移量
     * @param size    条数
     * @return 售后单列表
     */
    List<MallAfterSale> page(Long userId, String status, String keyword, long offset, int size);

    /**
     * 统计数量。
     *
     * @param userId  买家用户 ID（null 表示不限）
     * @param status  状态过滤
     * @param keyword 关键字
     * @return 总数
     */
    long count(Long userId, String status, String keyword);

    /**
     * 占用订单行的退款额度。
     *
     * @param orderItemId 订单明细 ID
     * @param quantity    本次退数量
     * @param amount      本次退金额
     * @return 占用成功返回 true；可退数量不足返回 false
     */
    boolean occupyItemQuota(Long orderItemId, int quantity, BigDecimal amount);

    /**
     * 释放订单行的退款额度（售后被驳回或撤销时回滚）。
     *
     * @param orderItemId 订单明细 ID
     * @param quantity    释放数量
     * @param amount      释放金额
     * @return 释放成功返回 true；无可释放额度返回 false
     */
    boolean releaseItemQuota(Long orderItemId, int quantity, BigDecimal amount);

    /**
     * 原子累计订单退款金额。
     *
     * @param orderId 订单 ID
     * @param amount  本次退款金额
     * @return 累计成功返回 true；订单状态不允许或可退金额不足返回 false
     */
    boolean applyOrderRefund(Long orderId, BigDecimal amount);
}
