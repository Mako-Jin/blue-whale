package com.bluewhale.file.infrastructure.storage;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.domain.storage.StoragePlugin;
import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import com.bluewhale.file.domain.storage.StoragePluginInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 存储插件注册表：收集类路径下全部 {@link StoragePlugin}，按 {@code whale.file.storage.type} 选择活动插件。
 *
 * <p>本类同时适配 {@link FileStorage} 与 {@link StoragePluginCatalog} 两个领域端口：业务层注入
 * {@code FileStorage} 即透明使用当前活动插件，注入 {@code StoragePluginCatalog} 即读取插件清单；
 * 新增存储介质只需新增插件模块并放入类路径，无需改动业务代码。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class StoragePluginRegistry implements FileStorage, StoragePluginCatalog {

    /** 插件类型到插件实例的映射（按类型字典序，便于可观测性输出稳定） */
    private final Map<String, StoragePlugin> plugins;

    /** 当前活动插件 */
    private final StoragePlugin active;

    /**
     * 以容器内全部插件与文件服务配置构造注册表。
     *
     * @param pluginBeans 容器内全部存储插件 Bean
     * @param fileProperties 文件服务配置（读取活动插件类型）
     * @throws BizException 无任何插件、或配置的活动插件类型不存在时抛出 {@code SYSTEM_ERROR}
     */
    public StoragePluginRegistry(List<StoragePlugin> pluginBeans, FileProperties fileProperties) {
        this.plugins = pluginBeans.stream()
                .collect(Collectors.toMap(StoragePlugin::getType, plugin -> plugin,
                        (left, right) -> left, TreeMap::new));
        if (plugins.isEmpty()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "未找到任何文件存储插件");
        }
        String activeType = fileProperties.getStorage().getType();
        StoragePlugin selected = plugins.get(activeType);
        if (selected == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(),
                    "未找到文件存储插件: " + activeType + "，可用插件: " + plugins.keySet());
        }
        this.active = selected;
        log.info("文件存储插件已装配: active={}, available={}", activeType, plugins.keySet());
    }

    @Override
    public String activeType() {
        return active.getType();
    }

    @Override
    public List<StoragePluginInfo> plugins() {
        return plugins.values().stream().map(StoragePluginInfo::from).toList();
    }

    @Override
    public void store(String bucket, String objectKey, InputStream in) {
        active.store(bucket, objectKey, in);
    }

    @Override
    public InputStream load(String bucket, String objectKey) {
        return active.load(bucket, objectKey);
    }

    @Override
    public void delete(String bucket, String objectKey) {
        active.delete(bucket, objectKey);
    }

    @Override
    public String presignedDownloadUrl(String bucket, String objectKey, int expiresSeconds) {
        if (!active.supportsPresigned()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "当前存储插件不支持预签名下载: " + active.getType());
        }
        return active.presignedDownloadUrl(bucket, objectKey, expiresSeconds);
    }
}
