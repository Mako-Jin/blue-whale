package com.bluewhale.pay.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 度支殿概览的统计口径（T5.3）。
 *
 * <p><b>为什么单独开一个端口，而不是让 provider 直接拿 mapper：</b>
 * provider 关心的是"这殿今天怎么样"，而"待支付算不算积压、退款率怎么算、
 * 失败流水从哪张表取"是会计口径。口径散在 provider 里，下一个支付报表页就会
 * 用另一套算法，然后"总控说今日收款 12 万，报表说 11.8 万"永远说不清。</p>
 *
 * <p><b>租户显式传参。</b>聚合总控时线程上下文挂的是"调用者"身份，
 * 靠 {@code TenantContext} 取租户等于把别人的数算进这一屏；显式传参让这种漏数
 * 在编译期就写不出来。实现里仍会走一遍行级租户插件（同值，不会放大范围）。</p>
 *
 * @author BlueWhale
 */
public interface PayOverviewStatQuery {

    /**
     * 收款统计（按支付时间）
     *
     * @param orders 收款笔数
     * @param amount 收款金额
     */
    record PaidStat(long orders, BigDecimal amount) {
    }

    /**
     * 待支付统计
     *
     * @param pending 当前待支付笔数
     * @param stale   其中超过阈值仍未支付的笔数
     */
    record PendingStat(long pending, long stale) {
    }

    /**
     * 按天聚合的收款额
     *
     * @param day    日期
     * @param amount 当日收款金额
     */
    record DayAmount(LocalDate day, BigDecimal amount) {
    }

    /**
     * 按渠道聚合的收款额
     *
     * @param channel 渠道编码（历史数据为空时按 UNKNOWN 归并）
     * @param orders  笔数
     * @param amount  金额
     */
    record ChannelAmount(String channel, long orders, BigDecimal amount) {
    }

    /**
     * 时间窗内的收款（{@code status IN (PAID, REFUNDED)} 且 {@code pay_time} 落在窗内）
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 笔数与金额；参数缺失时返回零值
     */
    PaidStat paidBetween(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 时间窗内新建的支付单笔数（{@code create_time} 落在窗内，不看状态）
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 建单笔数
     */
    long createdBetween(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 当前待支付与超时未支付笔数
     *
     * @param tenantId    租户 ID
     * @param staleBefore 早于该时刻仍未支付视为可疑（渠道回调可能丢失）
     * @return 待支付统计；参数缺失时返回零值
     */
    PendingStat pending(Long tenantId, LocalDateTime staleBefore);

    /**
     * 时间窗内成功退款金额（按流水，不按支付单累计值）
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 退款金额；无数据时为 0
     */
    BigDecimal refundBetween(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 时间窗内支付失败流水笔数
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 失败笔数
     */
    long failedPaymentsBetween(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 按天聚合的收款额
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 有数据的日期各一行（缺的日期不补，由调用方补 0）
     */
    List<DayAmount> paidByDay(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 按渠道聚合的收款额
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 每个渠道一行，按金额降序
     */
    List<ChannelAmount> paidByChannel(Long tenantId, LocalDateTime from, LocalDateTime to);
}
