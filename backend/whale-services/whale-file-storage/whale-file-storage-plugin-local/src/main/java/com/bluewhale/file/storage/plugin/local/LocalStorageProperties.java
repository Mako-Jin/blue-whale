package com.bluewhale.file.storage.plugin.local;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地存储插件配置项（前缀 {@code whale.file.storage}）。
 *
 * <p>与既有配置键保持兼容：{@code whale.file.storage.root} 同时被本地插件与服务侧分片临时目录复用；
 * {@code whale.file.storage.type} 由服务侧插件注册表读取，本类忽略该字段。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.file.storage")
public class LocalStorageProperties {

    /** 本地存储根目录，按 {@code ${root}/${bucket}/${objectKey}} 落盘 */
    private String root = "D:/data/whale-file";
}
