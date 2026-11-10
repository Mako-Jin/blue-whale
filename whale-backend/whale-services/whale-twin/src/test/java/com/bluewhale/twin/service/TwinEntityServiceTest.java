package com.bluewhale.twin.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.twin.dto.EntitySaveRequest;
import com.bluewhale.twin.dto.EntityVO;
import com.bluewhale.twin.entity.TwinEntity;
import com.bluewhale.twin.entity.TwinScene;
import com.bluewhale.twin.mapper.TwinEntityMapper;
import com.bluewhale.twin.mapper.TwinSceneMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实体服务单测：编码唯一、场景归属、环检测与树装配。
 *
 * @author BlueWhale
 */
class TwinEntityServiceTest {

    private TwinEntityMapper entityMapper;

    private TwinSceneService sceneService;

    private TwinEntityService service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), TwinEntity.class);
    }

    @BeforeEach
    void setUp() {
        entityMapper = mock(TwinEntityMapper.class);
        sceneService = mock(TwinSceneService.class);
        service = new TwinEntityService(entityMapper, sceneService);
    }

    @Test
    void createRejectsDuplicateCodeInScene() {
        when(entityMapper.selectList(any())).thenReturn(List.of(entity(10L, "boiler-01", null)));

        BizException exception = assertThrows(BizException.class,
                () -> service.create(1L, request("boiler-01", null)));

        assertTrue(exception.getMessage().contains("已存在"));
        verify(entityMapper, never()).insert(any(TwinEntity.class));
    }

    @Test
    void createRejectsParentFromOtherScene() {
        when(entityMapper.selectList(any())).thenReturn(List.of());
        TwinEntity foreign = entity(20L, "other", null);
        foreign.setSceneId(2L);
        when(entityMapper.selectById(20L)).thenReturn(foreign);

        BizException exception = assertThrows(BizException.class,
                () -> service.create(1L, request("boiler-01", 20L)));

        assertTrue(exception.getMessage().contains("不属于当前场景"));
    }

    @Test
    void updateRejectsCycleToOwnDescendant() {
        // 树：10(根) → 11(子)；把 10 移到 11 下应报环
        TwinEntity root = entity(10L, "root", null);
        TwinEntity child = entity(11L, "child", 10L);
        when(entityMapper.selectList(any())).thenReturn(List.of(root, child));
        when(entityMapper.selectById(10L)).thenReturn(root);
        when(entityMapper.selectById(11L)).thenReturn(child);

        BizException exception = assertThrows(BizException.class,
                () -> service.update(1L, 10L, request("root", 11L)));

        assertTrue(exception.getMessage().contains("环"));
    }

    @Test
    void updateAllowsMoveToSiblingSubtree() {
        // 树：10(根) → 11(子)、12(子)；把 12 移到 11 下合法
        TwinEntity root = entity(10L, "root", null);
        TwinEntity child = entity(11L, "child", 10L);
        TwinEntity sibling = entity(12L, "sibling", 10L);
        when(entityMapper.selectList(any())).thenReturn(List.of(root, child, sibling));
        when(entityMapper.selectById(10L)).thenReturn(root);
        when(entityMapper.selectById(11L)).thenReturn(child);
        when(entityMapper.selectById(12L)).thenReturn(sibling);

        EntityVO vo = service.update(1L, 12L, request("sibling", 11L));

        assertEquals("11", vo.getParentId());
        verify(entityMapper).updateById(sibling);
    }

    @Test
    void deleteRejectsNodeWithChildren() {
        TwinEntity parent = entity(10L, "root", null);
        when(entityMapper.selectById(10L)).thenReturn(parent);
        when(entityMapper.selectCount(any())).thenReturn(2L);

        BizException exception = assertThrows(BizException.class,
                () -> service.delete(1L, 10L));

        assertTrue(exception.getMessage().contains("2 个子节点"));
    }

    @Test
    void treeAssemblesHierarchyAndSort() {
        // sortOrder：11(1) < 12(0)? 不——12 排前；根下应按 sortOrder 升序
        TwinEntity root = entity(10L, "root", null);
        root.setSortOrder(0);
        TwinEntity first = entity(11L, "first", 10L);
        first.setSortOrder(0);
        TwinEntity second = entity(12L, "second", 10L);
        second.setSortOrder(5);
        when(entityMapper.selectList(any())).thenReturn(List.of(root, second, first));

        List<EntityVO> tree = service.tree(1L);

        assertEquals(1, tree.size());
        assertEquals(2, tree.get(0).getChildren().size());
        assertEquals("11", tree.get(0).getChildren().get(0).getId());
    }

    @Test
    void orphanEntityFallsBackToRoot() {
        TwinEntity orphan = entity(30L, "orphan", 99L);
        when(entityMapper.selectList(any())).thenReturn(List.of(orphan));

        List<EntityVO> tree = service.tree(1L);

        assertEquals(1, tree.size());
        assertEquals("30", tree.get(0).getId());
    }

    /**
     * 构造属于场景 1 的实体。
     *
     * @param id   实体ID
     * @param code 编码
     * @param pid  父ID
     * @return 实体
     */
    private static TwinEntity entity(Long id, String code, Long pid) {
        TwinEntity entity = new TwinEntity();
        entity.setId(id);
        entity.setSceneId(1L);
        entity.setEntityCode(code);
        entity.setName(code);
        entity.setParentId(pid);
        entity.setSortOrder(0);
        return entity;
    }

    /**
     * 构造保存请求。
     *
     * @param code 编码
     * @param pid  父ID
     * @return 请求
     */
    private static EntitySaveRequest request(String code, Long pid) {
        EntitySaveRequest request = new EntitySaveRequest();
        request.setEntityCode(code);
        request.setName(code);
        request.setParentId(pid);
        return request;
    }
}
