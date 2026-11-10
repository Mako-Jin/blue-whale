package com.bluewhale.music.domain.repository;

import com.bluewhale.music.domain.model.CommentStatus;
import com.bluewhale.music.domain.model.SongComment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 歌曲评论仓储端口。
 *
 * @author BlueWhale
 */
public interface CommentRepository {

    /**
     * 按主键查询评论。
     *
     * @param id 评论 ID
     * @return 评论；不存在返回空
     */
    Optional<SongComment> findById(Long id);

    /**
     * 新增评论。
     *
     * @param comment 评论
     */
    void insert(SongComment comment);

    /**
     * 更新评论状态（审核通过 / 隐藏）。
     *
     * @param comment 评论
     */
    void update(SongComment comment);

    /**
     * 分页查询歌曲下的可见顶级评论（按创建时间倒序）。
     *
     * @param songId 歌曲 ID
     * @param offset 偏移量
     * @param limit 条数
     * @return 顶级评论列表
     */
    List<SongComment> pageTopLevel(Long songId, long offset, long limit);

    /**
     * 统计歌曲下的可见顶级评论数。
     *
     * @param songId 歌曲 ID
     * @return 总数
     */
    long countTopLevel(Long songId);

    /**
     * 批量查询给定父评论下的可见回复（按创建时间正序）。
     *
     * @param songId 歌曲 ID
     * @param parentIds 父评论 ID 集合；为空返回空列表
     * @return 回复列表
     */
    List<SongComment> findReplies(Long songId, Collection<Long> parentIds);

    /**
     * 运营审核分页（按创建时间倒序，可按状态 / 歌曲 / 关键词过滤）。
     *
     * @param songId 歌曲 ID；{@code null} 表示不过滤
     * @param status 评论状态；{@code null} 表示不过滤
     * @param keyword 正文关键词；{@code null} 或空白表示不过滤
     * @param offset 偏移量
     * @param limit 条数
     * @return 评论列表
     */
    List<SongComment> pageAudit(Long songId, CommentStatus status, String keyword, long offset, long limit);

    /**
     * 统计审核分页总数。
     *
     * @param songId 歌曲 ID；{@code null} 表示不过滤
     * @param status 评论状态；{@code null} 表示不过滤
     * @param keyword 正文关键词；{@code null} 或空白表示不过滤
     * @return 总数
     */
    long countAudit(Long songId, CommentStatus status, String keyword);
}
