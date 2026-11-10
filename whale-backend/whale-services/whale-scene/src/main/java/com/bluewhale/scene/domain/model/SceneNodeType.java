package com.bluewhale.scene.domain.model;

/**
 * 场景节点类型常量（与 DDL 的 {@code sc_scene_node.node_type} 取值一致）。
 *
 * <p>{@code REGION} 为区域容器，{@code BUILDING} 为建筑，{@code ENTRANCE} 为可绑定
 * 业务入口的门洞 / 门户节点。2D 地图按 {@code x}/{@code y} 坐标渲染，节点类型只影响
 * 视觉样式，不改变树结构。</p>
 *
 * @author BlueWhale
 */
public final class SceneNodeType {

    /** 区域：城池内的分区容器 */
    public static final String REGION = "REGION";

    /** 建筑：区域内的建筑 */
    public static final String BUILDING = "BUILDING";

    /** 入口：可绑定系统 / 页面 / 知识库 / 外链的节点 */
    public static final String ENTRANCE = "ENTRANCE";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private SceneNodeType() {
    }

    /**
     * 判断节点类型是否合法。
     *
     * @param nodeType 节点类型
     * @return 合法返回 {@code true}
     */
    public static boolean isValid(String nodeType) {
        return REGION.equals(nodeType) || BUILDING.equals(nodeType) || ENTRANCE.equals(nodeType);
    }
}
