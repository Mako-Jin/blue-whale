package com.bluewhale.scene.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.scene.application.command.ScenePreferenceSaveCommand;
import com.bluewhale.scene.application.support.SceneViewModes;
import com.bluewhale.scene.application.view.ScenePreferenceView;
import com.bluewhale.scene.domain.exception.SceneErrorCode;
import com.bluewhale.scene.domain.model.Scene;
import com.bluewhale.scene.domain.model.SceneStatus;
import com.bluewhale.scene.domain.repository.SceneRepository;
import com.bluewhale.scene.infrastructure.client.AuthScenePreferenceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScenePreferenceAppServiceImpl} 单元测试。
 *
 * <p>Wave 6.25 起存储交给 auth 统一配置，本测试只覆盖「业务语义校验 +
 * 委托给客户端」两件事；客户端自身的 JSON 与失败语义见
 * {@code AuthScenePreferenceClientTest}。</p>
 *
 * @author BlueWhale
 */
class ScenePreferenceAppServiceImplTest {

    private final AuthScenePreferenceClient client = mock(AuthScenePreferenceClient.class);

    private final SceneRepository sceneRepository = mock(SceneRepository.class);

    private final ScenePreferenceAppServiceImpl service = new ScenePreferenceAppServiceImpl(
            client, sceneRepository);

    @Test
    @DisplayName("无配置时返回默认 2D 且不指定场景")
    void shouldReturnDefaultWhenAbsent() {
        when(client.read(1L, 7L)).thenReturn(Optional.empty());

        ScenePreferenceView view = service.get(1L, 7L);

        assertEquals(SceneViewModes.VIEW_MODE_2D, view.getViewMode());
        assertNull(view.getDefaultSceneKey());
    }

    @Test
    @DisplayName("有配置时原样返回存储值")
    void shouldReturnStoredPreference() {
        ScenePreferenceView stored = new ScenePreferenceView();
        stored.setViewMode(SceneViewModes.VIEW_MODE_3D);
        stored.setDefaultSceneKey("bluewhale-city");
        when(client.read(1L, 7L)).thenReturn(Optional.of(stored));

        ScenePreferenceView view = service.get(1L, 7L);

        assertEquals(SceneViewModes.VIEW_MODE_3D, view.getViewMode());
        assertEquals("bluewhale-city", view.getDefaultSceneKey());
    }

    @Test
    @DisplayName("保存 3D 与已发布默认场景：场景标识去除首尾空格并写入 auth")
    void shouldSaveThreeDWithPublishedScene() {
        when(sceneRepository.findByKey("bluewhale-city")).thenReturn(Optional.of(publishedScene()));

        ScenePreferenceSaveCommand command = new ScenePreferenceSaveCommand();
        command.setViewMode(SceneViewModes.VIEW_MODE_3D);
        command.setDefaultSceneKey(" bluewhale-city ");

        ScenePreferenceView view = service.save(1L, 7L, command);

        assertEquals(SceneViewModes.VIEW_MODE_3D, view.getViewMode());
        assertEquals("bluewhale-city", view.getDefaultSceneKey());
        ArgumentCaptor<ScenePreferenceView> captor = ArgumentCaptor.forClass(ScenePreferenceView.class);
        verify(client).write(eq(1L), eq(7L), captor.capture(), eq(7L));
        assertEquals(SceneViewModes.VIEW_MODE_3D, captor.getValue().getViewMode());
        assertEquals("bluewhale-city", captor.getValue().getDefaultSceneKey());
    }

    @Test
    @DisplayName("命令为空时按默认 2D 保存（清空默认场景）")
    void shouldDefaultToTwoDWhenCommandEmpty() {
        ScenePreferenceView view = service.save(1L, 7L, null);

        assertEquals(SceneViewModes.VIEW_MODE_2D, view.getViewMode());
        assertNull(view.getDefaultSceneKey());
        verify(client).write(eq(1L), eq(7L), any(ScenePreferenceView.class), eq(7L));
    }

    @Test
    @DisplayName("非法视图模式拒绝保存且不写库")
    void shouldRejectInvalidViewMode() {
        ScenePreferenceSaveCommand command = new ScenePreferenceSaveCommand();
        command.setViewMode("VR");

        BizException exception = assertThrows(BizException.class, () -> service.save(1L, 7L, command));

        assertEquals(SceneErrorCode.PREFERENCE_INVALID, exception.getCode());
        verify(client, never()).write(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("未发布的默认场景拒绝保存且不写库")
    void shouldRejectUnpublishedScene() {
        Scene draft = publishedScene();
        draft.setStatus(SceneStatus.DRAFT);
        when(sceneRepository.findByKey("bluewhale-city")).thenReturn(Optional.of(draft));

        ScenePreferenceSaveCommand command = new ScenePreferenceSaveCommand();
        command.setViewMode(SceneViewModes.VIEW_MODE_3D);
        command.setDefaultSceneKey("bluewhale-city");

        BizException exception = assertThrows(BizException.class, () -> service.save(1L, 7L, command));

        assertEquals(SceneErrorCode.PREFERENCE_SCENE_INVALID, exception.getCode());
        verify(client, never()).write(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("场景标识超长拒绝保存且不写库")
    void shouldRejectTooLongSceneKey() {
        ScenePreferenceSaveCommand command = new ScenePreferenceSaveCommand();
        command.setDefaultSceneKey("x".repeat(65));

        BizException exception = assertThrows(BizException.class, () -> service.save(1L, 7L, command));

        assertEquals(SceneErrorCode.PREFERENCE_INVALID, exception.getCode());
        verify(client, never()).write(anyLong(), anyLong(), any(), anyLong());
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
