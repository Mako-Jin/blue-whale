package com.bluewhale.file.api;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.file.model.response.StoragePluginResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 文件存储插件运维 API 契约。
 *
 * <p>只读展示已装配插件与当前活动插件，插件启用仍由启动配置
 * {@code whale.file.storage.type} 决定，不提供运行时切换入口。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/file/v{version}/storage", version = "1")
public interface StoragePluginApi {

    /**
     * 查询已装配的存储插件清单。
     *
     * @return 插件清单（含类型、展示名、预签名能力与是否活动）
     */
    @GetMapping("/plugins")
    Result<List<StoragePluginResponse>> plugins();
}
