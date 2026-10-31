package com.bluewhale.music.domain.repository;

import com.bluewhale.music.domain.model.AppealStatus;
import com.bluewhale.music.domain.model.SongAppeal;

import java.util.List;
import java.util.Optional;

/**
 * 版权下架申诉仓储端口。
 *
 * @author BlueWhale
 */
public interface SongAppealRepository {

    /**
     * 按主键查询申诉。
     *
     * @param id 申诉 ID
     * @return 申诉；不存在返回空
     */
    Optional<SongAppeal> findById(Long id);

    /**
     * 判断歌曲是否已存在待复核申诉（同一作品同一时刻只允许一条）。
     *
     * @param songId 歌曲 ID
     * @return 存在待复核申诉返回 {@code true}
     */
    boolean existsPending(Long songId);

    /**
     * 新增申诉。
     *
     * @param appeal 申诉
     */
    void insert(SongAppeal appeal);

    /**
     * 更新申诉状态与复核信息。
     *
     * @param appeal 申诉
     */
    void update(SongAppeal appeal);

    /**
     * 运营复核分页（按创建时间倒序，可按歌曲 / 状态过滤）。
     *
     * @param songId 歌曲 ID；{@code null} 表示不过滤
     * @param status 申诉状态；{@code null} 表示不过滤
     * @param offset 偏移量
     * @param limit 条数
     * @return 申诉列表
     */
    List<SongAppeal> pageAudit(Long songId, AppealStatus status, long offset, long limit);

    /**
     * 统计复核分页总数。
     *
     * @param songId 歌曲 ID；{@code null} 表示不过滤
     * @param status 申诉状态；{@code null} 表示不过滤
     * @return 总数
     */
    long countAudit(Long songId, AppealStatus status);
}
