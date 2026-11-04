package com.bluewhale.scene.application.view;

import lombok.Data;

/**
 * 可见性规则视图。
 *
 * @author BlueWhale
 */
@Data
public class SceneVisibilityView {

    /** 规则 ID */
    private Long id;

    /** 场景 ID */
    private Long sceneId;

    /** 节点 ID；0 表示场景级 */
    private Long nodeId;

    /** 主体类型 */
    private String subjectType;

    /** 主体标识 */
    private String subjectId;

    /** 效果 */
    private String effect;
}
