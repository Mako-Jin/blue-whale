// 语言状态：本地持久化（不依赖 React，供 HTTP 客户端读取）
//
// 语言只认用户显式选择：localStorage 没有记录时固定中文，绝不根据
// navigator.language 猜测。浏览器语言与产品默认语言无关（en-US 浏览器用户
// 未主动切换却被显示英文是缺陷，bbec96db 曾因此在 CI 上集体超时），
// 且与后端 LocaleSupport.DEFAULT_LANGUAGE = zh-CN、网关注入的 X-Lang 保持一致。

/** 受支持的语言 */
export type Language = 'zh-CN' | 'en-US';

/** 默认语言：简体中文 */
export const DEFAULT_LANGUAGE: Language = 'zh-CN';

/** 语言持久化键 */
const STORAGE_KEY = 'whale-admin-lang';

/**
 * 归一化语言标签：仅接受中文/英文，其余返回 null。
 *
 * @param value 原始语言标签
 * @returns 受支持语言或 null
 */
export function normalizeLanguage(value?: string | null): Language | null {
  if (!value) {
    return null;
  }
  const normalized = value.trim().toLowerCase();
  if (normalized.startsWith('en')) {
    return 'en-US';
  }
  if (normalized.startsWith('zh')) {
    return 'zh-CN';
  }
  return null;
}

/**
 * 读取当前语言：本地存储中的用户显式选择优先，未选择时固定默认中文。
 *
 * 不再读取 navigator.language——浏览器语言不代表产品语言偏好，
 * 自动跟随会让 en-US 环境的用户/CI 在未主动切换的情况下看到英文。
 *
 * @returns 当前生效语言
 */
export function detectLanguage(): Language {
  try {
    const stored = normalizeLanguage(window.localStorage.getItem(STORAGE_KEY));
    if (stored) {
      return stored;
    }
  } catch {
    // 隐私模式等禁用 localStorage 的场景忽略，用默认中文
  }
  return DEFAULT_LANGUAGE;
}

/**
 * 读取当前语言（供非 React 层使用，如 HTTP 请求头）。
 *
 * @returns 当前语言
 */
export function getCurrentLanguage(): Language {
  return detectLanguage();
}

/**
 * 持久化语言选择。
 *
 * @param language 目标语言
 */
export function saveLanguage(language: Language): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, language);
  } catch {
    // 存储不可用时仅本次会话生效
  }
}
