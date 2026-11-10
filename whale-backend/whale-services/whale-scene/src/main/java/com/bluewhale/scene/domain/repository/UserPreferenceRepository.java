package com.bluewhale.scene.domain.repository;

import com.bluewhale.scene.domain.model.UserPreference;

import java.util.Optional;

/**
 * 用户场景偏好仓储端口。
 *
 * @author BlueWhale
 */
public interface UserPreferenceRepository {

    /**
     * 按用户查询偏好（租户隔离由持久化插件保证）。
     *
     * @param userId 用户 ID
     * @return 偏好
     */
    Optional<UserPreference> findByUserId(Long userId);

    /**
     * 保存偏好：存在则更新，不存在则新增。
     *
     * @param preference 偏好
     */
    void save(UserPreference preference);
}
