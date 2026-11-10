package com.bluewhale.file.domain.storage;

/**
 * 存储插件信息值对象：面向运维查询的插件描述，不暴露插件的存储 I/O 能力。
 *
 * @param type 插件类型（与 {@code whale.file.storage.type} 取值一致）
 * @param displayName 插件展示名称
 * @param presignedSupported 是否支持预签名下载
 * @author BlueWhale
 */
public record StoragePluginInfo(String type, String displayName, boolean presignedSupported) {

    /**
     * 由插件实例提取只读描述信息
     *
     * @param plugin 存储插件实例
     * @return 插件信息值对象
     */
    public static StoragePluginInfo from(StoragePlugin plugin) {
        return new StoragePluginInfo(plugin.getType(), plugin.getDisplayName(), plugin.supportsPresigned());
    }
}
