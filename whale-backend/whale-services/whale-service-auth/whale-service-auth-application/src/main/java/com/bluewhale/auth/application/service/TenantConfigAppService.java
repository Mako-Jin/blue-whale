package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.TenantConfigEntry;
import com.bluewhale.auth.application.model.command.SaveTenantConfigCommand;

import java.util.List;

/**
 * 租户配置应用服务：租户管理员维护本租户的默认值层。
 *
 * <p>接口侧由 {@code @RequirePermission("sys:tenant:config:list/edit")} 控制准入；
 * 本服务只做存储口径校验。</p>
 *
 * @author BlueWhale
 */
public interface TenantConfigAppService {

    /**
     * 列出指定租户的配置（可按前缀过滤）
     *
     * @param tenantId 租户 ID
     * @param prefix   配置键前缀，空表示不过滤
     * @return 配置条目列表（按 config_key 升序）
     */
    List<TenantConfigEntry> list(Long tenantId, String prefix);

    /**
     * upsert 一条租户配置
     *
     * @param tenantId 租户 ID
     * @param command  保存命令
     */
    void upsert(Long tenantId, SaveTenantConfigCommand command);
}
