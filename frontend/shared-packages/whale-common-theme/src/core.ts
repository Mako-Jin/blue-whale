// 主题核心：状态、持久化、CSS 变量注入与跨标签页同步
// 移植自 platform-terminator/packages/common-theme，移除对 common-tools 的依赖

import type { ThemeConfig, ThemeId, ThemeListener, ThemeMode, ThemeState } from './types';
import { AVAILABLE_THEMES, DEFAULT_THEME_ID, getThemeById } from './palettes';

/** 主题偏好存储键（与外部模块一致） */
const STORAGE_KEY = 'theme_preference';

/** 旧版平台主题模式存储键，用于平滑迁移 */
const LEGACY_MODE_KEY = 'whale_theme';

/** 跨标签页同步频道名 */
const SYNC_CHANNEL = 'bluewhale-theme';

const isBrowser = typeof window !== 'undefined';

const listeners: ThemeListener[] = [];
let syncChannel: BroadcastChannel | null = null;

/**
 * 校验主题 ID 是否可用。
 *
 * @param id 待校验 ID
 * @returns 是否合法
 */
function isValidThemeId(id: string): id is ThemeId {
  return (AVAILABLE_THEMES as string[]).includes(id);
}

/**
 * 按时间推断默认明暗模式（6:00-18:00 亮色）。
 *
 * @returns 默认模式
 */
function getDefaultModeByTime(): ThemeMode {
  const hours = new Date().getHours();
  return hours >= 6 && hours < 18 ? 'light' : 'dark';
}

/**
 * 读取本地主题偏好。
 *
 * @returns 偏好片段，读取失败或不存在时为 null
 */
function getStoredPreference(): Partial<ThemeState> | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<ThemeState>;
      if (parsed.mode !== 'light' && parsed.mode !== 'dark') {
        const legacy = window.localStorage.getItem(LEGACY_MODE_KEY);
        if (legacy === 'light' || legacy === 'dark') {
          parsed.mode = legacy;
        }
      }
      return parsed;
    }
    const legacy = window.localStorage.getItem(LEGACY_MODE_KEY);
    return legacy === 'light' || legacy === 'dark' ? { mode: legacy } : null;
  } catch {
    return null;
  }
}

/**
 * 合并写入主题偏好。
 *
 * @param pref 需要覆盖的偏好片段
 */
function setStoredPreference(pref: Partial<ThemeState>): void {
  try {
    const next = { ...(getStoredPreference() ?? {}), ...pref };
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } catch {
    // 隐私模式或存储配额不足时静默降级
  }
}

/**
 * 将配色写入 `<html>` 的 CSS 自定义属性与 data 属性。
 *
 * 同时输出外部模块的 `--color-*` 变量与 BlueWhale 既有的 `--bw-*` 变量，保证旧样式零改动生效。
 *
 * @param theme 主题配置
 * @param mode 明暗模式
 */
function applyTheme(theme: ThemeConfig, mode: ThemeMode): void {
  if (!isBrowser || !document?.documentElement) return;
  const palette = mode === 'light' ? theme.light : theme.dark;
  const root = document.documentElement;
  const style = root.style;

  style.setProperty('--color-primary', palette.primary);
  style.setProperty('--color-secondary', palette.secondary);
  style.setProperty('--color-accent', palette.accent);
  style.setProperty('--color-neutral-dark', palette.neutralDark);
  style.setProperty('--color-neutral-light', palette.neutralLight);
  style.setProperty('--color-background', palette.background);
  style.setProperty('--color-card-bg', palette.cardBackground);
  style.setProperty('--color-text', palette.text);
  style.setProperty('--color-border', palette.border);
  style.setProperty('--color-focus', palette.accent);

  style.setProperty('--bw-color-primary', palette.primary);
  style.setProperty('--bw-color-info', palette.accent);
  style.setProperty('--bw-color-text', palette.text);
  style.setProperty('--bw-color-border', palette.border);
  style.setProperty('--bw-color-bg-layout', palette.background);
  style.setProperty('--bw-color-bg-container', palette.cardBackground);
  style.setProperty('--bw-text', palette.text);
  style.setProperty(
    '--bw-text-secondary',
    mode === 'light' ? 'rgba(0, 0, 0, 0.45)' : 'rgba(255, 255, 255, 0.45)',
  );
  style.setProperty('--bw-border', palette.border);
  style.setProperty('--bw-header-bg', palette.cardBackground);
  style.setProperty('--bw-content-bg', palette.background);

  root.setAttribute('data-theme-id', theme.id);
  root.setAttribute('data-theme-mode', mode);
  root.setAttribute('data-theme', mode);
  style.colorScheme = mode;
}

/**
 * 向其他标签页广播主题变更。
 *
 * @param state 当前主题状态
 */
function broadcast(state: ThemeState): void {
  if (!isBrowser) return;
  try {
    syncChannel?.postMessage(state);
  } catch {
    // 频道关闭时忽略
  }
}

