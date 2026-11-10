package com.bluewhale.twin.service;

import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.dto.BindingStateVO;
import com.bluewhale.twin.entity.TwinBinding;
import com.bluewhale.twin.entity.TwinEntity;
import com.bluewhale.twin.mapper.TwinEntityMapper;
import com.bluewhale.twin.support.IotStateClient;
import com.bluewhale.twin.support.InternalApiStateClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 绑定运行时单测：差分推送、离线处理、属性映射与到期节流。
 *
 * <p>构造时关闭 runtime.enabled，调度线程不启动，tick 由测试手工驱动。</p>
 *
 * @author BlueWhale
 */
class TwinBindingRuntimeServiceTest {

    private static final Long SCENE_ID = 100L;

    private static final Long ENTITY_ID = 200L;

    private TwinBindingService bindingService;

    private TwinEntityMapper entityMapper;

    private IotStateClient iotStateClient;

    private InternalApiStateClient internalApiStateClient;

    private TwinStreamService streamService;

    private TwinBindingRuntimeService service;

    @BeforeEach
    void setUp() {
        bindingService = mock(TwinBindingService.class);
        entityMapper = mock(TwinEntityMapper.class);
        iotStateClient = mock(IotStateClient.class);
        internalApiStateClient = mock(InternalApiStateClient.class);
        streamService = mock(TwinStreamService.class);
        TwinProperties properties = new TwinProperties();
        properties.getRuntime().setEnabled(false);
        service = new TwinBindingRuntimeService(bindingService, entityMapper,
                iotStateClient, internalApiStateClient, streamService, properties);
    }

    @Test
    void firstTickPublishesAllChangedBindings() {
        TwinBinding binding = binding("{\"temperature\":\"color\"}");
        TwinEntity entity = entity(1L);
        when(bindingService.listByScene(SCENE_ID)).thenReturn(List.of(binding));
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(entity);
        when(iotStateClient.fetchDeviceState(300L, 1L))
                .thenReturn(Map.of("online", 1, "reported", Map.of("temperature", 82.5)));
        when(streamService.activeScenes()).thenReturn(java.util.Set.of(SCENE_ID));

        service.tick();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(streamService).publish(eq(SCENE_ID), eq(TwinStreamService.EVENT_BINDING), captor.capture());
        @SuppressWarnings("unchecked")
        List<BindingStateVO> items = (List<BindingStateVO>) captor.getValue().get("items");
        assertEquals(1, items.size());
        assertTrue(items.get(0).getOnline());
        assertEquals(82.5, items.get(0).getValues().get("color"));
    }

    @Test
    void secondTickWithSameValuesSkipsPublish() {
        TwinBinding binding = binding("{\"temperature\":\"color\"}");
        TwinEntity entity = entity(1L);
        when(bindingService.listByScene(SCENE_ID)).thenReturn(List.of(binding));
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(entity);
        when(iotStateClient.fetchDeviceState(300L, 1L))
                .thenReturn(Map.of("online", 1, "reported", Map.of("temperature", 82.5)));
        when(streamService.activeScenes()).thenReturn(java.util.Set.of(SCENE_ID));

        service.tick();
        service.nextDueAt.clear();
        service.tick();

        // 只有第一个 tick 推送（第二个 tick 值未变，差分不通过）
        verify(streamService, times(1))
                .publish(eq(SCENE_ID), eq(TwinStreamService.EVENT_BINDING), any());
    }

    @Test
    void changedValueRepublishes() {
        TwinBinding binding = binding("{\"temperature\":\"color\"}");
        TwinEntity entity = entity(1L);
        when(bindingService.listByScene(SCENE_ID)).thenReturn(List.of(binding));
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(entity);
        when(streamService.activeScenes()).thenReturn(java.util.Set.of(SCENE_ID));
        when(iotStateClient.fetchDeviceState(300L, 1L))
                .thenReturn(Map.of("online", 1, "reported", Map.of("temperature", 82.5)))
                .thenReturn(Map.of("online", 1, "reported", Map.of("temperature", 95.0)));

        service.tick();
        service.nextDueAt.clear();
        service.tick();

        verify(streamService, times(2))
                .publish(eq(SCENE_ID), eq(TwinStreamService.EVENT_BINDING), any());
    }

