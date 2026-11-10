package com.bluewhale.video.application.service.impl;

import com.bluewhale.video.application.service.AdminStatAppService;
import com.bluewhale.video.application.view.StatOverviewView;
import com.bluewhale.video.domain.model.CollectionQuery;
import com.bluewhale.video.domain.model.CommentStatus;
import com.bluewhale.video.domain.model.ContentQuery;
import com.bluewhale.video.domain.model.ContentType;
import com.bluewhale.video.domain.model.CreatorQuery;
import com.bluewhale.video.domain.model.PublishState;
import com.bluewhale.video.domain.repository.CommentRepository;
import com.bluewhale.video.domain.repository.ContentRepository;
import com.bluewhale.video.domain.repository.CreatorRepository;
import com.bluewhale.video.domain.repository.MovieCollectionRepository;
import com.bluewhale.video.domain.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 视频运营统计实现。
 *
 * <p>内容维度分别统计总数、已发布数与三种内容类型数量；播放次数取
 * {@code vd_content.play_count} 汇总；授权到期以运营提醒窗口（默认 7 天）统计
 * 已发布电影的即将到期 / 已到期数量。后台统计无写操作，不额外缓存。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AdminStatAppServiceImpl implements AdminStatAppService {

    private final ContentRepository contentRepository;

    private final CommentRepository commentRepository;

    private final MovieRepository movieRepository;

    private final MovieCollectionRepository movieCollectionRepository;

    private final CreatorRepository creatorRepository;

    @Override
    public StatOverviewView overview(int expiringDays) {
        StatOverviewView view = new StatOverviewView();
        view.setTotalContent(contentRepository.count(typeQuery(null, null)));
        view.setPublishedContent(contentRepository.count(typeQuery(null, PublishState.PUBLISHED)));
        view.setShortVideoCount(contentRepository.count(typeQuery(ContentType.SHORT_VIDEO, null)));
        view.setVideoCount(contentRepository.count(typeQuery(ContentType.VIDEO, null)));
        view.setMovieCount(contentRepository.count(typeQuery(ContentType.MOVIE, null)));
        view.setTotalPlayCount(contentRepository.totalPlayCount());
        view.setPendingCommentCount(commentRepository.countByStatus(CommentStatus.PENDING));
        view.setExpiringLicenseCount(
                movieRepository.countLicenseExpiring(LocalDateTime.now(), expiringDays));
        view.setCollectionCount(movieCollectionRepository.count(new CollectionQuery()));
        view.setCreatorCount(creatorRepository.count(new CreatorQuery()));
        return view;
    }

    /**
     * 构造统计查询条件。
     *
     * @param type 内容类型，{@code null} 表示全部类型
     * @param publishState 发布状态，{@code null} 表示全部状态
     * @return 查询条件
     */
    private ContentQuery typeQuery(ContentType type, PublishState publishState) {
        ContentQuery query = new ContentQuery();
        query.setContentType(type);
        query.setPublishState(publishState);
        return query;
    }
}
