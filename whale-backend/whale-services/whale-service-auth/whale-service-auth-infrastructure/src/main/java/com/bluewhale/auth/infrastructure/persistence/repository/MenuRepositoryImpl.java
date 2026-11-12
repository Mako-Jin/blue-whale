package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.domain.aggregate.Menu;
import com.bluewhale.auth.domain.repository.MenuRepository;
import com.bluewhale.auth.infrastructure.persistence.entity.SysMenu;
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
    public Menu save(Menu menu) {
        SysMenu po = MenuPersistenceAssembler.toPo(menu);
        if (po.getId() == null) {
            menuMapper.insert(po);
        } else {
            menuMapper.updateById(po);
        }
        return MenuPersistenceAssembler.toDomain(po);
    }

    @Override
    public Optional<Menu> findById(Long id) {
        SysMenu po = menuMapper.selectOne(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getId, id)
                .eq(SysMenu::getTenantId, TenantContext.requireId()));
        return Optional.ofNullable(po).map(MenuPersistenceAssembler::toDomain);
    }

    @Override
    public List<Menu> findAll() {
        List<SysMenu> pos = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getTenantId, TenantContext.requireId())
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getId));
        return pos.stream().map(MenuPersistenceAssembler::toDomain).toList();
    }

    @Override
    public List<Menu> findAllByTenantId(Long tenantId) {
        List<SysMenu> pos = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getTenantId, tenantId)
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getId));
        return pos.stream().map(MenuPersistenceAssembler::toDomain).toList();
    }

    @Override
    public List<Menu> findAllByParentId(Long parentId) {
        List<SysMenu> pos = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getTenantId, TenantContext.requireId())
                .eq(SysMenu::getParentId, parentId));
        return pos.stream().map(MenuPersistenceAssembler::toDomain).toList();
    }

    @Override
    public List<Menu> findEnabledVisible() {
        List<SysMenu> pos = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getTenantId, TenantContext.requireId())
                .eq(SysMenu::getStatus, Menu.STATUS_ENABLED)
                .eq(SysMenu::getVisible, Menu.VISIBLE_SHOWN)
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getId));
        return pos.stream().map(MenuPersistenceAssembler::toDomain).toList();
    }

    @Override
    public long countChildren(Long parentId) {
        Long count = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getTenantId, TenantContext.requireId())
                .eq(SysMenu::getParentId, parentId));
        return count == null ? 0L : count;
    }

    @Override
    public void deleteById(Long id) {
        menuMapper.delete(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getId, id)
                .eq(SysMenu::getTenantId, TenantContext.requireId()));
    }
}
