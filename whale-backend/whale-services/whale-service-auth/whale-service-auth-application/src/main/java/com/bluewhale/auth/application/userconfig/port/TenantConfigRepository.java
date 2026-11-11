package com.bluewhale.auth.application.userconfig.port;

import com.bluewhale.auth.application.userconfig.TenantConfigEntry;

import java.util.List;
import java.util.Map;

/**
 * 租户配置仓储端口。
 *
 * @author BlueWhale
 */
public interface TenantConfigRepository {

    /**
     * 按前缀列出指定租户的配置
     *
     * @param tenantId 租户 ID
     * @param prefix   配置键前缀，空/blank 表示不过滤
     * @return 配置条目列表（按 config_key 升序）
     */
    List<TenantConfigEntry> listByTenant(Long tenantId, String prefix);

    /**
     * 批量读取多个配置键的值
     *
     * @param tenantId 租户 ID
     * @param keys     配置键列表
     * @return {configKey: configValue}，只含存在的键
     */
    Map<String, String> findValues(Long tenantId, List<String> keys);

    /**
     * upsert 一条租户配置（存在即覆盖）
     *
     * @param tenantId    租户 ID
     * @param configKey   配置键
     * @param configValue 配置值（JSON）
     * @param updatedBy   最后更新人用户 ID，可为 null
     */
    void upsert(Long tenantId, String configKey, String configValue, Long updatedBy);
}
