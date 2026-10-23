package com.bluewhale.file.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.file.storage.api.StoragePlugin;
import com.bluewhale.file.storage.plugin.StoragePluginRegistry;
import com.bluewhale.file.vo.StoragePluginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文件存储插件运维接口。
 *
 * <p>只读展示已装配插件与当前活动插件，便于确认对象存储接入状态；
 * 插件启用仍由启动配置 {@code whale.file.storage.type} 决定，不提供运行时切换入口。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/file/storage")
@RequiredArgsConstructor
public class StoragePluginController {

    private final StoragePluginRegistry storagePluginRegistry;

    /**
     * 查询已装配的存储插件清单。
     *
     * @return 插件清单（含类型、展示名、预签名能力与是否活动）
     */
    @GetMapping("/plugins")
    @RequirePermission("sys:file:list")
    public Result<List<StoragePluginVO>> plugins() {
        String activeType = storagePluginRegistry.getActiveType();
        List<StoragePluginVO> plugins = storagePluginRegistry.getPlugins().stream()
                .map(plugin -> toVo(plugin, activeType))
                .toList();
        return Result.ok(plugins);
    }

    /**
     * 插件实例转视图对象。
     *
     * @param plugin 插件实例
     * @param activeType 当前活动插件类型
     * @return 插件视图对象
     */
    private static StoragePluginVO toVo(StoragePlugin plugin, String activeType) {
        StoragePluginVO vo = new StoragePluginVO();
        vo.setType(plugin.getType());
        vo.setDisplayName(plugin.getDisplayName());
        vo.setPresignedSupported(plugin.supportsPresigned());
        vo.setActive(plugin.getType().equals(activeType));
        return vo;
    }
}
