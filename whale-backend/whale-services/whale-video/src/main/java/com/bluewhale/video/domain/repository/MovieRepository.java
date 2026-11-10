package com.bluewhale.video.domain.repository;

import com.bluewhale.video.domain.model.Movie;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 电影元数据仓储端口（以内容 ID 为标识，1:1 扩展 {@code vd_content}）。
 *
 * @author BlueWhale
 */
public interface MovieRepository {

    /**
     * 按内容 ID 查询电影元数据。
     *
     * @param contentId 内容 ID
     * @return 电影元数据；不存在返回空
     */
    Optional<Movie> findByContentId(Long contentId);

    /**
     * 按内容 ID 批量查询电影元数据。
     *
     * @param contentIds 内容 ID 集合
     * @return 内容 ID → 电影元数据；集合为空返回空映射
     */
    Map<Long, Movie> findByContentIds(Collection<Long> contentIds);

    /**
     * 新增电影元数据。
     *
     * @param movie 电影元数据
     */
    void insert(Movie movie);

    /**
     * 更新电影元数据（按内容 ID）。
     *
     * @param movie 电影元数据
     */
    void update(Movie movie);

    /**
     * 删除电影元数据。
     *
     * @param contentId 内容 ID
     */
    void delete(Long contentId);

    /**
     * 查询授权已到期且内容处于已发布状态的内容 ID（定时下线用）。
     *
     * @param now 当前时间
     * @param limit 单批上限
     * @return 内容 ID 列表
     */
    List<Long> listExpiredLicenseContentIds(LocalDateTime now, int limit);

    /**
     * 统计授权将在指定天数内到期（含已到期）的已发布电影数量。
     *
     * @param now 当前时间
     * @param days 提醒窗口天数
     * @return 数量
     */
    long countLicenseExpiring(LocalDateTime now, int days);
}
