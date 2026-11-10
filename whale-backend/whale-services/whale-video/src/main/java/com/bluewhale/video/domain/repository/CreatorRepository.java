package com.bluewhale.video.domain.repository;

import com.bluewhale.video.domain.model.Creator;
import com.bluewhale.video.domain.model.CreatorQuery;

import java.util.List;
import java.util.Optional;

/**
 * 创作者仓储端口。
 *
 * @author BlueWhale
 */
public interface CreatorRepository {

    /**
     * 按用户查询创作者资料。
     *
     * @param userId 用户 ID
     * @return 创作者；未登记返回空
     */
    Optional<Creator> findByUserId(Long userId);

    /**
     * 按主键查询创作者。
     *
     * @param id 创作者 ID
     * @return 创作者；不存在返回空
     */
    Optional<Creator> findById(Long id);

    /**
     * 新增创作者。
     *
     * @param creator 创作者
     */
    void insert(Creator creator);

    /**
     * 更新创作者。
     *
     * @param creator 创作者
     */
    void update(Creator creator);

    /**
     * 运营侧创作者分页（按主键倒序）。
     *
     * @param query 查询条件
     * @return 当前页创作者
     */
    List<Creator> page(CreatorQuery query);

    /**
     * 统计符合条件的创作者总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(CreatorQuery query);
}
