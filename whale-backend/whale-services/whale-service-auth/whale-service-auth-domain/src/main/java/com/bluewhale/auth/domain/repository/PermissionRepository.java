package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.aggregate.PermissionAggregate;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Permission 聚合仓储端口（ADR-AUTH-004）：契约定义于领域层，由 {@code infrastructure} 适配实现。
 *
 * <p>本轮（AUTH-16）仅覆盖读路径；写路径留待 AUTH-17。</p>
 *
 * @author BlueWhale
 */
public interface PermissionRepository {

    /**
     * 保存权限点聚合（无 ID 时新增，有 ID 时按主键更新）
     *
     * @param permission 权限点聚合根
     */
    void save(PermissionAggregate permission);

    /**
     * 按权限点 ID 查询权限点聚合
     *
     * @param id 权限点 ID
     * @return 权限点聚合；不存在时返回空
     */
    Optional<PermissionAggregate> findById(Long id);

    /**
     * 查询全部启用权限点（对应 {@code listAllCodes} 对 {@code sys_permission} 的读）
     *
     * @return 启用权限点聚合列表
     */
    List<PermissionAggregate> findEnabledAll();

    /**
     * 分页查询权限点（{@code keyword} 模糊匹配权限编码/名称，{@code permType} 精确过滤）
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 权限编码或名称模糊匹配关键字（可空）
     * @param permType 权限类型精确过滤条件（可空）
     * @return 权限点分页结果
     */
    PermissionPage page(long current, long size, String keyword, String permType);

    /**
     * 判断权限码是否已存在（供权限码唯一性不变量校验）
     *
     * @param permCode 权限码
     * @param excludeId 需要排除的权限点 ID（更新场景传自身 ID；新建场景传 {@code null}）
     * @return 已存在返回 true，否则返回 false
     */
    boolean existsByPermCode(String permCode, Long excludeId);

    /**
     * 统计给定权限点 ID 中实际存在的记录数（供分配权限时校验目标权限点是否全部有效）
     *
     * @param ids 权限点 ID 集合
     * @return 存在的记录数；{@code ids} 为空时返回 0
     */
    long countByIds(Collection<Long> ids);

    /**
     * 统计指定父权限点下的直接子节点数量（供删除前树完整性校验）
     *
     * @param parentId 父权限点 ID
     * @return 子节点数量
     */
    long countChildren(Long parentId);

    /**
     * 删除权限点（逻辑删除）
     *
     * @param id 权限点 ID
     */
    void deleteById(Long id);

    /**
     * 权限点分页结果：领域层自有分页载体，避免向领域层泄漏 MyBatis 分页类型。
     *
     * @param records 当前页权限点聚合列表
     * @param total 总记录数
     * @author BlueWhale
     */
    record PermissionPage(List<PermissionAggregate> records, long total) {
    }
}
