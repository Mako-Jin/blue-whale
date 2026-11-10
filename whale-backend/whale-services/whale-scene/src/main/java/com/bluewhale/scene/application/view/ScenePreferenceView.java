package com.bluewhale.scene.application.view;

import lombok.Data;

/**
 * 用户场景偏好视图。
 *
 * @author BlueWhale
 */
@Data
public class ScenePreferenceView {

    /** 视图模式：2D / 3D */
    private String viewMode;

    /** 默认场景标识，空表示未指定 */
    private String defaultSceneKey;
}
