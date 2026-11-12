package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.application.model.UserConfigEntry;
import com.bluewhale.auth.application.port.UserConfigRepository;
import com.bluewhale.auth.infrastructure.persistence.TenantContextSupport;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserConfigPo;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 用户配置仓储实现。
 *
 * <p>查询/写入统一经 {@link TenantContextSupport#runWithoutTenant} 清空租户上下文
 * （绕开租户行级插件），以显式 {@code tenant_id} 条件过滤。</p>
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
            wrapper.eq(SysUserConfigPo::getUserId, userId);
            if (StringUtils.hasText(prefix)) {
                wrapper.likeRight(SysUserConfigPo::getConfigKey, prefix);
            }
            // 排序下推数据库（configKey 有索引前缀），避免应用内存排序
            wrapper.orderByAsc(SysUserConfigPo::getConfigKey);
        }).stream()
                .map(po -> new UserConfigEntry(po.getTenantId(), po.getUserId(),
                        po.getConfigKey(), po.getConfigValue()))
                .toList();
    }

    @Override
    public Optional<String> findValue(Long tenantId, Long userId, String configKey) {
        List<SysUserConfigPo> rows = query(tenantId, wrapper -> {
            wrapper.eq(SysUserConfigPo::getUserId, userId);
            wrapper.eq(SysUserConfigPo::getConfigKey, configKey);
        });
        return rows.stream().findFirst().map(SysUserConfigPo::getConfigValue);
    }

    @Override
    public void upsert(Long tenantId, Long userId, String configKey, String configValue,
                       Long updatedBy) {
        TenantContextSupport.runWithoutTenant(() -> {
            SysUserConfigPo existing = userConfigMapper.selectOne(
                    new LambdaQueryWrapper<SysUserConfigPo>()
                            .eq(SysUserConfigPo::getTenantId, tenantId)
                            .eq(SysUserConfigPo::getUserId, userId)
                            .eq(SysUserConfigPo::getConfigKey, configKey));
            if (existing == null) {
                SysUserConfigPo po = new SysUserConfigPo();
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
        });
    }

    @Override
    public int delete(Long tenantId, Long userId, String configKey) {
        return TenantContextSupport.runWithoutTenant(() -> userConfigMapper.delete(
                new LambdaQueryWrapper<SysUserConfigPo>()
                        .eq(SysUserConfigPo::getTenantId, tenantId)
                        .eq(SysUserConfigPo::getUserId, userId)
                        .eq(SysUserConfigPo::getConfigKey, configKey)));
    }

    /**
     * 在清空租户上下文的口径下执行查询（绕开租户行级插件，显式 tenant_id 条件过滤）
     *
     * @param tenantId   租户 ID
     * @param customizer 查询条件定制器（不含 tenant_id 条件，由本方法统一追加）
     * @return 实体列表
     */
    private List<SysUserConfigPo> query(Long tenantId,
                                        Consumer<LambdaQueryWrapper<SysUserConfigPo>> customizer) {
        return TenantContextSupport.runWithoutTenant(() -> {
            LambdaQueryWrapper<SysUserConfigPo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysUserConfigPo::getTenantId, tenantId);
            customizer.accept(wrapper);
            return userConfigMapper.selectList(wrapper);
        });
    }
}
