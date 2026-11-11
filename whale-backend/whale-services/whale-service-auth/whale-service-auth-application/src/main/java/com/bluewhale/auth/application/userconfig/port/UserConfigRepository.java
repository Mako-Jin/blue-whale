package com.bluewhale.auth.application.userconfig.port;

import com.bluewhale.auth.application.userconfig.UserConfigEntry;

import java.util.List;
import java.util.Optional;

/**
 * 用户配置仓储端口。
 *
 * @author BlueWhale
 */
public interface UserConfigRepository {

    /**
     * 按前缀列出指定用户的配置
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param prefix   配置键前缀，空/blank 表示不过滤
     * @return 配置条目列表（按 config_key 升序）
     */
    List<UserConfigEntry> listByUser(Long tenantId, Long userId, String prefix);

    /**
     * 读取单条配置值
     *
     * @param tenantId  租户 ID
     * @param userId    用户 ID
     * @param configKey 配置键
     * @return 配置值；不存在返回 empty
     */
    Optional<String> findValue(Long tenantId, Long userId, String configKey);

    /**
     * upsert 一条配置（存在即覆盖）
     *
     * @param tenantId    租户 ID
     * @param userId      用户 ID
     * @param configKey   配置键
     * @param configValue 配置值（JSON）
     * @param updatedBy   最后更新人用户 ID，可为 null
     */
    void upsert(Long tenantId, Long userId, String configKey, String configValue, Long updatedBy);

    /**
     * 删除一条配置（物理删除；删除后回到租户默认/消费方默认）
     *
     * @param tenantId  租户 ID
     * @param userId    用户 ID
     * @param configKey 配置键
     * @return 实际删除的行数
     */
    int delete(Long tenantId, Long userId, String configKey);
}
