import { describe, expect, it } from 'vitest';
import {
  detectRenderMode,
  escapeFormatTokens,
  formatTimestamp,
  hexToRgb,
  paintAnsi,
  resolveTheme,
  stripFormatGuard,
  toAnsiPrefix,
  toCssStyle,
} from '../src/logger';

describe('hexToRgb', () => {
  it('解析 6 位十六进制', () => {
    expect(hexToRgb('#ff8800')).toEqual([255, 136, 0]);
    expect(hexToRgb('ff8800')).toEqual([255, 136, 0]);
  });

  it('解析 3 位缩写', () => {
    expect(hexToRgb('#f80')).toEqual([255, 136, 0]);
  });

  it('解析 rgb / rgba', () => {
    expect(hexToRgb('rgb(1, 2, 3)')).toEqual([1, 2, 3]);
    expect(hexToRgb('rgba(1,2,3,0.5)')).toEqual([1, 2, 3]);
  });

  it('无法解析时返回 null，而不是抛错或产出乱码', () => {
    expect(hexToRgb('linear-gradient(90deg, #000, #fff)')).toBeNull();
    expect(hexToRgb('')).toBeNull();
  });
});

describe('toCssStyle', () => {
  it('拼成 %c 可用的 CSS 字符串', () => {
    expect(toCssStyle({ color: '#fff', 'font-weight': '700' })).toBe('color:#fff;font-weight:700');
  });
});

describe('toAnsiPrefix / paintAnsi', () => {
  it('转为 24 位真彩转义序列', () => {
    expect(toAnsiPrefix({ color: '#ff0000' })).toBe('\u001b[38;2;255;0;0m');
    expect(toAnsiPrefix({ color: '#ff0000', background: '#00ff00' })).toBe(
      '\u001b[38;2;255;0;0;48;2;0;255;0m',
    );
  });

  it('600 及以上字重映射为加粗', () => {
    expect(toAnsiPrefix({ 'font-weight': 700 })).toBe('\u001b[1m');
    expect(toAnsiPrefix({ 'font-weight': 'bold' })).toBe('\u001b[1m');
  });

  it('无可用属性时返回空串，不产生多余转义', () => {
    expect(toAnsiPrefix({})).toBe('');
    expect(toAnsiPrefix({ color: 'not-a-color' })).toBe('');
  });

  it('着色后带复位序列', () => {
    expect(paintAnsi('x', { color: '#ff0000' })).toBe('\u001b[38;2;255;0;0mx\u001b[0m');
    expect(paintAnsi('x', {})).toBe('x');
  });
});

describe('formatTimestamp', () => {
  const date = new Date(2024, 0, 2, 3, 4, 5, 6);

  it('各内置格式生效（旧实现里配置是被注释掉的）', () => {
    expect(formatTimestamp(date, 'HH:mm:ss')).toBe('03:04:05');
    expect(formatTimestamp(date, 'HH:mm:ss.SSS')).toBe('03:04:05.006');
    expect(formatTimestamp(date, 'YYYY-MM-DD HH:mm:ss')).toBe('2024-01-02 03:04:05');
    expect(formatTimestamp(date, 'YYYY-MM-DD HH:mm:ss.SSS')).toBe('2024-01-02 03:04:05.006');
  });

  it('支持自定义格式化函数', () => {
    expect(formatTimestamp(date, (d) => `Y${d.getFullYear()}`)).toBe('Y2024');
  });

  it('ISO 输出完整时间（UTC，与时区无关）', () => {
    expect(formatTimestamp(date, 'ISO')).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
  });
});

describe('escapeFormatTokens', () => {
  it('隔断会被 console 当作说明符的组合', () => {
    const escaped = escapeFormatTokens('折扣 50%off');
    expect(escaped).not.toContain('%o');
    expect(stripFormatGuard(escaped)).toBe('折扣 50%off');
  });

  it('转义 %c，避免吞掉后续样式参数', () => {
    expect(escapeFormatTokens('%c')).not.toContain('%c');
  });

  it('不影响普通百分号', () => {
    expect(escapeFormatTokens('完成 100%')).toBe('完成 100%');
    expect(escapeFormatTokens('50% 折扣')).toBe('50% 折扣');
  });
});

describe('detectRenderMode', () => {
  it('jsdom（有 document）下判定为 css', () => {
    expect(detectRenderMode()).toBe('css');
  });
});

describe('resolveTheme', () => {
  it('按级别、按字段深合并，不会出现"半个色板"', () => {
    const theme = resolveTheme({ colors: { info: { fg: '#ff0000' } } });
    expect(theme.colors.info.fg).toBe('#ff0000');
    // 关键：bg / border 必须保留基主题的值
    expect(theme.colors.info.bg).toBe('#0c4a6e');
    expect(theme.colors.info.border).toBe('#0ea5e9');
    // 未涉及的级别不受影响
    expect(theme.colors.error.fg).toBe('#fca5a5');
  });

  it('palette 按字段覆盖', () => {
    const theme = resolveTheme({ palette: { accent: '#123456' } });
    expect(theme.palette.accent).toBe('#123456');
    expect(theme.palette.link).toBe('#60a5fa');
  });
});
