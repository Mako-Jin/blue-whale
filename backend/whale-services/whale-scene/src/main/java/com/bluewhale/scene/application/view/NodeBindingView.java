package com.bluewhale.scene.application.view;

import lombok.Data;

/**
 * 节点入口绑定视图。
 *
 * @author BlueWhale
 */
@Data
public class NodeBindingView {

    /** 绑定 ID */
    private Long id;

    /** 节点 ID */
    private Long nodeId;

    /** 入口类型 */
    private String entryType;

    /** 目标地址 */
    private String targetPath;

    /** 所需权限码 */
    private String permissionCode;

    /** 是否启用 */
    private Boolean enabled;
}
