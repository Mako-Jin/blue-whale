// 轻量界面语言读取：供跨应用共享组件（如强制改密弹窗）在没有 I18nProvider 时使用。
//
// 与 admin/src/i18n/language.ts 同源：共用 whale-admin-lang 键、只认用户显式选择、
// localStorage 为空时固定中文。绝不读取 navigator.language——浏览器语言不代表产品偏好，
// 无 i18n 的子应用若跟随浏览器语言，会让 en-US 环境的用户看到半英文界面（bbec96db 教训）。

/** 受支持的界面语言（与 admin 的 Language 保持同构） */
export type UiLang = 'zh-CN' | 'en-US';

/** 默认语言：简体中文 */
export const DEFAULT_UI_LANG: UiLang = 'zh-CN';

/** 语言持久化键：必须与 admin/src/i18n/language.ts 的 STORAGE_KEY 一致 */
export const UI_LANG_STORAGE_KEY = 'whale-admin-lang';

/**
 * 语言变更事件名：qiankun 主子应用共享同一 window，admin 切换语言后广播，
 * 基座（无 i18n）监听以刷新菜单/面包屑，避免主子应用中英混杂。
 */
export const UI_LANG_CHANGE_EVENT = 'whale-ui-lang-change';

/**
 * 归一化语言标签：宽松匹配中英文（en-GB→en-US、zh-TW→zh-CN），其余返回 null。
 *
 * @param value 原始标签
 * @returns 受支持语言或 null
 */
export function normalizeUiLang(value?: string | null): UiLang | null {
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
 * 读取当前界面语言：localStorage 中的显式选择优先，无记录时固定中文。
 *
 * <p>不读取 navigator.language，不持久化探测结果；无 window 的 SSR/测试环境返回默认中文。</p>
 *
 * @returns 当前生效语言
 */
export function detectUiLang(): UiLang {
  try {
    if (typeof localStorage === 'undefined') {
      return DEFAULT_UI_LANG;
    }
    const stored = normalizeUiLang(localStorage.getItem(UI_LANG_STORAGE_KEY));
    if (stored) {
      return stored;
    }
  } catch {
    // 隐私模式等禁用 localStorage 的场景忽略，用默认中文
  }
  return DEFAULT_UI_LANG;
}

/**
 * 持久化界面语言并广播变更。供无 I18nProvider 的基座切换器使用。
 *
 * @param language 目标语言
 */
export function saveUiLang(language: UiLang): void {
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(UI_LANG_STORAGE_KEY, language);
    }
  } catch {
    // 隐私模式忽略，仍广播以便本会话生效
  }
  broadcastUiLangChange(language);
}

/**
 * 广播语言变更事件。写入 localStorage 后调用，供同 window 下的基座等消费者刷新。
 *
 * @param language 新语言
 */
export function broadcastUiLangChange(language: UiLang): void {
  try {
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent(UI_LANG_CHANGE_EVENT, { detail: language }));
    }
  } catch {
    // 无 DOM 环境（SSR/测试）忽略
  }
}

/**
 * 订阅语言变更（含本窗口广播事件与跨标签页 storage 事件）。
 *
 * @param listener 语言变化回调
 * @returns 取消订阅函数
 */
export function subscribeUiLang(listener: (language: UiLang) => void): () => void {
  const onCustom = (event: Event) => {
    const next = normalizeUiLang((event as CustomEvent<UiLang>).detail);
    if (next) {
      listener(next);
    }
  };
  const onStorage = (event: StorageEvent) => {
    if (event.key === UI_LANG_STORAGE_KEY) {
      listener(detectUiLang());
    }
  };
  try {
    if (typeof window !== 'undefined') {
      window.addEventListener(UI_LANG_CHANGE_EVENT, onCustom);
      window.addEventListener('storage', onStorage);
      return () => {
        window.removeEventListener(UI_LANG_CHANGE_EVENT, onCustom);
        window.removeEventListener('storage', onStorage);
      };
    }
  } catch {
    // 无 DOM 环境忽略
  }
  return () => undefined;
}
