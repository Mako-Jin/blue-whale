package com.bluewhale.twin.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.dto.BindingSaveRequest;
import com.bluewhale.twin.dto.BindingVO;
import com.bluewhale.twin.entity.TwinBinding;
import com.bluewhale.twin.entity.TwinEntity;
import com.bluewhale.twin.mapper.TwinBindingMapper;
import com.bluewhale.twin.mapper.TwinEntityMapper;
import com.bluewhale.twin.support.InternalApiStateClient;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 绑定服务单测：场景归属、绑定键形态、属性映射校验与类型不可改。
 *
 * @author BlueWhale
 */
class TwinBindingServiceTest {

    private static final Long SCENE_ID = 100L;

    private static final Long ENTITY_ID = 200L;

    private TwinBindingMapper bindingMapper;

    private TwinEntityMapper entityMapper;

    private InternalApiStateClient internalApiStateClient;

    private TwinBindingService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, TwinBinding.class);
        TableInfoHelper.initTableInfo(assistant, TwinEntity.class);
    }

    @BeforeEach
    void setUp() {
        bindingMapper = mock(TwinBindingMapper.class);
        entityMapper = mock(TwinEntityMapper.class);
        internalApiStateClient = new InternalApiStateClient(new TwinProperties());
        service = new TwinBindingService(bindingMapper, entityMapper,
                internalApiStateClient, new TwinProperties());
    }

    @Test
    void createSucceedsWithValidRequest() {
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(entity());
        when(bindingMapper.insert(any(TwinBinding.class))).thenAnswer(invocation -> {
            TwinBinding binding = invocation.getArgument(0);
            binding.setId(1L);
            return 1;
        });

        BindingVO vo = service.create(SCENE_ID, request());

        assertEquals("1", vo.getId());
        assertEquals("200", vo.getEntityId());
        assertEquals("boiler-01", vo.getEntityCode());
        assertEquals(5000, vo.getPollIntervalMs());
        assertEquals(1, vo.getEnabled());
    }

    @Test
    void createRejectsEntityOutsideScene() {
        TwinEntity foreign = entity();
        foreign.setSceneId(999L);
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(foreign);

        assertThrows(BizException.class, () -> service.create(SCENE_ID, request()));
        verify(bindingMapper, never()).insert(any(TwinBinding.class));
    }

    @Test
    void createRejectsNonNumericDeviceKey() {
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(entity());
        BindingSaveRequest request = request();
        request.setBindKey("not-a-device-id");

        assertThrows(BizException.class, () -> service.create(SCENE_ID, request));
    }

    @Test
    void createRejectsNonObjectPropertyMap() {
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(entity());
        BindingSaveRequest request = request();
        request.setPropertyMap("[1,2,3]");

        assertThrows(BizException.class, () -> service.create(SCENE_ID, request));
    }

    @Test
    void updateRejectsSourceTypeChange() {
        TwinBinding existing = binding();
        when(bindingMapper.selectById(1L)).thenReturn(existing);
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(entity());
        BindingSaveRequest request = request();
        request.setSourceType(TwinBindingService.SOURCE_INTERNAL_API);

        assertThrows(BizException.class, () -> service.update(SCENE_ID, 1L, request));
    }

    @Test
    void deleteRequiresBindingInScene() {
        TwinBinding existing = binding();
        when(bindingMapper.selectById(1L)).thenReturn(existing);
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(null);

        assertThrows(BizException.class, () -> service.delete(SCENE_ID, 1L));
        verify(bindingMapper, never()).deleteById(1L);
    }

    @Test
    void listSkipsBindingsWithDeletedEntity() {
        TwinEntity entity = entity();
        when(entityMapper.selectList(any())).thenReturn(List.of(entity));
        TwinBinding kept = binding();
        TwinBinding orphaned = binding();
        orphaned.setId(2L);
        orphaned.setEntityId(999L);
        when(bindingMapper.selectList(any())).thenReturn(List.of(kept, orphaned));
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(entity);
        when(entityMapper.selectById(999L)).thenReturn(null);

        List<BindingVO> result = service.list(SCENE_ID);

        assertEquals(1, result.size());
        assertEquals("1", result.get(0).getId());
    }

    @Test
    void listReturnsEmptyForEmptyScene() {
        when(entityMapper.selectList(any())).thenReturn(List.of());

        assertNotNull(service.list(SCENE_ID));
        verify(bindingMapper, never()).selectList(any());
    }

    @Test
    void createAcceptsWhitelistedInternalApiBindKey() {
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(entity());
        when(bindingMapper.insert(any(TwinBinding.class))).thenAnswer(invocation -> {
            TwinBinding binding = invocation.getArgument(0);
            binding.setId(9L);
            return 1;
        });
        BindingSaveRequest request = request();
        request.setSourceType(TwinBindingService.SOURCE_INTERNAL_API);
        request.setBindKey("/iot/internal/device/state?deviceId=300");

        BindingVO vo = service.create(SCENE_ID, request);

        assertEquals("9", vo.getId());
        assertEquals("/iot/internal/device/state?deviceId=300", vo.getBindKey());
    }

    @Test
    void createRejectsInternalApiBindKeyOutsideWhitelist() {
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(entity());
        BindingSaveRequest request = request();
        request.setSourceType(TwinBindingService.SOURCE_INTERNAL_API);
        request.setBindKey("/system/admin/users");

        assertThrows(BizException.class, () -> service.create(SCENE_ID, request));
    }

    @Test
    void createRejectsInternalApiPathTraversal() {
        when(entityMapper.selectById(ENTITY_ID)).thenReturn(entity());
        BindingSaveRequest request = request();
        request.setSourceType(TwinBindingService.SOURCE_INTERNAL_API);
        request.setBindKey("/iot/internal/../admin/users");

        assertThrows(BizException.class, () -> service.create(SCENE_ID, request));
    }

    @Test
    void listScenesWithEnabledBindingsDedupesAndCapsScenes() {
        TwinBinding first = binding();
        TwinBinding second = binding();
        second.setId(2L);
        when(bindingMapper.selectList(any())).thenReturn(List.of(first, second));
        TwinEntity sameScene = entity();
        TwinEntity otherScene = entity();
        otherScene.setId(201L);
        otherScene.setSceneId(101L);
        when(entityMapper.selectList(any())).thenReturn(List.of(sameScene, otherScene, entity()));

        List<Long> scenes = service.listScenesWithEnabledBindings(10);
        List<Long> capped = service.listScenesWithEnabledBindings(1);

        assertEquals(List.of(SCENE_ID, 101L), scenes);
        assertEquals(List.of(SCENE_ID), capped);
    }

    @Test
    void listScenesWithEnabledBindingsReturnsEmptyWhenNoBindings() {
        when(bindingMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.listScenesWithEnabledBindings(10).isEmpty());
        verify(entityMapper, never()).selectList(any());
    }

    /**
     * 构造场景内实体。
     *
     * @return 实体
     */
    private static TwinEntity entity() {
        TwinEntity entity = new TwinEntity();
        entity.setId(ENTITY_ID);
        entity.setSceneId(SCENE_ID);
        entity.setEntityCode("boiler-01");
        entity.setName("一号锅炉");
        entity.setTenantId(1L);
        entity.setCreateTime(LocalDateTime.of(2026, 11, 10, 9, 0));
        return entity;
    }

    /**
     * 构造既有绑定。
     *
     * @return 绑定
     */
    private static TwinBinding binding() {
        TwinBinding binding = new TwinBinding();
        binding.setId(1L);
        binding.setEntityId(ENTITY_ID);
        binding.setSourceType(TwinBindingService.SOURCE_IOT_DEVICE);
        binding.setBindKey("300");
        binding.setPropertyMap("{\"temperature\":{\"stateKey\":\"color\",\"thresholds\":[50,80]}}");
        binding.setPollIntervalMs(5000);
        binding.setEnabled(1);
        binding.setCreateTime(LocalDateTime.of(2026, 11, 10, 9, 0));
        return binding;
    }

    /**
     * 构造保存请求。
     *
     * @return 请求
     */
    private static BindingSaveRequest request() {
        BindingSaveRequest request = new BindingSaveRequest();
        request.setEntityId(ENTITY_ID);
        request.setSourceType(TwinBindingService.SOURCE_IOT_DEVICE);
        request.setBindKey("300");
        request.setPropertyMap("{\"temperature\":\"color\"}");
        request.setPollIntervalMs(null);
        request.setEnabled(null);
        return request;
    }
}