/** 全局主题核心单例 */
export const themeCore = {
  currentThemeId: DEFAULT_THEME_ID as ThemeId,
  currentMode: 'light' as ThemeMode,
  isUserCustomMode: false,
  isInitialized: false,

  /** 初始化：恢复用户偏好并应用主题（幂等） */
  init(): void {
    if (this.isInitialized) return;
    if (!isBrowser) {
      this.isInitialized = true;
      return;
    }
    const pref = getStoredPreference();
    this.currentThemeId =
      pref?.themeId && isValidThemeId(pref.themeId) ? pref.themeId : DEFAULT_THEME_ID;
    if (pref) {
      this.currentMode = pref.mode === 'dark' ? 'dark' : 'light';
      this.isUserCustomMode = pref.isUserCustomMode ?? true;
    } else {
      this.currentMode = getDefaultModeByTime();
      this.isUserCustomMode = false;
    }
    applyTheme(getThemeById(this.currentThemeId), this.currentMode);
    this.isInitialized = true;
  },

  /**
   * 绑定跨标签页同步。
   *
   * @returns 解绑函数
   */
  bindCrossTabSync(): () => void {
    if (!isBrowser || typeof BroadcastChannel === 'undefined') {
      return () => undefined;
    }
    if (!syncChannel) {
      syncChannel = new BroadcastChannel(SYNC_CHANNEL);
      syncChannel.onmessage = (event: MessageEvent<ThemeState>) => {
        const payload = event.data;
        if (!payload?.themeId || !isValidThemeId(payload.themeId)) return;
        const mode: ThemeMode = payload.mode === 'dark' ? 'dark' : 'light';
        if (payload.themeId === this.currentThemeId && mode === this.currentMode) return;
        this.currentThemeId = payload.themeId;
        this.currentMode = mode;
        if (typeof payload.isUserCustomMode === 'boolean') {
          this.isUserCustomMode = payload.isUserCustomMode;
        }
        applyTheme(getThemeById(this.currentThemeId), mode);
        this._notifyListeners();
      };
    }
    return () => {
      syncChannel?.close();
      syncChannel = null;
    };
  },

  /**
   * 设置主题配色。
   *
   * @param themeId 主题 ID
   */
  setTheme(themeId: ThemeId): void {
    const nextId = isValidThemeId(themeId) ? themeId : DEFAULT_THEME_ID;
    this.currentThemeId = nextId;
    applyTheme(getThemeById(nextId), this.currentMode);
    setStoredPreference({ themeId: nextId });
    this.isInitialized = true;
    this._notifyListeners();
    broadcast(this.getState());
  },

  /**
   * 设置明暗模式。
   *
   * @param mode 明暗模式
   * @param isUserCustom 是否视为用户手动选择
   */
  setMode(mode: ThemeMode, isUserCustom = true): void {
    this.currentMode = mode;
    this.isUserCustomMode = isUserCustom;
    applyTheme(getThemeById(this.currentThemeId), mode);
    setStoredPreference({
      themeId: this.currentThemeId,
      mode,
      isUserCustomMode: isUserCustom,
    });
    this.isInitialized = true;
    this._notifyListeners();
    broadcast(this.getState());
  },

  /** 切换明暗模式 */
  toggleMode(): void {
    this.setMode(this.currentMode === 'light' ? 'dark' : 'light', true);
  },

  /** 重置为按时间自动选择模式 */
  resetToAutoMode(): void {
    this.isUserCustomMode = false;
    this.setMode(getDefaultModeByTime(), false);
  },

  /**
   * 获取当前模式。
   *
   * @returns 明暗模式
   */
  getMode(): ThemeMode {
    return this.currentMode;
  },

  /**
   * 获取当前主题 ID。
   *
   * @returns 主题 ID
   */
  getThemeId(): ThemeId {
    return this.currentThemeId;
  },

  /**
   * 获取当前主题完整配置。
   *
   * @returns 主题配置
   */
  getThemeConfig(): ThemeConfig {
    return getThemeById(this.currentThemeId);
  },

  /**
   * 读取当前注入的 CSS 变量。
   *
   * @returns 变量名 → 值
   */
  getCSSVariables(): Record<string, string> {
    if (!isBrowser) return {};
    const style = getComputedStyle(document.documentElement);
    return {
      primary: style.getPropertyValue('--color-primary').trim(),
      secondary: style.getPropertyValue('--color-secondary').trim(),
      accent: style.getPropertyValue('--color-accent').trim(),
      neutralDark: style.getPropertyValue('--color-neutral-dark').trim(),
      neutralLight: style.getPropertyValue('--color-neutral-light').trim(),
      background: style.getPropertyValue('--color-background').trim(),
      cardBackground: style.getPropertyValue('--color-card-bg').trim(),
      text: style.getPropertyValue('--color-text').trim(),
      border: style.getPropertyValue('--color-border').trim(),
    };
  },

  /**
   * 获取当前状态。
   *
   * @returns 主题状态快照
   */
  getState(): ThemeState {
    return {
      themeId: this.currentThemeId,
      mode: this.currentMode,
      isUserCustomMode: this.isUserCustomMode,
    };
  },

  /**
   * 订阅主题变更（立即推送一次当前状态）。
   *
   * @param listener 监听函数
   * @returns 取消订阅函数
   */
  subscribe(listener: ThemeListener): () => void {
    listeners.push(listener);
    listener(this.getState());
    return () => {
      const index = listeners.indexOf(listener);
      if (index > -1) listeners.splice(index, 1);
    };
  },

  /** 通知所有监听器 */
  _notifyListeners(): void {
    const state = this.getState();
    listeners.forEach((fn) => fn(state));
  },
};