    @Test
    void offlineDevicePushesEmptyValues() {
        TwinBinding binding = binding("{\"temperature\":\"color\"}");
        TwinEntity entity = entity(1L);
        when(bindingService.listByScene(SCENE_ID)).thenReturn(List.of(binding));
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(entity);
        when(streamService.activeScenes()).thenReturn(java.util.Set.of(SCENE_ID));
        when(iotStateClient.fetchDeviceState(300L, 1L))
                .thenReturn(Map.of("online", 0, "reported", Map.of("temperature", 82.5)));

        service.tick();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(streamService).publish(eq(SCENE_ID), eq(TwinStreamService.EVENT_BINDING), captor.capture());
        @SuppressWarnings("unchecked")
        List<BindingStateVO> items = (List<BindingStateVO>) captor.getValue().get("items");
        assertFalse(items.get(0).getOnline());
        assertTrue(items.get(0).getValues().isEmpty());
    }

    @Test
    void stateKeyFromObjectMappingIsHonored() {
        TwinBinding binding = binding(
                "{\"temperature\":{\"stateKey\":\"temp\",\"thresholds\":[50,80]}}");
        TwinEntity entity = entity(1L);
        when(bindingService.listByScene(SCENE_ID)).thenReturn(List.of(binding));
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(entity);
        when(streamService.activeScenes()).thenReturn(java.util.Set.of(SCENE_ID));
        when(iotStateClient.fetchDeviceState(300L, 1L))
                .thenReturn(Map.of("online", 1, "reported", Map.of("temperature", 61)));

        List<BindingStateVO> states = service.currentStates(SCENE_ID);

        assertEquals(1, states.size());
        assertEquals(61, states.get(0).getValues().get("temp"));
    }

    @Test
    void disabledBindingsAreSkipped() {
        TwinBinding disabled = binding("{\"temperature\":\"color\"}");
        disabled.setEnabled(0);
        when(bindingService.listByScene(SCENE_ID)).thenReturn(List.of(disabled));
        when(streamService.activeScenes()).thenReturn(java.util.Set.of(SCENE_ID));

        service.tick();

        verify(streamService, never()).publish(anyLong(), anyString(), any());
        verify(iotStateClient, never()).fetchDeviceState(anyLong(), anyLong());
    }

    @Test
    void internalApiBindingPublishesMappedValues() {
        TwinBinding binding = apiBinding("{\"temperature\":\"color\"}");
        when(bindingService.listByScene(SCENE_ID)).thenReturn(List.of(binding));
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(entity(1L));
        when(internalApiStateClient.fetch("/iot/internal/device/state?deviceId=300", 1L))
                .thenReturn(Map.of("temperature", 71));
        when(streamService.activeScenes()).thenReturn(java.util.Set.of(SCENE_ID));

        service.tick();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(streamService).publish(eq(SCENE_ID), eq(TwinStreamService.EVENT_BINDING), captor.capture());
        @SuppressWarnings("unchecked")
        List<BindingStateVO> items = (List<BindingStateVO>) captor.getValue().get("items");
        assertEquals(1, items.size());
        assertTrue(items.get(0).getOnline());
        assertEquals(71, items.get(0).getValues().get("color"));
        // iot 源不应被触发
        verify(iotStateClient, never()).fetchDeviceState(anyLong(), anyLong());
    }

