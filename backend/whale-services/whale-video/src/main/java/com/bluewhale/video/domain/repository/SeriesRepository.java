package com.bluewhale.video.domain.repository;

import com.bluewhale.video.domain.model.Series;
import com.bluewhale.video.domain.model.SeriesQuery;

import java.util.List;
import java.util.Optional;

/**
 * 系列仓储端口。
 *
 * @author BlueWhale
 */
public interface SeriesRepository {

    /**
     * 按主键查询系列。
     *
     * @param id 系列 ID
     * @return 系列；不存在返回空
     */
    Optional<Series> findById(Long id);

    /**
     * 新增系列。
     *
     * @param series 系列
     */
    void insert(Series series);

    /**
     * 更新系列。
     *
     * @param series 系列
     */
    void update(Series series);

    /**
     * 逻辑删除系列。
     *
     * @param id 系列 ID
     */
    void delete(Long id);

    /**
     * 分页查询系列（按运营排序升序）。
     *
     * @param query 查询条件
     * @return 当前页系列
     */
    List<Series> page(SeriesQuery query);

    /**
     * 统计符合条件的系列总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(SeriesQuery query);

    /**
     * 刷新系列已发布集数。
     *
     * @param seriesId 系列 ID
     * @param episodeCount 已发布集数
     */
    void updateEpisodeCount(Long seriesId, int episodeCount);
}
