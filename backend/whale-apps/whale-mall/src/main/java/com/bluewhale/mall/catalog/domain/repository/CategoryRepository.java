package com.bluewhale.mall.catalog.domain.repository;

import com.bluewhale.mall.catalog.domain.model.Category;
import com.bluewhale.mall.catalog.domain.model.CategoryQuery;

import java.util.List;
import java.util.Optional;

/**
 * 类目仓储。
 *
 * @author BlueWhale
 */
public interface CategoryRepository {

    /**
     * 按条件查询类目（不分页：类目总量小，前端一次拿全树）。
     *
     * @param query 查询条件
     * @return 类目列表
     */
    List<Category> list(CategoryQuery query);

    /**
     * 按 ID 查询。
     *
     * @param id 类目 ID
     * @return 类目
     */
    Optional<Category> findById(Long id);

    /**
     * 查询下级类目。
     *
     * @param parentId 上级 ID
     * @return 下级类目列表
     */
    List<Category> listChildren(Long parentId);

    /**
     * 同级是否已存在同名类目（排除指定 ID）。
     *
     * @param parentId 上级 ID
     * @param categoryName 类目名称
     * @param excludeId 需排除的 ID，可为 null
     * @return 存在返回 true
     */
    boolean existsSiblingName(Long parentId, String categoryName, Long excludeId);

    /**
     * 保存（新建或更新）。
     *
     * @param category 类目
     * @return 类目 ID
     */
    Long save(Category category);

    /**
     * 逻辑删除。
     *
     * @param id 类目 ID
     */
    void remove(Long id);
}
