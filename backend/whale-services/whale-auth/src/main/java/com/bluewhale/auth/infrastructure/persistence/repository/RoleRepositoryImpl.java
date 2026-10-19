package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.auth.domain.role.aggregate.Role;
import com.bluewhale.auth.domain.role.repository.RoleRepository;
import com.bluewhale.auth.entity.SysRole;
import com.bluewhale.auth.infrastructure.persistence.assembler.RolePersistenceAssembler;
import com.bluewhale.auth.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link RoleRepository} 的 MyBatis-Plus 实现（REQ-ARCH-001 / T-DDD-5）：以既有 {@link SysRoleMapper} 完成
 * PO 读写，并借 {@link RolePersistenceAssembler} 完成 PO ↔ 领域对象转换。
 *
 * <p>分页查询在实现内直采 Mapper 的 {@code selectPage} 能力，对领域层只暴露自有分页载体
 * {@link RoleRepository.RolePage}，避免 MyBatis 分页类型泄漏到领域层。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {

    private final SysRoleMapper roleMapper;

    @Override
    public void save(Role role) {
        SysRole po = RolePersistenceAssembler.toPo(role);
        if (po.getId() == null) {
            roleMapper.insert(po);
        } else {
            roleMapper.updateById(po);
        }
    }

    @Override
    public Optional<Role> findById(Long id) {
        SysRole po = roleMapper.selectById(id);
        return Optional.ofNullable(po).map(RolePersistenceAssembler::toDomain);
    }

    @Override
    public List<Role> listEnabledByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<SysRole> pos = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, ids)
                .eq(SysRole::getStatus, Role.STATUS_ENABLED));
        return pos.stream().map(RolePersistenceAssembler::toDomain).toList();
    }

    @Override
    public RolePage page(long current, long size, String keyword) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysRole::getRoleCode, keyword)
                    .or().like(SysRole::getRoleName, keyword));
        }
        wrapper.orderByAsc(SysRole::getSortNo).orderByDesc(SysRole::getCreateTime);
        Page<SysRole> page = roleMapper.selectPage(new Page<>(current, size), wrapper);
        List<Role> records = page.getRecords().stream().map(RolePersistenceAssembler::toDomain).toList();
        return new RolePage(records, page.getTotal());
    }
}
