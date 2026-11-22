// 共享界面语言工具测试：钉死"未显式选择固定中文、不读浏览器语言"（bbec96db 根因回归）

import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  DEFAULT_UI_LANG,
  UI_LANG_CHANGE_EVENT,
  UI_LANG_STORAGE_KEY,
  broadcastUiLangChange,
  detectUiLang,
  normalizeUiLang,
  saveUiLang,
  subscribeUiLang,
} from './language';

/** 模拟一个 localStorage，并把 navigator.language 设为 en-US（验证我们根本不读它） */
function stubEnvironment(stored: string | null) {
  const getItem = vi.fn(() => stored);
  vi.stubGlobal('localStorage', { getItem });
  vi.stubGlobal('navigator', { language: 'en-US', languages: ['en-US', 'en'] });
  return getItem;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('normalizeUiLang', () => {
  it('宽松归一化中英文标签，其余返回 null', () => {
    expect(normalizeUiLang('en-US')).toBe('en-US');
    expect(normalizeUiLang('en-GB')).toBe('en-US');
    expect(normalizeUiLang('zh-TW')).toBe('zh-CN');
    expect(normalizeUiLang('  ZH  ')).toBe('zh-CN');
    expect(normalizeUiLang('ja-JP')).toBeNull();
    expect(normalizeUiLang('')).toBeNull();
    expect(normalizeUiLang(null)).toBeNull();
  });
});

describe('detectUiLang', () => {
  it('存储键与 admin 一致（否则用户切换无法跨应用生效）', () => {
    expect(UI_LANG_STORAGE_KEY).toBe('whale-admin-lang');
    expect(DEFAULT_UI_LANG).toBe('zh-CN');
  });

  it('无显式选择时固定中文——即便浏览器是 en-US 也不跟随', () => {
    const getItem = stubEnvironment(null);
    expect(detectUiLang()).toBe('zh-CN');
    expect(getItem).toHaveBeenCalledWith(UI_LANG_STORAGE_KEY);
  });

  it('读取显式选择（en-US 生效）', () => {
    stubEnvironment('en-US');
    expect(detectUiLang()).toBe('en-US');
  });

  it('存储中的非法值回落默认中文', () => {
    stubEnvironment('ja-JP');
    expect(detectUiLang()).toBe('zh-CN');
  });

  it('localStorage 不可用时（隐私模式）回落默认中文而非抛错', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('SecurityError');
      },
    });
    expect(detectUiLang()).toBe('zh-CN');
  });
});

/** 模拟一个可增删监听、可派发事件的 window（共享包测试跑在 node 环境） */
function stubWindow() {
  const listeners = new Map<string, Set<(event: unknown) => void>>();
  const win = {
    addEventListener: vi.fn((type: string, fn: (event: unknown) => void) => {
      if (!listeners.has(type)) listeners.set(type, new Set());
      listeners.get(type)!.add(fn);
    }),
    removeEventListener: vi.fn((type: string, fn: (event: unknown) => void) => {
      listeners.get(type)?.delete(fn);
    }),
    dispatchEvent: vi.fn((event: { type: string; detail?: unknown }) => {
      listeners.get(event.type)?.forEach((fn) => fn(event));
      return true;
    }),
  };
  vi.stubGlobal('window', win);
  return win;
}

describe('saveUiLang / subscribeUiLang', () => {
  it('saveUiLang 写入存储并广播，订阅者收到新语言', () => {
    const store = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => store.set(key, value),
    });
    const win = stubWindow();

    const received: string[] = [];
    const unsubscribe = subscribeUiLang((lang) => received.push(lang));

    saveUiLang('en-US');
    expect(store.get(UI_LANG_STORAGE_KEY)).toBe('en-US');
    expect(win.dispatchEvent).toHaveBeenCalled();
    expect(received).toEqual(['en-US']);
    expect(detectUiLang()).toBe('en-US');

    unsubscribe();
    broadcastUiLangChange('zh-CN');
    expect(received).toEqual(['en-US']); // 退订后不再收到
  });

  it('事件名与 admin 约定一致（跨应用联动的契约）', () => {
    expect(UI_LANG_CHANGE_EVENT).toBe('whale-ui-lang-change');
  });

  it('storage 事件只响应语言键变化', () => {
    stubWindow();
    vi.stubGlobal('localStorage', {
      getItem: () => 'en-US',
      setItem: () => undefined,
    });
    const received: string[] = [];
    const unsubscribe = subscribeUiLang((lang) => received.push(lang));

    // 取出注册到 stub window 上的 storage 监听器直接触发
    const addEventListener = (window as unknown as { addEventListener: ReturnType<typeof vi.fn> })
      .addEventListener;
    const storageCall = addEventListener.mock.calls.find(
      (call: unknown[]) => call[0] === 'storage',
    ) as [string, (e: StorageEvent) => void];
    expect(storageCall).toBeDefined();
    storageCall[1]({ key: 'other-key' } as StorageEvent);
    expect(received).toEqual([]);
    storageCall[1]({ key: UI_LANG_STORAGE_KEY } as StorageEvent);
    expect(received).toEqual(['en-US']);

    unsubscribe();
  });
});
