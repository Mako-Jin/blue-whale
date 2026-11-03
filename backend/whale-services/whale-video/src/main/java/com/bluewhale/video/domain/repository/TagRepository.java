package com.bluewhale.video.domain.repository;

import com.bluewhale.video.domain.model.Tag;

import java.util.List;
import java.util.Optional;

/**
 * 标签仓储端口。
 *
 * @author BlueWhale
 */
public interface TagRepository {

    /**
     * 查询全部标签（按排序值升序）。
     *
     * @return 标签列表
     */
    List<Tag> listAll();

    /**
     * 按主键查询标签。
     *
     * @param id 标签 ID
     * @return 标签；不存在返回空
     */
    Optional<Tag> findById(Long id);

    /**
     * 批量按主键查询标签。
     *
     * @param ids 标签 ID 集合
     * @return 标签列表
     */
    List<Tag> findByIds(List<Long> ids);
}
