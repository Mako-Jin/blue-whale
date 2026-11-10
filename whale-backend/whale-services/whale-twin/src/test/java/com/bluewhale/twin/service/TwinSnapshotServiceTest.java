package com.bluewhale.twin.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.twin.dto.SnapshotVO;
import com.bluewhale.twin.entity.TwinBinding;
import com.bluewhale.twin.entity.TwinEntity;
import com.bluewhale.twin.entity.TwinMarker;
import com.bluewhale.twin.entity.TwinScene;
import com.bluewhale.twin.entity.TwinSnapshot;
import com.bluewhale.twin.mapper.TwinBindingMapper;
import com.bluewhale.twin.mapper.TwinEntityMapper;
import com.bluewhale.twin.mapper.TwinMarkerMapper;
import com.bluewhale.twin.mapper.TwinSceneMapper;
import com.bluewhale.twin.mapper.TwinSnapshotMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 快照服务单测：整包冻结、发布拦截与回滚重建（父子/绑定/标注重映射）。
 *
 * <p>纯 Mockito 环境，四个实体的 LambdaQueryWrapper 元数据在
 * {@code @BeforeAll} 手工注册。</p>
 *
 * @author BlueWhale
 */
class TwinSnapshotServiceTest {

    private TwinSnapshotMapper snapshotMapper;

    private TwinSceneMapper sceneMapper;

    private TwinEntityMapper entityMapper;

    private TwinBindingMapper bindingMapper;

    private TwinMarkerMapper markerMapper;

    private TwinBindingService bindingService;

    private TwinSceneService sceneService;

