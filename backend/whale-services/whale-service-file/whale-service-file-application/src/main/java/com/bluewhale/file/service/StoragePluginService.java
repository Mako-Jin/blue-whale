package com.bluewhale.file.service;

import com.bluewhale.file.vo.StoragePluginVO;

import java.util.List;

/**
 * 存储插件运维查询服务：只读展示已装配插件与当前活动插件。
 *
 * <p>插件启用仍由启动配置 {@code whale.file.storage.type} 决定，不提供运行时切换入口。</p>
 *
 * @author BlueWhale
 */
public interface StoragePluginService {

    /**
     * 查询已装配的存储插件清单
     *
     * @return 插件清单（含类型、展示名、预签名能力与是否活动）
     */
    List<StoragePluginVO> plugins();
}
