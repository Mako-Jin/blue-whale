package com.bluewhale.video.overview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.video.domain.model.CommentStatus;
import com.bluewhale.video.domain.model.CopyrightState;
import com.bluewhale.video.domain.model.PublishState;
import com.bluewhale.video.infrastructure.persistence.entity.ContentCommentEntity;
import com.bluewhale.video.infrastructure.persistence.entity.ContentEntity;
import com.bluewhale.video.infrastructure.persistence.mapper.CommentMapper;
import com.bluewhale.video.infrastructure.persistence.mapper.ContentMapper;
import com.bluewhale.video.infrastructure.persistence.mapper.MovieMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 影戏院（VIDEO）统计口径：内容流水线、评论审核、电影授权三本账。
 *
 * <p><b>口径只在这里定义一次。</b>内容发布四态取枚举名
 * {@link PublishState}（草稿 / 待审 / 已发布 / 已下线）；版权二态取
 * {@link CopyrightState}（有效 / 下架）；待审评论只数
 * {@link CommentStatus#PENDING} 一段——隐藏与可见都已出结论，不挂待办。</p>
 *
 * <p><b>不写 GROUP BY，按状态各 count 一次。</b>状态码值固定且都有索引，
 * 几次索引 count 比一次聚合直观；更重要的是不走自定义 SQL 就不用自己拼
 * {@code tenant_id} 条件——租户隔离由 MyBatis-Plus 插件保证，一旦漏了就是
 * 跨租户串数，且不会报错（营造成批的老教训）。</p>
 *
 * <p><b>租户 ID 必须显式传入。</b>概览 provider 在独立线程里执行，SDK 会把
 * 租户绑到执行线程（{@code OverviewService} 负责），这里是第二道闸：
 * 传 {@code null} 直接抛，缺租户说明调用方配错了，早点降级比在屏风上
 * 显示一个跨租户的和强。</p>
 *
 * <p>统计只读，不写库：概览被前端按 30s 轮询，任何写操作都会变成"每 30 秒写一次"。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class VideoOverviewStatsService {

    /** 发布状态：草稿 */
    private static final String PUBLISH_DRAFT = PublishState.DRAFT.name();

    /** 发布状态：待审核 */
    private static final String PUBLISH_PENDING_REVIEW = PublishState.PENDING_REVIEW.name();

    /** 发布状态：已发布 */
    private static final String PUBLISH_PUBLISHED = PublishState.PUBLISHED.name();

    /** 发布状态：已下线 */
    private static final String PUBLISH_OFFLINE = PublishState.OFFLINE.name();

    /** 版权状态：版权下架（须留痕，强制下线） */
    private static final String COPYRIGHT_TAKEDOWN = CopyrightState.TAKEDOWN.name();

    /** 评论状态：待审 */
    private static final String COMMENT_PENDING = CommentStatus.PENDING.name();

    /** 授权到期提醒窗口（天）：与 2D 运营统计的默认窗口一致 */
    private static final int LICENSE_WINDOW_DAYS = 7;

    private final ContentMapper contentMapper;

    private final CommentMapper commentMapper;

    private final MovieMapper movieMapper;

    /**
     * 取一次统计快照。
     *
     * @param tenantId 租户 ID（必填）
     * @return 统计快照
     */
    public VideoOverviewStats snapshot(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for video overview stats");
        }
        long draft = count(contentMapper.selectCount(new LambdaQueryWrapper<ContentEntity>()
                .eq(ContentEntity::getPublishState, PUBLISH_DRAFT)));
        long pendingReview = count(contentMapper.selectCount(new LambdaQueryWrapper<ContentEntity>()
                .eq(ContentEntity::getPublishState, PUBLISH_PENDING_REVIEW)));
        long published = count(contentMapper.selectCount(new LambdaQueryWrapper<ContentEntity>()
                .eq(ContentEntity::getPublishState, PUBLISH_PUBLISHED)));
        long offline = count(contentMapper.selectCount(new LambdaQueryWrapper<ContentEntity>()
                .eq(ContentEntity::getPublishState, PUBLISH_OFFLINE)));
        long takedown = count(contentMapper.selectCount(new LambdaQueryWrapper<ContentEntity>()
                .eq(ContentEntity::getCopyrightState, COPYRIGHT_TAKEDOWN)));
        long commentPending = count(commentMapper.selectCount(new LambdaQueryWrapper<ContentCommentEntity>()
                .eq(ContentCommentEntity::getStatus, COMMENT_PENDING)));
        long licenseExpiring = movieMapper.countLicenseExpiring(
                LocalDateTime.now().plusDays(LICENSE_WINDOW_DAYS));
        long totalPlay = contentMapper.sumPlayCount();
        return new VideoOverviewStats(draft, pendingReview, published, offline,
                commentPending, takedown, licenseExpiring, totalPlay);
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
