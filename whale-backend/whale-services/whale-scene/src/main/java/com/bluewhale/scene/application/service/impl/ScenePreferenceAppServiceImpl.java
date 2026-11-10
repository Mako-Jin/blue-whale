package com.bluewhale.scene.application.service.impl;

import com.bluewhale.scene.application.command.ScenePreferenceSaveCommand;
import com.bluewhale.scene.application.service.ScenePreferenceAppService;
import com.bluewhale.scene.application.view.ScenePreferenceView;
import com.bluewhale.scene.domain.exception.SceneErrorCode;
import com.bluewhale.scene.domain.model.SceneStatus;
import com.bluewhale.scene.domain.model.UserPreference;
import com.bluewhale.scene.domain.repository.SceneRepository;
import com.bluewhale.scene.domain.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户场景偏好用例实现。
 *
 * <p>默认场景只允许指向已发布场景，避免保存后在导航中找不到；
 * 偏好记录按用户维度 upsert，租户隔离由持久化插件保证。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ScenePreferenceAppServiceImpl implements ScenePreferenceAppService {

    /** 场景标识最大长度（与 DDL 一致） */
    private static final int SCENE_KEY_MAX_LENGTH = 64;

    private final UserPreferenceRepository userPreferenceRepository;

    private final SceneRepository sceneRepository;

    @Override
    public ScenePreferenceView get(Long userId) {
        return userPreferenceRepository.findByUserId(userId)
                .map(this::toView)
                .orElseGet(() -> toView(defaultPreference(userId)));
    }

    @Override
    @Transactional
    public ScenePreferenceView save(Long userId, ScenePreferenceSaveCommand command) {
        ScenePreferenceSaveCommand saveCommand = command == null
                ? new ScenePreferenceSaveCommand() : command;
        String viewMode = StringUtils.hasText(saveCommand.getViewMode())
                ? saveCommand.getViewMode().trim() : UserPreference.VIEW_MODE_2D;
        if (!UserPreference.isValidViewMode(viewMode)) {
            throw SceneErrorCode.exception(SceneErrorCode.PREFERENCE_INVALID);
        }
        String sceneKey = StringUtils.hasText(saveCommand.getDefaultSceneKey())
                ? saveCommand.getDefaultSceneKey().trim() : null;
        if (sceneKey != null) {
            if (sceneKey.length() > SCENE_KEY_MAX_LENGTH) {
                throw SceneErrorCode.exception(SceneErrorCode.PREFERENCE_INVALID);
            }
            boolean published = sceneRepository.findByKey(sceneKey)
                    .filter(scene -> SceneStatus.PUBLISHED.equals(scene.getStatus()))
                    .isPresent();
            if (!published) {
                throw SceneErrorCode.exception(SceneErrorCode.PREFERENCE_SCENE_INVALID);
            }
        }
        UserPreference preference = userPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> defaultPreference(userId));
        preference.setViewMode(viewMode);
        preference.setDefaultSceneKey(sceneKey);
        userPreferenceRepository.save(preference);
        return toView(preference);
    }

    /**
     * 构造默认偏好（2D、未指定场景）。
     *
     * @param userId 用户 ID
     * @return 默认偏好
     */
    private UserPreference defaultPreference(Long userId) {
        UserPreference preference = new UserPreference();
        preference.setUserId(userId);
        preference.setViewMode(UserPreference.VIEW_MODE_2D);
        return preference;
    }

    /**
     * 偏好转视图。
     *
     * @param preference 偏好
     * @return 偏好视图
     */
    private ScenePreferenceView toView(UserPreference preference) {
        ScenePreferenceView view = new ScenePreferenceView();
        view.setViewMode(preference.getViewMode() == null
                ? UserPreference.VIEW_MODE_2D : preference.getViewMode());
        view.setDefaultSceneKey(preference.getDefaultSceneKey());
        return view;
    }
}
