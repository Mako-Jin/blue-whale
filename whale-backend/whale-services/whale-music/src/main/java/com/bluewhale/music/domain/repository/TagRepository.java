package com.bluewhale.music.domain.repository;

import com.bluewhale.music.domain.model.Tag;
import com.bluewhale.music.domain.model.TagType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 标签仓储端口。
 *
 * @author BlueWhale
 */
public interface TagRepository {

    /**
     * 按主键查询标签。
     *
     * @param id 标签 ID
     * @return 标签；不存在返回空
     */
    Optional<Tag> findById(Long id);

    /**
     * 判断租户内标签名称与类型组合是否已存在。
     *
     * @param name 标签名称
     * @param tagType 标签类型
     * @param excludeId 排除的标签 ID（更新时传当前 ID），新建传 {@code null}
     * @return 已存在返回 {@code true}
     */
    boolean exists(String name, TagType tagType, Long excludeId);

    /**
     * 新增标签。
     *
     * @param tag 标签
     */
    void insert(Tag tag);

    /**
     * 更新标签。
     *
     * @param tag 标签
     */
    void update(Tag tag);

    /**
     * 逻辑删除标签。
     *
     * @param id 标签 ID
     */
    void delete(Long id);

    /**
     * 查询全部标签（按类型与排序号升序）。
     *
     * @return 标签列表
     */
    List<Tag> list();

    /**
     * 按 ID 集合批量查询标签。
     *
     * @param ids 标签 ID 集合
     * @return 标签列表；集合为空返回空列表
     */
    List<Tag> findByIds(Collection<Long> ids);
}
