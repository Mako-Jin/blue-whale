import { LOG_LEVELS, isLogLevel } from './levels';
import type { LogLevelThreshold } from './types';

interface EnvProbe {
  process?: {
    env?: Record<string, string | undefined>;
  };
}

/**
 * 解析默认日志阈值。
 *
 * <p>优先级：显式环境变量 `VITE_LOG_LEVEL` / `LOG_LEVEL` → Node 的 `NODE_ENV`
 * → 兜底 `info`。浏览器宿主通常在 {@code initAuditLogging} 中按 `import.meta.env.DEV`
 * 再覆盖一次，因此这里只负责安全默认值。</p>
 */
export function resolveDefaultLevel(): LogLevelThreshold {
  const env = (globalThis as EnvProbe).process?.env ?? {};
  const configured = env.VITE_LOG_LEVEL ?? env.LOG_LEVEL;

  if (configured === 'silent') return 'silent';
  if (
    configured &&
    (LOG_LEVELS as readonly string[]).includes(configured) &&
    isLogLevel(configured)
  ) {
    return configured;
  }

  return env.NODE_ENV === 'development' ? 'debug' : 'info';
}
