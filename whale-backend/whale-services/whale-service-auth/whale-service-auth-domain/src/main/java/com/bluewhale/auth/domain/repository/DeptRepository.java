package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.aggregate.Dept;

import java.util.List;
import java.util.Optional;

/**
 * Dept 聚合仓储端口（ADR-AUTH-004）：契约定义于领域层，由 {@code infrastructure} 适配实现。
 *
 * <p>本轮（AUTH-16）仅覆盖读路径；写路径留待 AUTH-17。</p>
 *
 * @author BlueWhale
 */
public interface DeptRepository {

    /**
     * 保存部门聚合（无 ID 时新增，有 ID 时按主键更新）
     *
     * @param dept 部门聚合根
     * @return 回填主键后的部门聚合根
     */
    Dept save(Dept dept);

    /**
     * 按部门 ID 查询部门聚合（当前租户内查询，跨租户视为不存在）
     *
     * @param id 部门 ID
     * @return 部门聚合；不存在时返回空
     */
    Optional<Dept> findById(Long id);

    /**
     * 查询全部部门（当前租户内，按 sort 升序、id 升序，对应部门树 {@code tree} 的读）
     *
     * @return 全部部门聚合列表
     */
    List<Dept> findAll();

    /**
     * 查询指定父部门下的直接子部门
     *
     * @param parentId 父部门 ID
     * @return 该父部门下的子部门聚合列表
     */
    List<Dept> findAllByParentId(Long parentId);

    /**
     * 统计指定父部门下的直接子部门数量（供删除前树完整性校验）
     *
     * @param parentId 父部门 ID
     * @return 子部门数量
     */
    long countChildren(Long parentId);

    /**
     * 删除部门（逻辑删除）
     *
     * @param id 部门 ID
     */
    void deleteById(Long id);
}
