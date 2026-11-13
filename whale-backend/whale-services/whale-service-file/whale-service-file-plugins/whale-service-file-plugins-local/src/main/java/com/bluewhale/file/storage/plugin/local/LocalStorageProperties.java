package com.bluewhale.file.storage.plugin.local;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地存储插件配置项（前缀 {@code whale.file.storage}）。
 *
 * <p>与既有配置键保持兼容：{@code whale.file.storage.root} 同时被本地插件与服务侧分片临时目录复用；
 * {@code whale.file.storage.type} 不在此声明，由服务侧插件注册表读取（Spring Boot 自动忽略未知字段）。</p>
 *
 * <p>该键在配置层 {@code FileProperties.Storage.root} 中另有一份声明（配置层与插件层不可互相依赖），
 * <b>修改默认值时必须两处同步</b>。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.file.storage")
public class LocalStorageProperties {

    /**
     * 本地存储根目录，按 {@code ${root}/${bucket}/${objectKey}} 落盘；
     * 默认相对进程工作目录，跨平台可用，生产环境应显式配置绝对路径
     */
    private String root = "./data/whale-file";
}
