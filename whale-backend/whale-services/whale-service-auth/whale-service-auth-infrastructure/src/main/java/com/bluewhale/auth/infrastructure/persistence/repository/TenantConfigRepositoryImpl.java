package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.application.model.TenantConfigEntry;
import com.bluewhale.auth.application.port.TenantConfigRepository;
import com.bluewhale.auth.infrastructure.persistence.TenantContextSupport;
import com.bluewhale.auth.infrastructure.persistence.entity.SysTenantConfig;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysTenantConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户配置仓储实现。
 *
 * <p>查询/写入统一经 {@link TenantContextSupport#runWithoutTenant} 清空租户上下文
 * （绕开租户行级插件），以显式 {@code tenant_id} 条件过滤。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class TenantConfigRepositoryImpl implements TenantConfigRepository {

    /** 租户配置 Mapper */
    private final SysTenantConfigMapper tenantConfigMapper;

    @Override
    public List<TenantConfigEntry> listByTenant(Long tenantId, String prefix) {
        return TenantContextSupport.runWithoutTenant(() -> {
            LambdaQueryWrapper<SysTenantConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysTenantConfig::getTenantId, tenantId);
            if (StringUtils.hasText(prefix)) {
                wrapper.likeRight(SysTenantConfig::getConfigKey, prefix);
            }
            // 排序下推数据库（configKey 有索引前缀），避免应用内存排序
            return tenantConfigMapper.selectList(wrapper).stream()
                    .map(po -> new TenantConfigEntry(po.getTenantId(), po.getConfigKey(),
                            po.getConfigValue()))
                    .sorted(Comparator.comparing(TenantConfigEntry::configKey))
                    .toList();
        });
    }

    @Override
    public Map<String, String> findValues(Long tenantId, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        return TenantContextSupport.runWithoutTenant(() -> {
            LambdaQueryWrapper<SysTenantConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysTenantConfig::getTenantId, tenantId);
            wrapper.in(SysTenantConfig::getConfigKey, keys);
            Map<String, String> values = new HashMap<>();
            tenantConfigMapper.selectList(wrapper)
                    .forEach(po -> values.put(po.getConfigKey(), po.getConfigValue()));
            return values;
        });
    }

    @Override
    public void upsert(Long tenantId, String configKey, String configValue, Long updatedBy) {
        TenantContextSupport.runWithoutTenant(() -> {
            SysTenantConfig existing = tenantConfigMapper.selectOne(
                    new LambdaQueryWrapper<SysTenantConfig>()
                            .eq(SysTenantConfig::getTenantId, tenantId)
                            .eq(SysTenantConfig::getConfigKey, configKey));
            if (existing == null) {
                SysTenantConfig po = new SysTenantConfig();
                po.setTenantId(tenantId);
                po.setConfigKey(configKey);
                po.setConfigValue(configValue);
                po.setUpdatedBy(updatedBy);
                tenantConfigMapper.insert(po);
            } else {
                existing.setConfigValue(configValue);
                existing.setUpdatedBy(updatedBy);
                tenantConfigMapper.updateById(existing);
            }
        });
    }
}
