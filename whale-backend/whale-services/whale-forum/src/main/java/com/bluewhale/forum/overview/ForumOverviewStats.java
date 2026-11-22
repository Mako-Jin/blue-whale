package com.bluewhale.forum.overview;

/**
 * 清议堂（FORUM）统计快照：帖子、举报、申诉、封禁四本账。
 *
 * <p>做成 record 而不是散着传：{@link ForumOverviewProvider} 要拿同一份快照
 * 拼 KPI、柱图和告警三处，值一旦分头取就可能出现"牌上写 5 条待审、告警说 3 条"
 * 这种同一屏自相矛盾。</p>
 *
 * <p>{@code threadTotal()} 只累计算进柱图的五态，不含草稿与定时发布：
 * 那两态是作者自己的草稿箱，既不进审核也不在讨论里，算进"全站在议规模"
 * 会让人以为清议堂很热闹。</p>
 *
 * @param threadPending   待审帖（PENDING）
 * @param threadPublished 在议帖（PUBLISHED，开放回复）
 * @param threadSolved    已解决（SOLVED，问答帖已采纳，仍可回复）
 * @param threadClosed    已关闭（CLOSED，禁新回复）
 * @param threadRejected  已驳回（REJECTED，作者可申诉）
 * @param threadPinned    悬榜（置顶）帖数
 * @param threadEssence   题金榜（精华）帖数
 * @param reportPending   待受理举报（PENDING + HANDLING）
 * @param appealPending   待理申诉（PENDING）
 * @param banActive       封禁中用户（ACTIVE，含禁言与封号）
 * @author BlueWhale
 */
public record ForumOverviewStats(
        long threadPending,
        long threadPublished,
        long threadSolved,
        long threadClosed,
        long threadRejected,
        long threadPinned,
        long threadEssence,
        long reportPending,
        long appealPending,
        long banActive) {

    /**
     * 进柱图的五态合计。
     *
     * @return 帖子总数（不含草稿与定时发布）
     */
    public long threadTotal() {
        return threadPending + threadPublished + threadSolved + threadClosed + threadRejected;
    }
}
