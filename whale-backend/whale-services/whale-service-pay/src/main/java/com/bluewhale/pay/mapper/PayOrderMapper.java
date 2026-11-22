package com.bluewhale.pay.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pay.po.PayOrderPo;
import com.bluewhale.pay.mapper.stat.PayChannelAmountPo;
import com.bluewhale.pay.mapper.stat.PayDayAmountPo;
import com.bluewhale.pay.mapper.stat.PayPaidStatPo;
import com.bluewhale.pay.mapper.stat.PayPendingStatPo;
import com.bluewhale.pay.mapper.stat.PayStaleOrderPo;
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
     * 扫描停留在 {@code PAYING} 的支付单（平台级跨租户对账）。
     *
     * <p>定时对账线程没有租户上下文，这里显式忽略租户拦截器，扫到全部租户的停留单；
     * 结果带回 {@code tenant_id}，由调用方逐单还原租户上下文后再更新与记账。</p>
     *
     * @param before 更新时间早于该时刻才视为停留
     * @param limit  单轮最大条数
     * @return 支付单摘要（ID + 租户）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id, tenant_id FROM pay_order "
            + "WHERE status = 'PAYING' AND deleted = 0 AND update_time < #{before} "
            + "ORDER BY update_time ASC LIMIT #{limit}")
    List<PayStaleOrderPo> selectStalePaying(@Param("before") LocalDateTime before,
                                            @Param("limit") int limit);

    /**
     * 按支付单号反查租户（渠道回调使用）。
     *
     * <p>回调请求不信任入站租户头，必须先跨租户定位支付单。唯一键是
     * {@code (tenant_id, order_no)}，若同一单号命中多个租户说明数据异常，
     * 由调用方拒绝并留痕，不做猜测。</p>
     *
     * @param orderNo 支付单号
     * @return 命中租户列表
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT tenant_id FROM pay_order WHERE order_no = #{orderNo} AND deleted = 0")
    List<Long> selectTenantIdsByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 跨租户列出存在支付单的租户（批量对账的日切入口）。
     *
     * <p>定时线程没有租户上下文，必须先拿到租户清单再逐租户还原上下文比对，
     * 否则差异与流水会落到默认租户。</p>
     *
     * @return 租户 ID 列表
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT DISTINCT tenant_id FROM pay_order WHERE deleted = 0 AND tenant_id IS NOT NULL")
    List<Long> selectDistinctTenantIds();

    /**
     * 某业务日需要参与对账的渠道清单。
     *
     * <p>口径：当日到账（PAID/REFUNDED 按 {@code pay_time}）与当日进入支付中
     * （PAYING 按 {@code update_time}）的订单所属渠道；CREATED 从未发往渠道，不纳入。</p>
     *
     * @param tenantId 租户 ID
     * @param from     业务日起（含）
     * @param to       业务日止（不含）
     * @return 渠道清单
     */
    @Select("SELECT DISTINCT channel FROM pay_order "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND channel IS NOT NULL AND channel <> '' AND ("
            + "(status IN ('PAID', 'REFUNDED') AND pay_time >= #{from} AND pay_time < #{to}) "
            + "OR (status = 'PAYING' AND update_time >= #{from} AND update_time < #{to}))")
    List<String> selectReconcileChannels(@Param("tenantId") Long tenantId,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);

    /**
     * 某业务日某渠道参与对账的本地订单。
     *
     * <p>与 {@link #selectReconcileChannels} 同一口径，保证「渠道清单」与「订单集合」
     * 不会出现一个有一个没有的错位。</p>
     *
     * @param tenantId 租户 ID
     * @param channel  渠道编码
     * @param from     业务日起（含）
     * @param to       业务日止（不含）
     * @return 支付单列表
     */
    @Select("SELECT * FROM pay_order "
            + "WHERE tenant_id = #{tenantId} AND channel = #{channel} AND deleted = 0 AND ("
            + "(status IN ('PAID', 'REFUNDED') AND pay_time >= #{from} AND pay_time < #{to}) "
            + "OR (status = 'PAYING' AND update_time >= #{from} AND update_time < #{to}))")
    List<PayOrderPo> selectReconcileOrders(@Param("tenantId") Long tenantId,
                                           @Param("channel") String channel,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    /**
     * 渠道关单回调：仅未支付单可置为已取消（条件更新，并发安全）。
     *
     * @param id 支付单 ID
     * @return 影响行数（0 表示已支付/已退款/已取消）
     */
    @Update("UPDATE pay_order SET status = 'CANCELLED', update_time = NOW() "
            + "WHERE id = #{id} AND status IN ('CREATED', 'PAYING') AND deleted = 0")
    int closeIfUnpaid(@Param("id") Long id);

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
