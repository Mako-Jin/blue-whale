package com.bluewhale.scene.application.service;

import com.bluewhale.scene.application.command.ScenePreferenceSaveCommand;
import com.bluewhale.scene.application.view.ScenePreferenceView;

/**
 * 用户场景偏好用例服务。
 *
 * <p>Wave 6.25 起偏好存储交给 auth 统一配置（{@code sys_user_config} 的
 * {@code scene.view}），本服务只保留业务语义校验。</p>
 *
 * @author BlueWhale
 */
public interface ScenePreferenceAppService {

    /**
     * 查询当前用户偏好；无记录或 auth 不可用时返回默认偏好（2D、未指定场景）。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 偏好视图
     */
    ScenePreferenceView get(Long tenantId, Long userId);

    /**
     * 保存当前用户偏好（存在则覆盖）。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param command  保存命令
     * @return 保存后的偏好视图
     */
    ScenePreferenceView save(Long tenantId, Long userId, ScenePreferenceSaveCommand command);
}
