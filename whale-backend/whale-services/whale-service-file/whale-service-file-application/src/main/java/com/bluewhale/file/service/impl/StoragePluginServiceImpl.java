package com.bluewhale.file.service.impl;

import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import com.bluewhale.file.domain.storage.StoragePluginInfo;
import com.bluewhale.file.service.StoragePluginService;
import com.bluewhale.file.vo.StoragePluginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 存储插件运维查询服务实现：把领域层的插件描述转换为接口层视图对象。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class StoragePluginServiceImpl implements StoragePluginService {

    private final StoragePluginCatalog storagePluginCatalog;

    @Override
    public List<StoragePluginVO> plugins() {
        String activeType = storagePluginCatalog.activeType();
        return storagePluginCatalog.plugins().stream()
                .map(info -> toVo(info, activeType))
                .toList();
    }

    /**
     * 插件信息转视图对象
     *
     * @param info 插件只读描述
     * @param activeType 当前活动插件类型
     * @return 插件视图对象
     */
    private static StoragePluginVO toVo(StoragePluginInfo info, String activeType) {
        StoragePluginVO vo = new StoragePluginVO();
        vo.setType(info.type());
        vo.setDisplayName(info.displayName());
        vo.setPresignedSupported(info.presignedSupported());
        vo.setActive(info.type().equals(activeType));
        return vo;
    }
}
