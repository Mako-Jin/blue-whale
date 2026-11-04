package com.bluewhale.scene.application.support;

import com.bluewhale.scene.application.view.NodeBindingView;
import com.bluewhale.scene.application.view.SceneNodeView;
import com.bluewhale.scene.application.view.SceneView;
import com.bluewhale.scene.application.view.SceneVisibilityView;
import com.bluewhale.scene.domain.model.LayoutVersion;
import com.bluewhale.scene.domain.model.NodeBinding;
import com.bluewhale.scene.domain.model.Scene;
import com.bluewhale.scene.domain.model.SceneNode;
import com.bluewhale.scene.domain.model.SceneVisibility;

/**
 * 领域对象到视图的装配器（应用层，禁止接口层直接触碰领域对象）。
 *
 * @author BlueWhale
 */
public final class SceneViewAssembler {

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private SceneViewAssembler() {
    }

    /**
     * 场景转列表视图。
     *
     * @param scene 场景
     * @return 场景视图
     */
    public static SceneView toView(Scene scene) {
        SceneView view = new SceneView();
        view.setId(scene.getId());
        view.setSceneKey(scene.getSceneKey());
        view.setName(scene.getName());
        view.setDescription(scene.getDescription());
        view.setStatus(scene.getStatus());
        view.setLayoutVersion(scene.getLayoutVersion());
        view.setPublishedAt(scene.getPublishedAt());
        view.setCreateTime(scene.getCreateTime());
        view.setUpdateTime(scene.getUpdateTime());
        return view;
    }

    /**
     * 节点转视图。
     *
     * @param node 节点
     * @return 节点视图
     */
    public static SceneNodeView toView(SceneNode node) {
        SceneNodeView view = new SceneNodeView();
        view.setId(node.getId());
        view.setSceneId(node.getSceneId());
        view.setNodeKey(node.getNodeKey());
        view.setParentKey(node.getParentKey());
        view.setNodeType(node.getNodeType());
        view.setName(node.getName());
        view.setIcon(node.getIcon());
        view.setX(node.getX());
        view.setY(node.getY());
        view.setZ(node.getZ());
        view.setSortNo(node.getSortNo());
        view.setStatus(node.getStatus());
        return view;
    }

    /**
     * 入口绑定转视图。
     *
     * @param binding 绑定
     * @return 绑定视图
     */
    public static NodeBindingView toView(NodeBinding binding) {
        NodeBindingView view = new NodeBindingView();
        view.setId(binding.getId());
        view.setNodeId(binding.getNodeId());
        view.setEntryType(binding.getEntryType());
        view.setTargetPath(binding.getTargetPath());
        view.setPermissionCode(binding.getPermissionCode());
        view.setEnabled(binding.getEnabled());
        return view;
    }

    /**
     * 可见性规则转视图。
     *
     * @param visibility 规则
     * @return 规则视图
     */
    public static SceneVisibilityView toView(SceneVisibility visibility) {
        SceneVisibilityView view = new SceneVisibilityView();
        view.setId(visibility.getId());
        view.setSceneId(visibility.getSceneId());
        view.setNodeId(visibility.getNodeId());
        view.setSubjectType(visibility.getSubjectType());
        view.setSubjectId(visibility.getSubjectId());
        view.setEffect(visibility.getEffect());
        return view;
    }

    /**
     * 布局快照转配送视图（仅管理端用于回滚比对，不对外暴露渲染层）。
     *
     * @param layoutVersion 布局快照
     * @return 布局 JSON
     */
    public static String layoutJson(LayoutVersion layoutVersion) {
        return layoutVersion == null ? null : layoutVersion.getLayoutJson();
    }
}
