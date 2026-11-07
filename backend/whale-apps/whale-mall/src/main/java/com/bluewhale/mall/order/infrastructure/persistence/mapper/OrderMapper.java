package com.bluewhale.mall.order.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.order.infrastructure.persistence.entity.OrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

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
}
