package com.bluewhale.scene.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 场景节点实体，对应表 {@code sc_scene_node}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sc_scene_node")
public class SceneNodeEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属场景 ID */
    private Long sceneId;

    /** 节点标识（场景内唯一） */
    private String nodeKey;

    /** 父节点标识 */
    private String parentKey;

    /** 节点类型：REGION / BUILDING / ENTRANCE */
    private String nodeType;

    /** 节点名称 */
    private String name;

    /** 图标标识 */
    private String icon;

    /** 2D 横坐标（0–1000） */
    private Integer coordinateX;

    /** 2D 纵坐标（0–1000） */
    private Integer coordinateY;

    /** 3D 高度（M3 使用） */
    private Integer coordinateZ;

    /** 同级排序号 */
    private Integer sortNo;

    /** 状态：ENABLED / DISABLED */
    private String status;
}
