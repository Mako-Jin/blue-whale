package com.bluewhale.scene.domain.model;

import lombok.Data;

/**
 * 场景节点（区域 / 建筑 / 入口）。
 *
 * <p>{@code nodeKey} 在场景内唯一，{@code parentKey} 为空表示根节点。坐标
 * {@code x}/{@code y} 取值 0–1000，前端按百分比换算成 2D 地图位置；{@code z}
 * 预留给 M3 的 3D 高度，M1/M2 只保存不使用。</p>
 *
 * @author BlueWhale
 */
@Data
public class SceneNode {

    /** 主键 */
    private Long id;

    /** 所属场景 ID */
    private Long sceneId;

    /** 节点标识（场景内唯一） */
    private String nodeKey;

    /** 父节点标识；根节点为空 */
    private String parentKey;

    /** 节点类型：REGION / BUILDING / ENTRANCE */
    private String nodeType;

    /** 节点名称 */
    private String name;

    /** 图标标识（前端图标名，可为空） */
    private String icon;

    /** 2D 横坐标（0–1000） */
    private Integer x;

    /** 2D 纵坐标（0–1000） */
    private Integer y;

    /** 3D 高度（M3 使用，M1/M2 可为空） */
    private Integer z;

    /** 同级排序号 */
    private Integer sortNo;

    /** 状态：ENABLED / DISABLED（M1 先固定 ENABLED） */
    private String status;

    /**
     * 判断是否为根节点。
     *
     * @return 无父节点返回 {@code true}
     */
    public boolean isRoot() {
        return parentKey == null || parentKey.isBlank();
    }
}
