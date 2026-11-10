package com.bluewhale.scene.application.service.impl;

import com.bluewhale.scene.application.support.NavigationUser;
import com.bluewhale.scene.application.view.NavigationSceneView;
import com.bluewhale.scene.config.SceneProperties;
import com.bluewhale.scene.domain.model.NodeBinding;
import com.bluewhale.scene.domain.model.Scene;
import com.bluewhale.scene.domain.model.SceneNode;
import com.bluewhale.scene.domain.model.SceneVisibility;
import com.bluewhale.scene.domain.repository.NodeBindingRepository;
import com.bluewhale.scene.domain.repository.SceneNodeRepository;
import com.bluewhale.scene.domain.repository.SceneRepository;
import com.bluewhale.scene.domain.repository.SceneVisibilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SceneNavigationAppServiceImpl} 单元测试（可见性与权限过滤）。
 *
 * @author BlueWhale
 */
class SceneNavigationAppServiceImplTest {

    private final SceneRepository sceneRepository = mock(SceneRepository.class);

    private final SceneNodeRepository sceneNodeRepository = mock(SceneNodeRepository.class);

    private final NodeBindingRepository nodeBindingRepository = mock(NodeBindingRepository.class);

    private final SceneVisibilityRepository sceneVisibilityRepository = mock(SceneVisibilityRepository.class);

    private final SceneProperties sceneProperties = new SceneProperties();

    private final SceneNavigationAppServiceImpl service = new SceneNavigationAppServiceImpl(sceneRepository,
            sceneNodeRepository, nodeBindingRepository, sceneVisibilityRepository, sceneProperties);

    private final Scene scene = scene(5001L, "bluewhale-city");

    @BeforeEach
    @DisplayName("准备已发布场景与节点树")
    void setUp() {
        when(sceneRepository.listPublished()).thenReturn(List.of(scene));
        when(sceneNodeRepository.listBySceneId(5001L)).thenReturn(List.of(
                node(5101L, "root", null, "REGION", 500, 500, 1),
                node(5102L, "oa", "root", "ENTRANCE", 250, 300, 2),
                node(5103L, "erp", "root", "ENTRANCE", 500, 300, 3),
                node(5104L, "erp-sub", "erp", "ENTRANCE", 600, 300, 4)));
        when(sceneVisibilityRepository.listBySceneId(5001L)).thenReturn(List.of());
    }

    @Test
    @DisplayName("默认可见：无规则时返回全部节点与入口")
    void shouldReturnAllWhenNoRules() {
        when(nodeBindingRepository.listByNodeIds(anyCollection())).thenReturn(List.of(
                binding(5102L, "/oa", null, true),
                binding(5103L, "/erp", "sys:erp:item:list", true)));

        List<NavigationSceneView> views = service.navigation(
                new NavigationUser(1L, Set.of("employee"), Set.of("sys:erp:item:list"), null));

        assertEquals(1, views.size());
        NavigationSceneView view = views.get(0);
        assertEquals("bluewhale-city", view.getSceneKey());
        assertEquals(1, view.getLayoutVersion());
        assertEquals(4, view.getNodes().size());
        assertTrue(view.getNodes().get(1).getEntries().get(0).getTargetPath().equals("/oa"));
        assertTrue(view.getNodes().get(2).getEntries().get(0).getTargetPath().equals("/erp"));
    }

    @Test
    @DisplayName("入口权限不足时不返回入口")
    void shouldFilterEntryByPermission() {
        when(nodeBindingRepository.listByNodeIds(anyCollection())).thenReturn(List.of(
                binding(5103L, "/erp", "sys:erp:item:list", true)));

        List<NavigationSceneView> views = service.navigation(
                new NavigationUser(1L, Set.of("employee"), Set.of(), null));

        assertTrue(views.get(0).getNodes().stream()
                .filter(item -> "erp".equals(item.getNodeKey()))
                .findFirst().orElseThrow().getEntries().isEmpty());
    }

    @Test
    @DisplayName("DENY 优先：命中用户时隐藏节点及其整棵子树")
    void shouldHideSubtreeWhenDenied() {
        when(sceneVisibilityRepository.listBySceneId(5001L)).thenReturn(List.of(
                visibility(5103L, "USER", "1", "DENY")));

        List<NavigationSceneView> views = service.navigation(
                new NavigationUser(1L, Set.of("employee"), Set.of(), null));

        assertEquals(List.of("root", "oa"), views.get(0).getNodes().stream()
                .map(item -> item.getNodeKey()).toList());
    }

    @Test
    @DisplayName("ALLOW 白名单：未命中角色时节点不可见")
    void shouldHideWhenAllowNotMatched() {
        when(sceneVisibilityRepository.listBySceneId(5001L)).thenReturn(List.of(
                visibility(5102L, "ROLE", "scene-admin", "ALLOW")));

        List<NavigationSceneView> views = service.navigation(
                new NavigationUser(1L, Set.of("employee"), Set.of(), null));

        assertTrue(views.get(0).getNodes().stream().noneMatch(item -> "oa".equals(item.getNodeKey())));
    }

