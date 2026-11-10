package com.bluewhale.agent.dto;

import lombok.Data;

/**
 * 孪生场景概要工具入参（sceneId 或 sceneCode 二选一）。
 *
 * @author BlueWhale
 */
@Data
public class TwinSceneToolRequest {

    /** 场景ID */
    private Long sceneId;

    /** 场景编码 */
    private String sceneCode;
}
