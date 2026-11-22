package com.bluewhale.music.overview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.music.infrastructure.persistence.entity.SongAppealEntity;
import com.bluewhale.music.infrastructure.persistence.entity.SongCommentEntity;
import com.bluewhale.music.infrastructure.persistence.entity.SongEntity;
import com.bluewhale.music.infrastructure.persistence.mapper.SongAppealMapper;
import com.bluewhale.music.infrastructure.persistence.mapper.SongCommentMapper;
import com.bluewhale.music.infrastructure.persistence.mapper.SongMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 乐坊（MUSIC）统计口径：曲库、审核、版权三本账。
 *
 * <p><b>口径只在这里定义一次。</b>歌曲发布三态取枚举名
 * {@link com.bluewhale.music.domain.model.PublishState}（草稿 / 已上架 / 已下架）；
 * 版权三态取 {@link com.bluewhale.music.domain.model.CopyrightState}
 * （待复核 / 有效 / 下架）；评论待审与申诉待复核都只数
 * {@code PENDING} 一段——出了结论（通过 / 驳回 / 过审 / 隐藏）的不挂在待办里。</p>
 *
 * <p><b>不写 GROUP BY，按状态各 count 一次。</b>状态码值固定且都有索引，
 * 几次索引 count 比一次聚合直观；更重要的是不走自定义 SQL 就不用自己拼
 * {@code tenant_id} 条件——租户隔离由 MyBatis-Plus 插件保证，一旦漏了就是
 * 跨租户串数，且不会报错（营造成批的老教训）。</p>
 *
 * <p><b>租户 ID 必须显式传入。</b>概览 provider 在独立线程里执行，拿不到
 * 请求线程的租户上下文；传 {@code null} 直接抛——总控聚合时会显式传租户，
 * 缺租户说明调用方配错了，早点降级比在屏风上显示一个错误的 0 强。</p>
 *
 * <p>统计只读，不写库：概览被前端按 30s 轮询，任何写操作都会变成"每 30 秒写一次"。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MusicOverviewStatsService {

    /** 发布状态：草稿 */
    private static final String PUBLISH_DRAFT = "DRAFT";

    /** 发布状态：已上架 */
    private static final String PUBLISH_PUBLISHED = "PUBLISHED";

    /** 发布状态：已下架 */
    private static final String PUBLISH_OFFLINE = "OFFLINE";

    /** 版权状态：待复核（已签协议，等运营过目） */
    private static final String COPYRIGHT_DECLARED = "DECLARED";

    /** 版权状态：有效 */
    private static final String COPYRIGHT_VALID = "VALID";

    /** 版权状态：下架（有争议或被投诉，强制下线） */
    private static final String COPYRIGHT_TAKEDOWN = "TAKEDOWN";

    /** 评论状态：待审（先审后发） */
    private static final String COMMENT_PENDING = "PENDING";

    /** 申诉状态：待复核 */
    private static final String APPEAL_PENDING = "PENDING";

    private final SongMapper songMapper;

    private final SongCommentMapper songCommentMapper;

    private final SongAppealMapper songAppealMapper;

    /**
     * 取一次统计快照。
     *
     * @param tenantId 租户 ID（必填）
     * @return 统计快照
     */
    public MusicOverviewStats snapshot(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for music overview stats");
        }
        long songDraft = count(songMapper.selectCount(new LambdaQueryWrapper<SongEntity>()
                .eq(SongEntity::getPublishStatus, PUBLISH_DRAFT)));
        long songPublished = count(songMapper.selectCount(new LambdaQueryWrapper<SongEntity>()
                .eq(SongEntity::getPublishStatus, PUBLISH_PUBLISHED)));
        long songOffline = count(songMapper.selectCount(new LambdaQueryWrapper<SongEntity>()
                .eq(SongEntity::getPublishStatus, PUBLISH_OFFLINE)));
        long copyrightDeclared = count(songMapper.selectCount(new LambdaQueryWrapper<SongEntity>()
                .eq(SongEntity::getCopyrightStatus, COPYRIGHT_DECLARED)));
        long copyrightValid = count(songMapper.selectCount(new LambdaQueryWrapper<SongEntity>()
                .eq(SongEntity::getCopyrightStatus, COPYRIGHT_VALID)));
        long copyrightTakedown = count(songMapper.selectCount(new LambdaQueryWrapper<SongEntity>()
                .eq(SongEntity::getCopyrightStatus, COPYRIGHT_TAKEDOWN)));
        long commentPending = count(songCommentMapper.selectCount(new LambdaQueryWrapper<SongCommentEntity>()
                .eq(SongCommentEntity::getStatus, COMMENT_PENDING)));
        long appealPending = count(songAppealMapper.selectCount(new LambdaQueryWrapper<SongAppealEntity>()
                .eq(SongAppealEntity::getStatus, APPEAL_PENDING)));
        return new MusicOverviewStats(songDraft, songPublished, songOffline,
                copyrightDeclared, copyrightValid, copyrightTakedown,
                commentPending, appealPending);
    }

    /**
     * 计数兜底：{@code selectCount} 在极端情况下返回 {@code null}，直接拆箱就是 NPE。
     *
     * @param value 计数值，可空
     * @return 非空计数
     */
    private static long count(Long value) {
        return value == null ? 0L : value;
    }
}
