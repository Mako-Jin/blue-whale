import type { LogLevel } from 'common-tools';
import { describe, expect, it } from 'vitest';

import { LEVEL_META, LEVEL_ORDER, levelClassName, levelLabel } from '../src/levels';

describe('级别元数据', () => {
  it('每个级别都有元数据，且顺序与日志包一致', () => {
    // LEVEL_META 是 Record<LogLevel, ...>：日志包将来新增级别时这里会编译失败，
    // 因此这条运行时断言只需要守住"两个清单没有各写一份"这件事
    expect(LEVEL_ORDER).toHaveLength(Object.keys(LEVEL_META).length);
    for (const level of LEVEL_ORDER) {
      expect(LEVEL_META[level as LogLevel]).toBeDefined();
    }
  });

  it('级别标签是等宽对齐的短标签', () => {
    expect(LEVEL_META.success.label).toBe('OK');
    expect(LEVEL_META.error.label).toBe('ERROR');
  });

  it('未知级别原样展示，不能变成空白', () => {
    // 服务端可能先上线新级别，此时前端必须把它显示出来而不是隐藏
    expect(levelLabel('fatal')).toBe('fatal');
    expect(levelClassName('fatal')).toBe('lv-unknown');
  });

  it('缺少级别时给出占位符', () => {
    expect(levelLabel(undefined)).toBe('—');
    expect(levelClassName(undefined)).toBe('lv-unknown');
  });

  it('已知级别映射到对应的 CSS 类', () => {
    expect(levelLabel('warn')).toBe('WARN');
    expect(levelClassName('warn')).toBe('lv-warn');
  });
});
