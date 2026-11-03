package com.bluewhale.video.domain.repository;

import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.ContentQuery;
import com.bluewhale.video.domain.model.FeedQuery;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内容仓储端口（含内容-标签关系与计数原子更新）。
 *
 * @author BlueWhale
 */
public interface ContentRepository {

    /**
     * 按主键查询内容。
     *
     * @param id 内容 ID
     * @return 内容；不存在返回空
     */
    Optional<Content> findById(Long id);

    /**
     * 按主键批量查询内容。
     *
     * @param ids 内容 ID 集合
     * @return 内容列表；集合为空返回空列表
     */
    List<Content> findByIds(Collection<Long> ids);

    /**
     * 新增内容。
     *
     * @param content 内容
     */
    void insert(Content content);

    /**
     * 更新内容。
     *
     * @param content 内容
     */
    void update(Content content);

    /**
     * 逻辑删除内容。
     *
     * @param id 内容 ID
     */
    void delete(Long id);

    /**
     * 分页查询内容。
     *
     * @param query 查询条件
     * @return 当前页内容
     */
    List<Content> page(ContentQuery query);

    /**
     * 统计符合条件的内容总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(ContentQuery query);

    /**
     * 键集查询 feed 内容（仅游标之后的数据，按最新或热度排序）。
     *
     * @param query feed 查询条件
     * @return 内容列表；无匹配返回空列表
     */
    List<Content> feed(FeedQuery query);

    /**
     * 原子调整点赞数（+1 / -1，结果不小于 0）。
     *
     * @param contentId 内容 ID
     * @param delta 增量
     */
    void addLikeCount(Long contentId, long delta);

    /**
     * 原子调整收藏数（+1 / -1，结果不小于 0）。
     *
     * @param contentId 内容 ID
     * @param delta 增量
     */
    void addFavoriteCount(Long contentId, long delta);

    /**
     * 原子调整评论数（仅统计可见评论）。
     *
     * @param contentId 内容 ID
     * @param delta 增量
     */
    void addCommentCount(Long contentId, long delta);

    /**
     * 原子累加播放次数（Redis 增量定时回刷）。
     *
     * @param contentId 内容 ID
     * @param delta 增量
     */
    void addPlayCount(Long contentId, long delta);

    /**
     * 覆盖式保存内容标签关系。
     *
     * @param contentId 内容 ID
     * @param tagIds 标签 ID 集合；为空表示清空
     */
    void replaceTags(Long contentId, Collection<Long> tagIds);

    /**
     * 查询内容的标签 ID。
     *
     * @param contentId 内容 ID
     * @return 标签 ID 列表
     */
    List<Long> findTagIds(Long contentId);

    /**
     * 批量查询内容标签关系。
     *
     * @param contentIds 内容 ID 集合
     * @return 内容 ID → 标签 ID 列表；集合为空返回空映射
     */
    Map<Long, List<Long>> findTagIdsByContentIds(Collection<Long> contentIds);

    /**
     * 判断系列内集数是否已被占用。
     *
     * @param seriesId 系列 ID
     * @param episodeNo 集数
     * @param excludeContentId 需排除的内容 ID（更新场景），可为 {@code null}
     * @return 已存在返回 {@code true}
     */
    boolean existsSeriesEpisode(Long seriesId, Integer episodeNo, Long excludeContentId);

    /**
     * 统计系列内已发布且公开的集数。
     *
     * @param seriesId 系列 ID
     * @return 集数
     */
    int countPublishedEpisodes(Long seriesId);

    /**
     * 查询系列内已发布且公开的集数（按集数升序）。
     *
     * @param seriesId 系列 ID
     * @return 集数列表；无集数返回空列表
     */
    List<Content> listPublishedEpisodes(Long seriesId);

    /**
     * 汇总全部未删除内容的播放次数（运营统计用）。
     *
     * @return 播放次数总和
     */
    long totalPlayCount();
}
