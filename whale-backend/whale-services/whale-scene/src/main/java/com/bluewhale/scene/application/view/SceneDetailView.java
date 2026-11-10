package com.bluewhale.scene.application.view;

import lombok.Data;

import java.util.List;

/**
 * 场景详情视图（管理端一次返回节点 / 绑定 / 可见性）。
 *
 * @author BlueWhale
 */
@Data
public class SceneDetailView {

    /** 场景基本信息 */
    private SceneView scene;

    /** 节点列表 */
    private List<SceneNodeView> nodes;

    /** 节点入口绑定 */
    private List<NodeBindingView> bindings;

    /** 可见性规则 */
    private List<SceneVisibilityView> visibilities;

    /** 最近一次发布的布局快照（未发布为 null） */
    private LayoutSnapshotView latestLayout;
}
