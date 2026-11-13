package com.bluewhale.scene.application.service.impl;

import com.bluewhale.scene.application.command.ScenePreferenceSaveCommand;
import com.bluewhale.scene.application.port.ScenePreferencePort;
import com.bluewhale.scene.application.service.ScenePreferenceAppService;
import com.bluewhale.scene.application.support.SceneViewModes;
import com.bluewhale.scene.application.view.ScenePreferenceView;
import com.bluewhale.scene.domain.exception.SceneErrorCode;
import com.bluewhale.scene.domain.model.SceneStatus;
import com.bluewhale.scene.domain.repository.SceneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户场景偏好用例实现。
 *
 * <p>Wave 6.25 起存储落在 auth 统一配置（{@code sys_user_config} 的 {@code scene.view}），
 * 本服务只负责业务语义校验：视图模式枚举、默认场景必须已发布。存储/传输由
 * {@link ScenePreferencePort} 承担（实现在基础设施层的 AuthScenePreferenceClient），
 * 读取失败按默认偏好回落（fail-open），写入失败直接报错（fail-closed）。</p>
 *
 * <p>3D 视图受 {@code whale.scene.three-d-enabled} 约束：开关关闭时仍允许保存
 * 3D 偏好（前端 M3 解冻后可直接生效），但默认值始终为 2D。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ScenePreferenceAppServiceImpl implements ScenePreferenceAppService {

    /** 场景标识最大长度（与前端/DTO 口径一致） */
    private static final int SCENE_KEY_MAX_LENGTH = 64;

    /** 偏好存储端口（auth 统一配置的内网实现） */
    private final ScenePreferencePort scenePreferencePort;

    /** 场景仓储（校验默认场景是否已发布） */
    private final SceneRepository sceneRepository;

    @Override
    public ScenePreferenceView get(Long tenantId, Long userId) {
        return scenePreferencePort.read(tenantId, userId)
                .orElseGet(this::defaultView);
    }

    @Override
    public ScenePreferenceView save(Long tenantId, Long userId, ScenePreferenceSaveCommand command) {
        ScenePreferenceSaveCommand saveCommand = command == null
                ? new ScenePreferenceSaveCommand() : command;
        String viewMode = StringUtils.hasText(saveCommand.getViewMode())
                ? saveCommand.getViewMode().trim() : SceneViewModes.VIEW_MODE_2D;
        if (!SceneViewModes.isValid(viewMode)) {
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
        ScenePreferenceView view = new ScenePreferenceView();
        view.setViewMode(viewMode);
        view.setDefaultSceneKey(sceneKey);
        scenePreferencePort.write(tenantId, userId, view, userId);
        return view;
    }

    /**
     * 构造默认偏好（2D、未指定场景）。
     *
     * @return 默认偏好视图
     */
    private ScenePreferenceView defaultView() {
        ScenePreferenceView view = new ScenePreferenceView();
        view.setViewMode(SceneViewModes.VIEW_MODE_2D);
        return view;
    }
}
