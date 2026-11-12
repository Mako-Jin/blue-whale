package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.application.model.UserConfigEntry;
import com.bluewhale.auth.application.port.UserConfigRepository;
import com.bluewhale.auth.infrastructure.persistence.entity.SysUserConfig;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserConfigMapper;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 用户配置仓储实现。
 *
 * <p>与 {@code TenantPasswordPolicyRepositoryImpl} 同口径：查询前清空
 * {@link TenantContext}（绕开租户行级插件），以显式 {@code tenant_id} 条件过滤，
 * 结束后恢复上下文。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class UserConfigRepositoryImpl implements UserConfigRepository {

    /** 用户配置 Mapper */
    private final SysUserConfigMapper userConfigMapper;

    @Override
    public List<UserConfigEntry> listByUser(Long tenantId, Long userId, String prefix) {
        return query(tenantId, wrapper -> {
            wrapper.eq(SysUserConfig::getUserId, userId);
            if (StringUtils.hasText(prefix)) {
                wrapper.likeRight(SysUserConfig::getConfigKey, prefix);
            }
            return null;
        }).stream()
                .map(po -> new UserConfigEntry(po.getTenantId(), po.getUserId(),
                        po.getConfigKey(), po.getConfigValue()))
                .sorted(java.util.Comparator.comparing(UserConfigEntry::configKey))
                .toList();
    }

    @Override
    public Optional<String> findValue(Long tenantId, Long userId, String configKey) {
        List<SysUserConfig> rows = query(tenantId, wrapper -> {
            wrapper.eq(SysUserConfig::getUserId, userId);
            wrapper.eq(SysUserConfig::getConfigKey, configKey);
            return null;
        });
        return rows.stream().findFirst().map(SysUserConfig::getConfigValue);
    }

    @Override
    public void upsert(Long tenantId, Long userId, String configKey, String configValue,
                       Long updatedBy) {
        withTenantCleared(() -> {
            SysUserConfig existing = userConfigMapper.selectOne(
                    new LambdaQueryWrapper<SysUserConfig>()
                            .eq(SysUserConfig::getTenantId, tenantId)
                            .eq(SysUserConfig::getUserId, userId)
                            .eq(SysUserConfig::getConfigKey, configKey));
            if (existing == null) {
                SysUserConfig po = new SysUserConfig();
                po.setTenantId(tenantId);
                po.setUserId(userId);
                po.setConfigKey(configKey);
                po.setConfigValue(configValue);
                po.setUpdatedBy(updatedBy);
                userConfigMapper.insert(po);
            } else {
                existing.setConfigValue(configValue);
                existing.setUpdatedBy(updatedBy);
                userConfigMapper.updateById(existing);
            }
            return null;
        });
    }

    @Override
    public int delete(Long tenantId, Long userId, String configKey) {
        return withTenantCleared(() -> userConfigMapper.delete(
                new LambdaQueryWrapper<SysUserConfig>()
                        .eq(SysUserConfig::getTenantId, tenantId)
                        .eq(SysUserConfig::getUserId, userId)
                        .eq(SysUserConfig::getConfigKey, configKey)));
    }

    /**
     * 在清空租户上下文的口径下执行查询（绕开租户行级插件，显式 tenant_id 条件过滤）
     *
     * @param tenantId  租户 ID
     * @param customizer 查询条件定制器（不含 tenant_id 条件，由本方法统一追加）
     * @return 实体列表
     */
    private List<SysUserConfig> query(Long tenantId,
                                      java.util.function.Function<
                                              LambdaQueryWrapper<SysUserConfig>, Void> customizer) {
        return withTenantCleared(() -> {
            LambdaQueryWrapper<SysUserConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysUserConfig::getTenantId, tenantId);
            customizer.apply(wrapper);
            return userConfigMapper.selectList(wrapper);
        });
    }

    /**
     * 清空租户上下文执行逻辑并在结束后恢复
     *
     * @param action 待执行逻辑
     * @param <T>    返回类型
     * @return 执行结果
     */
    private <T> T withTenantCleared(java.util.function.Supplier<T> action) {
        Long previous = TenantContext.get();
        TenantContext.clear();
        try {
            return action.get();
        } finally {
            TenantContext.set(previous);
        }
    }
}
