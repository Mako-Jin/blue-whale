package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.application.user.password.TenantPasswordPolicy;
import com.bluewhale.auth.application.user.password.port.TenantPasswordPolicyRepository;
import com.bluewhale.auth.entity.SysTenantPasswordPolicy;
import com.bluewhale.auth.mapper.SysTenantPasswordPolicyMapper;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link TenantPasswordPolicyRepository} 的 MyBatis-Plus 实现（REQ-SEC-023）。
 *
 * <p>到期提醒任务跨租户运行，而登录/刷新签发令牌时读策略发生在已绑定租户的请求线程中。
 * 为避免 MyBatis-Plus 租户行级插件按会话上下文追加错误的 {@code tenant_id} 条件，
 * 本实现统一在读写前临时清空租户上下文，并以显式 {@code tenant_id} 条件定位记录，
 * 执行后恢复原值。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class TenantPasswordPolicyRepositoryImpl implements TenantPasswordPolicyRepository {

    private final SysTenantPasswordPolicyMapper tenantPasswordPolicyMapper;

    @Override
    public Optional<TenantPasswordPolicy> findByTenantId(Long tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        Long previous = TenantContext.get();
        TenantContext.clear();
        try {
            return Optional.ofNullable(tenantPasswordPolicyMapper.selectOne(
                            new LambdaQueryWrapper<SysTenantPasswordPolicy>()
                                    .eq(SysTenantPasswordPolicy::getTenantId, tenantId)))
                    .map(TenantPasswordPolicyRepositoryImpl::toDomain);
        } finally {
            TenantContext.set(previous);
        }
    }

    @Override
    public List<TenantPasswordPolicy> findAll() {
        Long previous = TenantContext.get();
        TenantContext.clear();
        try {
            return tenantPasswordPolicyMapper.selectList(new LambdaQueryWrapper<>()).stream()
                    .map(TenantPasswordPolicyRepositoryImpl::toDomain)
                    .toList();
        } finally {
            TenantContext.set(previous);
        }
    }

    @Override
    public TenantPasswordPolicy save(TenantPasswordPolicy policy) {
        if (policy == null || policy.tenantId() == null) {
            return policy;
        }
        Long previous = TenantContext.get();
        TenantContext.clear();
        try {
            SysTenantPasswordPolicy existing = tenantPasswordPolicyMapper.selectOne(
                    new LambdaQueryWrapper<SysTenantPasswordPolicy>()
                            .eq(SysTenantPasswordPolicy::getTenantId, policy.tenantId()));
            if (existing == null) {
                SysTenantPasswordPolicy inserted = new SysTenantPasswordPolicy();
                inserted.setTenantId(policy.tenantId());
                inserted.setMaxAgeDays(policy.maxAgeDays());
                inserted.setWarnDays(policy.warnDays());
                inserted.setDeleted(0);
                tenantPasswordPolicyMapper.insert(inserted);
                return toDomain(inserted);
            }
            existing.setMaxAgeDays(policy.maxAgeDays());
            existing.setWarnDays(policy.warnDays());
            tenantPasswordPolicyMapper.updateById(existing);
            return toDomain(existing);
        } finally {
            TenantContext.set(previous);
        }
    }

    /**
     * 持久化对象转应用层策略
     *
     * @param po 持久化对象
     * @return 租户策略
     */
    private static TenantPasswordPolicy toDomain(SysTenantPasswordPolicy po) {
        return new TenantPasswordPolicy(po.getTenantId(), po.getMaxAgeDays(), po.getWarnDays());
    }
}
