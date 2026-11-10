package com.bluewhale.scene.application.command;

import lombok.Data;

/**
 * 用户场景偏好保存命令。
 *
 * @author BlueWhale
 */
@Data
public class ScenePreferenceSaveCommand {

    /** 视图模式：2D / 3D，为空时按 2D 处理 */
    private String viewMode;

    /** 默认场景标识，为空表示不指定 */
    private String defaultSceneKey;
}
