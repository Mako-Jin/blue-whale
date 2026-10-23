package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.domain.dept.aggregate.Dept;
import com.bluewhale.auth.domain.dept.repository.DeptRepository;
import com.bluewhale.auth.entity.SysDept;
import com.bluewhale.auth.infrastructure.persistence.assembler.DeptPersistenceAssembler;
import com.bluewhale.auth.mapper.SysDeptMapper;
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
    public void save(Dept dept) {
        SysDept po = DeptPersistenceAssembler.toPo(dept);
        if (po.getId() == null) {
            deptMapper.insert(po);
        } else {
            deptMapper.updateById(po);
        }
    }

    @Override
    public Optional<Dept> findById(Long id) {
        SysDept po = deptMapper.selectById(id);
        return Optional.ofNullable(po).map(DeptPersistenceAssembler::toDomain);
    }

    @Override
    public List<Dept> findAll() {
        List<SysDept> pos = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .orderByAsc(SysDept::getSort)
                .orderByAsc(SysDept::getId));
        return pos.stream().map(DeptPersistenceAssembler::toDomain).toList();
    }

    @Override
    public List<Dept> findAllByParentId(Long parentId) {
        List<SysDept> pos = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, parentId));
        return pos.stream().map(DeptPersistenceAssembler::toDomain).toList();
    }

    @Override
    public long countChildren(Long parentId) {
        Long count = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, parentId));
        return count == null ? 0L : count;
    }

    @Override
    public void deleteById(Long id) {
        deptMapper.deleteById(id);
    }
}
