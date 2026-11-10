package com.bluewhale.file.vo;

import lombok.Data;

/**
 * 存储插件视图对象：用于运维查看已装配插件与当前活动插件。
 *
 * @author BlueWhale
 */
@Data
public class StoragePluginVO {

    /** 插件类型（与 {@code whale.file.storage.type} 取值一致） */
    private String type;

    /** 插件展示名称 */
    private String displayName;

    /** 是否支持预签名下载 */
    private boolean presignedSupported;

    /** 是否为当前活动插件 */
    private boolean active;
}
