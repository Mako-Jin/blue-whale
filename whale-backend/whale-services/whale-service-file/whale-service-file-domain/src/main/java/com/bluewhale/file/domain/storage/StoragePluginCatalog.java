package com.bluewhale.file.domain.storage;

import java.util.List;
import java.util.Map;

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

    /**
     * 探测全部插件的健康状态（按类型字典序）
     *
     * <p>单个插件探测抛异常时降级为该插件 DOWN，不影响其余插件与其他健康项。</p>
     *
     * @return 插件类型 → 健康状态
     */
    Map<String, StorageHealth> health();
}
