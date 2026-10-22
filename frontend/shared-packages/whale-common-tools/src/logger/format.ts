import type { RenderMode, StyleMap, TimestampFormat } from './types';

/** ANSI 复位序列。 */
export const ANSI_RESET = '\u001b[0m';

/**
 * 会被 console 当作格式说明符的字符。
 *
 * <p>`console.log` 首个参数支持 `%s %d %i %f %o %O %c` 替换，
 * 业务消息里出现这些组合（如 `折扣 50%off` 的 `%o`）会吞掉后续参数或错位，必须转义。</p>
 */
const FORMAT_SPECIFIER = /%(?=[sdifoOc])/g;

/** 零宽空格，用于隔断 `%` 与说明符字符，显示效果不变但不再触发替换。 */
const FORMAT_GUARD = '\u200b';

/** 转义可能被 console 误认为格式说明符的片段。 */
export function escapeFormatTokens(text: string): string {
  return text.replace(FORMAT_SPECIFIER, `%${FORMAT_GUARD}`);
}

/** 去掉转义用的零宽空格（供写入文件等需要干净文本的场景）。 */
export function stripFormatGuard(text: string): string {
  return text.split(FORMAT_GUARD).join('');
}

function pad(value: number, length = 2): string {
  return String(value).padStart(length, '0');
}

/** 时间戳格式化；支持传入自定义函数。 */
export function formatTimestamp(
  date: Date,
  format: TimestampFormat | ((date: Date) => string),
): string {
  if (typeof format === 'function') return format(date);

  const clock = `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  const millis = pad(date.getMilliseconds(), 3);

  switch (format) {
    case 'HH:mm:ss':
      return clock;
    case 'HH:mm:ss.SSS':
      return `${clock}.${millis}`;
    case 'YYYY-MM-DD HH:mm:ss': {
      const day = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
      return `${day} ${clock}`;
    }
    case 'YYYY-MM-DD HH:mm:ss.SSS': {
      const day = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
      return `${day} ${clock}.${millis}`;
    }
    case 'ISO':
      return date.toISOString();
    default:
      return `${clock}.${millis}`;
  }
}

/**
 * 颜色值 → RGB 三元组。
 *
 * <p>支持 `#rgb` / `#rrggbb` / `rgb()` / `rgba()`；无法解析时返回 null，
 * 该属性在终端渲染时被忽略，而不是打印乱码。</p>
 */
export function hexToRgb(input: string): [number, number, number] | null {
  const value = input.trim();

  const rgbMatch = /^rgba?\(\s*(\d{1,3})\s*[,\s]\s*(\d{1,3})\s*[,\s]\s*(\d{1,3})/i.exec(value);
  if (rgbMatch) {
    return [Number(rgbMatch[1]), Number(rgbMatch[2]), Number(rgbMatch[3])];
  }

  const hex = value.replace(/^#/, '');
  if (/^[0-9a-f]{3}$/i.test(hex)) {
    return [
      Number.parseInt(hex[0] + hex[0], 16),
      Number.parseInt(hex[1] + hex[1], 16),
      Number.parseInt(hex[2] + hex[2], 16),
    ];
  }
  if (/^[0-9a-f]{6}$/i.test(hex)) {
    return [
      Number.parseInt(hex.slice(0, 2), 16),
      Number.parseInt(hex.slice(2, 4), 16),
      Number.parseInt(hex.slice(4, 6), 16),
    ];
  }
  return null;
}

function asRgb(value: unknown): [number, number, number] | null {
  return typeof value === 'string' ? hexToRgb(value) : null;
}

/** 样式表 → `%c` 用的 CSS 字符串。 */
export function toCssStyle(style: StyleMap): string {
  return Object.entries(style)
    .map(([key, value]) => `${key}:${value}`)
    .join(';');
}

/** 样式表 → ANSI 真彩前缀；无可用属性时返回空串。 */
export function toAnsiPrefix(style: StyleMap): string {
  const codes: string[] = [];

  const foreground = asRgb(style.color);
  if (foreground) codes.push(`38;2;${foreground[0]};${foreground[1]};${foreground[2]}`);

  const background = asRgb(style.background);
  if (background) codes.push(`48;2;${background[0]};${background[1]};${background[2]}`);

  const weight = style['font-weight'];
  const numericWeight =
    typeof weight === 'number' ? weight : typeof weight === 'string' ? Number(weight) : Number.NaN;
  if (weight === 'bold' || (!Number.isNaN(numericWeight) && numericWeight >= 600)) codes.push('1');

  if (style['font-style'] === 'italic') codes.push('3');
  if (style['text-decoration'] === 'underline') codes.push('4');

  return codes.length ? `\u001b[${codes.join(';')}m` : '';
}

/** 用 ANSI 为文本着色；无可用样式时原样返回。 */
export function paintAnsi(text: string, style: StyleMap): string {
  const prefix = toAnsiPrefix(style);
  return prefix ? `${prefix}${text}${ANSI_RESET}` : text;
}

interface RuntimeProbe {
  document?: unknown;
  process?: {
    stdout?: { isTTY?: boolean };
    env?: Record<string, string | undefined>;
  };
}

/**
 * 探测当前环境应使用的渲染模式。
 *
 * <p>浏览器 → css，终端 → ansi，管道 / 关闭 → plain，并遵循 NO_COLOR / FORCE_COLOR 约定。</p>
 */
export function detectRenderMode(): RenderMode {
  const runtime = globalThis as RuntimeProbe;

  if (typeof runtime.document !== 'undefined') return 'css';

  const env = runtime.process?.env ?? {};
  if (env.NO_COLOR) return 'plain';
  if (env.FORCE_COLOR && env.FORCE_COLOR !== '0') return 'ansi';

  return runtime.process?.stdout?.isTTY ? 'ansi' : 'plain';
}
