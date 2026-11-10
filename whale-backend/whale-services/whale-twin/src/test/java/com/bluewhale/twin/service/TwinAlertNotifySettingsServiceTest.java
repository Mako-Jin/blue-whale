package com.bluewhale.twin.service;

import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.dto.TwinAlertNotifySettingsRequest;
import com.bluewhale.twin.dto.TwinAlertNotifySettingsVO;
import com.bluewhale.twin.entity.TwinScene;
import com.bluewhale.twin.mapper.TwinSceneMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 场景级告警通知设置单测（Wave 6.10 M2）：sceneConfig 合并语义与全局叠加口径。
 *
 * @author BlueWhale
 */
class TwinAlertNotifySettingsServiceTest {

    private static final Long SCENE_ID = 100L;

    private static final ObjectMapper JSON = new ObjectMapper();

    private TwinSceneMapper sceneMapper;

    private TwinProperties properties;

    private TwinAlertNotifySettingsService service;

    @BeforeEach
    void setUp() {
        sceneMapper = mock(TwinSceneMapper.class);
        properties = new TwinProperties();
        service = new TwinAlertNotifySettingsService(sceneMapper, properties);
    }

    /**
     * 纯单测没有 MyBatis-Plus 启动流程，Lambda 列名缓存不会自动就绪
     * （{@code LambdaUpdateWrapper.set} 会抛 can not find lambda cache），这里手工注册一次。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, TwinScene.class);
    }

    @Test
    void followsGlobalWhenSceneHasNoConfig() {
        properties.getAlertNotify().setEnabled(true);
        properties.getAlertNotify().setWebhookUrls(List.of("https://hook.example.com/a"));
        properties.getAlertNotify().setInappEnabled(true);
        when(sceneMapper.selectById(SCENE_ID)).thenReturn(scene(null));

        TwinAlertNotifySettingsVO vo = service.get(SCENE_ID);

        assertTrue(vo.isEffectiveWebhookEnabled());
        assertTrue(vo.isEffectiveInappEnabled());
        assertEquals("warn", vo.getEffectiveMinLevel());
        assertTrue(vo.isFallbackToCreator());
        assertNull(vo.getWebhookEnabled());
    }

    @Test
    void sceneSwitchCannotEnableChannelWhileGlobalOff() {
        // 全局关 + 场景「打开」也不能发出去（单向收紧）
        when(sceneMapper.selectById(SCENE_ID))
                .thenReturn(scene("{\"alertNotify\":{\"webhookEnabled\":true}}"));

        TwinAlertNotifySettingsVO vo = service.get(SCENE_ID);

        assertFalse(vo.isEffectiveWebhookEnabled());
        assertFalse(vo.isGlobalWebhookReady());
    }

    @Test
    void mergeKeepsOtherSceneConfigKeys() {
        TwinAlertNotifySettingsRequest request = new TwinAlertNotifySettingsRequest();
        request.setWebhookEnabled(false);

        String merged = service.mergeConfig(
                "{\"background\":\"#101820\",\"camera\":{\"fov\":45}}", request);

        JsonNode root = read(merged);
        assertEquals("#101820", root.path("background").asText());
        assertEquals(45, root.path("camera").path("fov").asInt());
        assertFalse(root.path("alertNotify").path("webhookEnabled").asBoolean());
    }

    @Test
    void mergeClearsConfigWhenAllFieldsNull() {
        TwinAlertNotifySettingsRequest request = new TwinAlertNotifySettingsRequest();

        String merged = service.mergeConfig(
                "{\"alertNotify\":{\"webhookEnabled\":false,\"minLevel\":\"critical\"}}", request);

        // 没有其它键时整体写 null（清空场景配置），不能留 "{}"
        assertNull(merged);
    }

    @Test
    void mergeDedupesAndCapsRecipients() {
        TwinAlertNotifySettingsRequest request = new TwinAlertNotifySettingsRequest();
        List<Long> ids = new ArrayList<>();
        for (long i = 1; i <= 25; i++) {
            ids.add(i);
        }
        ids.add(1L);
        request.setUserIds(ids);

        String merged = service.mergeConfig(null, request);

        JsonNode stored = read(merged).path("alertNotify").path("userIds");
        assertEquals(20, stored.size());
        assertEquals(1L, stored.get(0).asLong());
    }

    @Test
    void mergeNormalizesMinLevelCase() {
        TwinAlertNotifySettingsRequest request = new TwinAlertNotifySettingsRequest();
        request.setMinLevel("CRITICAL");

        String merged = service.mergeConfig(null, request);

        assertEquals("critical", read(merged).path("alertNotify").path("minLevel").asText());
    }

    @Test
    void saveIsAllowedOnPublishedScene() {
        TwinScene published = scene(null);
        published.setStatus("published");
        when(sceneMapper.selectById(SCENE_ID)).thenReturn(published);
        TwinAlertNotifySettingsRequest request = new TwinAlertNotifySettingsRequest();
        request.setMinLevel("critical");

        // 通知设置属运营配置：已发布场景也必须能改（编辑锁只管几何/绑定）
        assertDoesNotThrow(() -> service.save(SCENE_ID, request));
        verify(sceneMapper).update(isNull(), any());
    }

    @Test
    void mergeStoresRecoveryFlagAndQuietWindow() {
        TwinAlertNotifySettingsRequest request = new TwinAlertNotifySettingsRequest();
        request.setRecoveryEnabled(false);
        request.setQuietStart("22:00");
        request.setQuietEnd("8:05");

        JsonNode notify = read(service.mergeConfig(null, request)).path("alertNotify");

        assertFalse(notify.path("recoveryEnabled").asBoolean());
        assertEquals("22:00", notify.path("quietStart").asText());
        // 单位数小时补零到 HH:mm，避免前端 / 日志里出现两种写法
        assertEquals("08:05", notify.path("quietEnd").asText());
    }

    @Test
    void mergeDropsIllegalQuietTime() {
        TwinAlertNotifySettingsRequest request = new TwinAlertNotifySettingsRequest();
        request.setQuietStart("25:00");
        request.setQuietEnd("");

        // 非法值直接不落库（旧值也会被清掉），窗口半残会导致判定失真
        assertNull(service.mergeConfig(null, request));
    }

    @Test
    void quietActiveCombinesGlobalAndSceneWindows() {
        String[] global = windowAroundNow();
        properties.getAlertNotify().setQuietHoursStart(global[0]);
        properties.getAlertNotify().setQuietHoursEnd(global[1]);
        LocalTime now = LocalTime.now();

        assertTrue(service.quietActive(TwinAlertNotifySettingsService.SceneSettings.EMPTY,
                "warn", now));
        // 严重告警默认穿透静默
        assertFalse(service.quietActive(TwinAlertNotifySettingsService.SceneSettings.EMPTY,
                "critical", now));

        properties.getAlertNotify().setQuietHoursCriticalBypass(false);
        assertTrue(service.quietActive(TwinAlertNotifySettingsService.SceneSettings.EMPTY,
                "critical", now));
    }

    @Test
    void sceneQuietWindowAppliesWithoutGlobalWindow() {
        String[] sceneWindow = windowAroundNow();
        TwinAlertNotifySettingsService.SceneSettings settings =
                new TwinAlertNotifySettingsService.SceneSettings(null, null, null, null,
                        sceneWindow[0], sceneWindow[1], List.of());

        assertTrue(service.quietActive(settings, "warn", LocalTime.now()));
        // 场景窗口之外的时刻不受影响
        assertFalse(service.quietActive(settings, "warn",
                LocalTime.now().plusHours(3)));
    }

    @Test
    void viewExposesRecoveryAndQuietFields() {
        properties.getAlertNotify().setQuietHoursStart("22:00");
        properties.getAlertNotify().setQuietHoursEnd("08:00");
        when(sceneMapper.selectById(SCENE_ID))
                .thenReturn(scene("{\"alertNotify\":{\"recoveryEnabled\":false}}"));

        TwinAlertNotifySettingsVO vo = service.get(SCENE_ID);

        assertFalse(vo.getRecoveryEnabled());
        assertFalse(vo.isEffectiveRecoveryEnabled());
        assertEquals("22:00", vo.getGlobalQuietStart());
        assertEquals("08:00", vo.getEffectiveQuietEnd());
        assertTrue(vo.isQuietCriticalBypass());
        // 02:00 在 22:00–08:00 内 → quietNow 为真
        assertTrue(service.buildVo(SCENE_ID, service.current(SCENE_ID),
                LocalTime.of(2, 0)).isQuietNow());
    }

    /**
     * 取一个「包含当前时刻」的静默窗口（±1 小时，自动处理跨零点）。
     *
     * @return 长度为 2 的数组：[开始, 结束]，格式 HH:mm
     */
    private static String[] windowAroundNow() {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime now = LocalTime.now();
        return new String[]{format.format(now.minusHours(1)), format.format(now.plusHours(1))};
    }

    /**
     * 造场景行。
     *
     * @param sceneConfig sceneConfig 原文
     * @return 场景实体
     */
    private TwinScene scene(String sceneConfig) {
        TwinScene scene = new TwinScene();
        scene.setId(SCENE_ID);
        scene.setName("示范场景");
        scene.setSceneConfig(sceneConfig);
        scene.setCreatedBy(7L);
        return scene;
    }

    /**
     * 解析 JSON（测试内部断言用）。
     *
     * @param json 原文
     * @return 节点
     */
    private JsonNode read(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
