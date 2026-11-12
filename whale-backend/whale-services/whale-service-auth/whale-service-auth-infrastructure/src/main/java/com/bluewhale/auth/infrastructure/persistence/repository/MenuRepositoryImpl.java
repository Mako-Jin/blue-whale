package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.domain.aggregate.MenuAggregate;
import com.bluewhale.auth.domain.repository.MenuRepository;
import com.bluewhale.auth.infrastructure.persistence.po.SysMenuPo;
import com.bluewhale.auth.infrastructure.persistence.assembler.MenuPersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysMenuMapper;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link MenuRepository} 的 MyBatis-Plus 实现（REQ-ARCH-001 / T-DDD-6）：以既有 {@link SysMenuMapper} 完成
 * PO 读写，并借 {@link MenuPersistenceAssembler} 完成 PO ↔ 领域对象转换。
 *
 * <p>列表查询在实现内直采 Mapper 的 {@code selectList} 能力，对领域层只暴露领域对象集合。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class MenuRepositoryImpl implements MenuRepository {

    private final SysMenuMapper menuMapper;

    @Override
    public MenuAggregate save(MenuAggregate menu) {
        SysMenuPo po = MenuPersistenceAssembler.toPo(menu);
        if (po.getId() == null) {
            menuMapper.insert(po);
        } else {
            menuMapper.updateById(po);
        }
        return MenuPersistenceAssembler.toDomain(po);
    }

    @Override
    public Optional<MenuAggregate> findById(Long id) {
        SysMenuPo po = menuMapper.selectOne(new LambdaQueryWrapper<SysMenuPo>()
                .eq(SysMenuPo::getId, id)
                .eq(SysMenuPo::getTenantId, TenantContext.requireId()));
        return Optional.ofNullable(po).map(MenuPersistenceAssembler::toDomain);
    }

    @Override
    public List<MenuAggregate> findAll() {
        List<SysMenuPo> pos = menuMapper.selectList(new LambdaQueryWrapper<SysMenuPo>()
                .eq(SysMenuPo::getTenantId, TenantContext.requireId())
                .orderByAsc(SysMenuPo::getSort)
                .orderByAsc(SysMenuPo::getId));
        return pos.stream().map(MenuPersistenceAssembler::toDomain).toList();
    }

    @Override
    public List<MenuAggregate> findAllByTenantId(Long tenantId) {
        List<SysMenuPo> pos = menuMapper.selectList(new LambdaQueryWrapper<SysMenuPo>()
                .eq(SysMenuPo::getTenantId, tenantId)
                .orderByAsc(SysMenuPo::getSort)
                .orderByAsc(SysMenuPo::getId));
        return pos.stream().map(MenuPersistenceAssembler::toDomain).toList();
    }

    @Override
    public List<MenuAggregate> findAllByParentId(Long parentId) {
        List<SysMenuPo> pos = menuMapper.selectList(new LambdaQueryWrapper<SysMenuPo>()
                .eq(SysMenuPo::getTenantId, TenantContext.requireId())
                .eq(SysMenuPo::getParentId, parentId));
        return pos.stream().map(MenuPersistenceAssembler::toDomain).toList();
    }

    @Override
    public List<MenuAggregate> findEnabledVisible() {
        List<SysMenuPo> pos = menuMapper.selectList(new LambdaQueryWrapper<SysMenuPo>()
                .eq(SysMenuPo::getTenantId, TenantContext.requireId())
                .eq(SysMenuPo::getStatus, MenuAggregate.STATUS_ENABLED)
                .eq(SysMenuPo::getVisible, MenuAggregate.VISIBLE_SHOWN)
                .orderByAsc(SysMenuPo::getSort)
                .orderByAsc(SysMenuPo::getId));
        return pos.stream().map(MenuPersistenceAssembler::toDomain).toList();
    }

    @Override
    public long countChildren(Long parentId) {
        Long count = menuMapper.selectCount(new LambdaQueryWrapper<SysMenuPo>()
                .eq(SysMenuPo::getTenantId, TenantContext.requireId())
                .eq(SysMenuPo::getParentId, parentId));
        return count == null ? 0L : count;
    }

    @Override
    public void deleteById(Long id) {
        menuMapper.delete(new LambdaQueryWrapper<SysMenuPo>()
                .eq(SysMenuPo::getId, id)
                .eq(SysMenuPo::getTenantId, TenantContext.requireId()));
    }
}
