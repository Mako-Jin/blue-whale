package com.bluewhale.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pay.po.PayOrderPo;
import com.bluewhale.pay.mapper.stat.PayChannelAmountPo;
import com.bluewhale.pay.mapper.stat.PayDayAmountPo;
import com.bluewhale.pay.mapper.stat.PayPaidStatPo;
import com.bluewhale.pay.mapper.stat.PayPendingStatPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付单 Mapper
 *
 * <p>不用标注 {@code @Mapper}：由 starter 的 {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描。</p>
 *
 * @author BlueWhale
 */
public interface PayOrderMapper extends BaseMapper<PayOrderPo> {

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
    @Update("UPDATE pay_order SET refund_amount = COALESCE(refund_amount, 0) + #{amount}, "
            + "refund_time = IF(COALESCE(refund_amount, 0) + #{amount} >= amount, NOW(), refund_time), "
            + "status = IF(COALESCE(refund_amount, 0) + #{amount} >= amount, 'REFUNDED', status), "
            + "update_time = NOW() "
            + "WHERE id = #{id} AND status = 'PAID' AND amount - COALESCE(refund_amount, 0) >= #{amount}")
    int applyRefund(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 时间窗内的收款统计（度支殿概览 T5.3）
     *
     * <p>口径按 {@code pay_time} 而不是 {@code create_time}：今天建的单一周后才付款，
     * 钱是后到账的。条件列不套函数（{@code >= / <}），保住 {@code idx_tenant_pay_time}。</p>
     *
     * @param tenantId 租户 ID
     * @param from 起始时刻（含）
     * @param to 截止时刻（不含）
     * @return 笔数与金额；无数据时金额为 0
     */
    @Select("SELECT COUNT(*) AS paidOrders, COALESCE(SUM(amount), 0) AS paidAmount "
            + "FROM pay_order "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND status IN ('PAID', 'REFUNDED') "
            + "AND pay_time >= #{from} AND pay_time < #{to}")
    PayPaidStatPo sumPaidBetween(@Param("tenantId") Long tenantId,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    /**
     * 今日建单量（度支殿概览 T5.3）
     *
     * @param tenantId 租户 ID
     * @param from 起始时刻（含）
     * @param to 截止时刻（不含）
     * @return 建单笔数
     */
    @Select("SELECT COUNT(*) FROM pay_order "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND create_time >= #{from} AND create_time < #{to}")
    long countCreatedBetween(@Param("tenantId") Long tenantId,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to);

    /**
     * 待支付单与其中超时未支付的笔数（度支殿概览 T5.3）
     *
     * <p>{@code PAYING} 是渠道调用中的中间态，尚未落账，同样计入待支付；
     * 长时间停在 PAYING 属于异常，需要按超时口径暴露出来。</p>
     *
     * @param tenantId 租户 ID
     * @param staleBefore 超过该时刻仍未支付视为可疑（回调可能丢失）
     * @return 待支付总数与超时数
     */
    @Select("SELECT COALESCE(SUM(CASE WHEN status IN ('CREATED', 'PAYING') THEN 1 ELSE 0 END), 0) AS pendingOrders, "
            + "COALESCE(SUM(CASE WHEN status IN ('CREATED', 'PAYING') AND create_time < #{staleBefore} "
            + "THEN 1 ELSE 0 END), 0) AS staleOrders "
            + "FROM pay_order WHERE tenant_id = #{tenantId} AND deleted = 0")
    PayPendingStatPo countPending(@Param("tenantId") Long tenantId,
                                  @Param("staleBefore") LocalDateTime staleBefore);

    /**
     * 近 N 日收款趋势（度支殿概览 T5.3）
     *
     * <p>只在输出上套 {@code DATE()}，条件列保持原始列：把函数写进 {@code WHERE}
     * 会让时间索引失效。</p>
     *
     * @param tenantId 租户 ID
     * @param from 起始时刻（含）
     * @param to 截止时刻（不含）
     * @return 每天一行，缺数据的日期由调用方补 0
     */
    @Select("SELECT DATE(pay_time) AS day, COALESCE(SUM(amount), 0) AS amount "
            + "FROM pay_order "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND status IN ('PAID', 'REFUNDED') "
            + "AND pay_time >= #{from} AND pay_time < #{to} "
            + "GROUP BY DATE(pay_time) ORDER BY day")
    List<PayDayAmountPo> sumPaidByDay(@Param("tenantId") Long tenantId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    /**
     * 时间窗内按渠道聚合收款（度支殿概览 T5.3）
     *
     * @param tenantId 租户 ID
     * @param from 起始时刻（含）
     * @param to 截止时刻（不含）
     * @return 每个渠道一行，按金额降序
     */
    @Select("SELECT channel AS channel, COUNT(*) AS orders, COALESCE(SUM(amount), 0) AS amount "
            + "FROM pay_order "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND status IN ('PAID', 'REFUNDED') "
            + "AND pay_time >= #{from} AND pay_time < #{to} "
            + "GROUP BY channel ORDER BY amount DESC")
    List<PayChannelAmountPo> sumPaidByChannel(@Param("tenantId") Long tenantId,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);
}
