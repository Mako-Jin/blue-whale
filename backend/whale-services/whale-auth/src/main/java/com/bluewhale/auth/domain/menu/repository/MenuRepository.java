package com.bluewhale.auth.domain.menu.repository;

import com.bluewhale.auth.domain.menu.aggregate.Menu;

import java.util.List;
import java.util.Optional;

/**
 * Menu 聚合仓储端口（ADR-AUTH-004）：契约定义于领域层，由 {@code infrastructure} 适配实现。
 *
 * <p>本轮（AUTH-16）仅覆盖读路径；写路径留待 AUTH-17。</p>
 *
 * @author BlueWhale
 */
public interface MenuRepository {

    /**
     * 保存菜单聚合（无 ID 时新增，有 ID 时按主键更新）
     *
     * @param menu 菜单聚合根
     */
    void save(Menu menu);

    /**
     * 按菜单 ID 查询菜单聚合
     *
     * @param id 菜单 ID
     * @return 菜单聚合；不存在时返回空
     */
    Optional<Menu> findById(Long id);

    /**
     * 查询全部菜单（按 sort 升序、id 升序，对应菜单树 {@code tree} 的读）
     *
     * @return 全部菜单聚合列表
     */
    List<Menu> findAll();

    /**
     * 查询指定父菜单下的直接子菜单
     *
     * @param parentId 父菜单 ID
     * @return 该父菜单下的子菜单聚合列表
     */
    List<Menu> findAllByParentId(Long parentId);

    /**
     * 查询全部启用且可见的菜单（按 sort 升序、id 升序，对应权限过滤菜单树的读）
     *
     * @return 启用且可见的菜单聚合列表
     */
    List<Menu> findEnabledVisible();

    /**
     * 统计指定父菜单下的直接子菜单数量（供删除前树完整性校验）
     *
     * @param parentId 父菜单 ID
     * @return 子菜单数量
     */
    long countChildren(Long parentId);

    /**
     * 删除菜单（逻辑删除）
     *
     * @param id 菜单 ID
     */
    void deleteById(Long id);
}
