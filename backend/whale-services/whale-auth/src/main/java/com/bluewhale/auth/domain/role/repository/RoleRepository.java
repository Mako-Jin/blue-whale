package com.bluewhale.auth.domain.role.repository;

import com.bluewhale.auth.domain.role.aggregate.Role;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Role 聚合仓储端口（ADR-AUTH-004）：契约定义于领域层，由 {@code infrastructure} 适配实现。
 *
 * <p>本轮（AUTH-16）仅覆盖读路径；写路径与角色-权限关联维护留待 AUTH-17。</p>
 *
 * @author BlueWhale
 */
public interface RoleRepository {

    /**
     * 保存角色聚合（无 ID 时新增，有 ID 时按主键更新）
     *
     * @param role 角色聚合根
     */
    void save(Role role);

    /**
     * 按角色 ID 查询角色聚合
     *
     * @param id 角色 ID
     * @return 角色聚合；不存在时返回空
     */
    Optional<Role> findById(Long id);

    /**
     * 按角色编码查询角色聚合
     *
     * @param roleCode 角色编码
     * @return 角色聚合；不存在时返回空
     */
    Optional<Role> findByCode(String roleCode);

    /**
     * 按角色 ID 集合查询其中处于启用状态的角色（对应 {@code listRoleCodesByUserId} 对 {@code sys_role} 的读）
     *
     * @param ids 角色 ID 集合
     * @return 命中的启用角色聚合列表；{@code ids} 为空时返回空列表
     */
    List<Role> listEnabledByIds(Collection<Long> ids);

    /**
     * 分页查询角色（{@code keyword} 模糊匹配角色编码/名称，按 sortNo 升序、createTime 降序）
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 角色编码或名称模糊匹配关键字（可空）
     * @return 角色分页结果
     */
    RolePage page(long current, long size, String keyword);

    /**
     * 判断角色编码是否已存在（供角色编码唯一性不变量校验）
     *
     * @param roleCode 角色编码
     * @return 已存在返回 true，否则返回 false
     */
    boolean existsByCode(String roleCode);

    /**
     * 统计给定角色 ID 中实际存在的记录数（供分配角色时校验目标角色是否全部有效）
     *
     * @param ids 角色 ID 集合
     * @return 存在的记录数；{@code ids} 为空时返回 0
     */
    long countByIds(Collection<Long> ids);

    /**
     * 删除角色（逻辑删除角色并按物理删除清理角色-权限关联）
     *
     * @param id 角色 ID
     */
    void deleteById(Long id);

    /**
     * 全量覆盖角色-权限关联（先清空后重建）
     *
     * @param roleId 角色 ID
     * @param permissionIds 目标权限点 ID 列表
     */
    void replacePermissions(Long roleId, List<Long> permissionIds);

    /**
     * 查询角色已分配的权限点 ID 列表
     *
     * @param roleId 角色 ID
     * @return 权限点 ID 列表
     */
    List<Long> listPermissionIds(Long roleId);

    /**
     * 角色分页结果：领域层自有分页载体，避免向领域层泄漏 MyBatis 分页类型。
     *
     * @param records 当前页角色聚合列表
     * @param total 总记录数
     * @author BlueWhale
     */
    record RolePage(List<Role> records, long total) {
    }
}
