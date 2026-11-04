package com.bluewhale.scene.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.scene.application.command.NodeBindingSaveCommand;
import com.bluewhale.scene.application.command.SceneNodeSaveCommand;
import com.bluewhale.scene.application.command.SceneQueryCommand;
import com.bluewhale.scene.application.command.SceneSaveCommand;
import com.bluewhale.scene.application.command.SceneVisibilityCommand;
import com.bluewhale.scene.application.view.SceneDetailView;
import com.bluewhale.scene.application.view.SceneNodeView;
import com.bluewhale.scene.application.view.SceneView;
import com.bluewhale.scene.config.SceneProperties;
import com.bluewhale.scene.domain.exception.SceneErrorCode;
import com.bluewhale.scene.domain.model.LayoutVersion;
import com.bluewhale.scene.domain.model.NodeBinding;
import com.bluewhale.scene.domain.model.Scene;
import com.bluewhale.scene.domain.model.SceneNode;
import com.bluewhale.scene.domain.model.SceneStatus;
import com.bluewhale.scene.domain.model.SceneVisibility;
import com.bluewhale.scene.domain.repository.LayoutVersionRepository;
import com.bluewhale.scene.domain.repository.NodeBindingRepository;
import com.bluewhale.scene.domain.repository.SceneNodeRepository;
import com.bluewhale.scene.domain.repository.SceneRepository;
import com.bluewhale.scene.domain.repository.SceneVisibilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SceneAppServiceImpl} 单元测试（场景 / 节点 / 入口 / 可见性）。
 *
 * @author BlueWhale
 */
class SceneAppServiceImplTest {

    private final SceneRepository sceneRepository = mock(SceneRepository.class);

    private final SceneNodeRepository sceneNodeRepository = mock(SceneNodeRepository.class);

    private final NodeBindingRepository nodeBindingRepository = mock(NodeBindingRepository.class);

    private final SceneVisibilityRepository sceneVisibilityRepository = mock(SceneVisibilityRepository.class);

    private final LayoutVersionRepository layoutVersionRepository = mock(LayoutVersionRepository.class);

    private final SceneProperties sceneProperties = new SceneProperties();

    private final SceneAppServiceImpl service = new SceneAppServiceImpl(sceneRepository, sceneNodeRepository,
            nodeBindingRepository, sceneVisibilityRepository, layoutVersionRepository, sceneProperties);

    private final Scene scene = new Scene();

