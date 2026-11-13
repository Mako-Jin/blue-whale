package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.aggregate.RoleConstraintAggregate;

import java.util.List;
import java.util.Optional;

/**
 * 角色约束仓储端口（权限中心扩展 M3）：契约定义于领域层，由 infrastructure 适配实现。
 *
 * @author BlueWhale
 */
public interface RoleConstraintRepository {

    /**
     * 保存约束聚合（无 ID 时新增，有 ID 时按主键更新）
     *
     * @param constraint 约束聚合
     * @return 回填主键后的聚合
     */
    RoleConstraintAggregate save(RoleConstraintAggregate constraint);

    /**
     * 按主键查询约束
     *
     * @param id 主键
     * @return 约束聚合；不存在时返回空
     */
    Optional<RoleConstraintAggregate> findById(Long id);

    /**
     * 判断约束编码在租户内是否已存在（排除指定约束自身）
     *
     * <p>并发说明：先查后插的友好预检，唯一性由 DB 约束 {@code uk_constraint_code} 兜底。</p>
     *
     * @param tenantId 租户 ID
     * @param constraintCode 约束编码
     * @param excludeId 排除的约束 ID（更新场景传自身主键，新建传 {@code null}）
     * @return 已存在返回 true
     */
    boolean existsByCode(Long tenantId, String constraintCode, Long excludeId);

    /**
     * 查询租户内全部启用约束（分配角色时校验用）
     *
     * @param tenantId 租户 ID
     * @return 启用约束列表
     */
    List<RoleConstraintAggregate> listEnabledByTenant(Long tenantId);

    /**
     * 分页查询约束（keyword 模糊匹配编码/名称，按 createTime 降序）
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 关键字（可空）
     * @param constraintType 类型过滤（可空）
     * @return 约束分页结果
     */
    ConstraintPage page(long current, long size, String keyword, String constraintType);

    /**
     * 删除约束（逻辑删除）
     *
     * @param id 约束 ID
     */
    void deleteById(Long id);

    /**
     * 约束分页结果：领域层自有分页载体
     *
     * @param records 当前页约束聚合列表
     * @param total 总记录数
     * @author BlueWhale
     */
    record ConstraintPage(List<RoleConstraintAggregate> records, long total) {
    }
}
