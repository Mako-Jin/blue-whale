package com.bluewhale.message.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 通政殿概览的统计口径（T5.8）。
 *
 * <p><b>为什么不复用 {@code MessageDashboardService}：</b>那个方法是为 2D 投递看板服务的，
 * 它把窗口内投递按 {@code channel + status} 分组全部回捞到内存再拼渠道表与失败 Top10。
 * 屏风页每 30 秒轮询一次，拿它当数据源等于每 30 秒拉一遍渠道 × 状态的组合行。
 * 本端口只做 count 型聚合，一条 SQL 出一个数。</p>
 *
 * <p><b>口径必须与 2D 看板一致。</b>成功 = {@code DELIVERED + READ}，失败 =
 * {@code FAILED + BOUNCED}；这里不重新定义，只换一种取数方式。</p>
 *
 * <p><b>租户显式传参。</b>聚合总控时线程上下文挂的是"调用者"身份，靠
 * {@code TenantContext} 取租户等于把别人的数算进这一屏。</p>
 *
 * @author BlueWhale
 */
public interface MessageOverviewStatQuery {

    /**
     * 时间窗内的投递统计
     *
     * @param total   投递总条数
     * @param success 成功条数（DELIVERED + READ）
     * @param failed  失败条数（FAILED + BOUNCED）
     * @param bounced 其中退信条数（BOUNCED，重试无用）
     */
    record DeliveryStat(long total, long success, long failed, long bounced) {
    }

    /**
     * 按天聚合的投递条数
     *
     * @param day        日期
     * @param deliveries 当日投递条数
     */
    record DayDelivery(LocalDate day, long deliveries) {
    }

    /**
     * 按渠道聚合的失败条数
     *
     * @param channel  渠道（为空时按「未知渠道」归并）
     * @param failures 失败条数
     */
    record ChannelFail(String channel, long failures) {
    }

    /**
     * 时间窗内的投递条数
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 投递条数；参数缺失时返回 0
     */
    long sentCount(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 时间窗内的投递成败统计
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 统计；参数缺失时返回零值
     */
    DeliveryStat deliveryStat(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 创建时间早于阈值、状态仍停在未投出的消息条数
     *
     * @param tenantId 租户 ID
     * @param deadline 创建时间早于该值视为积压
     * @return 积压条数；参数缺失时返回 0
     */
    long backlog(Long tenantId, LocalDateTime deadline);

    /**
     * 按天聚合的投递条数
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 有数据的日期各一行（缺的日期不补，由调用方补 0）
     */
    List<DayDelivery> dailyDeliveries(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 时间窗内失败条数最多的渠道
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @param limit    取前几名
     * @return 按失败条数降序；无失败时返回空表
     */
    List<ChannelFail> failedChannels(Long tenantId, LocalDateTime from, LocalDateTime to, int limit);
}
