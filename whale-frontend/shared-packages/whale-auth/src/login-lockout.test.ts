// 登录锁定解析的单元测试：只覆盖纯函数部分
// （useLoginLockout 是薄壳，倒计时行为靠实机验收，见 Wave 6.28 记录）

import { describe, expect, it } from 'vitest';

import {
  LOCKOUT_FALLBACK_SECONDS,
  LOGIN_LOCKED_CODE,
  formatRemaining,
  parseLockoutSeconds,
} from './login-lockout';

/** 构造一个形如后端 ApiError 的错误对象 */
function apiError(code: number, data?: unknown): Error & { code: number; data?: unknown } {
  return Object.assign(new Error('账号已锁定'), { code, data });
}

describe('parseLockoutSeconds', () => {
  it('42301 且带 retryAfterSeconds 时取该秒数（向上取整）', () => {
    expect(parseLockoutSeconds(apiError(LOGIN_LOCKED_CODE, { retryAfterSeconds: 899.2 }))).toBe(
      900,
    );
    expect(parseLockoutSeconds(apiError(LOGIN_LOCKED_CODE, { retryAfterSeconds: 300 }))).toBe(300);
  });

  it('Jackson 3 把 Long 序列化成字符串，形如 "898" 也要解析（否则每次锁定都退化成兜底）', () => {
    expect(parseLockoutSeconds(apiError(LOGIN_LOCKED_CODE, { retryAfterSeconds: '898' }))).toBe(
      898,
    );
  });

  it('42301 但缺 retryAfterSeconds 时按兜底秒数，绝不解锁成 0', () => {
    expect(parseLockoutSeconds(apiError(LOGIN_LOCKED_CODE))).toBe(LOCKOUT_FALLBACK_SECONDS);
    expect(parseLockoutSeconds(apiError(LOGIN_LOCKED_CODE, { other: 1 }))).toBe(
      LOCKOUT_FALLBACK_SECONDS,
    );
  });

  it('retryAfterSeconds 非法（0 / 负数 / 非数字串）时按兜底秒数', () => {
    expect(parseLockoutSeconds(apiError(LOGIN_LOCKED_CODE, { retryAfterSeconds: 0 }))).toBe(
      LOCKOUT_FALLBACK_SECONDS,
    );
    expect(parseLockoutSeconds(apiError(LOGIN_LOCKED_CODE, { retryAfterSeconds: -5 }))).toBe(
      LOCKOUT_FALLBACK_SECONDS,
    );
    expect(parseLockoutSeconds(apiError(LOGIN_LOCKED_CODE, { retryAfterSeconds: 'abc' }))).toBe(
      LOCKOUT_FALLBACK_SECONDS,
    );
  });

  it('非 42301 的错误不进入锁定态（401 口令错误、网络错误等）', () => {
    expect(parseLockoutSeconds(apiError(401))).toBeNull();
    expect(parseLockoutSeconds(new Error('Network Error'))).toBeNull();
    expect(parseLockoutSeconds(null)).toBeNull();
    expect(parseLockoutSeconds(undefined)).toBeNull();
    expect(parseLockoutSeconds('42301')).toBeNull();
  });
});

describe('formatRemaining', () => {
  it('格式化为 MM:SS 并补零', () => {
    expect(formatRemaining(900)).toBe('15:00');
    expect(formatRemaining(59)).toBe('00:59');
    expect(formatRemaining(5)).toBe('00:05');
    expect(formatRemaining(3605)).toBe('60:05');
  });

  it('负数与小数按 0 与向下取整处理，不出现 "-1:-5" 之类脏文案', () => {
    expect(formatRemaining(-3)).toBe('00:00');
    expect(formatRemaining(4.9)).toBe('00:04');
  });
});
