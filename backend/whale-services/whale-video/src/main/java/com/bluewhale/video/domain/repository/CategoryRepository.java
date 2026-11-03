package com.bluewhale.video.domain.repository;

import com.bluewhale.video.domain.model.Category;

import java.util.List;
import java.util.Optional;

/**
 * 分类仓储端口。
 *
 * @author BlueWhale
 */
public interface CategoryRepository {

    /**
     * 查询全部启用分类（按排序值升序）。
     *
     * @return 分类列表
     */
    List<Category> listEnabled();

    /**
     * 按主键查询分类。
     *
     * @param id 分类 ID
     * @return 分类；不存在返回空
     */
    Optional<Category> findById(Long id);

    /**
     * 新增分类。
     *
     * @param category 分类
     */
    void insert(Category category);

    /**
     * 更新分类。
     *
     * @param category 分类
     */
    void update(Category category);
}
