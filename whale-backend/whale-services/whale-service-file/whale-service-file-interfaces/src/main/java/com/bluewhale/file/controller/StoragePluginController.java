package com.bluewhale.file.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.file.service.StoragePluginService;
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
 * 插件启用仍由启动配置 {@code whale.file.storage.type} 决定，不提供运行时切换入口。
 * 若未来新增切换类写接口，必须加 {@code sys:file:manage} 权限码并做二次确认，防止运维接口被滥用。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/file/storage")
@RequiredArgsConstructor
public class StoragePluginController {

    private final StoragePluginService storagePluginService;

    /**
     * 查询已装配的存储插件清单。
     *
     * @return 插件清单（含类型、展示名、预签名能力与是否活动）
     */
    @GetMapping("/plugins")
    @RequirePermission("sys:file:list")
    public Result<List<StoragePluginVO>> plugins() {
        return Result.ok(storagePluginService.plugins());
    }
}
