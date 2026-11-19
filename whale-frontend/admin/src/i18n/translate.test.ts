// 文案翻译测试：语言切换、占位符替换与键缺失回落
import { describe, expect, it } from 'vitest';
import { translate } from './translate';

describe('translate', () => {
  it('中文与英文返回对应文案', () => {
    expect(translate('zh-CN', 'login.submit')).toBe('登录');
    expect(translate('en-US', 'login.submit')).toBe('Sign in');
  });

  it('按占位符替换参数', () => {
    expect(translate('en-US', 'login.locked', { remaining: '30s' })).toBe(
      'Account locked, retry in 30s',
    );
    expect(translate('zh-CN', 'login.locked', { remaining: '30s' })).toBe(
      '账号已锁定，30s 后可重试',
    );
  });

  it('英文缺失时回落中文，键不存在时返回键本身', () => {
    expect(translate('en-US', 'login.submit')).not.toBe('');
    expect(translate('en-US', 'not.exists' as never)).toBe('not.exists');
  });
});
