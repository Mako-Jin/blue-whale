package com.bluewhale.auth.application.userconfig.vo;

/**
 * 用户配置视图对象。
 *
 * @param configKey   配置键
 * @param configValue 配置值（JSON）
 * @author BlueWhale
 */
public record UserConfigVO(String configKey, String configValue) {
}
