package com.bluewhale.file.infrastructure.storage;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.domain.storage.StorageHealth;
import com.bluewhale.file.domain.storage.StoragePlugin;
import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import com.bluewhale.file.domain.storage.StoragePluginInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
                        (left, right) -> {
                            // 同类型插件重复注册时保留先注册者，但必须告警，避免排障时误以为后注册插件生效
                            log.warn("检测到重复的文件存储插件类型: {}，保留 {}，忽略 {}",
                                    left.getType(), left.getClass().getName(), right.getClass().getName());
                            return left;
                        }, TreeMap::new));
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
    public InputStream load(String bucket, String objectKey, String storageType) {
        return pluginFor(storageType).load(bucket, objectKey);
    }

    @Override
    public InputStream load(String bucket, String objectKey, long offset, long length, String storageType) {
        return pluginFor(storageType).load(bucket, objectKey, offset, length);
    }

    @Override
    public void delete(String bucket, String objectKey, String storageType) {
        pluginFor(storageType).delete(bucket, objectKey);
    }

    @Override
    public void move(String bucket, String objectKey, String targetBucket, String storageType) {
        pluginFor(storageType).move(bucket, objectKey, targetBucket);
    }

    @Override
    public String presignedDownloadUrl(String bucket, String objectKey, int expiresSeconds, String storageType) {
        StoragePlugin plugin = pluginFor(storageType);
        if (!plugin.supportsPresigned()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "当前存储插件不支持预签名下载: " + plugin.getType());
        }
        return plugin.presignedDownloadUrl(bucket, objectKey, expiresSeconds);
    }

    @Override
    public Map<String, StorageHealth> health() {
        Map<String, StorageHealth> result = new TreeMap<>();
        plugins.forEach((type, plugin) -> {
            try {
                StorageHealth health = plugin.health();
                result.put(type, health == null ? StorageHealth.down("插件未返回健康状态") : health);
            } catch (RuntimeException e) {
                // 单插件健康探测异常不得拖垮整体健康端点，降级为该插件 DOWN 并保留原因
                result.put(type, StorageHealth.down("健康检查异常: " + e.getMessage()));
            }
        });
        return result;
    }

    /**
     * 按落盘时的插件类型选择插件：类型为空回落活动插件。
     *
     * @param storageType 落盘时的插件类型，可为 {@code null}
     * @return 插件实例
     * @throws BizException 类型对应的插件未装配时抛出 {@code SYSTEM_ERROR}
     */
    private StoragePlugin pluginFor(String storageType) {
        if (!StringUtils.hasText(storageType)) {
            return active;
        }
        StoragePlugin plugin = plugins.get(storageType.trim());
        if (plugin == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(),
                    "文件记录的存储插件不可用: " + storageType + "，可用插件: " + plugins.keySet());
        }
        return plugin;
    }
}
