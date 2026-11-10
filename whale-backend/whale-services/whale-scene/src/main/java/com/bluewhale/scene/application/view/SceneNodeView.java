package com.bluewhale.scene.application.view;

import lombok.Data;

/**
 * 场景节点视图（管理端）。
 *
 * @author BlueWhale
 */
@Data
public class SceneNodeView {

    /** 节点 ID */
    private Long id;

    /** 所属场景 ID */
    private Long sceneId;

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

    /** 3D 高度 */
    private Integer z;

    /** 排序号 */
    private Integer sortNo;

    /** 状态 */
    private String status;
}
