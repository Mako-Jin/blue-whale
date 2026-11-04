package com.bluewhale.scene.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.scene.application.command.NodeBindingSaveCommand;
import com.bluewhale.scene.application.command.SceneNodeSaveCommand;
import com.bluewhale.scene.application.command.SceneQueryCommand;
import com.bluewhale.scene.application.command.SceneSaveCommand;
import com.bluewhale.scene.application.command.SceneVisibilityCommand;
import com.bluewhale.scene.application.service.SceneAppService;
import com.bluewhale.scene.application.support.SceneLayoutSnapshot;
import com.bluewhale.scene.application.support.SceneValidator;
import com.bluewhale.scene.application.support.SceneViewAssembler;
import com.bluewhale.scene.application.view.LayoutSnapshotView;
import com.bluewhale.scene.application.view.NodeBindingView;
import com.bluewhale.scene.application.view.SceneDetailView;
import com.bluewhale.scene.application.view.SceneNodeView;
import com.bluewhale.scene.application.view.SceneView;
import com.bluewhale.scene.application.view.SceneVisibilityView;
import com.bluewhale.scene.config.SceneProperties;
import com.bluewhale.scene.domain.exception.SceneErrorCode;
import com.bluewhale.scene.domain.model.LayoutVersion;
import com.bluewhale.scene.domain.model.NodeBinding;
import com.bluewhale.scene.domain.model.Scene;
import com.bluewhale.scene.domain.model.SceneNode;
import com.bluewhale.scene.domain.model.SceneQuery;
import com.bluewhale.scene.domain.model.SceneStatus;
import com.bluewhale.scene.domain.model.SceneVisibility;
import com.bluewhale.scene.domain.repository.LayoutVersionRepository;
import com.bluewhale.scene.domain.repository.NodeBindingRepository;
import com.bluewhale.scene.domain.repository.SceneNodeRepository;
import com.bluewhale.scene.domain.repository.SceneRepository;
import com.bluewhale.scene.domain.repository.SceneVisibilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 场景管理用例实现。
 *
 * <p>事务只包住本地聚合加载 → 领域行为 → 保存；发布快照与场景状态更新在同一事务内，
 * 保证版本号与快照一致。跨服务调用本服务不发起，入口只保存地址与权限码。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class SceneAppServiceImpl implements SceneAppService {

    /** 节点默认状态 */
    private static final String NODE_ENABLED = "ENABLED";

    private final SceneRepository sceneRepository;

    private final SceneNodeRepository sceneNodeRepository;

    private final NodeBindingRepository nodeBindingRepository;

    private final SceneVisibilityRepository sceneVisibilityRepository;

    private final LayoutVersionRepository layoutVersionRepository;

    private final SceneProperties sceneProperties;

    @Override
    public PageResult<SceneView> page(SceneQueryCommand command) {
        SceneQueryCommand query = command == null ? new SceneQueryCommand() : command;
        SceneQuery domainQuery = query.toQuery();
        List<SceneView> records = sceneRepository.page(domainQuery).stream()
                .map(SceneViewAssembler::toView)
                .toList();
        return PageResult.of(records, sceneRepository.count(domainQuery),
                domainQuery.getCurrent(), domainQuery.getSize());
    }

    @Override
    public SceneDetailView detail(Long id) {
        Scene scene = requireScene(id);
        List<SceneNode> nodes = sceneNodeRepository.listBySceneId(id);
        List<SceneNode> finalNodes = nodes;
        List<NodeBinding> bindings = nodeBindingRepository.listByNodeIds(
                finalNodes.stream().map(SceneNode::getId).toList());
        List<SceneVisibility> visibilities = sceneVisibilityRepository.listBySceneId(id);

        SceneDetailView detail = new SceneDetailView();
        detail.setScene(SceneViewAssembler.toView(scene));
        detail.setNodes(finalNodes.stream().map(SceneViewAssembler::toView).toList());
        detail.setBindings(bindings.stream().map(SceneViewAssembler::toView).toList());
        detail.setVisibilities(visibilities.stream().map(SceneViewAssembler::toView).toList());
        detail.setLatestLayout(layoutView(layoutVersionRepository.findLatest(id).orElse(null)));
        return detail;
    }

    @Override
    public SceneView create(SceneSaveCommand command) {
        SceneValidator.validateSceneKey(command.getSceneKey());
        requireName(command.getName());
        sceneRepository.findByKey(command.getSceneKey()).ifPresent(existing -> {
            throw SceneErrorCode.exception(SceneErrorCode.SCENE_KEY_CONFLICT);
        });
        Scene scene = new Scene();
        scene.setSceneKey(command.getSceneKey());
        scene.setName(command.getName().trim());
        scene.setDescription(command.getDescription());
        scene.setStatus(SceneStatus.DRAFT);
        scene.setLayoutVersion(0);
        sceneRepository.insert(scene);
        return SceneViewAssembler.toView(scene);
    }

    @Override
    public SceneView update(Long id, SceneSaveCommand command) {
        Scene scene = requireScene(id);
        SceneValidator.validateSceneKey(command.getSceneKey());
        requireName(command.getName());
        if (SceneStatus.PUBLISHED.equals(scene.getStatus())
                && !scene.getSceneKey().equals(command.getSceneKey())) {
            throw SceneErrorCode.exception(SceneErrorCode.SCENE_STATE_CONFLICT);
        }
        if (!scene.getSceneKey().equals(command.getSceneKey())
                && sceneRepository.findByKey(command.getSceneKey()).isPresent()) {
            throw SceneErrorCode.exception(SceneErrorCode.SCENE_KEY_CONFLICT);
        }
        scene.setSceneKey(command.getSceneKey());
        scene.setName(command.getName().trim());
        scene.setDescription(command.getDescription());
        sceneRepository.update(scene);
        return SceneViewAssembler.toView(scene);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SceneView publish(Long id) {
        Scene scene = requireScene(id);
        List<SceneNode> nodes = sceneNodeRepository.listBySceneId(id);
        if (nodes.isEmpty()) {
            throw SceneErrorCode.exception(SceneErrorCode.BINDING_REQUIRED);
        }
        if (nodes.size() > sceneProperties.getMaxNodesPerScene()) {
            throw SceneErrorCode.exception(SceneErrorCode.NODE_COUNT_EXCEEDED);
        }
        SceneValidator.validateTree(nodes, sceneProperties.getMaxNodeDepth());

        List<NodeBinding> bindings = nodeBindingRepository.listByNodeIds(
                nodes.stream().map(SceneNode::getId).toList());
        List<NodeBinding> enabled = bindings.stream()
                .filter(binding -> Boolean.TRUE.equals(binding.getEnabled()))
                .toList();
        if (enabled.isEmpty()) {
            throw SceneErrorCode.exception(SceneErrorCode.BINDING_REQUIRED);
        }
        for (NodeBinding binding : enabled) {
            SceneValidator.validateEntryType(binding.getEntryType());
            SceneValidator.validateTargetPath(binding.getTargetPath(), sceneProperties.getAllowedSchemes());
        }

        Map<Long, NodeBinding> bindingByNodeId = new LinkedHashMap<>();
        bindings.forEach(binding -> bindingByNodeId.put(binding.getNodeId(), binding));
        SceneLayoutSnapshot.Snapshot snapshot = SceneLayoutSnapshot.build(
                scene.getSceneKey(), nodes, bindingByNodeId);

        int nextVersion = (scene.getLayoutVersion() == null ? 0 : scene.getLayoutVersion()) + 1;
        LayoutVersion layoutVersion = new LayoutVersion();
        layoutVersion.setSceneId(id);
        layoutVersion.setVersion(nextVersion);
        layoutVersion.setLayoutJson(snapshot.layoutJson());
        layoutVersion.setChecksum(snapshot.checksum());
        layoutVersionRepository.insert(layoutVersion);

        scene.publish(nextVersion, LocalDateTime.now());
        sceneRepository.update(scene);
        return SceneViewAssembler.toView(scene);
    }

    @Override
    public SceneView offline(Long id) {
        Scene scene = requireScene(id);
        if (!SceneStatus.PUBLISHED.equals(scene.getStatus())) {
            throw SceneErrorCode.exception(SceneErrorCode.SCENE_STATE_CONFLICT);
        }
        scene.offline();
        sceneRepository.update(scene);
        return SceneViewAssembler.toView(scene);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SceneNodeView saveNode(Long sceneId, Long nodeId, SceneNodeSaveCommand command) {
        Scene scene = requireScene(sceneId);
        SceneValidator.validateNodeKey(command.getNodeKey());
        SceneValidator.validateNodeShape(command.getNodeType(), command.getX(), command.getY());
        requireName(command.getName());

        List<SceneNode> nodes = sceneNodeRepository.listBySceneId(sceneId);
        String parentKey = StringUtils.hasText(command.getParentKey()) ? command.getParentKey().trim() : null;
        if (parentKey != null) {
            SceneNode parent = nodes.stream()
                    .filter(node -> parentKey.equals(node.getNodeKey()))
                    .findFirst()
                    .orElseThrow(() -> SceneErrorCode.exception(SceneErrorCode.NODE_INVALID));
            if (parentKey.equals(command.getNodeKey())) {
                throw SceneErrorCode.exception(SceneErrorCode.NODE_CYCLE);
            }
        }

        SceneNode node;
        if (nodeId == null) {
            boolean duplicated = nodes.stream().anyMatch(item -> command.getNodeKey().equals(item.getNodeKey()));
            if (duplicated) {
                throw SceneErrorCode.exception(SceneErrorCode.NODE_KEY_CONFLICT);
            }
            if (nodes.size() >= sceneProperties.getMaxNodesPerScene()) {
                throw SceneErrorCode.exception(SceneErrorCode.NODE_COUNT_EXCEEDED);
            }
            node = new SceneNode();
            node.setSceneId(scene.getId());
            node.setStatus(NODE_ENABLED);
        } else {
            node = sceneNodeRepository.findById(nodeId)
                    .orElseThrow(() -> SceneErrorCode.exception(SceneErrorCode.NODE_NOT_FOUND));
            if (!sceneId.equals(node.getSceneId())) {
                throw SceneErrorCode.exception(SceneErrorCode.NODE_NOT_FOUND);
            }
            ensureNoCycle(nodes, node.getNodeKey(), parentKey);
        }
        node.setNodeKey(command.getNodeKey());
        node.setParentKey(parentKey);
        node.setNodeType(command.getNodeType());
        node.setName(command.getName().trim());
        node.setIcon(command.getIcon());
        node.setX(command.getX());
        node.setY(command.getY());
        node.setZ(command.getZ());
        node.setSortNo(command.getSortNo() == null ? 1 : command.getSortNo());
        if (nodeId == null) {
            sceneNodeRepository.insert(node);
        } else {
            sceneNodeRepository.update(node);
        }
        return SceneViewAssembler.toView(node);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNode(Long nodeId) {
        SceneNode node = sceneNodeRepository.findById(nodeId)
                .orElseThrow(() -> SceneErrorCode.exception(SceneErrorCode.NODE_NOT_FOUND));
        boolean hasChildren = sceneNodeRepository.listBySceneId(node.getSceneId()).stream()
                .anyMatch(item -> node.getNodeKey().equals(item.getParentKey()));
        if (hasChildren) {
            throw SceneErrorCode.exception(SceneErrorCode.NODE_HAS_CHILDREN);
        }
        nodeBindingRepository.deleteByNodeId(nodeId);
        sceneVisibilityRepository.deleteByNodeId(nodeId);
        sceneNodeRepository.deleteById(nodeId);
    }

    @Override
    public NodeBindingView saveBinding(Long nodeId, NodeBindingSaveCommand command) {
        SceneNode node = sceneNodeRepository.findById(nodeId)
                .orElseThrow(() -> SceneErrorCode.exception(SceneErrorCode.NODE_NOT_FOUND));
        SceneValidator.validateEntryType(command.getEntryType());
        SceneValidator.validateTargetPath(command.getTargetPath(), sceneProperties.getAllowedSchemes());
        NodeBinding binding = nodeBindingRepository.findByNodeId(nodeId).orElseGet(() -> {
            NodeBinding created = new NodeBinding();
            created.setNodeId(node.getId());
            return created;
        });
        binding.setEntryType(command.getEntryType());
        binding.setTargetPath(command.getTargetPath().trim());
        binding.setPermissionCode(StringUtils.hasText(command.getPermissionCode())
                ? command.getPermissionCode().trim() : null);
        binding.setEnabled(command.getEnabled() == null || command.getEnabled());
        if (binding.getId() == null) {
            nodeBindingRepository.insert(binding);
        } else {
            nodeBindingRepository.update(binding);
        }
        return SceneViewAssembler.toView(binding);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SceneVisibilityView> replaceVisibility(Long sceneId, List<SceneVisibilityCommand> commands) {
        requireScene(sceneId);
        Set<Long> nodeIds = new HashSet<>();
        sceneNodeRepository.listBySceneId(sceneId).forEach(node -> nodeIds.add(node.getId()));
        List<SceneVisibilityCommand> rules = commands == null ? List.of() : commands;
        for (SceneVisibilityCommand rule : rules) {
            SceneValidator.validateVisibility(rule.getSubjectType(), rule.getSubjectId(),
                    rule.getEffect(), rule.getNodeId(), nodeIds);
        }
        sceneVisibilityRepository.deleteBySceneId(sceneId);
        List<SceneVisibilityView> views = new ArrayList<>();
        for (SceneVisibilityCommand rule : rules) {
            SceneVisibility visibility = new SceneVisibility();
            visibility.setSceneId(sceneId);
            visibility.setNodeId(rule.getNodeId() == null ? SceneVisibility.SCENE_LEVEL_NODE_ID : rule.getNodeId());
            visibility.setSubjectType(rule.getSubjectType());
            visibility.setSubjectId("ALL".equals(rule.getSubjectType()) ? null : rule.getSubjectId());
            visibility.setEffect(rule.getEffect());
            sceneVisibilityRepository.insert(visibility);
            views.add(SceneViewAssembler.toView(visibility));
        }
        return views;
    }

    /**
     * 加载场景，不存在按「不存在或无权访问」处理（跨租户不泄露存在性）。
     *
     * @param id 场景 ID
     * @return 场景
     */
    private Scene requireScene(Long id) {
        if (id == null) {
            throw SceneErrorCode.exception(SceneErrorCode.SCENE_NOT_FOUND);
        }
        return sceneRepository.findById(id)
                .orElseThrow(() -> SceneErrorCode.exception(SceneErrorCode.SCENE_NOT_FOUND));
    }

    /**
     * 校验场景名称非空。
     *
     * @param name 场景名称
     */
    private void requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw SceneErrorCode.exception(SceneErrorCode.SCENE_NAME_INVALID);
        }
    }

    /**
     * 防止把节点挂到自己的后代下形成环。
     *
     * @param nodes 场景节点
     * @param nodeKey 待移动节点标识
     * @param parentKey 目标父节点标识
     */
    private void ensureNoCycle(List<SceneNode> nodes, String nodeKey, String parentKey) {
        if (parentKey == null) {
            return;
        }
        Map<String, String> parentOf = new LinkedHashMap<>();
        nodes.forEach(node -> parentOf.put(node.getNodeKey(), node.getParentKey()));
        Set<String> visited = new HashSet<>();
        String current = parentKey;
        while (StringUtils.hasText(current)) {
            if (!visited.add(current) || nodeKey.equals(current)) {
                throw SceneErrorCode.exception(SceneErrorCode.NODE_CYCLE);
            }
            current = parentOf.get(current);
        }
    }

    /**
     * 布局快照转视图。
     *
     * @param layoutVersion 快照
     * @return 快照视图；无快照返回 {@code null}
     */
    private LayoutSnapshotView layoutView(LayoutVersion layoutVersion) {
        if (layoutVersion == null) {
            return null;
        }
        LayoutSnapshotView view = new LayoutSnapshotView();
        view.setVersion(layoutVersion.getVersion());
        view.setChecksum(layoutVersion.getChecksum());
        view.setLayoutJson(layoutVersion.getLayoutJson());
        return view;
    }
}
