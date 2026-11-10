package com.bluewhale.file.domain.storage;

import java.util.List;

/**
 * 存储插件目录查询端口：供应用层只读展示已装配插件与当前活动插件。
 *
 * <p>插件启用仍由启动配置 {@code whale.file.storage.type} 决定，不提供运行时切换入口。</p>
 *
 * @author BlueWhale
 */
public interface StoragePluginCatalog {

    /**
     * 获取当前活动插件类型
     *
     * @return 活动插件类型
     */
    String activeType();

    /**
     * 列出已装配的全部插件（按类型字典序）
     *
     * @return 插件信息列表
     */
    List<StoragePluginInfo> plugins();
}
