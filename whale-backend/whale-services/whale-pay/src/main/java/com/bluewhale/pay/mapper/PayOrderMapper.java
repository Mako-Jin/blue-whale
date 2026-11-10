package com.bluewhale.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pay.entity.PayOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 支付单 Mapper
 *
 * <p>不用标注 {@code @Mapper}：由 starter 的 {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描。</p>
 *
 * @author BlueWhale
 */
public interface PayOrderMapper extends BaseMapper<PayOrder> {

    /**
     * 原子退款累计：单条 SQL 完成「可退校验 + 累计 + 全额置 REFUNDED」。
     *
     * <p>条件 {@code amount - refund_amount >= 本次金额} 天然拒绝超额退款，
     * 并发多笔退款时各自原子累计，不会互相覆盖。</p>
     *
     * @param id     支付单 ID
     * @param amount 本次退款金额
     * @return 影响行数（0 表示状态不允许或余额不足）
     */
    @Update("UPDATE pay_order SET refund_amount = refund_amount + #{amount}, "
            + "refund_time = IF(refund_amount + #{amount} >= amount, NOW(), refund_time), "
            + "status = IF(refund_amount + #{amount} >= amount, 'REFUNDED', status), "
            + "update_time = NOW() "
            + "WHERE id = #{id} AND status = 'PAID' AND amount - refund_amount >= #{amount}")
    int applyRefund(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
