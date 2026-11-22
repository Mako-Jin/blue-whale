package com.bluewhale.mail.overview;

/**
 * 邮驿（MAIL）一次统计快照。
 *
 * <p>账号四态（近一日已同步 / 超时未同步 / 从未同步 / 未开同步）互斥且合计等于
 * {@link #accountTotal()}，柱图直接用这四格，不需要再减。</p>
 *
 * @param accountTotal  在册邮箱账号数（未删除）
 * @param accountEnabled 启用中的账号数
 * @param accountDisabled 停用账号数
 * @param syncFresh     开启同步且最近 24 小时内拉取过
 * @param syncOverdue   开启同步、拉取过但已超过 24 小时没动静
 * @param syncNever     开启同步但一次都没跑成功过
 * @param syncOff       未开启同步的账号
 * @param quotaTight    配额已用 ≥80% 的账号数（配额未知的不算）
 * @param bounce7d      近 7 日退信回执数
 * @param bounceHard7d  近 7 日其中的永久退信数（5.x.x）
 * @author BlueWhale
 */
public record MailOverviewStats(
        long accountTotal,
        long accountEnabled,
        long accountDisabled,
        long syncFresh,
        long syncOverdue,
        long syncNever,
        long syncOff,
        long quotaTight,
        long bounce7d,
        long bounceHard7d) {

    /**
     * 开启同步的账号合计（三态之和）。
     *
     * @return 开启同步的账号数
     */
    public long syncEnabled() {
        return syncFresh + syncOverdue + syncNever;
    }
}
