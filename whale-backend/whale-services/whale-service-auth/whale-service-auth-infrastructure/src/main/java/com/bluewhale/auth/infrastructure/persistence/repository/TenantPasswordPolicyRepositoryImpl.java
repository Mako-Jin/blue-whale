package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.application.model.TenantPasswordPolicy;
import com.bluewhale.auth.application.port.TenantPasswordPolicyRepository;
import com.bluewhale.auth.infrastructure.persistence.TenantContextSupport;
import com.bluewhale.auth.infrastructure.persistence.entity.SysTenantPasswordPolicyPo;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysTenantPasswordPolicyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link TenantPasswordPolicyRepository} 的 MyBatis-Plus 实现（REQ-SEC-023）。
 *
 * <p>到期提醒任务跨租户运行，而登录/刷新签发令牌时读策略发生在已绑定租户的请求线程中。
 * 为避免 MyBatis-Plus 租户行级插件按会话上下文追加错误的 {@code tenant_id} 条件，
 * 读写统一经 {@link TenantContextSupport#runWithoutTenant} 临时清空租户上下文，
 * 并以显式 {@code tenant_id} 条件定位记录。</p>
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
        return TenantContextSupport.runWithoutTenant(() ->
                Optional.ofNullable(tenantPasswordPolicyMapper.selectOne(
                                new LambdaQueryWrapper<SysTenantPasswordPolicyPo>()
                                        .eq(SysTenantPasswordPolicyPo::getTenantId, tenantId)))
                        .map(TenantPasswordPolicyRepositoryImpl::toDomain));
    }

    @Override
    public List<TenantPasswordPolicy> findAll() {
        return TenantContextSupport.runWithoutTenant(() ->
                tenantPasswordPolicyMapper.selectList(new LambdaQueryWrapper<>()).stream()
                        .map(TenantPasswordPolicyRepositoryImpl::toDomain)
                        .toList());
    }

    @Override
    public TenantPasswordPolicy save(TenantPasswordPolicy policy) {
        if (policy == null || policy.tenantId() == null) {
            return policy;
        }
        return TenantContextSupport.runWithoutTenant(() -> {
            SysTenantPasswordPolicyPo existing = tenantPasswordPolicyMapper.selectOne(
                    new LambdaQueryWrapper<SysTenantPasswordPolicyPo>()
                            .eq(SysTenantPasswordPolicyPo::getTenantId, policy.tenantId()));
            if (existing == null) {
                SysTenantPasswordPolicyPo inserted = new SysTenantPasswordPolicyPo();
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
        });
    }

    /**
     * 持久化对象转应用层策略
     *
     * @param po 持久化对象
     * @return 租户策略
     */
    private static TenantPasswordPolicy toDomain(SysTenantPasswordPolicyPo po) {
        return new TenantPasswordPolicy(po.getTenantId(), po.getMaxAgeDays(), po.getWarnDays());
    }
}
