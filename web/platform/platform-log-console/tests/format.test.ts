import { describe, expect, it } from 'vitest';

import { formatClock, formatDateTime, formatRelative, toPrettyJson } from '../src/format';

describe('formatClock', () => {
  it('输出到毫秒并补零', () => {
    // 用本地时间构造，避免测试结果随运行时区变化
    const ts = new Date(2026, 9, 9, 8, 5, 3, 7).getTime();
    expect(formatClock(ts)).toBe('08:05:03.007');
  });

  it('拿不到时间时显示占位符而不是 NaN', () => {
    expect(formatClock(undefined)).toBe('—');
    expect(formatClock(null)).toBe('—');
  });
});

describe('formatDateTime', () => {
  it('带日期，用于详情里看清是哪天', () => {
    const ts = new Date(2026, 9, 9, 8, 5, 3, 7).getTime();
    expect(formatDateTime(ts)).toBe('2026-10-09 08:05:03.007');
  });
});

describe('formatRelative', () => {
  const now = 1_700_000_000_000;

  it('按量级切换单位', () => {
    expect(formatRelative(now - 500, now)).toBe('500ms 前');
    expect(formatRelative(now - 30_000, now)).toBe('30s 前');
    expect(formatRelative(now - 90_000, now)).toBe('1min 前');
    expect(formatRelative(now - 7_200_000, now)).toBe('2h 前');
  });

  it('客户端时钟快于服务端时不能说"负多少毫秒前"', () => {
    expect(formatRelative(now + 5000, now)).toBe('刚刚');
  });

  it('拿不到时间时显示占位符', () => {
    expect(formatRelative(undefined)).toBe('—');
  });
});

describe('toPrettyJson', () => {
  it('按缩进输出，便于在详情里读结构化数据', () => {
    expect(toPrettyJson({ sku: 'BW-001' })).toBe('{\n  "sku": "BW-001"\n}');
  });

  it('空值输出空串，而不是 "undefined" 字样', () => {
    expect(toPrettyJson(undefined)).toBe('');
    expect(toPrettyJson(null)).toBe('');
  });

  it('遇到循环引用不抛异常：详情面板不能因为一条脏数据整页白屏', () => {
    const circular: Record<string, unknown> = { name: 'loop' };
    circular.self = circular;
    expect(() => toPrettyJson(circular)).not.toThrow();
  });
});
