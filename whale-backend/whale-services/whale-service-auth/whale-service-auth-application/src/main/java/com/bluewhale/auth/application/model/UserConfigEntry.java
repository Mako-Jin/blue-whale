package com.bluewhale.auth.application.model;

/**
 * 用户配置条目（应用层模型）。
 *
 * <p>{@code configValue} 存 JSON 原文：Auth 只负责存取与按 key 分域，不解释其内部结构；
 * 结构解释与校验由消费方完成（租户层默认值由 {@link TenantConfigEntry} 承载）。
 * 这是「auth 只存取不解释」的有意设计，新增配置项无需 Auth 发版。</p>
 *
 * @param tenantId    租户 ID
 * @param userId      用户 ID
 * @param configKey   配置键（前缀分域）
 * @param configValue 配置值（JSON 原文，Auth 不解析）
 * @author BlueWhale
 */
public record UserConfigEntry(Long tenantId, Long userId, String configKey, String configValue) {
}
