package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.domain.aggregate.DeptAggregate;
import com.bluewhale.auth.domain.repository.DeptRepository;
import com.bluewhale.auth.infrastructure.persistence.entity.SysDeptPo;
import com.bluewhale.auth.infrastructure.persistence.assembler.DeptPersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysDeptMapper;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link DeptRepository} 的 MyBatis-Plus 实现（REQ-ARCH-001 / T-DDD-7）：以既有 {@link SysDeptMapper} 完成
 * PO 读写，并借 {@link DeptPersistenceAssembler} 完成 PO ↔ 领域对象转换。
 *
 * <p>列表查询在实现内直采 Mapper 的 {@code selectList} 能力，对领域层只暴露领域对象集合。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class DeptRepositoryImpl implements DeptRepository {

    private final SysDeptMapper deptMapper;

    @Override
    public DeptAggregate save(DeptAggregate dept) {
        SysDeptPo po = DeptPersistenceAssembler.toPo(dept);
        if (po.getId() == null) {
            deptMapper.insert(po);
        } else {
            deptMapper.updateById(po);
        }
        return DeptPersistenceAssembler.toDomain(po);
    }

    @Override
    public Optional<DeptAggregate> findById(Long id) {
        SysDeptPo po = deptMapper.selectOne(new LambdaQueryWrapper<SysDeptPo>()
                .eq(SysDeptPo::getId, id)
                .eq(SysDeptPo::getTenantId, TenantContext.requireId()));
        return Optional.ofNullable(po).map(DeptPersistenceAssembler::toDomain);
    }

    @Override
    public List<DeptAggregate> findAll() {
        List<SysDeptPo> pos = deptMapper.selectList(new LambdaQueryWrapper<SysDeptPo>()
                .eq(SysDeptPo::getTenantId, TenantContext.requireId())
                .orderByAsc(SysDeptPo::getSort)
                .orderByAsc(SysDeptPo::getId));
        return pos.stream().map(DeptPersistenceAssembler::toDomain).toList();
    }

    @Override
    public List<DeptAggregate> findAllByParentId(Long parentId) {
        List<SysDeptPo> pos = deptMapper.selectList(new LambdaQueryWrapper<SysDeptPo>()
                .eq(SysDeptPo::getTenantId, TenantContext.requireId())
                .eq(SysDeptPo::getParentId, parentId));
        return pos.stream().map(DeptPersistenceAssembler::toDomain).toList();
    }

    @Override
    public long countChildren(Long parentId) {
        Long count = deptMapper.selectCount(new LambdaQueryWrapper<SysDeptPo>()
                .eq(SysDeptPo::getTenantId, TenantContext.requireId())
                .eq(SysDeptPo::getParentId, parentId));
        return count == null ? 0L : count;
    }

    @Override
    public void deleteById(Long id) {
        deptMapper.delete(new LambdaQueryWrapper<SysDeptPo>()
                .eq(SysDeptPo::getId, id)
                .eq(SysDeptPo::getTenantId, TenantContext.requireId()));
    }
}
