// 语言探测回归测试：未主动选择语言时固定中文，不受浏览器语言影响
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DEFAULT_LANGUAGE, detectLanguage, normalizeLanguage } from './language';

const STORAGE_KEY = 'whale-admin-lang';

describe('language', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('浏览器为 en-US 且未选择语言时仍返回默认中文', () => {
    // bbec96db 回归：en-US 浏览器（含 CI Chromium）曾让登录页占位符变英文
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('en-US');
    expect(detectLanguage()).toBe(DEFAULT_LANGUAGE);
  });

  it('浏览器为 en-GB 且未选择语言时仍返回默认中文', () => {
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('en-GB');
    expect(detectLanguage()).toBe(DEFAULT_LANGUAGE);
  });

  it('只有 localStorage 中的显式选择生效', () => {
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('en-US');
    window.localStorage.setItem(STORAGE_KEY, 'en-US');
    expect(detectLanguage()).toBe('en-US');

    window.localStorage.setItem(STORAGE_KEY, 'zh-CN');
    expect(detectLanguage()).toBe('zh-CN');
  });

  it('localStorage 中的非法值回落默认中文', () => {
    window.localStorage.setItem(STORAGE_KEY, 'fr-FR');
    expect(detectLanguage()).toBe(DEFAULT_LANGUAGE);
  });

  it('归一化统一中英变体，其余返回 null', () => {
    expect(normalizeLanguage('zh_CN')).toBe('zh-CN');
    expect(normalizeLanguage('en-GB')).toBe('en-US');
    expect(normalizeLanguage('ja-JP')).toBeNull();
    expect(normalizeLanguage(null)).toBeNull();
  });
});
