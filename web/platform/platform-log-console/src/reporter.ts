import type { ClientLogItem, ClientLogReportBody, ClientLogReportResult } from 'common-api';

/** 上报通道的对外状态 */
export interface ReporterState {
  /** 待上报条数 */
  pending: number;
  /** 后端确认落库的条数 */
  sent: number;
  /** 后端确认拒收的条数（请求成功但条目不合法） */
  rejected: number;
  /** 整批失败的次数（网络/服务端错误） */
  failedBatches: number;
  /** 因缓冲上限被丢弃的条数 */
  dropped: number;
  /** 最近一次失败原因 */
  lastError: string | undefined;
  /** 当前的退避时长（毫秒），0 表示正常间隔 */
  backoffMs: number;
}

export interface LogReporterOptions {
  app: string;
  release: string;
  sessionId: string;
  send: (body: ClientLogReportBody) => Promise<ClientLogReportResult>;
  /** 攒够多少条立即上报 */
  batchSize?: number;
  /** 定时上报间隔 */
  flushIntervalMs?: number;
  /** 缓冲上限，超出丢弃最旧的 */
  maxBuffer?: number;
  /** 退避上限 */
  maxBackoffMs?: number;
  onChange?: (state: ReporterState) => void;
}

const EMPTY_STATE: ReporterState = {
  pending: 0,
  sent: 0,
  rejected: 0,
  failedBatches: 0,
  dropped: 0,
  lastError: undefined,
  backoffMs: 0,
};

/**
 * 日志批量上报缓冲。
 *
 * <p><b>这是一个"尽力而为"的通道，四条设计约束全部来自真实故障：</b>
 *
 * <ol>
 *   <li><b>批量</b> —— 一条日志一个请求，请求量会等于日志量（用户数 × 时长 × 密度），
 *       后端会被自己的客户端打挂。</li>
 *   <li><b>有界</b> —— 缓冲与队列都有上限，超出丢弃最旧的。
 *       日志通道绝不能因为自身积压把页面拖垮，那比丢日志严重得多。</li>
 *   <li><b>退避</b> —— 后端挂了的时候，固定间隔重试等于用请求持续冲击一个已经倒下的服务。
 *       失败次数越多间隔越长，恢复后自动回到正常节奏。</li>
 *   <li><b>不抛异常、不自我记录</b> —— 上报失败如果走日志通道，会再次触发上报，
 *       一次后端故障就演变成客户端与服务端之间的雪崩。失败只更新状态，由页面呈现。</li>
 * </ol>
 */
export class LogReporter {
  private buffer: ClientLogItem[] = [];

  private timer: ReturnType<typeof setTimeout> | undefined;

  private state: ReporterState = { ...EMPTY_STATE };

  private consecutiveFailures = 0;

  private running = false;

  private flushing = false;

  private readonly batchSize: number;

  private readonly flushIntervalMs: number;

  private readonly maxBuffer: number;

  private readonly maxBackoffMs: number;

  constructor(private readonly options: LogReporterOptions) {
    this.batchSize = options.batchSize ?? 20;
    this.flushIntervalMs = options.flushIntervalMs ?? 3000;
    this.maxBuffer = options.maxBuffer ?? 500;
    this.maxBackoffMs = options.maxBackoffMs ?? 30_000;
  }

  getState(): ReporterState {
    return { ...this.state, pending: this.buffer.length };
  }

  start(): void {
    this.running = true;
    this.schedule();
  }

  stop(): void {
    this.running = false;
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = undefined;
    }
    void this.flush();
  }

  isRunning(): boolean {
    return this.running;
  }

  push(item: ClientLogItem): void {
    if (!this.running) return;

    this.buffer.push(item);

    if (this.buffer.length > this.maxBuffer) {
      const overflow = this.buffer.length - this.maxBuffer;
      this.buffer.splice(0, overflow);
      this.state.dropped += overflow;
    }

    this.emit();

    if (this.buffer.length >= this.batchSize) void this.flush();
  }

  /** 立即上报一批。任何失败都被吞掉并记录进状态，不向外抛 */
  async flush(): Promise<void> {
    if (this.flushing || this.buffer.length === 0) {
      this.schedule();
      return;
    }
    this.flushing = true;

    const batch = this.buffer.splice(0, this.batchSize);
    const body: ClientLogReportBody = {
      app: this.options.app,
      release: this.options.release,
      sessionId: this.options.sessionId,
      logs: batch,
    };

    try {
      const result = await this.options.send(body);
      this.state.sent += result?.accepted ?? batch.length;
      this.state.rejected += result?.rejected ?? 0;
      this.state.lastError = undefined;
      this.consecutiveFailures = 0;
      this.state.backoffMs = 0;
    } catch (error) {
      this.consecutiveFailures += 1;
      this.state.failedBatches += 1;
      this.state.lastError = error instanceof Error ? error.message : String(error);

      // 指数退避，封顶 maxBackoffMs
      this.state.backoffMs = Math.min(
        this.flushIntervalMs * 2 ** this.consecutiveFailures,
        this.maxBackoffMs,
      );

      // 放回队首以便下次重试；放不下就丢弃（同样遵守有界原则）
      const room = this.maxBuffer - this.buffer.length;
      const restored = Math.max(0, Math.min(batch.length, room));
      if (restored > 0) this.buffer.unshift(...batch.slice(0, restored));
      this.state.dropped += batch.length - restored;
    } finally {
      this.flushing = false;
      this.emit();
      this.schedule();
    }
  }

  private schedule(): void {
    if (!this.running) return;
    if (this.timer) clearTimeout(this.timer);

    const delay = this.state.backoffMs > 0 ? this.state.backoffMs : this.flushIntervalMs;
    this.timer = setTimeout(() => {
      this.timer = undefined;
      void this.flush();
    }, delay);
  }

  private emit(): void {
    this.options.onChange?.(this.getState());
  }
}
