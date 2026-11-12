package com.bluewhale.auth.application.model;

/**
 * 租户配置条目（应用层模型）。
 *
 * <p>{@code configValue} 存 JSON 原文：Auth 只负责存取与按 key 分域，不解释其内部结构；
 * 结构解释与校验由消费方（如网关、前端默认值层）完成。这是「auth 只存取不解释」的有意设计，
 * 新增配置项无需 Auth 发版。</p>
 *
 * @param tenantId    租户 ID
 * @param configKey   配置键（前缀分域）
 * @param configValue 配置值（JSON 原文，Auth 不解析），作为该租户用户的默认值层
 * @author BlueWhale
 */
public record TenantConfigEntry(Long tenantId, String configKey, String configValue) {
}
