/**
 * 上报队列：采样 / 连续重复去重 / 满批或定时 flush / 分片（≤50）/ 指数退避重试。
 *
 * <p>所有异常（含上报失败）均被吞掉并通过 {@code onError} 回调上报，绝不冒泡到业务代码。</p>
 */

import { MAX_BATCH_SIZE } from './constants';
import type { LogBatchRequest, LogItem, Transport } from './types';

/** 单通道配置。 */
export interface ChannelConfig {
  /** 上报端点（完整路径，含 baseUrl 前缀） */
  endpoint: string;
  /** 触发 flush 的队列长度阈值 */
  size: number;
  /** 触发 flush 的时间间隔（毫秒） */
  intervalMs: number;
  /** 最大额外重试次数 */
  maxRetries: number;
  /** 指数退避基数（毫秒） */
  backoffBaseMs: number;
  /** 采样率（[0,1]） */
  sampleRate: number;
  /** 上报传输 */
  transport: Transport;
  /** 请求体构造器（注入 traceId/userId/clientId/pageUrl） */
  buildRequest: (items: LogItem[]) => LogBatchRequest;
  /** 内部异常回调（默认静默） */
  onError: (error: unknown) => void;
}

/** 按固定大小切分数组（不足一组的作为最后一组）。 */
export function chunkItems<T>(items: T[], size: number): T[][] {
  const effective = Math.max(1, Math.floor(size));
  const chunks: T[][] = [];
  for (let index = 0; index < items.length; index += effective) {
    chunks.push(items.slice(index, index + effective));
  }
  return chunks;
}

/** 采样判定：rate≥1 全采；rate≤0 全不采；否则按概率。 */
export function shouldSample(rate: number): boolean {
  if (rate >= 1) {
    return true;
  }
  if (rate <= 0) {
    return false;
  }
  return Math.random() < rate;
}

/** 条目去重键（同级别 + 同事件 + 同文本视为重复）。 */
export function itemKey(item: LogItem): string {
  return [item.logLevel ?? '', item.eventType ?? '', item.eventName ?? '', item.message ?? ''].join(
    '|',
  );
}

/** 延时（退避等待；不 unref，避免 Node 在重试期间提前退出）。 */
function delay(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

/** 带指数退避的重试发送；返回是否最终成功。 */
async function sendWithRetry(items: LogItem[], config: ChannelConfig): Promise<boolean> {
  const body = config.buildRequest(items);
  for (let attempt = 0; attempt <= config.maxRetries; attempt += 1) {
    try {
      const ok = await config.transport(config.endpoint, body);
      if (ok) {
        return true;
      }
    } catch (error) {
      config.onError(error);
    }
    if (attempt < config.maxRetries) {
      await delay(config.backoffBaseMs * 2 ** attempt);
    }
  }
  return false;
}

/** 单通道队列。 */
export class LogChannel {
  private pending: LogItem[] = [];

  private lastKey: string | null = null;

  private timer: ReturnType<typeof setInterval> | null = null;

  private flushing = false;

  private destroyed = false;

  private readonly config: ChannelConfig;

  /**
   * 构造单通道队列并启动定时 flush。
   *
   * @param config 通道配置
   */
  constructor(config: ChannelConfig) {
    this.config = config;
    this.startTimer();
  }

  /** 启动定时 flush（Node 环境下 unref，避免阻塞进程退出）。 */
  private startTimer(): void {
    if (this.config.intervalMs <= 0) {
      return;
    }
    this.timer = setInterval(() => {
      void this.flush();
    }, this.config.intervalMs);
    if (typeof (this.timer as { unref?: () => void }).unref === 'function') {
      (this.timer as { unref: () => void }).unref();
    }
  }

  /**
   * 入队一条记录：先采样，再对连续重复去重，达到阈值则立刻 flush。
   *
   * @param item 日志条目
   */
  push(item: LogItem): void {
    if (this.destroyed || !shouldSample(this.config.sampleRate)) {
      return;
    }
    const key = itemKey(item);
    if (key === this.lastKey) {
      return;
    }
    this.lastKey = key;
    this.pending.push(item);
    if (this.pending.length >= this.config.size) {
      void this.flush();
    }
  }

  /** 立即 flush：分片（≤50）并逐片重试上报。 */
  async flush(): Promise<void> {
    if (this.destroyed || this.flushing || this.pending.length === 0) {
      return;
    }
    this.flushing = true;
    const items = this.pending;
    this.pending = [];
    this.lastKey = null;
    try {
      for (const batch of chunkItems(items, MAX_BATCH_SIZE)) {
        await sendWithRetry(batch, this.config);
      }
    } catch (error) {
      this.config.onError(error);
    } finally {
      this.flushing = false;
    }
  }

  /** 清空定时器与队列。 */
  destroy(): void {
    this.destroyed = true;
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
    this.pending = [];
    this.lastKey = null;
  }
}