    private TwinSnapshotService service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), TwinSnapshot.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), TwinEntity.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), TwinBinding.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), TwinMarker.class);
    }

    @BeforeEach
    void setUp() {
        snapshotMapper = mock(TwinSnapshotMapper.class);
        sceneMapper = mock(TwinSceneMapper.class);
        entityMapper = mock(TwinEntityMapper.class);
        bindingMapper = mock(TwinBindingMapper.class);
        markerMapper = mock(TwinMarkerMapper.class);
        bindingService = mock(TwinBindingService.class);
        sceneService = mock(TwinSceneService.class);
        service = new TwinSnapshotService(snapshotMapper, sceneMapper, entityMapper,
                bindingMapper, markerMapper, bindingService, sceneService);
    }

    @Test
    void freezeSerializesWholePackageAndClearsSameVersion() {
        TwinScene scene = scene(1L);
        scene.setVersion(3);
        TwinEntity entity = new TwinEntity();
        entity.setId(11L);
        entity.setEntityCode("boiler-01");
        entity.setSceneId(1L);
        when(entityMapper.selectList(any())).thenReturn(List.of(entity));
        when(bindingService.listByScene(1L)).thenReturn(List.of());

        service.freeze(scene, "首发版本", 7L);

        verify(snapshotMapper).physicalDeleteByVersion(1L, 3);
        ArgumentCaptor<TwinSnapshot> captor = ArgumentCaptor.forClass(TwinSnapshot.class);
        verify(snapshotMapper).insert(captor.capture());
        TwinSnapshot snapshot = captor.getValue();
        assertEquals(3, snapshot.getVersion());
        assertEquals(7L, snapshot.getPublishedBy());
        assertEquals("首发版本", snapshot.getRemark());
        assertTrue(snapshot.getSnapshotJson().contains("boiler-01"));
        assertTrue(snapshot.getSnapshotJson().contains("\"entities\""));
        assertTrue(snapshot.getSnapshotJson().contains("\"bindings\""));
        assertTrue(snapshot.getSnapshotJson().contains("\"markers\""));
    }

    @Test
    void rollbackRejectsPublishedScene() {
        when(sceneService.requireExists(1L)).thenReturn(publishedScene());

        BizException exception = assertThrows(BizException.class,
                () -> service.rollback(1L, 2L));

        assertTrue(exception.getMessage().contains("先停用"));
    }

    @Test
    void rollbackRebuildsEntitiesBindingsMarkersWithRemap() {
        when(sceneService.requireExists(1L)).thenReturn(disabledScene(1L));
        when(snapshotMapper.selectById(2L)).thenReturn(snapshot(2L, rollbackJson()));
        AtomicLong seq = new AtomicLong(100);
        doAnswer(invocation -> {
            TwinEntity entity = invocation.getArgument(0);
            entity.setId(seq.incrementAndGet());
            return 1;
        }).when(entityMapper).insert(any(TwinEntity.class));

        SnapshotVO vo = service.rollback(1L, 2L);

        assertEquals(1L, Long.parseLong(vo.getSceneId()));
        // 旧工作副本物理清空
        verify(bindingMapper).physicalDeleteByScene(1L);
        verify(entityMapper).physicalDeleteByScene(1L);
        verify(markerMapper).physicalDeleteByScene(1L);
        // 场景元数据回滚：版本自增 + 状态回草稿
        ArgumentCaptor<TwinScene> sceneCaptor = ArgumentCaptor.forClass(TwinScene.class);
        verify(sceneMapper).updateById(sceneCaptor.capture());
        assertEquals(2, sceneCaptor.getValue().getVersion());
        assertEquals("draft", sceneCaptor.getValue().getStatus());
        // 父子重映射：pump-01（旧父 11）挂到新父 101
        ArgumentCaptor<TwinEntity> entityCaptor = ArgumentCaptor.forClass(TwinEntity.class);
        verify(entityMapper).updateById(entityCaptor.capture());
        assertEquals(101L, entityCaptor.getValue().getParentId());
        assertEquals("pump-01", entityCaptor.getValue().getEntityCode());
        // 绑定重映射到新实体 101
        ArgumentCaptor<TwinBinding> bindingCaptor = ArgumentCaptor.forClass(TwinBinding.class);
        verify(bindingMapper).insert(bindingCaptor.capture());
        assertEquals(101L, bindingCaptor.getValue().getEntityId());
        // 标注重映射到新实体 102
        ArgumentCaptor<TwinMarker> markerCaptor = ArgumentCaptor.forClass(TwinMarker.class);
        verify(markerMapper).insert(markerCaptor.capture());
        assertEquals(102L, markerCaptor.getValue().getEntityId());
    }

    @Test
    void rollbackDropsBindingForMissingEntity() {
        when(sceneService.requireExists(1L)).thenReturn(disabledScene(1L));
        String json = "{\"scene\":{},\"entities\":[],\"bindings\":"
                + "[{\"entityId\":99,\"sourceType\":\"iot_device\",\"bindKey\":\"9001\"}],\"markers\":[]}";
        when(snapshotMapper.selectById(2L)).thenReturn(snapshot(2L, json));

        service.rollback(1L, 2L);

        verify(bindingMapper, never()).insert(any(TwinBinding.class));
    }

    @Test
    void detailCarriesSnapshotJsonWhileListDoesNot() {
        when(sceneService.requireExists(1L)).thenReturn(disabledScene(1L));
        when(snapshotMapper.selectById(2L)).thenReturn(snapshot(2L, "{\"scene\":{}}"));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(snapshot(2L, "{\"scene\":{}}")));

        SnapshotVO detail = service.get(1L, 2L);
        List<SnapshotVO> list = service.list(1L);

        assertTrue(detail.getSnapshotJson() != null);
        assertTrue(list.get(0).getSnapshotJson() == null);
    }

    @Test
    void listRequiresSceneExist() {
        // 场景不存在时由 requireExists 抛出，不触达任何清理与更新路径
        when(sceneService.requireExists(9L)).thenThrow(new BizException("场景不存在"));

        BizException exception = assertThrows(BizException.class, () -> service.list(9L));

        assertTrue(exception.getMessage().contains("场景不存在"));
        verify(snapshotMapper, never()).selectList(any());
        verify(sceneMapper, never()).updateById(any(TwinScene.class));
        verify(entityMapper, never()).physicalDeleteByScene(anyLong());
        verify(bindingMapper, never()).physicalDeleteByScene(anyLong());
        verify(markerMapper, never()).physicalDeleteByScene(eq(9L));
    }

    /**
     * 构造场景。
     *
     * @param id 场景ID
     * @return 场景
     */
    private static TwinScene scene(Long id) {
        TwinScene scene = new TwinScene();
        scene.setId(id);
        scene.setCode("plant-demo");
        scene.setName("示范厂区");
        scene.setSceneConfig("{\"backgroundColor\":\"#1a2b3c\"}");
        return scene;
    }

    /**
     * 构造已发布场景。
     *
     * @param id 场景ID
     * @return 场景
     */
    private static TwinScene publishedScene() {
        TwinScene scene = scene(1L);
        scene.setVersion(1);
        scene.setStatus(TwinSceneService.STATUS_PUBLISHED);
        return scene;
    }

    /**
     * 构造停用场景。
     *
     * @param id 场景ID
     * @return 场景
     */
    private static TwinScene disabledScene(Long id) {
        TwinScene scene = scene(id);
        scene.setVersion(1);
        scene.setStatus(TwinSceneService.STATUS_DISABLED);
        return scene;
    }

    /**
     * 构造快照实体。
     *
     * @param id   快照ID
     * @param json 快照 JSON
     * @return 快照
     */
    private static TwinSnapshot snapshot(Long id, String json) {
        TwinSnapshot snapshot = new TwinSnapshot();
        snapshot.setId(id);
        snapshot.setSceneId(1L);
        snapshot.setVersion(1);
        snapshot.setSnapshotJson(json);
        return snapshot;
    }

    /**
     * 回滚用快照 JSON：锅炉（根）+ 泵（挂锅炉）+ 绑定 + 告警标注。
     *
     * @return JSON 串
     */
    private static String rollbackJson() {
        return "{\"scene\":{\"name\":\"示范厂区\",\"description\":\"回滚目标\","
                + "\"thumbnailFileId\":null,\"sceneConfig\":\"{}\"},"
                + "\"entities\":["
                + "{\"id\":11,\"entityCode\":\"boiler-01\",\"name\":\"锅炉\",\"parentId\":null,"
                + "\"modelFileId\":null,\"transformJson\":null,\"extraConfig\":null,\"sortOrder\":0},"
                + "{\"id\":12,\"entityCode\":\"pump-01\",\"name\":\"泵\",\"parentId\":11,"
                + "\"modelFileId\":null,\"transformJson\":null,\"extraConfig\":null,\"sortOrder\":1}],"
                + "\"bindings\":[{\"id\":21,\"entityId\":11,\"sourceType\":\"iot_device\","
                + "\"bindKey\":\"9001\",\"propertyMap\":null,\"pollIntervalMs\":5000,\"enabled\":1}],"
                + "\"markers\":[{\"id\":31,\"entityId\":12,\"markerType\":\"alarm\","
                + "\"content\":\"超温\",\"positionJson\":\"{\\\"x\\\":1}\","
                + "\"linkUrl\":null,\"enabled\":1}]}";
    }
}
