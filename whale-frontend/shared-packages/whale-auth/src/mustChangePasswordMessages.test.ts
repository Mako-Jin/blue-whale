// 改密弹窗文案测试：钉死中英键对齐、强度提示双语、无硬编码中文遗漏

import { describe, expect, it } from 'vitest';

import { changePasswordMessages } from './mustChangePasswordMessages';

describe('changePasswordMessages', () => {
  it('中英文键集合完全一致（缺键会显示 undefined）', () => {
    const zhKeys = Object.keys(changePasswordMessages['zh-CN']).sort();
    const enKeys = Object.keys(changePasswordMessages['en-US']).sort();
    expect(enKeys).toEqual(zhKeys);
  });

  it('每个键都有非空文案', () => {
    for (const lang of ['zh-CN', 'en-US'] as const) {
      for (const [key, value] of Object.entries(changePasswordMessages[lang])) {
        expect(value, `${lang}.${key}`).toBeTruthy();
      }
    }
  });

  it('英文文案不含中文字符（遗漏检测）', () => {
    const han = /[\u4e00-\u9fff]/;
    for (const [key, value] of Object.entries(changePasswordMessages['en-US'])) {
      expect(han.test(value), `en-US.${key} 含中文: ${value}`).toBe(false);
    }
  });

  it('密码强度各缺陷与 HTTP 错误文案在两语言下均齐备', () => {
    const strengthKeys = [
      'errorTooShort',
      'errorNeedLower',
      'errorNeedUpper',
      'errorNeedDigit',
      'errorNeedSpecial',
      'errorHttp',
    ] as const;
    for (const lang of ['zh-CN', 'en-US'] as const) {
      for (const key of strengthKeys) {
        expect(changePasswordMessages[lang][key], `${lang}.${key}`).toBeTruthy();
      }
    }
    expect(changePasswordMessages['en-US'].errorHttp).toContain('{status}');
    expect(changePasswordMessages['zh-CN'].errorHttp).toContain('{status}');
  });
});
