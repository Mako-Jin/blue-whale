package com.bluewhale.video.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.video.application.command.PlayProgressCommand;
import com.bluewhale.video.application.service.PlayAppService;
import com.bluewhale.video.application.support.ContentAssembler;
import com.bluewhale.video.application.support.PublicContentViewAssembler;
import com.bluewhale.video.application.view.PlayTicketBatchView;
import com.bluewhale.video.application.view.PlayTicketView;
import com.bluewhale.video.application.view.PublicContentView;
import com.bluewhale.video.application.view.VideoStreamView;
import com.bluewhale.video.application.view.WatchHistoryView;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.PlayTicket;
import com.bluewhale.video.domain.model.VideoStream;
import com.bluewhale.video.domain.model.WatchHistory;
import com.bluewhale.video.domain.repository.ContentFavoriteRepository;
import com.bluewhale.video.domain.repository.ContentLikeRepository;
import com.bluewhale.video.domain.repository.ContentRepository;
import com.bluewhale.video.domain.repository.ContentStreamRepository;
import com.bluewhale.video.domain.repository.PlayCountRepository;
import com.bluewhale.video.domain.repository.PlayTicketRepository;
import com.bluewhale.video.domain.repository.PlayableCacheRepository;
import com.bluewhale.video.domain.repository.WatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 播放与互动应用服务实现。
 *
 * <p>票据签发与每次流式读取都校验可见性、发布状态与版权状态；可播状态按内容缓存
 * 30 秒，下架 / 版权下架时由内容服务主动失效；播放次数先累加到 Redis，再由定时任务
 * 回刷 {@code vd_content.play_count}。公开视图与非流式接口均不暴露文件 ID。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PlayAppServiceImpl implements PlayAppService {

    /** 流式播放地址前缀 */
    private static final String STREAM_PATH = "/video/stream/";

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    /** 默认观看来源 */
    private static final String DEFAULT_SOURCE = "MY";

    /** 批量票据单次上限 */
    private static final int MAX_BATCH_SIZE = 20;

    /** HTTP 全量响应状态码 */
    private static final int STATUS_OK = 200;

    /** HTTP 分段响应状态码 */
    private static final int STATUS_PARTIAL = 206;

    private final ContentRepository contentRepository;

    private final ContentStreamRepository contentStreamRepository;

    private final PlayTicketRepository playTicketRepository;

    private final PlayableCacheRepository playableCacheRepository;

    private final PlayCountRepository playCountRepository;

    private final WatchHistoryRepository watchHistoryRepository;

    private final ContentLikeRepository contentLikeRepository;

    private final ContentFavoriteRepository contentFavoriteRepository;

    private final PublicContentViewAssembler contentViewAssembler;

    private final VideoProperties videoProperties;

    @Override
    public PlayTicketView issueTicket(Long contentId, Long userId, Long tenantId) {
        Content content = requirePlayable(contentId, userId);
        PlayTicket ticket = issue(content.getId(), userId, tenantId);
        return toTicketView(content, ticket, userId);
    }

    @Override
    public PlayTicketBatchView issueTickets(List<Long> contentIds, Long userId, Long tenantId) {
        List<Long> ids = distinctIds(contentIds);
        Map<Long, Content> contentMap = contentRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(Content::getId, Function.identity()));
        List<PlayTicketView> tickets = new ArrayList<>(ids.size());
        List<Long> rejected = new ArrayList<>();
        for (Long id : ids) {
            Content content = contentMap.get(id);
            if (content == null || !content.visibleTo(userId, false) || !content.playable()) {
                rejected.add(id);
                continue;
            }
            tickets.add(toTicketView(content, issue(content.getId(), userId, tenantId), userId));
        }
        PlayTicketBatchView view = new PlayTicketBatchView();
        view.setTickets(tickets);
        view.setRejectedContentIds(rejected);
        return view;
    }

    @Override
    public VideoStreamView openStream(Long contentId, String ticket, Long userId, Long tenantId,
                                      String rangeHeader, String ifRangeHeader) {
        requireMatchingTicket(contentId, ticket, userId, tenantId);
        Boolean cached = playableCacheRepository.find(contentId);
        if (cached != null && !cached) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_PLAYABLE);
        }
        Content content = requireVisible(contentId, userId);
        if (!content.playable() || content.getVideoFileId() == null || content.getVideoFileId() <= 0) {
            playableCacheRepository.save(contentId, false);
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_PLAYABLE);
        }
        playableCacheRepository.save(contentId, true);
        VideoStream stream = contentStreamRepository.open(
                content.getVideoFileId(), tenantId, rangeHeader, ifRangeHeader);
        if (stream.status() == STATUS_OK || stream.status() == STATUS_PARTIAL) {
            playCountRepository.increment(contentId);
        }
        return toStreamView(stream);
    }

    @Override
    public VideoStreamView openImage(Long fileId, Long tenantId, String rangeHeader, String ifRangeHeader) {
        VideoStream stream = contentStreamRepository.openImage(fileId, tenantId, rangeHeader, ifRangeHeader);
        return toStreamView(stream);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportProgress(PlayProgressCommand command, Long userId) {
        Content content = requirePlayable(command.getContentId(), userId);
        long progress = command.getProgressMs() == null ? 0L : command.getProgressMs();
        if (progress < 0 || (content.getDurationMs() != null && content.getDurationMs() > 0
                && progress > content.getDurationMs())) {
            throw VideoErrorCode.exception(VideoErrorCode.PLAY_PROGRESS_INVALID);
        }
        WatchHistory history = new WatchHistory();
        history.setUserId(userId);
        history.setContentId(content.getId());
        history.setProgressMs(progress);
        history.setDurationMs(content.getDurationMs());
        history.setSource(command.getSource() == null || command.getSource().isBlank()
                ? DEFAULT_SOURCE : command.getSource());
        history.setDevice(command.getDevice());
        history.setWatchedAt(LocalDateTime.now());
        watchHistoryRepository.saveProgress(history);
    }

    @Override
    public PageResult<WatchHistoryView> recentHistory(Long userId, long current, long size) {
        long page = Math.max(current, 1L);
        long pageSize = clampSize(size);
        long total = watchHistoryRepository.countByUser(userId);
        List<WatchHistory> histories = watchHistoryRepository.pageByUser(userId, (page - 1) * pageSize, pageSize);
        Map<Long, Content> contentMap = contentRepository.findByIds(
                        histories.stream().map(WatchHistory::getContentId).toList()).stream()
                .collect(Collectors.toMap(Content::getId, Function.identity()));
        List<WatchHistoryView> records = histories.stream()
                .map(history -> toHistoryView(history, contentMap.get(history.getContentId()), userId))
                .toList();
        return PageResult.of(records, total, page, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeHistory(Long contentId, Long userId) {
        watchHistoryRepository.delete(userId, contentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void like(Long contentId, Long userId) {
        requireVisible(contentId, userId);
        if (contentLikeRepository.add(userId, contentId)) {
            contentRepository.addLikeCount(contentId, 1L);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlike(Long contentId, Long userId) {
        if (contentLikeRepository.remove(userId, contentId)) {
            contentRepository.addLikeCount(contentId, -1L);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void favorite(Long contentId, Long userId) {
        requireVisible(contentId, userId);
        if (contentFavoriteRepository.add(userId, contentId)) {
            contentRepository.addFavoriteCount(contentId, 1L);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfavorite(Long contentId, Long userId) {
        if (contentFavoriteRepository.remove(userId, contentId)) {
            contentRepository.addFavoriteCount(contentId, -1L);
        }
    }

    @Override
    public PageResult<PublicContentView> myLikes(Long userId, long current, long size) {
        long page = Math.max(current, 1L);
        long pageSize = clampSize(size);
        List<Long> ids = contentLikeRepository.pageContentIds(userId, (page - 1) * pageSize, pageSize);
        return myPage(ids, contentLikeRepository.countByUser(userId), page, pageSize, userId);
    }

    @Override
    public PageResult<PublicContentView> myFavorites(Long userId, long current, long size) {
        long page = Math.max(current, 1L);
        long pageSize = clampSize(size);
        List<Long> ids = contentFavoriteRepository.pageContentIds(userId, (page - 1) * pageSize, pageSize);
        return myPage(ids, contentFavoriteRepository.countByUser(userId), page, pageSize, userId);
    }

    /**
     * 签发新的播放票据。
     *
     * @param contentId 内容 ID
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return 票据
     */
    private PlayTicket issue(Long contentId, Long userId, Long tenantId) {
        PlayTicket ticket = new PlayTicket();
        ticket.setTicket(UUID.randomUUID().toString().replace("-", ""));
        ticket.setTenantId(tenantId);
        ticket.setUserId(userId);
        ticket.setContentId(contentId);
        playTicketRepository.issue(ticket, videoProperties.getPlayTicketTtlSeconds());
        return ticket;
    }

    /**
     * 校验票据与当前请求三要素一致。
     *
     * @param contentId 内容 ID
     * @param ticket 票据令牌
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     */
    private void requireMatchingTicket(Long contentId, String ticket, Long userId, Long tenantId) {
        if (ticket == null || ticket.isBlank()) {
            throw VideoErrorCode.exception(VideoErrorCode.PLAY_TICKET_INVALID);
        }
        PlayTicket bound = playTicketRepository.find(ticket)
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.PLAY_TICKET_INVALID));
        if (!bound.matches(tenantId, userId, contentId)) {
            throw VideoErrorCode.exception(VideoErrorCode.PLAY_TICKET_INVALID);
        }
    }

    /**
     * 加载当前用户可见的内容，不可见按不存在处理。
     *
     * @param contentId 内容 ID
     * @param userId 用户 ID
     * @return 内容
     */
    private Content requireVisible(Long contentId, Long userId) {
        return contentRepository.findById(contentId)
                .filter(content -> content.visibleTo(userId, false))
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_FOUND));
    }

    /**
     * 加载当前用户可见且可播放的内容。
     *
     * @param contentId 内容 ID
     * @param userId 用户 ID
     * @return 内容
     */
    private Content requirePlayable(Long contentId, Long userId) {
        Content content = requireVisible(contentId, userId);
        if (!content.playable()) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_PLAYABLE);
        }
        return content;
    }

    /**
     * 去重并限制批量票据申请的内容 ID。
     *
     * @param contentIds 请求内容 ID 列表
     * @return 合法内容 ID 列表（最多 20 个）
     */
    private List<Long> distinctIds(List<Long> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : contentIds) {
            if (id != null && id > 0 && ids.size() < MAX_BATCH_SIZE) {
                ids.add(id);
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 组装播放票据视图。
     *
     * @param content 内容
     * @param ticket 票据
     * @param userId 用户 ID
     * @return 票据视图
     */
    private PlayTicketView toTicketView(Content content, PlayTicket ticket, Long userId) {
        PlayTicketView view = new PlayTicketView();
        view.setTicket(ticket.getTicket());
        view.setExpiresIn(videoProperties.getPlayTicketTtlSeconds());
        view.setRefreshIn(videoProperties.getPlayTicketRefreshSeconds());
        view.setContentId(content.getId());
        view.setContentType(content.getContentType() == null ? null : content.getContentType().name());
        view.setTitle(content.getTitle());
        view.setDurationMs(content.getDurationMs());
        view.setProgressMs(progressOf(userId, content.getId()));
        view.setStreamUrl(STREAM_PATH + content.getId() + "?ticket=" + ticket.getTicket());
        return view;
    }

    /**
     * 组装视频流视图。
     *
     * @param stream 领域流
     * @return 流视图
     */
    private VideoStreamView toStreamView(VideoStream stream) {
        VideoStreamView view = new VideoStreamView();
        view.setStatus(stream.status());
        view.setContentLength(stream.contentLength());
        view.setContentRange(stream.contentRange());
        view.setContentRangeTotal(stream.contentRangeTotal());
        view.setContentType(stream.contentType());
        view.setEtag(stream.etag());
        view.setBody(stream.body());
        return view;
    }

    /**
     * 组装最近观看视图（内容已删除时保留时间与进度，标题为空）。
     *
     * @param history 观看历史
     * @param content 内容，可能为 {@code null}
     * @param userId 用户 ID
     * @return 历史视图
     */
    private WatchHistoryView toHistoryView(WatchHistory history, Content content, Long userId) {
        WatchHistoryView view = new WatchHistoryView();
        view.setContentId(history.getContentId());
        view.setProgressMs(history.getProgressMs());
        view.setSource(history.getSource());
        view.setWatchedAt(history.getWatchedAt());
        if (content != null) {
            view.setContentType(content.getContentType() == null ? null : content.getContentType().name());
            view.setTitle(content.getTitle());
            view.setCoverUrl(ContentAssembler.coverUrl(content.getId(), content.getCoverFileId()));
            view.setDurationMs(content.getDurationMs() == null ? history.getDurationMs() : content.getDurationMs());
            view.setPlayable(content.visibleTo(userId, false) && content.playable());
        } else {
            view.setDurationMs(history.getDurationMs());
        }
        return view;
    }

    /**
     * 组装我的点赞 / 收藏分页。
     *
     * @param ids 内容 ID 列表
     * @param total 总数
     * @param page 页码
     * @param pageSize 每页条数
     * @param userId 用户 ID
     * @return 用户侧分页
     */
    private PageResult<PublicContentView> myPage(List<Long> ids, long total, long page, long pageSize, Long userId) {
        if (ids.isEmpty()) {
            return PageResult.of(List.of(), total, page, pageSize);
        }
        Map<Long, Content> contentMap = contentRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(Content::getId, Function.identity()));
        List<Content> visible = ids.stream().map(contentMap::get)
                .filter(content -> content != null && content.visibleTo(userId, false))
                .toList();
        return PageResult.of(contentViewAssembler.assemble(visible), total, page, pageSize);
    }

    /**
     * 查询观看进度。
     *
     * @param userId 用户 ID
     * @param contentId 内容 ID
     * @return 进度毫秒；未观看返回 0
     */
    private Long progressOf(Long userId, Long contentId) {
        return watchHistoryRepository.find(userId, contentId).map(WatchHistory::getProgressMs).orElse(0L);
    }

    /**
     * 限制分页大小。
     *
     * @param size 请求条数
     * @return 归一化条数
     */
    private long clampSize(long size) {
        return size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }
}
