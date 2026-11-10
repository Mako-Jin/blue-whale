package com.bluewhale.twin.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.twin.dto.SceneSaveRequest;
import com.bluewhale.twin.dto.SceneVO;
import com.bluewhale.twin.entity.TwinScene;
import com.bluewhale.twin.mapper.TwinEntityMapper;
import com.bluewhale.twin.mapper.TwinSceneMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 场景服务单测：编码唯一、状态机、版本自增与删除卡口。
 *
 * <p>纯 Mockito 环境（无 Spring 上下文），LambdaQueryWrapper 依赖的实体
 * 元数据需在 {@code @BeforeAll} 手工注册（对齐 whale-iot / whale-contract 先例）。</p>
 *
 * @author BlueWhale
 */
class TwinSceneServiceTest {

    private TwinSceneMapper sceneMapper;

    private TwinEntityMapper entityMapper;

    private TwinSceneService service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), TwinScene.class);
    }

    @BeforeEach
    void setUp() {
        sceneMapper = mock(TwinSceneMapper.class);
        entityMapper = mock(TwinEntityMapper.class);
        service = new TwinSceneService(sceneMapper, entityMapper);
    }

    @Test
    void createRejectsDuplicateCode() {
        when(sceneMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> service.create(request("plant-demo"), 7L));

        assertTrue(exception.getMessage().contains("已存在"));
        verify(sceneMapper, never()).insert(any(TwinScene.class));
    }

    @Test
    void createDefaultsToDraftAndVersionOne() {
        when(sceneMapper.selectCount(any())).thenReturn(0L);

        SceneVO vo = service.create(request("plant-demo"), 7L);

        assertEquals("draft", vo.getStatus());
        assertEquals(1, vo.getVersion());
        verify(sceneMapper).insert(any(TwinScene.class));
    }

    @Test
    void updateRejectsPublishedScene() {
        when(sceneMapper.selectById(1L)).thenReturn(publishedScene());

        BizException exception = assertThrows(BizException.class,
                () -> service.update(1L, request("plant-demo")));

        assertTrue(exception.getMessage().contains("已发布"));
    }

    @Test
    void updateBumpsVersion() {
        TwinScene scene = publishedScene();
        scene.setStatus(TwinSceneService.STATUS_DISABLED);
        when(sceneMapper.selectById(1L)).thenReturn(scene);
        when(entityMapper.selectCount(any())).thenReturn(0L);

        SceneVO vo = service.update(1L, request("plant-demo"));

        assertEquals(2, vo.getVersion());
        verify(sceneMapper).updateById(scene);
    }

    @Test
    void deleteRejectsSceneWithEntities() {
        when(sceneMapper.selectById(1L)).thenReturn(publishedScene());
        when(entityMapper.selectCount(any())).thenReturn(3L);

        BizException exception = assertThrows(BizException.class, () -> service.delete(1L));

        assertTrue(exception.getMessage().contains("3 个实体"));
        verify(sceneMapper, never()).deleteById(Mockito.any(Serializable.class));
    }

    @Test
    void deleteRemovesEmptyScene() {
        when(sceneMapper.selectById(1L)).thenReturn(publishedScene());
        when(entityMapper.selectCount(any())).thenReturn(0L);

        service.delete(1L);

        verify(sceneMapper).deleteById(1L);
    }

    /**
     * 构造已发布场景。
     *
     * @return 场景实体
     */
    private static TwinScene publishedScene() {
        TwinScene scene = new TwinScene();
        scene.setId(1L);
        scene.setCode("plant-demo");
        scene.setName("示范厂区");
        scene.setVersion(1);
        scene.setStatus(TwinSceneService.STATUS_PUBLISHED);
        return scene;
    }

    /**
     * 构造保存请求。
     *
     * @param code 编码
     * @return 请求
     */
    private static SceneSaveRequest request(String code) {
        SceneSaveRequest request = new SceneSaveRequest();
        request.setCode(code);
        request.setName("示范厂区");
        return request;
    }
}
