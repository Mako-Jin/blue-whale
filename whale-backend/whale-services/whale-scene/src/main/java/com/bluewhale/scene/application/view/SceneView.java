package com.bluewhale.scene.application.view;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 场景列表视图。
 *
 * @author BlueWhale
 */
@Data
public class SceneView {

    /** 场景 ID */
    private Long id;

    /** 场景标识 */
    private String sceneKey;

    /** 场景名称 */
    private String name;

    /** 场景说明 */
    private String description;

    /** 状态 */
    private String status;

    /** 已发布布局版本 */
    private Integer layoutVersion;

    /** 最近发布时间 */
    private LocalDateTime publishedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
