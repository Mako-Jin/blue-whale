package com.bluewhale.auth.application.port;

/**
 * refreshToken 一次性轮换策略端口（AUTH-39 / ADR-001）。
 *
 * <p>应用层只读取开关状态；具体取值由 infrastructure 以
 * {@code whale.auth.refresh.rotation.enabled} 配置绑定（灰度开关，默认关闭）。</p>
 *
 * @author BlueWhale
 */
public interface RefreshRotationPolicy {

    /**
     * 是否启用 refreshToken 一次性轮换
     *
     * @return 启用返回 {@code true}；关闭时刷新行为与未轮换实现一致
     */
    boolean isEnabled();
}
