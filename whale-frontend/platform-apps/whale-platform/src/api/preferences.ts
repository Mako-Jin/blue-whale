// preferences.ts —— 用户通知偏好（Wave 6.17d）
//
// 后端：whale-service-auth 通用用户配置域（Wave 6.17a）：
//   GET    /auth/v1/user-config?prefix=notify.   当前用户配置列表
//   PUT    /auth/v1/user-config                  upsert {configKey, configValue}
//   DELETE /auth/v1/user-config/{configKey}      删除（回到租户默认/消费方默认）
// 偏好语义解释在 whale-message（Wave 6.17b），前端只做结构化读写。

import { del, get, put } from './client';

/** 单条用户配置（对齐 auth UserConfigVO） */
export interface UserConfigItem {
  configKey: string;
  configValue: string;
}

/** 通知偏好值结构（notify.<bizType>，由 message resolve 语义定义） */
export interface NotifyPreference {
  /** 启用渠道集（EMAIL/SMS/IM/INAPP 子集；空 = 全部关闭） */
  channels?: string[];
  /** 最低级别阈值（INFO/WARN/ERROR/CRITICAL；空 = 不设阈值） */
  minLevel?: string;
  /** 静默开始 HH:mm（空 = 不静默） */
  quietStart?: string;
  /** 静默结束 HH:mm（可跨零点） */
  quietEnd?: string;
}

/**
 * 列出当前用户的通知偏好。
 *
 * @returns 配置项列表（notify.* 前缀）
 */
export async function listNotifyPreferences(): Promise<UserConfigItem[]> {
  const data = await get<UserConfigItem[]>('/auth/v1/user-config', { prefix: 'notify.' });
  return Array.isArray(data) ? data : [];
}

/**
 * 保存一条通知偏好。
 *
 * @param configKey  配置键（notify.<bizType>）
 * @param value      偏好值
 */
export async function saveNotifyPreference(
  configKey: string,
  value: NotifyPreference,
): Promise<void> {
  await put<void>('/auth/v1/user-config', {
    configKey,
    configValue: JSON.stringify(value),
  });
}

/**
 * 删除一条通知偏好（回到租户默认/消费方默认）。
 *
 * @param configKey 配置键
 */
export async function deleteNotifyPreference(configKey: string): Promise<void> {
  await del<void>(`/auth/v1/user-config/${encodeURIComponent(configKey)}`);
}
