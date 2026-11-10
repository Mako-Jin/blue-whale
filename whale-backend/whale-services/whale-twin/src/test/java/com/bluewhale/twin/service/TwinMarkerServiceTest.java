package com.bluewhale.twin.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.twin.dto.MarkerSaveRequest;
import com.bluewhale.twin.dto.MarkerVO;
import com.bluewhale.twin.entity.TwinEntity;
import com.bluewhale.twin.entity.TwinMarker;
import com.bluewhale.twin.mapper.TwinEntityMapper;
import com.bluewhale.twin.mapper.TwinMarkerMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 标注服务单测：坐标 JSON 结构、挂靠实体归属与场景级锚点。
 *
 * <p>纯 Mockito 环境，{@code TwinMarker}/{@code TwinEntity} 的
 * LambdaQueryWrapper 元数据在 {@code @BeforeAll} 手工注册。</p>
 *
 * @author BlueWhale
 */
class TwinMarkerServiceTest {

    private TwinMarkerMapper markerMapper;

    private TwinEntityMapper entityMapper;

    private TwinSceneService sceneService;

    private TwinMarkerService service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), TwinMarker.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), TwinEntity.class);
    }

    @BeforeEach
    void setUp() {
        markerMapper = mock(TwinMarkerMapper.class);
        entityMapper = mock(TwinEntityMapper.class);
        sceneService = mock(TwinSceneService.class);
        service = new TwinMarkerService(markerMapper, entityMapper, sceneService);
    }

    @Test
    void createRejectsNonObjectPositionJson() {
        when(sceneService.requireExists(1L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> service.create(1L, request("alarm", "[1,2,3]")));

        assertTrue(exception.getMessage().contains("JSON 对象"));
        verify(markerMapper, never()).insert(any(TwinMarker.class));
    }

    @Test
    void createRejectsCrossSceneEntity() {
        when(sceneService.requireExists(1L)).thenReturn(null);
        TwinEntity entity = new TwinEntity();
        entity.setId(11L);
        entity.setSceneId(2L);
        when(entityMapper.selectById(11L)).thenReturn(entity);

        BizException exception = assertThrows(BizException.class,
                () -> service.create(1L, requestWithEntity("label", 11L)));

        assertTrue(exception.getMessage().contains("不属于当前场景"));
    }

    @Test
    void createDefaultsEnabledAndSceneLevelAnchor() {
        when(sceneService.requireExists(1L)).thenReturn(null);

        MarkerSaveRequest request = request("hotspot", "{\"x\":1,\"y\":2,\"z\":3}");
        request.setContent("设备热点");
        MarkerVO vo = service.create(1L, request);

        assertEquals(1, vo.getEnabled());
        assertEquals("1", vo.getSceneId());
        verify(markerMapper).insert(any(TwinMarker.class));
    }

    @Test
    void updateClearsEntityAnchorWhenEntityIdAbsent() {
        TwinMarker marker = marker(1L);
        when(markerMapper.selectById(1L)).thenReturn(marker);

        service.update(1L, 1L, request("label", null));

        assertNull(marker.getEntityId());
        verify(markerMapper).updateById(marker);
    }

    @Test
    void deleteRequiresMarkerInScene() {
        TwinMarker marker = marker(1L);
        marker.setSceneId(9L);
        when(markerMapper.selectById(1L)).thenReturn(marker);

        BizException exception = assertThrows(BizException.class,
                () -> service.delete(1L, 1L));

        assertTrue(exception.getMessage().contains("不属于当前场景"));
        verify(markerMapper, never()).deleteById(1L);
    }

    /**
     * 构造标注实体。
     *
     * @param id 标注ID
     * @return 标注
     */
    private static TwinMarker marker(Long id) {
        TwinMarker marker = new TwinMarker();
        marker.setId(id);
        marker.setSceneId(1L);
        marker.setMarkerType("label");
        marker.setEnabled(1);
        return marker;
    }

    /**
     * 构造保存请求。
     *
     * @param markerType   类型
     * @param positionJson 坐标 JSON
     * @return 请求
     */
    private static MarkerSaveRequest request(String markerType, String positionJson) {
        MarkerSaveRequest request = new MarkerSaveRequest();
        request.setMarkerType(markerType);
        request.setPositionJson(positionJson);
        return request;
    }

    /**
     * 构造带挂靠实体的保存请求。
     *
     * @param markerType 类型
     * @param entityId   实体ID
     * @return 请求
     */
    private static MarkerSaveRequest requestWithEntity(String markerType, Long entityId) {
        MarkerSaveRequest request = request(markerType, null);
        request.setEntityId(entityId);
        return request;
    }
}
