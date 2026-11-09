package com.bluewhale.mall.order.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.order.infrastructure.persistence.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {

    /**
     * 原子累计订单退款金额，累计达总额时自动置 REFUNDED。
     *
     * <p>状态不从 SQL 里写死回 {@code PAID}：部分退款后订单应保持原状态
     * （{@code SHIPPED} 的订单退了一件，货还是在路上），只有整单退满才转
     * {@code REFUNDED}。{@code REFUNDING} 未退满的情况由应用层回退到
     * {@code PAID}，因为该状态的前置只能是已支付。</p>
     *
     * <p>条件 {@code total_amount - refund_amount >= 本次金额} 拒绝超额；
     * {@code status IN (...)} 把待支付、已取消这些没收到钱的订单挡在外面。</p>
     *
     * @param id     订单 ID
     * @param amount 本次退款金额
     * @return 影响行数（0 表示状态不允许或可退金额不足）
     */
    @Update("UPDATE mall_order SET refund_amount = refund_amount + #{amount}, "
            + "refund_time = IF(refund_amount + #{amount} >= total_amount, NOW(), refund_time), "
            + "status = IF(refund_amount + #{amount} >= total_amount, 'REFUNDED', status), "
            + "update_time = NOW() "
            + "WHERE id = #{id} AND total_amount - refund_amount >= #{amount} "
            + "AND status IN ('PAID', 'SHIPPED', 'COMPLETED', 'REFUNDING')")
    int applyRefund(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 按日聚合订单报表（直接聚合订单头，不建快照表）。
     *
     * <p>统计范围是「收过钱的订单」：PAID / SHIPPED / COMPLETED / REFUNDING / REFUNDED。
     * 待支付与已取消不算成交；REFUNDED 保留在成交里、退款另算一列，
     * 因为「卖出去又退回来」和「没卖出去」是两件事。paid_amount 含运费
     * （买家实付），coupon_discount 直接取订单头 discount_amount。</p>
     *
     * @param from       起始时间（含）
     * @param toExclusive 结束时间（不含）
     * @return 按日聚合结果（列名小写别名，SUM 为 NULL 的日子返回 0 由仓储兜底）
     */
    @Select("SELECT DATE(create_time) AS stat_date, COUNT(*) AS order_count, "
            + "IFNULL(SUM(goods_amount), 0) AS goods_amount, "
            + "IFNULL(SUM(freight_amount), 0) AS freight_amount, "
            + "IFNULL(SUM(discount_amount), 0) AS coupon_discount, "
            + "IFNULL(SUM(total_amount), 0) AS paid_amount, "
            + "IFNULL(SUM(refund_amount), 0) AS refund_amount "
            + "FROM mall_order "
            + "WHERE create_time >= #{from} AND create_time < #{toExclusive} "
            + "AND status IN ('PAID', 'SHIPPED', 'COMPLETED', 'REFUNDING', 'REFUNDED') "
            + "GROUP BY DATE(create_time) ORDER BY stat_date")
    List<Map<String, Object>> dailyReport(@Param("from") LocalDateTime from,
                                          @Param("toExclusive") LocalDateTime toExclusive);
}
