package com.bluewhale.mall.order.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.order.infrastructure.persistence.entity.OrderItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 订单明细 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemEntity> {

    /**
     * 占用行级退款额度。
     *
     * <p>条件 {@code quantity - refunded_quantity >= 本次数量} 天然拒绝超额：
     * 同一订单行被两张售后单并发申请时，后到的那条影响 0 行直接失败，
     * 而不是两张都通过、真正退款时才发现退不出。</p>
     *
     * @param itemId   订单明细 ID
     * @param quantity 本次退数量
     * @param amount   本次退金额
     * @return 影响行数（0 表示可退数量不足）
     */
    @Update("UPDATE mall_order_item SET refunded_quantity = refunded_quantity + #{quantity}, "
            + "refunded_amount = refunded_amount + #{amount}, "
            + "update_time = NOW() "
            + "WHERE id = #{itemId} AND quantity - refunded_quantity >= #{quantity}")
    int occupyRefundQuota(@Param("itemId") Long itemId,
                          @Param("quantity") int quantity,
                          @Param("amount") BigDecimal amount);

    /**
     * 释放行级退款额度（售后被驳回或撤销时回滚占用）。
     *
     * <p>不释放的后果很具体：商品被驳回一次就再也申请不了售后，
     * 因为额度还挂在那张失败的售后单上。条件 {@code refunded_quantity >= 本次数量}
     * 防止释放超过占用，把数量减成负数。</p>
     *
     * @param itemId   订单明细 ID
     * @param quantity 释放数量
     * @param amount   释放金额
     * @return 影响行数（0 表示无可释放额度）
     */
    @Update("UPDATE mall_order_item SET refunded_quantity = refunded_quantity - #{quantity}, "
            + "refunded_amount = refunded_amount - #{amount}, "
            + "update_time = NOW() "
            + "WHERE id = #{itemId} AND refunded_quantity >= #{quantity}")
    int releaseRefundQuota(@Param("itemId") Long itemId,
                           @Param("quantity") int quantity,
                           @Param("amount") BigDecimal amount);
}
