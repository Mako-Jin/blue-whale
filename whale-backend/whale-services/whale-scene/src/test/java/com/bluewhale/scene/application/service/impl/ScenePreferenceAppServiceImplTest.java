package com.bluewhale.scene.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.scene.application.command.ScenePreferenceSaveCommand;
import com.bluewhale.scene.application.view.ScenePreferenceView;
import com.bluewhale.scene.domain.exception.SceneErrorCode;
import com.bluewhale.scene.domain.model.Scene;
import com.bluewhale.scene.domain.model.SceneStatus;
import com.bluewhale.scene.domain.model.UserPreference;
import com.bluewhale.scene.domain.repository.SceneRepository;
import com.bluewhale.scene.domain.repository.UserPreferenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScenePreferenceAppServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class ScenePreferenceAppServiceImplTest {

    private final UserPreferenceRepository userPreferenceRepository = mock(UserPreferenceRepository.class);

    private final SceneRepository sceneRepository = mock(SceneRepository.class);

    private final ScenePreferenceAppServiceImpl service = new ScenePreferenceAppServiceImpl(
            userPreferenceRepository, sceneRepository);

    @Test
    @DisplayName("无偏好记录时返回默认 2D 且不指定场景")
    void shouldReturnDefaultWhenAbsent() {
        when(userPreferenceRepository.findByUserId(7L)).thenReturn(Optional.empty());

        ScenePreferenceView view = service.get(7L);

        assertEquals(UserPreference.VIEW_MODE_2D, view.getViewMode());
        assertEquals(null, view.getDefaultSceneKey());
    }

    @Test
    @DisplayName("保存 3D 与已发布默认场景")
    void shouldSaveThreeDWithPublishedScene() {
        when(userPreferenceRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(sceneRepository.findByKey("bluewhale-city")).thenReturn(Optional.of(publishedScene()));

        ScenePreferenceSaveCommand command = new ScenePreferenceSaveCommand();
        command.setViewMode(UserPreference.VIEW_MODE_3D);
        command.setDefaultSceneKey(" bluewhale-city ");

        ScenePreferenceView view = service.save(7L, command);

        assertEquals(UserPreference.VIEW_MODE_3D, view.getViewMode());
        assertEquals("bluewhale-city", view.getDefaultSceneKey());
        verify(userPreferenceRepository).save(any(UserPreference.class));
    }

    @Test
    @DisplayName("非法视图模式拒绝保存")
    void shouldRejectInvalidViewMode() {
        ScenePreferenceSaveCommand command = new ScenePreferenceSaveCommand();
        command.setViewMode("VR");

        BizException exception = assertThrows(BizException.class, () -> service.save(7L, command));

        assertEquals(SceneErrorCode.PREFERENCE_INVALID, exception.getCode());
        verify(userPreferenceRepository, never()).save(any(UserPreference.class));
    }

    @Test
    @DisplayName("未发布的默认场景拒绝保存")
    void shouldRejectUnpublishedScene() {
        Scene draft = publishedScene();
        draft.setStatus(SceneStatus.DRAFT);
        when(sceneRepository.findByKey("bluewhale-city")).thenReturn(Optional.of(draft));

        ScenePreferenceSaveCommand command = new ScenePreferenceSaveCommand();
        command.setViewMode(UserPreference.VIEW_MODE_3D);
        command.setDefaultSceneKey("bluewhale-city");

        BizException exception = assertThrows(BizException.class, () -> service.save(7L, command));

        assertEquals(SceneErrorCode.PREFERENCE_SCENE_INVALID, exception.getCode());
        verify(userPreferenceRepository, never()).save(any(UserPreference.class));
    }

    /**
     * 构造已发布场景。
     *
     * @return 场景
     */
    private Scene publishedScene() {
        Scene scene = new Scene();
        scene.setSceneKey("bluewhale-city");
        scene.setStatus(SceneStatus.PUBLISHED);
        return scene;
    }
}
