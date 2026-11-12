package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.TenantConfigEntry;
import com.bluewhale.auth.application.model.command.SaveTenantConfigCommand;
import com.bluewhale.auth.application.port.TenantConfigRepository;
import com.bluewhale.auth.application.service.TenantConfigAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 租户配置应用服务：租户管理员维护本租户的默认值层。
 *
 * <p>接口侧由 {@code @RequirePermission("sys:tenant:config:list/edit")} 控制准入；
 * 本服务只做存储口径校验。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class TenantConfigAppServiceImpl implements TenantConfigAppService {

    /** 租户配置仓储 */
    private final TenantConfigRepository tenantConfigRepository;

    /**
     * 列出指定租户的配置（可按前缀过滤）
     *
     * @param tenantId 租户 ID
     * @param prefix   配置键前缀，空表示不过滤
     * @return 配置条目列表（按 config_key 升序）
     */
    public List<TenantConfigEntry> list(Long tenantId, String prefix) {
        ConfigKeyValidator.requireValidPrefix(prefix);
        return tenantConfigRepository.listByTenant(tenantId, prefix);
    }

    /**
     * upsert 一条租户配置
     *
     * @param tenantId 租户 ID
     * @param command  保存命令
     */
    public void upsert(Long tenantId, SaveTenantConfigCommand command) {
        ConfigKeyValidator.requireValidKey(command.configKey());
        ConfigKeyValidator.requireValidValue(command.configValue());
        tenantConfigRepository.upsert(tenantId, command.configKey(), command.configValue(),
                command.updatedBy());
    }
}
