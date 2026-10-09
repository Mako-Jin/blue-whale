import type { LogLevelThreshold } from '../logger/types';

/** 运行环境类型。 */
export type RuntimeEnv = 'browser' | 'node' | 'unknown';

interface RuntimeProbe {
  window?: unknown;
  document?: unknown;
  location?: { hostname?: string; search?: string };
  process?: {
    env?: Record<string, string | undefined>;
    versions?: { node?: string };
  };
}

/** 本地开发时允许开启调试模式的主机名白名单。 */
export const DEBUG_HOSTNAMES: readonly string[] = ['localhost', '127.0.0.1', '0.0.0.0', '[::1]'];

function runtime(): RuntimeProbe {
  return globalThis as RuntimeProbe;
}

/**
 * 当前运行环境。
 *
 * 旧实现直接访问 `window.location`，在 Node / SSR / 构建脚本里会直接抛错。
 * 这里全部走能力探测，任何环境下都可安全调用。
 */
export function getRuntimeEnv(): RuntimeEnv {
  const probe = runtime();
  if (typeof probe.window !== 'undefined' && typeof probe.document !== 'undefined')
    return 'browser';
  if (probe.process?.versions?.node) return 'node';
  return 'unknown';
}

/** 是否运行在浏览器。 */
export function isBrowser(): boolean {
  return getRuntimeEnv() === 'browser';
}

/** 是否运行在 Node。 */
export function isNode(): boolean {
  return getRuntimeEnv() === 'node';
}

/**
 * 构建模式：`development` / `production` / `test`…
 *
 * 优先取 Vite 注入的 `import.meta.env.MODE`（浏览器），
 * 回落到 `process.env.NODE_ENV`（Node）。
 */
export function getEnvMode(): string {
  const viteMode = readViteMode();
  if (viteMode) return viteMode;
  return runtime().process?.env?.NODE_ENV ?? 'development';
}

function readViteMode(): string | undefined {
  try {
    // 非 Vite 环境下 import.meta.env 为 undefined，属性访问不会抛错
    const meta = import.meta as unknown as { env?: { MODE?: string } };
    return meta.env?.MODE;
  } catch {
    return undefined;
  }
}

/** 是否为开发模式。 */
export function isDev(): boolean {
  return getEnvMode() === 'development';
}

/** 是否为生产模式。 */
export function isProd(): boolean {
  return getEnvMode() === 'production';
}

/** 当前主机名（非浏览器环境返回 undefined）。 */
export function getCurrentHostname(): string | undefined {
  return runtime().location?.hostname;
}

let productionHostPatterns: (string | RegExp)[] = [];

/**
 * 配置生产环境域名匹配规则。
 *
 * 用于兜底：即使有人在生产域名上带上 `?mode=debug`，也不会开启调试输出。
 *
 * 支持两种写法：
 * - 精确 / 通配字符串：`'example.com'`、`'*.example.com'`（前缀通配，匹配所有子域名）
 * - 正则：`/\.example\.com$/`
 *
 * 默认**为空**。旧实现留了一段"默认规则"的注释，但整段被没闭合的
 * 注释块吞掉了，导致 DEFAULT_PRODUCTION_PATTERNS 恒为 `[]`、
 * 这段配置从未真正生效；这里不预设任何业务域名，由使用方显式声明。
 */
export function configureProductionHostnames(patterns: (string | RegExp)[]): void {
  productionHostPatterns = [...patterns];
}

/** 读取当前生效的生产域名规则（副本）。 */
export function getProductionHostnames(): (string | RegExp)[] {
  return [...productionHostPatterns];
}

function matchHostPattern(pattern: string, hostname: string): boolean {
  if (pattern === hostname) return true;
  if (pattern.startsWith('*.')) return hostname.endsWith(pattern.slice(1));
  return false;
}

/** 判断主机名是否命中生产域名规则。 */
export function isProductionHost(hostname: string | undefined = getCurrentHostname()): boolean {
  if (!hostname) return false;
  return productionHostPatterns.some((pattern) =>
    typeof pattern === 'string' ? matchHostPattern(pattern, hostname) : pattern.test(hostname),
  );
}

/**
 * 是否处于调试模式。
 *
 * 判定优先级：
 * 1. URL 上的 `?mode=debug`（命中生产域名规则时失效）；
 * 2. 开发构建 + localhost / 127.0.0.1 白名单；
 * 3. Node 下的 development 模式直接视为调试。
 */
export function isDebugMode(): boolean {
  const probe = runtime();
  const hostname = probe.location?.hostname;
  const search = probe.location?.search;

  if (search) {
    const params = new URLSearchParams(search);
    if (params.get('mode') === 'debug' && !isProductionHost(hostname)) return true;
  }

  if (!isDev()) return false;

  if (typeof hostname === 'undefined') return true;
  return DEBUG_HOSTNAMES.includes(hostname) && !isProductionHost(hostname);
}

/**
 * 日志默认阈值。
 *
 * 这是"配置"层面最实质的一处优化：旧实现只有 `debug` 一个开关 +
 * 一个 `debugMode` 布尔值，既无法压制 info、也无法只留 warn。
 * 现在由环境推导出一个默认阈值，调用方再用 `level` 精确覆盖。
 */
export function resolveDefaultLevel(): LogLevelThreshold {
  return isDebugMode() ? 'debug' : 'info';
}
