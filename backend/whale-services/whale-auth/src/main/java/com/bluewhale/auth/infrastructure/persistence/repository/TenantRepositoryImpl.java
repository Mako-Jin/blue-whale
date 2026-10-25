package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.domain.tenant.aggregate.Tenant;
import com.bluewhale.auth.domain.tenant.repository.TenantRepository;
import com.bluewhale.auth.entity.SysTenant;
import com.bluewhale.auth.infrastructure.persistence.assembler.TenantPersistenceAssembler;
import com.bluewhale.auth.mapper.SysTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link TenantRepository} 的 MyBatis-Plus 实现（Wave 5.54 Phase 0）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class TenantRepositoryImpl implements TenantRepository {

    private final SysTenantMapper tenantMapper;

    @Override
    public Optional<Tenant> findByCode(String tenantCode) {
        SysTenant po = tenantMapper.selectOne(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getTenantCode, tenantCode)
                .last("limit 1"));
        return Optional.ofNullable(po).map(TenantPersistenceAssembler::toDomain);
    }

    @Override
    public Optional<Tenant> findById(Long id) {
        SysTenant po = tenantMapper.selectById(id);
        return Optional.ofNullable(po).map(TenantPersistenceAssembler::toDomain);
    }
}
