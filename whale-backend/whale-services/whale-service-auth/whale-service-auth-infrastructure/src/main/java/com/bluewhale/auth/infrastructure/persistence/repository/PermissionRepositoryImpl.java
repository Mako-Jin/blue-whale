package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.auth.domain.aggregate.PermissionAggregate;
import com.bluewhale.auth.domain.repository.PermissionRepository;
import com.bluewhale.auth.infrastructure.persistence.entity.SysPermission;
import com.bluewhale.auth.infrastructure.persistence.assembler.PermissionPersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link PermissionRepository} 的 MyBatis-Plus 实现（REQ-ARCH-001 / T-DDD-6）：以既有 {@link SysPermissionMapper}
 * 完成 PO 读写，并借 {@link PermissionPersistenceAssembler} 完成 PO ↔ 领域对象转换。
 *
 * <p>分页查询在实现内直采 Mapper 的 {@code selectPage} 能力，对领域层只暴露自有分页载体
 * {@link PermissionRepository.PermissionPage}，避免 MyBatis 分页类型泄漏到领域层。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PermissionRepositoryImpl implements PermissionRepository {

    private final SysPermissionMapper permissionMapper;

    @Override
    public void save(PermissionAggregate permission) {
        SysPermission po = PermissionPersistenceAssembler.toPo(permission);
        if (po.getId() == null) {
            permissionMapper.insert(po);
        } else {
            permissionMapper.updateById(po);
        }
    }

    @Override
    public Optional<PermissionAggregate> findById(Long id) {
        SysPermission po = permissionMapper.selectById(id);
        return Optional.ofNullable(po).map(PermissionPersistenceAssembler::toDomain);
    }

    @Override
    public List<PermissionAggregate> findEnabledAll() {
        List<SysPermission> pos = permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getStatus, PermissionAggregate.STATUS_ENABLED));
        return pos.stream().map(PermissionPersistenceAssembler::toDomain).toList();
    }

    @Override
    public PermissionPage page(long current, long size, String keyword, String permType) {
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            // LIKE 关键字转义反斜杠/百分号/下划线，防止通配符被当作模式展开导致全表扫描
            String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            wrapper.and(w -> w.like(SysPermission::getPermCode, escaped)
                    .or().like(SysPermission::getPermName, escaped));
        }
        if (StringUtils.hasText(permType)) {
            wrapper.eq(SysPermission::getPermType, permType);
        }
        wrapper.orderByAsc(SysPermission::getSortNo).orderByDesc(SysPermission::getCreateTime);
        Page<SysPermission> page = permissionMapper.selectPage(Page.of(current, size), wrapper);
        List<PermissionAggregate> records = page.getRecords().stream()
                .map(PermissionPersistenceAssembler::toDomain).toList();
        return new PermissionPage(records, page.getTotal());
    }

    @Override
    public boolean existsByPermCode(String permCode, Long excludeId) {
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermCode, permCode);
        if (excludeId != null) {
            wrapper.ne(SysPermission::getId, excludeId);
        }
        Long count = permissionMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    @Override
    public long countByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0L;
        }
        Long count = permissionMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                .in(SysPermission::getId, ids));
        return count == null ? 0L : count;
    }

    @Override
    public long countChildren(Long parentId) {
        Long count = permissionMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getParentId, parentId));
        return count == null ? 0L : count;
    }

    @Override
    public void deleteById(Long id) {
        permissionMapper.deleteById(id);
    }
}
