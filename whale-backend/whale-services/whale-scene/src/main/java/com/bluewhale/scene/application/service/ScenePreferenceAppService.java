package com.bluewhale.scene.application.service;

import com.bluewhale.scene.application.command.ScenePreferenceSaveCommand;
import com.bluewhale.scene.application.view.ScenePreferenceView;

/**
 * 用户场景偏好用例服务。
 *
 * @author BlueWhale
 */
public interface ScenePreferenceAppService {

    /**
     * 查询当前用户偏好；无记录时返回默认偏好（2D、未指定场景）。
     *
     * @param userId 用户 ID
     * @return 偏好视图
     */
    ScenePreferenceView get(Long userId);

    /**
     * 保存当前用户偏好（不存在则新增）。
     *
     * @param userId 用户 ID
     * @param command 保存命令
     * @return 保存后的偏好视图
     */
    ScenePreferenceView save(Long userId, ScenePreferenceSaveCommand command);
}