    @BeforeEach
    @DisplayName("准备场景基础桩")
    void setUp() {
        scene.setId(5001L);
        scene.setSceneKey("bluewhale-city");
        scene.setName("蓝鲸城");
        scene.setStatus(SceneStatus.DRAFT);
        scene.setLayoutVersion(0);
        when(sceneRepository.findById(anyLong())).thenReturn(Optional.of(scene));
        when(sceneRepository.page(any())).thenReturn(List.of(scene));
        when(sceneRepository.count(any())).thenReturn(1L);
        when(sceneNodeRepository.listBySceneId(5001L)).thenReturn(List.of(
                node(5101L, "root", null, "REGION", 500, 500, 1),
                node(5102L, "oa", "root", "ENTRANCE", 250, 300, 2)));
        when(sceneNodeRepository.findById(5101L)).thenReturn(Optional.of(
                node(5101L, "root", null, "REGION", 500, 500, 1)));
        when(sceneNodeRepository.findById(5102L)).thenReturn(Optional.of(
                node(5102L, "oa", "root", "ENTRANCE", 250, 300, 2)));
        when(nodeBindingRepository.listByNodeIds(anyCollection())).thenReturn(List.of());
        when(sceneVisibilityRepository.listBySceneId(5001L)).thenReturn(List.of());
        when(layoutVersionRepository.findLatest(5001L)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("场景分页返回视图")
    void shouldPageScenes() {
        PageResult<SceneView> page = service.page(new SceneQueryCommand());
        assertEquals(1, page.getTotal());
        assertEquals("bluewhale-city", page.getRecords().get(0).getSceneKey());
    }

    @Test
    @DisplayName("场景详情包含节点与空绑定")
    void shouldLoadDetail() {
        SceneDetailView detail = service.detail(5001L);
        assertEquals(2, detail.getNodes().size());
        assertTrue(detail.getBindings().isEmpty());
        assertTrue(detail.getVisibilities().isEmpty());
    }

    @Test
    @DisplayName("新建场景成功")
    void shouldCreateScene() {
        doAnswer(invocation -> {
            Scene created = invocation.getArgument(0);
            created.setId(5002L);
            return null;
        }).when(sceneRepository).insert(any(Scene.class));

        SceneView view = service.create(command("new-city", "新城池"));

        assertEquals(5002L, view.getId());
        assertEquals("DRAFT", view.getStatus());
        assertEquals(0, view.getLayoutVersion());
    }

    @Test
    @DisplayName("场景标识重复时拒绝新建")
    void shouldRejectDuplicatedSceneKey() {
        when(sceneRepository.findByKey("bluewhale-city")).thenReturn(Optional.of(scene));
        BizException exception = assertThrows(BizException.class,
                () -> service.create(command("bluewhale-city", "重复")));
        assertEquals(SceneErrorCode.SCENE_KEY_CONFLICT, exception.getCode());
        verify(sceneRepository, never()).insert(any(Scene.class));
    }

    @Test
    @DisplayName("已发布场景禁止修改标识")
    void shouldRejectSceneKeyChangeWhenPublished() {
        scene.setStatus(SceneStatus.PUBLISHED);
        assertEquals(SceneErrorCode.SCENE_STATE_CONFLICT,
                assertThrows(BizException.class,
                        () -> service.update(5001L, command("other-city", "改名"))).getCode());
    }

    @Test
    @DisplayName("发布成功：生成布局快照并递增版本")
    void shouldPublishScene() {
        when(nodeBindingRepository.listByNodeIds(anyCollection())).thenReturn(List.of(
                binding(5102L, "/oa", null, true)));
        doAnswer(invocation -> {
            LayoutVersion snapshot = invocation.getArgument(0);
            snapshot.setId(9001L);
            return null;
        }).when(layoutVersionRepository).insert(any(LayoutVersion.class));

        SceneView view = service.publish(5001L);

        assertEquals(SceneStatus.PUBLISHED, view.getStatus());
        assertEquals(1, view.getLayoutVersion());
        assertNotNull(view.getPublishedAt());
        ArgumentCaptor<LayoutVersion> captor = ArgumentCaptor.forClass(LayoutVersion.class);
        verify(layoutVersionRepository).insert(captor.capture());
        assertEquals(1, captor.getValue().getVersion());
        assertEquals(64, captor.getValue().getChecksum().length());
        assertTrue(captor.getValue().getLayoutJson().contains("/oa"));
    }

    @Test
    @DisplayName("发布校验：无节点 / 无启用入口 / 非法地址均拒绝")
    void shouldRejectInvalidPublish() {
        when(sceneNodeRepository.listBySceneId(5001L)).thenReturn(List.of());
        assertEquals(SceneErrorCode.BINDING_REQUIRED,
                assertThrows(BizException.class, () -> service.publish(5001L)).getCode());

        when(sceneNodeRepository.listBySceneId(5001L)).thenReturn(List.of(
                node(5101L, "root", null, "REGION", 500, 500, 1)));
        when(nodeBindingRepository.listByNodeIds(anyCollection())).thenReturn(List.of(
                binding(5101L, "/oa", null, false)));
        assertEquals(SceneErrorCode.BINDING_REQUIRED,
                assertThrows(BizException.class, () -> service.publish(5001L)).getCode());

        when(nodeBindingRepository.listByNodeIds(anyCollection())).thenReturn(List.of(
                binding(5101L, "javascript:alert(1)", null, true)));
        assertEquals(SceneErrorCode.BINDING_URL_INVALID,
                assertThrows(BizException.class, () -> service.publish(5001L)).getCode());
        verify(layoutVersionRepository, never()).insert(any(LayoutVersion.class));
    }

    @Test
    @DisplayName("发布校验：节点数超限拒绝")
    void shouldRejectTooManyNodes() {
        sceneProperties.setMaxNodesPerScene(1);
        assertEquals(SceneErrorCode.NODE_COUNT_EXCEEDED,
                assertThrows(BizException.class, () -> service.publish(5001L)).getCode());
    }

    @Test
    @DisplayName("新建节点：重复标识拒绝，成功写入默认状态")
    void shouldCreateNode() {
        SceneNodeSaveCommand command = nodeCommand("oa");
        assertEquals(SceneErrorCode.NODE_KEY_CONFLICT,
                assertThrows(BizException.class, () -> service.saveNode(5001L, null, command)).getCode());

        when(sceneNodeRepository.listBySceneId(5001L)).thenReturn(List.of());
        doAnswer(invocation -> {
            SceneNode created = invocation.getArgument(0);
            created.setId(5105L);
            return null;
        }).when(sceneNodeRepository).insert(any(SceneNode.class));
        SceneNodeSaveCommand fresh = nodeCommand("hall");
        SceneNodeView view = service.saveNode(5001L, null, fresh);
        assertEquals(5105L, view.getId());
        assertEquals("hall", view.getNodeKey());
        assertEquals("ENABLED", view.getStatus());
    }

    @Test
    @DisplayName("编辑节点：父节点不存在或形成环时拒绝")
    void shouldRejectNodeCycle() {
        SceneNodeSaveCommand cycle = nodeCommand("root");
        cycle.setParentKey("oa");
        assertEquals(SceneErrorCode.NODE_CYCLE,
                assertThrows(BizException.class, () -> service.saveNode(5001L, 5101L, cycle)).getCode());

        SceneNodeSaveCommand missingParent = nodeCommand("oa");
        missingParent.setParentKey("ghost");
        assertEquals(SceneErrorCode.NODE_INVALID,
                assertThrows(BizException.class, () -> service.saveNode(5001L, 5102L, missingParent)).getCode());
    }

    @Test
    @DisplayName("删除节点：存在子节点时拒绝")
    void shouldRejectDeleteWithChildren() {
        assertEquals(SceneErrorCode.NODE_HAS_CHILDREN,
                assertThrows(BizException.class, () -> service.deleteNode(5101L)).getCode());
        verify(sceneNodeRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("入口绑定：新增与覆盖，地址非法拒绝")
    void shouldSaveBinding() {
        when(nodeBindingRepository.findByNodeId(5102L)).thenReturn(Optional.empty());
        NodeBindingSaveCommand command = new NodeBindingSaveCommand();
        command.setEntryType("SYSTEM");
        command.setTargetPath("/oa");
        service.saveBinding(5102L, command);
        verify(nodeBindingRepository).insert(any(NodeBinding.class));

        NodeBinding existing = binding(5102L, "/erp", null, true);
        existing.setId(5201L);
        when(nodeBindingRepository.findByNodeId(5102L)).thenReturn(Optional.of(existing));
        command.setTargetPath("https://example.com/erp");
        service.saveBinding(5102L, command);
        verify(nodeBindingRepository).update(any(NodeBinding.class));

        command.setTargetPath("http://127.0.0.1/erp");
        assertEquals(SceneErrorCode.BINDING_URL_INVALID,
                assertThrows(BizException.class, () -> service.saveBinding(5102L, command)).getCode());
    }

    @Test
    @DisplayName("可见性整表替换：合法规则落库，非法主体拒绝")
    void shouldReplaceVisibility() {
        SceneVisibilityCommand rule = new SceneVisibilityCommand();
        rule.setNodeId(5102L);
        rule.setSubjectType("ROLE");
        rule.setSubjectId("scene-admin");
        rule.setEffect("DENY");
        assertEquals(1, service.replaceVisibility(5001L, List.of(rule)).size());
        verify(sceneVisibilityRepository).deleteBySceneId(5001L);

        rule.setSubjectType("UNKNOWN");
        assertEquals(SceneErrorCode.VISIBILITY_INVALID,
                assertThrows(BizException.class,
                        () -> service.replaceVisibility(5001L, List.of(rule))).getCode());
    }

    @Test
    @DisplayName("下线：仅已发布场景允许")
    void shouldOfflinePublishedScene() {
        assertEquals(SceneErrorCode.SCENE_STATE_CONFLICT,
                assertThrows(BizException.class, () -> service.offline(5001L)).getCode());

        scene.setStatus(SceneStatus.PUBLISHED);
        assertEquals(SceneStatus.OFFLINE, service.offline(5001L).getStatus());
    }

    @Test
    @DisplayName("场景不存在返回 404 语义")
    void shouldRejectMissingScene() {
        when(sceneRepository.findById(6001L)).thenReturn(Optional.empty());
        assertEquals(SceneErrorCode.SCENE_NOT_FOUND,
                assertThrows(BizException.class, () -> service.detail(6001L)).getCode());
    }

    /**
     * 构造场景保存命令。
     *
     * @param sceneKey 场景标识
     * @param name 场景名称
     * @return 命令
     */
    private SceneSaveCommand command(String sceneKey, String name) {
        SceneSaveCommand command = new SceneSaveCommand();
        command.setSceneKey(sceneKey);
        command.setName(name);
        return command;
    }

    /**
     * 构造节点保存命令。
     *
     * @param nodeKey 节点标识
     * @return 命令
     */
    private SceneNodeSaveCommand nodeCommand(String nodeKey) {
        SceneNodeSaveCommand command = new SceneNodeSaveCommand();
        command.setNodeKey(nodeKey);
        command.setNodeType("ENTRANCE");
        command.setName(nodeKey);
        command.setX(100);
        command.setY(100);
        return command;
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
        item.setNodeId(nodeId);
        item.setEntryType("SYSTEM");
        item.setTargetPath(targetPath);
        item.setPermissionCode(permissionCode);
        item.setEnabled(enabled);
        return item;
    }
}
