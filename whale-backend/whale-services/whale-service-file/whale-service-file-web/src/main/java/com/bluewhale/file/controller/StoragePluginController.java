package com.bluewhale.file.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.file.api.StoragePluginApi;
import com.bluewhale.file.assemble.StoragePluginAssembler;
import com.bluewhale.file.model.response.StoragePluginResponse;
import com.bluewhale.file.service.StoragePluginService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文件存储插件运维接口实现（Web 适配层）。
 *
 * <p>只读展示已装配插件与当前活动插件，便于确认对象存储接入状态；
 * 插件启用仍由启动配置 {@code whale.file.storage.type} 决定，不提供运行时切换入口。
 * 若未来新增切换类写接口，必须加 {@code sys:file:manage} 权限码并做二次确认，
 * 防止运维接口被滥用。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class StoragePluginController implements StoragePluginApi {

    private final StoragePluginService storagePluginService;

    private final StoragePluginAssembler storagePluginAssembler;

    @Override
    @RequirePermission("sys:file:list")
    public Result<List<StoragePluginResponse>> plugins() {
        return Result.ok(storagePluginAssembler.toResponses(storagePluginService.plugins()));
    }
}
