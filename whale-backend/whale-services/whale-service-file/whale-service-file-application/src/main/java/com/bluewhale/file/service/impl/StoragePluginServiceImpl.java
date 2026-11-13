package com.bluewhale.file.service.impl;

import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import com.bluewhale.file.domain.storage.StoragePluginInfo;
import com.bluewhale.file.model.result.StoragePluginResult;
import com.bluewhale.file.service.StoragePluginService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 存储插件运维查询服务实现：把领域层的插件描述转换为应用层结果对象。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class StoragePluginServiceImpl implements StoragePluginService {

    private final StoragePluginCatalog storagePluginCatalog;

    @Override
    public List<StoragePluginResult> plugins() {
        String activeType = storagePluginCatalog.activeType();
        return storagePluginCatalog.plugins().stream()
                .map(info -> toResult(info, activeType))
                .toList();
    }

    /**
     * 插件信息转应用层结果
     *
     * @param info 插件只读描述
     * @param activeType 当前活动插件类型
     * @return 插件结果
     */
    private static StoragePluginResult toResult(StoragePluginInfo info, String activeType) {
        return new StoragePluginResult(info.type(), info.displayName(), info.presignedSupported(),
                info.type().equals(activeType));
    }
}
