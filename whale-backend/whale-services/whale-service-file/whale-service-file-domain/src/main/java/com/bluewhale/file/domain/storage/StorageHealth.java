package com.bluewhale.file.domain.storage;

/**
 * 存储插件健康状态值对象。
 *
 * <p>由插件自身探测介质可用性（本地目录可写、对象存储可达/桶存在），
 * 供 {@code StorageHealthIndicator} 汇总到 {@code /actuator/health}；
 * 领域层不依赖 Spring Actuator 类型。</p>
 *
 * @param up 介质是否可用
 * @param message 健康说明（DOWN 时包含失败原因，便于运维定位）
 * @author BlueWhale
 */
public record StorageHealth(boolean up, String message) {

    /**
     * 构造可用状态
     *
     * @param message 健康说明
     * @return 可用状态
     */
    public static StorageHealth up(String message) {
        return new StorageHealth(true, message);
    }

    /**
     * 构造不可用状态
     *
     * @param message 失败原因
     * @return 不可用状态
     */
    public static StorageHealth down(String message) {
        return new StorageHealth(false, message);
    }
}
