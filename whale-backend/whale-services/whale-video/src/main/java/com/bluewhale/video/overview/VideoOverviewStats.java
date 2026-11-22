package com.bluewhale.video.overview;

/**
 * 影戏院（VIDEO）统计快照：内容流水线、审核、授权三本账。
 *
 * <p>全部为租户内计数，一次 {@code snapshot} 调用取齐；字段只读，
 * 语义见 {@link VideoOverviewStatsService}。</p>
 *
 * @param contentDraft        草稿内容（{@code DRAFT}，上传后未送审）
 * @param contentPendingReview 待审内容（{@code PENDING_REVIEW}，运营要过目）
 * @param contentPublished    已发布内容（{@code PUBLISHED}，对外可见）
 * @param contentOffline      已下线内容（{@code OFFLINE}）
 * @param commentPending      待审评论（{@code PENDING}，先审后发模式积压）
 * @param copyrightTakedown   版权下架内容（{@code TAKEDOWN}，有争议强制下线）
 * @param licenseExpiring     授权将到期（7 天内到期或已到期的已发布电影）
 * @param totalPlay           累计播放次数
 * @author BlueWhale
 */
public record VideoOverviewStats(
        long contentDraft,
        long contentPendingReview,
        long contentPublished,
        long contentOffline,
        long commentPending,
        long copyrightTakedown,
        long licenseExpiring,
        long totalPlay) {

    /**
     * 内容总数：四态之和（草稿 + 待审 + 已发布 + 已下线）。
     *
     * @return 内容总数
     */
    public long contentTotal() {
        return contentDraft + contentPendingReview + contentPublished + contentOffline;
    }
}
