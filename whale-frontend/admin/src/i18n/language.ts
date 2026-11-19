// 语言状态：本地持久化 + 浏览器偏好探测（不依赖 React，供 HTTP 客户端读取）

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
 * 探测语言：本地存储优先，其次浏览器语言，最后默认中文。
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
    // 隐私模式等禁用 localStorage 的场景忽略，继续用浏览器语言
  }
  return normalizeLanguage(window.navigator.language) ?? DEFAULT_LANGUAGE;
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
