package com.bluewhale.scene.application.view;

import lombok.Data;

import java.util.List;

/**
 * 导航节点视图（只返回可见节点与可见入口，不含布局大字段）。
 *
 * @author BlueWhale
 */
@Data
public class NavigationNodeView {

    /** 节点标识 */
    private String nodeKey;

    /** 父节点标识 */
    private String parentKey;

    /** 节点类型 */
    private String nodeType;

    /** 节点名称 */
    private String name;

    /** 图标标识 */
    private String icon;

    /** 2D 横坐标 */
    private Integer x;

    /** 2D 纵坐标 */
    private Integer y;

    /** 排序号 */
    private Integer sortNo;

    /** 可见入口（0 或 1 个） */
    private List<NavigationEntryView> entries;
}