    @Test
    void internalApiFetchFailurePublishesOffline() {
        TwinBinding binding = apiBinding("{\"temperature\":\"color\"}");
        when(bindingService.listByScene(SCENE_ID)).thenReturn(List.of(binding));
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(entity(1L));
        when(internalApiStateClient.fetch(anyString(), anyLong())).thenReturn(null);
        when(streamService.activeScenes()).thenReturn(java.util.Set.of(SCENE_ID));

        service.tick();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(streamService).publish(eq(SCENE_ID), eq(TwinStreamService.EVENT_BINDING), captor.capture());
        @SuppressWarnings("unchecked")
        List<BindingStateVO> items = (List<BindingStateVO>) captor.getValue().get("items");
        assertFalse(items.get(0).getOnline());
        assertTrue(items.get(0).getValues().isEmpty());
    }

    @Test
    void internalApiPropertyMapSupportsDotAndArrayPath() {
        TwinBinding binding = apiBinding("{\"list[0].value\":\"k\",\"meta.name\":\"n\"}");
        when(bindingService.listByScene(SCENE_ID)).thenReturn(List.of(binding));
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(entity(1L));
        when(internalApiStateClient.fetch(anyString(), anyLong()))
                .thenReturn(Map.of(
                        "list", List.of(Map.of("value", 5)),
                        "meta", Map.of("name", "boilers")));
        when(streamService.activeScenes()).thenReturn(java.util.Set.of(SCENE_ID));

        List<BindingStateVO> states = service.currentStates(SCENE_ID);

        assertEquals(1, states.size());
        assertEquals(5, states.get(0).getValues().get("k"));
        assertEquals("boilers", states.get(0).getValues().get("n"));
    }

    @Test
    void internalApiSameBindKeyFetchedOncePerTick() {
        TwinBinding first = apiBinding("{\"temperature\":\"a\"}");
        first.setId(1L);
        TwinBinding second = apiBinding("{\"temperature\":\"b\"}");
        second.setId(2L);
        when(bindingService.listByScene(SCENE_ID)).thenReturn(List.of(first, second));
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(entity(1L));
        when(internalApiStateClient.fetch(anyString(), anyLong()))
                .thenReturn(Map.of("temperature", 71));
        when(streamService.activeScenes()).thenReturn(java.util.Set.of(SCENE_ID));

        service.currentStates(SCENE_ID);

        verify(internalApiStateClient, times(1)).fetch(anyString(), anyLong());
    }

    @Test
    void tickSwallowsExceptionsToKeepSchedulerAlive() {
        when(streamService.activeScenes()).thenThrow(new IllegalStateException("boom"));

        service.tick();

        verify(streamService, never()).publish(anyLong(), anyString(), any());
    }

    /**
     * 构造设备绑定。
     *
     * @param propertyMap 属性映射
     * @return 绑定
     */
    private static TwinBinding binding(String propertyMap) {
        TwinBinding binding = new TwinBinding();
        binding.setId(1L);
        binding.setEntityId(ENTITY_ID);
        binding.setSourceType(TwinBindingService.SOURCE_IOT_DEVICE);
        binding.setBindKey("300");
        binding.setPropertyMap(propertyMap);
        binding.setPollIntervalMs(1000);
        binding.setEnabled(1);
        return binding;
    }

    /**
     * 构造 internal_api 绑定（bindKey 指向 iot 内网只读端点）。
     *
     * @param propertyMap 属性映射
     * @return 绑定
     */
    private static TwinBinding apiBinding(String propertyMap) {
        TwinBinding binding = binding(propertyMap);
        binding.setSourceType(TwinBindingService.SOURCE_INTERNAL_API);
        binding.setBindKey("/iot/internal/device/state?deviceId=300");
        return binding;
    }

    /**
     * 构造实体。
     *
     * @param tenantId 租户ID
     * @return 实体
     */
    private static TwinEntity entity(Long tenantId) {
        TwinEntity entity = new TwinEntity();
        entity.setId(ENTITY_ID);
        entity.setSceneId(SCENE_ID);
        entity.setEntityCode("boiler-01");
        entity.setName("一号锅炉");
        entity.setTenantId(tenantId);
        return entity;
    }
}
