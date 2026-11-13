package com.bluewhale.file.model.result;

/**
 * 存储插件结果。
 *
 * @param type 插件类型
 * @param displayName 展示名称
 * @param presignedSupported 是否支持预签名下载
 * @param active 是否为当前活动插件
 * @author BlueWhale
 */
public record StoragePluginResult(String type, String displayName, boolean presignedSupported, boolean active) {
}
