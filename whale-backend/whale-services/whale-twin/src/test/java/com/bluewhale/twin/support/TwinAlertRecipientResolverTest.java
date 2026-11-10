package com.bluewhale.twin.support;

import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.entity.TwinScene;
import com.bluewhale.twin.mapper.TwinSceneMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 告警通知接收人解析单测：场景配置优先、创建人回退、异常与截断。
 *
 * @author BlueWhale
 */
class TwinAlertRecipientResolverTest {

    private static final Long SCENE_ID = 100L;

    private TwinSceneMapper sceneMapper;

    private TwinProperties properties;

    private TwinAlertRecipientResolver resolver;

    @BeforeEach
    void setUp() {
        sceneMapper = mock(TwinSceneMapper.class);
        properties = new TwinProperties();
        resolver = new TwinAlertRecipientResolver(sceneMapper, properties);
    }

    @Test
    void usesConfiguredUserIds() {
        when(sceneMapper.selectById(SCENE_ID))
                .thenReturn(scene("{\"alertNotify\":{\"userIds\":[7,8,7]}}", 99L));

        TwinAlertRecipientResolver.Recipients recipients = resolver.resolve(SCENE_ID);

        assertEquals(List.of(7L, 8L), recipients.userIds());
        assertEquals("示范场景", recipients.sceneName());
    }

    @Test
    void fallsBackToCreatorWhenNoConfig() {
        when(sceneMapper.selectById(SCENE_ID)).thenReturn(scene("{\"background\":\"#fff\"}", 99L));

        assertEquals(List.of(99L), resolver.resolve(SCENE_ID).userIds());
    }

    @Test
    void fallsBackToCreatorWhenConfigBroken() {
        when(sceneMapper.selectById(SCENE_ID)).thenReturn(scene("not-json", 99L));

        assertEquals(List.of(99L), resolver.resolve(SCENE_ID).userIds());
    }

    @Test
    void returnsEmptyWhenNoConfigAndNoCreator() {
        when(sceneMapper.selectById(SCENE_ID)).thenReturn(scene(null, null));

        assertTrue(resolver.resolve(SCENE_ID).userIds().isEmpty());
    }

    @Test
    void returnsEmptyWhenSceneMissing() {
        when(sceneMapper.selectById(anyLong())).thenReturn(null);

        assertTrue(resolver.resolve(SCENE_ID).userIds().isEmpty());
        assertNull(resolver.resolve(SCENE_ID).sceneName());
    }

    @Test
    void truncatesByConfiguredLimit() {
        properties.getAlertNotify().setMaxRecipients(2);
        when(sceneMapper.selectById(SCENE_ID))
                .thenReturn(scene("{\"alertNotify\":{\"userIds\":[1,2,3,4]}}", 99L));

        assertEquals(List.of(1L, 2L), resolver.resolve(SCENE_ID).userIds());
    }

    /**
     * 构造场景。
     *
     * @param sceneConfig 场景配置 JSON（可空）
     * @param createdBy   创建人（可空）
     * @return 场景实体
     */
    private static TwinScene scene(String sceneConfig, Long createdBy) {
        TwinScene scene = new TwinScene();
        scene.setId(SCENE_ID);
        scene.setName("示范场景");
        scene.setSceneConfig(sceneConfig);
        scene.setCreatedBy(createdBy);
        return scene;
    }
}
