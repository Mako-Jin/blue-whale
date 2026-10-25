package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.domain.user.repository.UserTenantRepository;
import com.bluewhale.auth.entity.SysUserTenant;
import com.bluewhale.auth.mapper.SysUserTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link UserTenantRepository} 的 MyBatis-Plus 实现（Wave 5.54 Phase 5.1）。
 *
 * <p>{@code sys_user_tenant} 已加入租户行过滤忽略白名单：本实现的所有查询都显式带
 * {@code user_id}，禁止出现仅按 {@code tenant_id} 的全表扫描。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class UserTenantRepositoryImpl implements UserTenantRepository {

    /** 状态：启用 */
    private static final int STATUS_ENABLED = 1;

    private final SysUserTenantMapper mapper;

    @Override
    public boolean existsEnabled(Long userId, Long tenantId) {
        Long count = mapper.selectCount(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getUserId, userId)
                .eq(SysUserTenant::getTenantId, tenantId)
                .eq(SysUserTenant::getStatus, STATUS_ENABLED));
        return count != null && count > 0;
    }

    @Override
    public List<Long> listEnabledTenantIds(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<SysUserTenant>()
                        .eq(SysUserTenant::getUserId, userId)
                        .eq(SysUserTenant::getStatus, STATUS_ENABLED)
                        .orderByAsc(SysUserTenant::getTenantId))
                .stream().map(SysUserTenant::getTenantId).toList();
    }

    @Override
    public void ensureEnabled(Long userId, Long tenantId) {
        SysUserTenant existing = mapper.selectOne(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getUserId, userId)
                .eq(SysUserTenant::getTenantId, tenantId)
                .last("limit 1"));
        if (existing == null) {
            SysUserTenant relation = new SysUserTenant();
            relation.setUserId(userId);
            relation.setTenantId(tenantId);
            relation.setStatus(STATUS_ENABLED);
            mapper.insert(relation);
            return;
        }
        if (existing.getStatus() == null || existing.getStatus() != STATUS_ENABLED) {
            existing.setStatus(STATUS_ENABLED);
            mapper.updateById(existing);
        }
    }
}
