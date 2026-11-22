package com.bluewhale.music.overview;

/**
 * 乐坊（MUSIC）统计快照：曲库、审核、版权三本账。
 *
 * <p>全部为租户内计数，一次 {@code snapshot} 调用取齐；字段只读，
 * 语义见 {@link MusicOverviewStatsService}。</p>
 *
 * @param songDraft        草稿歌曲（{@code DRAFT}，上传后未上架）
 * @param songPublished    已上架歌曲（{@code PUBLISHED}，公开可播）
 * @param songOffline      已下架歌曲（{@code OFFLINE}）
 * @param copyrightDeclared 版权待复核（{@code DECLARED}，已签署协议等运营复核）
 * @param copyrightValid   版权有效（{@code VALID}）
 * @param copyrightTakedown 版权下架（{@code TAKEDOWN}，有争议或被投诉强制下线）
 * @param commentPending   待审评论（{@code PENDING}，先审后发模式积压）
 * @param appealPending    待复核申诉（{@code PENDING}，上传者对版权下架不服）
 * @author BlueWhale
 */
public record MusicOverviewStats(
        long songDraft,
        long songPublished,
        long songOffline,
        long copyrightDeclared,
        long copyrightValid,
        long copyrightTakedown,
        long commentPending,
        long appealPending) {
}
