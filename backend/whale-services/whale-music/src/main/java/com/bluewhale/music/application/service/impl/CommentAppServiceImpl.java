package com.bluewhale.music.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.music.application.command.CommentAuditQueryCommand;
import com.bluewhale.music.application.command.CommentSubmitCommand;
import com.bluewhale.music.application.service.CommentAppService;
import com.bluewhale.music.application.view.CommentView;
import com.bluewhale.music.config.MusicProperties;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.CommentStatus;
import com.bluewhale.music.domain.model.Song;
import com.bluewhale.music.domain.model.SongComment;
import com.bluewhale.music.domain.repository.CommentNotificationPort;
import com.bluewhale.music.domain.repository.CommentRepository;
import com.bluewhale.music.domain.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 歌曲评论应用服务实现。
 *
 * <p>用户侧仅能查看可见评论；回复目标必须同歌曲且可见。审核通过与隐藏操作幂等，
 * 评论计数随可见性流转增减。评论回复与审核结果通知为旁路能力，
 * 在事务提交后发送，失败仅告警。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CommentAppServiceImpl implements CommentAppService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    private final CommentRepository commentRepository;

    private final SongRepository songRepository;

    private final MusicProperties musicProperties;

    private final CommentNotificationPort commentNotificationPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentView submit(Long songId, CommentSubmitCommand command, Long userId) {
        Song song = requireVisibleSong(songId, userId);
        SongComment parent = resolveParent(command.getParentId());
        SongComment comment = SongComment.create(songId, userId, parent, command.getContent(),
                musicProperties.isCommentPreAudit());
        commentRepository.insert(comment);
        if (comment.visible()) {
            songRepository.addCommentCount(songId, 1L);
        }
        if (parent != null && !parent.getUserId().equals(userId)) {
            afterCommit(() -> commentNotificationPort.sendSafely(TenantContext.get(),
                    CommentNotificationPort.BIZ_TYPE_REPLY, String.valueOf(comment.getId()),
                    "你的评论收到回复", "你在《 " + safeTitle(song) + " 》下的评论收到了新回复，点击查看。",
                    parent.getUserId()));
        }
        return toView(comment);
    }

    @Override
    public PageResult<CommentView> pageBySong(Long songId, long current, long size, Long viewerId) {
        requireVisibleSong(songId, viewerId);
        long pageNo = clampCurrent(current);
        long pageSize = clampSize(size);
        List<SongComment> topLevel = commentRepository.pageTopLevel(songId, (pageNo - 1) * pageSize, pageSize);
        List<Long> parentIds = topLevel.stream().map(SongComment::getId).toList();
        Map<Long, List<CommentView>> replyMap = commentRepository.findReplies(songId, parentIds).stream()
                .map(this::toView)
                .collect(Collectors.groupingBy(CommentView::getParentId));
        List<CommentView> records = new ArrayList<>(topLevel.size());
        for (SongComment comment : topLevel) {
            CommentView view = toView(comment);
            view.setReplies(replyMap.getOrDefault(comment.getId(), List.of()));
            records.add(view);
        }
        return PageResult.of(records, commentRepository.countTopLevel(songId), pageNo, pageSize);
    }

    @Override
    public PageResult<CommentView> pageAudit(CommentAuditQueryCommand command) {
        long pageNo = clampCurrent(command.getCurrent());
        long pageSize = clampSize(command.getSize());
        CommentStatus status = parseStatus(command.getStatus());
        List<SongComment> comments = commentRepository.pageAudit(command.getSongId(), status,
                command.getKeyword(), (pageNo - 1) * pageSize, pageSize);
        Map<Long, Song> songMap = songRepository.findByIds(
                comments.stream().map(SongComment::getSongId).distinct().toList()).stream()
                .collect(Collectors.toMap(Song::getId, Function.identity()));
        List<CommentView> records = comments.stream().map(comment -> {
            CommentView view = toView(comment);
            Song song = songMap.get(comment.getSongId());
            view.setSongTitle(song == null ? null : song.getTitle());
            return view;
        }).toList();
        return PageResult.of(records,
                commentRepository.countAudit(command.getSongId(), status, command.getKeyword()),
                pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentView approve(Long id) {
        SongComment comment = require(id);
        if (comment.approve()) {
            commentRepository.update(comment);
            songRepository.addCommentCount(comment.getSongId(), 1L);
            notifyModeration(comment, "已通过审核并对外展示");
        }
        return toView(comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentView hide(Long id) {
        SongComment comment = require(id);
        if (comment.hide()) {
            commentRepository.update(comment);
            songRepository.addCommentCount(comment.getSongId(), -1L);
            notifyModeration(comment, "已被隐藏，不再对外展示");
        }
        return toView(comment);
    }

    /**
     * 加载评论，不存在时抛出业务异常。
     *
     * @param id 评论 ID
     * @return 评论
     */
    private SongComment require(Long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.COMMENT_NOT_FOUND));
    }

    /**
     * 加载用户可见的歌曲（评论仅允许针对可见且已发布的歌曲）。
     *
     * @param songId 歌曲 ID
     * @param viewerId 查看者用户 ID
     * @return 歌曲
     */
    private Song requireVisibleSong(Long songId, Long viewerId) {
        return songRepository.findById(songId)
                .filter(song -> song.visibleTo(viewerId, false) && song.playable())
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.SONG_NOT_FOUND));
    }

    /**
     * 解析回复的父评论：顶级评论返回 {@code null}；父评论不存在按参数非法处理。
     *
     * @param parentId 父评论 ID
     * @return 父评论或 {@code null}
     */
    private SongComment resolveParent(Long parentId) {
        if (parentId == null || parentId == SongComment.TOP_LEVEL_PARENT_ID) {
            return null;
        }
        return commentRepository.findById(parentId)
                .orElseThrow(() -> MusicErrorCode.exception(MusicErrorCode.COMMENT_PARENT_INVALID));
    }

    /**
     * 解析审核状态过滤参数：空或非法值表示不过滤。
     *
     * @param status 状态字符串
     * @return 评论状态或 {@code null}
     */
    private CommentStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CommentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * 事务提交后发送审核结果通知。
     *
     * @param comment 评论
     * @param result 审核结果描述
     */
    private void notifyModeration(SongComment comment, String result) {
        afterCommit(() -> commentNotificationPort.sendSafely(TenantContext.get(),
                CommentNotificationPort.BIZ_TYPE_MODERATION, String.valueOf(comment.getId()),
                "评论审核结果通知", "你的评论" + result + "。", comment.getUserId()));
    }

    /**
     * 注册事务提交后回调；无事务环境直接执行。
     *
     * @param action 回调动作
     */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /**
     * 安全获取歌曲标题（通知文案使用，不记录评论正文）。
     *
     * @param song 歌曲
     * @return 歌曲标题
     */
    private String safeTitle(Song song) {
        return song.getTitle() == null ? "" : song.getTitle();
    }

    /**
     * 评论领域模型转视图。
     *
     * @param comment 评论
     * @return 评论视图
     */
    private CommentView toView(SongComment comment) {
        CommentView view = new CommentView();
        BeanUtils.copyProperties(comment, view);
        view.setStatus(comment.getStatus().name());
        return view;
    }

    /**
     * 限制页码。
     *
     * @param current 请求页码
     * @return 归一化页码
     */
    private long clampCurrent(long current) {
        return Math.max(current, 1L);
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
