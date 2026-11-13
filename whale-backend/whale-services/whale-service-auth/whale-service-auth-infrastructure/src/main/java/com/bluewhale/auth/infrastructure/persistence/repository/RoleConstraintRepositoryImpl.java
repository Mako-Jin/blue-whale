package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.auth.domain.aggregate.RoleConstraintAggregate;
import com.bluewhale.auth.domain.repository.RoleConstraintRepository;
import com.bluewhale.auth.infrastructure.persistence.assembler.RoleConstraintPersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysRoleConstraintMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysRoleConstraintPo;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 角色约束仓储 MyBatis-Plus 实现（权限中心扩展 M3）。
 *
 * <p>逻辑删除由 {@code deleted} 列承担（MyBatis-Plus 全局配置）；{@code roleIds}
 * 列 JSON 互转委托装配器。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class RoleConstraintRepositoryImpl implements RoleConstraintRepository {

    private final SysRoleConstraintMapper constraintMapper;

    /**
     * 保存约束聚合：无主键新增（回填 ID），有主键按 ID 更新
     *
     * @param constraint 约束聚合
     * @return 回填主键后的聚合
     */
    @Override
    public RoleConstraintAggregate save(RoleConstraintAggregate constraint) {
        SysRoleConstraintPo po = RoleConstraintPersistenceAssembler.toPo(constraint);
        if (po.getId() == null) {
            constraintMapper.insert(po);
        } else {
            constraintMapper.updateById(po);
        }
        return RoleConstraintPersistenceAssembler.toDomain(po);
    }

    /**
     * 按主键查询约束
     *
     * @param id 主键
     * @return 约束聚合；不存在时返回空
     */
    @Override
    public Optional<RoleConstraintAggregate> findById(Long id) {
        SysRoleConstraintPo po = constraintMapper.selectOne(new LambdaQueryWrapper<SysRoleConstraintPo>()
                .eq(SysRoleConstraintPo::getId, id)
                .eq(SysRoleConstraintPo::getTenantId, TenantContext.requireId()));
        return Optional.ofNullable(po).map(RoleConstraintPersistenceAssembler::toDomain);
    }

    /**
     * 判断约束编码在租户内是否已存在（排除指定约束自身）
     *
     * @param tenantId 租户 ID
     * @param constraintCode 约束编码
     * @param excludeId 排除的约束 ID（新建传 {@code null}）
     * @return 已存在返回 true
     */
    @Override
    public boolean existsByCode(Long tenantId, String constraintCode, Long excludeId) {
        LambdaQueryWrapper<SysRoleConstraintPo> wrapper = new LambdaQueryWrapper<SysRoleConstraintPo>()
                .eq(SysRoleConstraintPo::getTenantId, tenantId)
                .eq(SysRoleConstraintPo::getConstraintCode, constraintCode);
        if (excludeId != null) {
            wrapper.ne(SysRoleConstraintPo::getId, excludeId);
        }
        Long count = constraintMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /**
     * 查询租户内全部启用约束
     *
     * @param tenantId 租户 ID
     * @return 启用约束列表
     */
    @Override
    public List<RoleConstraintAggregate> listEnabledByTenant(Long tenantId) {
        return constraintMapper.selectList(new LambdaQueryWrapper<SysRoleConstraintPo>()
                        .eq(SysRoleConstraintPo::getTenantId, tenantId)
                        .eq(SysRoleConstraintPo::getStatus, RoleConstraintAggregate.STATUS_ENABLED)
                        .orderByDesc(SysRoleConstraintPo::getCreateTime))
                .stream().map(RoleConstraintPersistenceAssembler::toDomain).toList();
    }

    /**
     * 分页查询约束（keyword 模糊匹配编码/名称，按 createTime 降序）
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 关键字（可空）
     * @param constraintType 类型过滤（可空）
     * @return 约束分页结果
     */
    @Override
    public ConstraintPage page(long current, long size, String keyword, String constraintType) {
        LambdaQueryWrapper<SysRoleConstraintPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleConstraintPo::getTenantId, TenantContext.requireId());
        if (StringUtils.hasText(keyword)) {
            String escaped = escapeLike(keyword);
            wrapper.and(w -> w.like(SysRoleConstraintPo::getConstraintCode, escaped)
                    .or().like(SysRoleConstraintPo::getConstraintName, escaped));
        }
        if (StringUtils.hasText(constraintType)) {
            wrapper.eq(SysRoleConstraintPo::getConstraintType, constraintType);
        }
        wrapper.orderByDesc(SysRoleConstraintPo::getCreateTime);
        Page<SysRoleConstraintPo> page = constraintMapper.selectPage(Page.of(current, size), wrapper);
        List<RoleConstraintAggregate> records = page.getRecords().stream()
                .map(RoleConstraintPersistenceAssembler::toDomain).toList();
        return new ConstraintPage(records, page.getTotal());
    }

    /**
     * 删除约束（逻辑删除）
     *
     * @param id 约束 ID
     */
    @Override
    public void deleteById(Long id) {
        constraintMapper.delete(new LambdaQueryWrapper<SysRoleConstraintPo>()
                .eq(SysRoleConstraintPo::getId, id)
                .eq(SysRoleConstraintPo::getTenantId, TenantContext.requireId()));
    }

    /**
     * LIKE 关键字转义：反斜杠、百分号、下划线（MySQL LIKE 默认以反斜杠为转义符）
     *
     * @param keyword 原始关键字
     * @return 转义后的关键字
     */
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
