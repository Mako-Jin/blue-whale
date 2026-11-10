package com.bluewhale.scene.application.view;

import lombok.Data;

import java.util.List;

/**
 * 场景导航视图。
 *
 * @author BlueWhale
 */
@Data
public class NavigationSceneView {

    /** 场景标识 */
    private String sceneKey;

    /** 场景名称 */
    private String name;

    /** 已发布布局版本 */
    private Integer layoutVersion;

    /** 可见节点（已按可见性过滤） */
    private List<NavigationNodeView> nodes;
}
