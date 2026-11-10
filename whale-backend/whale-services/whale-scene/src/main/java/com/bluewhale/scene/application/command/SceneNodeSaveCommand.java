package com.bluewhale.scene.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 场景节点新建 / 编辑命令。
 *
 * @author BlueWhale
 */
@Data
public class SceneNodeSaveCommand {

    /** 节点标识（场景内唯一） */
    @NotBlank(message = "节点标识不能为空")
    @Size(max = 64, message = "节点标识不能超过 64 个字符")
    private String nodeKey;

    /** 父节点标识；根节点为空 */
    @Size(max = 64, message = "父节点标识不能超过 64 个字符")
    private String parentKey;

    /** 节点类型：REGION / BUILDING / ENTRANCE */
    @NotBlank(message = "节点类型不能为空")
    private String nodeType;

    /** 节点名称 */
    @NotBlank(message = "节点名称不能为空")
    @Size(max = 64, message = "节点名称不能超过 64 个字符")
    private String name;

    /** 图标标识 */
    @Size(max = 64, message = "图标标识不能超过 64 个字符")
    private String icon;

    /** 2D 横坐标（0–1000） */
    private Integer x;

    /** 2D 纵坐标（0–1000） */
    private Integer y;

    /** 3D 高度（M3 使用） */
    private Integer z;

    /** 同级排序号 */
    private Integer sortNo;
}
