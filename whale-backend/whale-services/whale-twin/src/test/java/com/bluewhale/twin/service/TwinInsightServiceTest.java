package com.bluewhale.twin.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.dto.BindingStateVO;
import com.bluewhale.twin.dto.InsightRequest;
import com.bluewhale.twin.dto.InsightVO;
import com.bluewhale.twin.entity.TwinEntity;
import com.bluewhale.twin.entity.TwinScene;
import com.bluewhale.twin.mapper.TwinEntityMapper;
import com.bluewhale.twin.support.AssistantChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TwinInsightService} 单元测试。
 *
 * @author BlueWhale
 */
class TwinInsightServiceTest {

    private TwinSceneService sceneService;

    private TwinEntityMapper entityMapper;

    private TwinBindingRuntimeService runtimeService;

    private AssistantChatClient assistantClient;

    private TwinInsightService service;

    @BeforeEach
    void setUp() {
        sceneService = mock(TwinSceneService.class);
        entityMapper = mock(TwinEntityMapper.class);
        runtimeService = mock(TwinBindingRuntimeService.class);
        assistantClient = mock(AssistantChatClient.class);
        service = new TwinInsightService(
                sceneService, entityMapper, runtimeService, assistantClient, new TwinProperties(),
                new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void insightBuildsContextWithTargetEntityAndStates() {
        when(sceneService.requireExists(1L)).thenReturn(scene());
        when(entityMapper.selectById(11L)).thenReturn(entity(11L, "boiler-01", "1号锅炉"));
        when(runtimeService.currentStates(1L)).thenReturn(states());
        Map<String, Object> answer = Map.of(
                "answer", "温度偏高",
                "degraded", false,
                "tokens", 120,
                "elapsedMillis", 900,
                "sessionId", "9",
                "citations", List.of());
        when(assistantClient.chat(anyString(), eq(7L), eq("1"), eq("twin:1:11"))).thenReturn(answer);

        InsightRequest request = new InsightRequest();
        request.setEntityId(11L);
        request.setQuestion("温度正常吗？");
        InsightVO vo = service.insight(1L, request, 7L, "1");

        assertEquals("温度偏高", vo.getAnswer());
        assertFalse(vo.isDegraded());
        assertEquals(120, vo.getTokens());
        assertEquals("9", vo.getSessionId());
    }

    @Test
    void insightWithoutEntityUsesWholeSceneStates() {
        when(sceneService.requireExists(1L)).thenReturn(scene());
        when(runtimeService.currentStates(1L)).thenReturn(states());
        Map<String, Object> answer = Map.of("answer", "场景整体正常", "tokens", 10);
        when(assistantClient.chat(anyString(), eq(7L), eq("1"), eq("twin:1:-"))).thenReturn(answer);

        InsightVO vo = service.insight(1L, new InsightRequest(), 7L, "1");

        assertEquals("场景整体正常", vo.getAnswer());
        assertEquals(10, vo.getTokens());
    }

    @Test
    void insightRejectsForeignEntity() {
        TwinScene scene = scene();
        scene.setId(2L);
        TwinEntity foreign = entity(99L, "other", "别的场景实体");
        foreign.setSceneId(2L);
        when(sceneService.requireExists(1L)).thenReturn(scene);
        when(entityMapper.selectById(99L)).thenReturn(foreign);

        InsightRequest request = new InsightRequest();
        request.setEntityId(99L);
        assertThrows(BizException.class, () -> service.insight(1L, request, 7L, "1"));
    }

    @Test
    void insightMissingEntity() {
        when(sceneService.requireExists(1L)).thenReturn(scene());
        when(entityMapper.selectById(404L)).thenReturn(null);

        InsightRequest request = new InsightRequest();
        request.setEntityId(404L);
        assertThrows(BizException.class, () -> service.insight(1L, request, 7L, "1"));
    }

    @Test
    void insightSurfacesDegradedFlag() {
        when(sceneService.requireExists(1L)).thenReturn(scene());
        when(runtimeService.currentStates(1L)).thenReturn(List.of());
        Map<String, Object> answer = Map.of("answer", "回落摘要", "degraded", true);
        when(assistantClient.chat(anyString(), any(), any(), anyString())).thenReturn(answer);

        InsightVO vo = service.insight(1L, new InsightRequest(), 7L, "1");

        assertTrue(vo.isDegraded());
        assertTrue(vo.getCitations().isEmpty());
    }

    private TwinScene scene() {
        TwinScene scene = new TwinScene();
        scene.setId(1L);
        scene.setCode("plant-demo");
        scene.setName("厂区孪生");
        scene.setStatus("published");
        return scene;
    }

    private TwinEntity entity(Long id, String code, String name) {
        TwinEntity entity = new TwinEntity();
        entity.setId(id);
        entity.setSceneId(1L);
        entity.setEntityCode(code);
        entity.setName(name);
        return entity;
    }

    private List<BindingStateVO> states() {
        return List.of(
                BindingStateVO.builder()
                        .bindingId("100")
                        .entityId("11")
                        .entityCode("boiler-01")
                        .sourceType("iot_device")
                        .bindKey("9001")
                        .online(true)
                        .values(Map.of("temperature", 86.5))
                        .build(),
                BindingStateVO.builder()
                        .bindingId("101")
                        .entityId("12")
                        .entityCode("pump-01")
                        .sourceType("iot_device")
                        .bindKey("9002")
                        .online(false)
                        .values(Map.of())
                        .build());
    }
}