    @Test
    @DisplayName("ALLOW 白名单：命中角色时节点可见")
    void shouldShowWhenAllowMatched() {
        when(sceneVisibilityRepository.listBySceneId(5001L)).thenReturn(List.of(
                visibility(5102L, "ROLE", "scene-admin", "ALLOW")));

        List<NavigationSceneView> views = service.navigation(
                new NavigationUser(1L, Set.of("scene-admin"), Set.of(), null));

        assertTrue(views.get(0).getNodes().stream().anyMatch(item -> "oa".equals(item.getNodeKey())));
    }

    @Test
    @DisplayName("场景级 DENY 与白名单：不返回该场景")
    void shouldSkipSceneWhenDenied() {
        when(sceneVisibilityRepository.listBySceneId(5001L)).thenReturn(List.of(
                visibility(0L, "ALL", null, "DENY")));
        assertTrue(service.navigation(new NavigationUser(1L, Set.of(), Set.of(), null)).isEmpty());

        when(sceneVisibilityRepository.listBySceneId(5001L)).thenReturn(List.of(
                visibility(0L, "ROLE", "scene-admin", "ALLOW")));
        assertTrue(service.navigation(new NavigationUser(1L, Set.of("employee"), Set.of(), null)).isEmpty());
        assertEquals(1, service.navigation(
                new NavigationUser(1L, Set.of("scene-admin"), Set.of(), null)).size());
    }

    @Test
    @DisplayName("禁用入口不返回")
    void shouldHideDisabledBinding() {
        when(nodeBindingRepository.listByNodeIds(anyCollection())).thenReturn(List.of(
                binding(5102L, "/oa", null, false)));

        List<NavigationSceneView> views = service.navigation(
                new NavigationUser(1L, Set.of(), Set.of(), null));

        assertTrue(views.get(0).getNodes().stream()
                .filter(item -> "oa".equals(item.getNodeKey()))
                .findFirst().orElseThrow().getEntries().isEmpty());
    }

    @Test
    @DisplayName("节点返回上限生效")
    void shouldApplyNodeLimit() {
        sceneProperties.setNavigationNodeLimit(2);
        when(nodeBindingRepository.listByNodeIds(any())).thenReturn(List.of());

        assertEquals(2, service.navigation(new NavigationUser(1L, Set.of(), Set.of(), null))
                .get(0).getNodes().size());
    }

    /**
     * 构造场景。
     *
     * @param id 场景 ID
     * @param sceneKey 场景标识
     * @return 场景
     */
    private Scene scene(Long id, String sceneKey) {
        Scene item = new Scene();
        item.setId(id);
        item.setSceneKey(sceneKey);
        item.setName("蓝鲸城");
        item.setStatus("PUBLISHED");
        item.setLayoutVersion(1);
        return item;
    }

    /**
     * 构造节点。
     *
     * @param id 节点 ID
     * @param nodeKey 节点标识
     * @param parentKey 父节点标识
     * @param nodeType 节点类型
     * @param x 横坐标
     * @param y 纵坐标
     * @param sortNo 排序号
     * @return 节点
     */
    private SceneNode node(Long id, String nodeKey, String parentKey, String nodeType,
                           int x, int y, int sortNo) {
        SceneNode item = new SceneNode();
        item.setId(id);
        item.setSceneId(5001L);
        item.setNodeKey(nodeKey);
        item.setParentKey(parentKey);
        item.setNodeType(nodeType);
        item.setName(nodeKey);
        item.setX(x);
        item.setY(y);
        item.setSortNo(sortNo);
        return item;
    }

    /**
     * 构造入口绑定。
     *
     * @param nodeId 节点 ID
     * @param targetPath 目标地址
     * @param permissionCode 权限码
     * @param enabled 是否启用
     * @return 绑定
     */
    private NodeBinding binding(Long nodeId, String targetPath, String permissionCode, boolean enabled) {
        NodeBinding item = new NodeBinding();
        item.setId(nodeId);
        item.setNodeId(nodeId);
        item.setEntryType("SYSTEM");
        item.setTargetPath(targetPath);
        item.setPermissionCode(permissionCode);
        item.setEnabled(enabled);
        return item;
    }

    /**
     * 构造可见性规则。
     *
     * @param nodeId 节点 ID
     * @param subjectType 主体类型
     * @param subjectId 主体标识
     * @param effect 效果
     * @return 规则
     */
    private SceneVisibility visibility(Long nodeId, String subjectType, String subjectId, String effect) {
        SceneVisibility item = new SceneVisibility();
        item.setSceneId(5001L);
        item.setNodeId(nodeId);
        item.setSubjectType(subjectType);
        item.setSubjectId(subjectId);
        item.setEffect(effect);
        return item;
    }
}
