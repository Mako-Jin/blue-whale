package com.bluewhale.scene.application.service.impl;

import com.bluewhale.scene.application.service.SceneNavigationAppService;
import com.bluewhale.scene.application.support.NavigationUser;
import com.bluewhale.scene.application.view.NavigationEntryView;
import com.bluewhale.scene.application.view.NavigationNodeView;
import com.bluewhale.scene.application.view.NavigationSceneView;
import com.bluewhale.scene.config.SceneProperties;
import com.bluewhale.scene.domain.model.NodeBinding;
import com.bluewhale.scene.domain.model.Scene;
import com.bluewhale.scene.domain.model.SceneNode;
import com.bluewhale.scene.domain.model.SceneVisibility;
import com.bluewhale.scene.domain.model.VisibilityEffect;
import com.bluewhale.scene.domain.repository.NodeBindingRepository;
import com.bluewhale.scene.domain.repository.SceneNodeRepository;
import com.bluewhale.scene.domain.repository.SceneRepository;
import com.bluewhale.scene.domain.repository.SceneVisibilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 场景导航用例实现。
 *
 * <p>可见性判定规则：命中任意 {@code DENY} 即不可见；若目标存在 {@code ALLOW} 规则，
 * 则只有命中的访问者可见（白名单语义）；无任何规则时默认可见。节点被拒绝时其整棵
 * 子树一并隐藏，入口再按权限码过滤。导航只返回可见节点与可见入口，不含布局大字段。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class SceneNavigationAppServiceImpl implements SceneNavigationAppService {

    private final SceneRepository sceneRepository;

    private final SceneNodeRepository sceneNodeRepository;

    private final NodeBindingRepository nodeBindingRepository;

    private final SceneVisibilityRepository sceneVisibilityRepository;

    private final SceneProperties sceneProperties;

    @Override
    public List<NavigationSceneView> navigation(NavigationUser user) {
        List<Scene> scenes = sceneRepository.listPublished();
        List<NavigationSceneView> result = new ArrayList<>();
        for (Scene scene : scenes) {
            List<SceneVisibility> visibilities = sceneVisibilityRepository.listBySceneId(scene.getId());
            if (isDenied(SceneVisibility.SCENE_LEVEL_NODE_ID, visibilities, user)) {
                continue;
            }
            List<SceneNode> nodes = sceneNodeRepository.listBySceneId(scene.getId());
            Set<Long> hiddenNodeIds = hiddenNodeIds(nodes, visibilities, user);
            Map<Long, NodeBinding> bindingByNodeId = bindingsByNodeId(nodes);
            NavigationSceneView view = new NavigationSceneView();
            view.setSceneKey(scene.getSceneKey());
            view.setName(scene.getName());
            view.setLayoutVersion(scene.getLayoutVersion() == null ? 0 : scene.getLayoutVersion());
            view.setNodes(nodes.stream()
                    .filter(node -> !hiddenNodeIds.contains(node.getId()))
                    .limit(Math.max(sceneProperties.getNavigationNodeLimit(), 1))
                    .map(node -> toNodeView(node, bindingByNodeId.get(node.getId()), user.permissions()))
                    .toList());
            result.add(view);
        }
        return result;
    }

    /**
     * 组装导航节点视图。
     *
     * @param node 节点
     * @param binding 入口绑定（可为空）
     * @param permissions 当前用户权限码
     * @return 导航节点视图
     */
    private NavigationNodeView toNodeView(SceneNode node, NodeBinding binding, Set<String> permissions) {
        NavigationNodeView view = new NavigationNodeView();
        view.setNodeKey(node.getNodeKey());
        view.setParentKey(node.getParentKey());
        view.setNodeType(node.getNodeType());
        view.setName(node.getName());
        view.setIcon(node.getIcon());
        view.setX(node.getX());
        view.setY(node.getY());
        view.setSortNo(node.getSortNo());
        List<NavigationEntryView> entries = new ArrayList<>();
        if (binding != null && binding.visibleTo(permissions)) {
            NavigationEntryView entry = new NavigationEntryView();
            entry.setEntryType(binding.getEntryType());
            entry.setTargetPath(binding.getTargetPath());
            entries.add(entry);
        }
        view.setEntries(entries);
        return view;
    }

    /**
     * 按节点建立入口索引。
     *
     * @param nodes 节点列表
     * @return 节点 ID → 入口
     */
    private Map<Long, NodeBinding> bindingsByNodeId(List<SceneNode> nodes) {
        Map<Long, NodeBinding> byNodeId = new LinkedHashMap<>();
        nodeBindingRepository.listByNodeIds(nodes.stream().map(SceneNode::getId).toList())
                .forEach(binding -> byNodeId.put(binding.getNodeId(), binding));
        return byNodeId;
    }

    /**
     * 计算需要隐藏的节点（被拒绝节点连同其子树一并隐藏）。
     *
     * @param nodes 节点列表
     * @param visibilities 可见性规则
     * @param user 访问者
     * @return 隐藏节点 ID 集合
     */
    private Set<Long> hiddenNodeIds(List<SceneNode> nodes, List<SceneVisibility> visibilities,
                                    NavigationUser user) {
        Set<Long> hidden = new HashSet<>();
        Map<String, SceneNode> byKey = new HashMap<>();
        for (SceneNode node : nodes) {
            byKey.put(node.getNodeKey(), node);
            if (isDenied(node.getId(), visibilities, user)) {
                hidden.add(node.getId());
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SceneNode node : nodes) {
                if (hidden.contains(node.getId()) || node.getParentKey() == null) {
                    continue;
                }
                SceneNode parent = byKey.get(node.getParentKey());
                if (parent != null && hidden.contains(parent.getId())) {
                    hidden.add(node.getId());
                    changed = true;
                }
            }
        }
        return hidden;
    }

    /**
     * 判断目标对当前访问者是否不可见。
     *
     * <p>优先级：命中 DENY → 不可见；存在 ALLOW 但未命中 → 不可见（白名单）；
     * 其余默认可见。</p>
     *
     * @param nodeId 节点 ID；0 表示场景级
     * @param visibilities 可见性规则
     * @param user 访问者
     * @return 不可见返回 {@code true}
     */
    private boolean isDenied(long nodeId, List<SceneVisibility> visibilities, NavigationUser user) {
        List<SceneVisibility> rules = visibilities.stream()
                .filter(item -> item.isSceneLevel() ? nodeId == SceneVisibility.SCENE_LEVEL_NODE_ID
                        : item.getNodeId() == nodeId)
                .toList();
        if (rules.isEmpty()) {
            return false;
        }
        boolean denied = rules.stream()
                .filter(item -> VisibilityEffect.DENY.equals(item.getEffect()))
                .anyMatch(item -> item.matches(user.roles(), user.userId(), user.deptId()));
        if (denied) {
            return true;
        }
        boolean hasAllow = rules.stream()
                .anyMatch(item -> VisibilityEffect.ALLOW.equals(item.getEffect()));
        if (!hasAllow) {
            return false;
        }
        return rules.stream()
                .filter(item -> VisibilityEffect.ALLOW.equals(item.getEffect()))
                .noneMatch(item -> item.matches(user.roles(), user.userId(), user.deptId()));
    }
}
