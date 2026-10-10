/**
 * 通用批量上报通道。
 *
 * <p><b>它不是"日志上报器"</b>：本模块不认识日志，也不认识事件。它只做一件事 ——
 * 把"随时零星产生的条目"攒成"有界、可退避、失败不抛出的一批批请求"。
 *
 * <p>之所以要抽出来：日志管道里那四条设计约束（批量 / 有界 / 退避 / 不抛异常）
 * 是**通道本身的属性**，与上报的内容无关。行为事件管道的需求与之逐条相同，
 * 各写一份的结果是两份实现立刻开始漂移 —— 一份修了并发缺陷，另一份没修。
 *
 * <p><b>四条约束全部来自真实故障，不是理论</b>：
 *
 * <ol>
 *   <li><b>批量</b> —— 一条一个请求，请求量会等于条目量（用户数 × 时长 × 密度），
 *       后端会被自己的客户端打挂。</li>
 *   <li><b>有界</b> —— 缓冲有上限，超出丢弃最旧的。上报通道绝不能因为自身积压
 *       把页面拖垮，那比丢几条数据严重得多。</li>
 *   <li><b>退避</b> —— 后端挂了的时候，固定间隔重试等于用请求持续冲击一个已经倒下的服务。
 *       失败次数越多间隔越长，恢复后自动回到正常节奏。</li>
 *   <li><b>不抛异常、不自我记录</b> —— 上报失败如果走到日志/事件通道本身，
 *       会再次触发上报，一次后端故障就演变成客户端与服务端之间的雪崩。
 *       失败只更新状态，由调用方呈现。</li>
 * </ol>
 */

/** 通道的对外状态。调用方据此渲染"上报健康度"，通道自己不做任何呈现 */
export interface BatchChannelState {
  /** 待上报条数 */
  pending: number;
  /** 后端确认接收的条数 */
  sent: number;
  /** 后端确认拒收的条数（请求成功但条目不合法） */
  rejected: number;
  /** 整批失败的次数（网络 / 服务端错误） */
  failedBatches: number;
  /** 因缓冲上限被丢弃的条数 */
  dropped: number;
  /** 最近一次失败原因 */
  lastError: string | undefined;
  /** 当前的退避时长（毫秒），0 表示正常间隔 */
  backoffMs: number;
}

export interface BatchChannelOptions<T, B, R> {
  /** 把一批条目装配成请求体 */
  buildBody: (items: T[]) => B;
  /** 发送。抛出即视为整批失败，条目会被退回缓冲等待重试 */
  send: (body: B) => Promise<R>;
  /**
   * 从返回结果里解出「确认条数 / 拒收条数」。
   *
   * <p>缺省视为整批接受 —— 但不是所有后端都会返回这两个数，
   * 而"按批大小计"与"按返回值计"在部分接受时结果不同，因此做成显式钩子而不是猜。
   */
  resolveCounts?: (result: R, batch: T[]) => { accepted: number; rejected: number };
  /** 攒够多少条立即上报 */
  batchSize?: number;
  /** 定时上报间隔 */
  flushIntervalMs?: number;
  /** 缓冲上限，超出丢弃最旧的 */
  maxBuffer?: number;
  /** 退避上限 */
  maxBackoffMs?: number;
  onChange?: (state: BatchChannelState) => void;
  /**
   * 页面卸载时的兜底发送。
   *
   * <p><b>为什么必须有：</b>页面关闭是这条通道最大的数据损失来源 —— 定时器还没到点，
   * 缓冲里那一批就随页面一起消失，而"用户离开"往往正是最值得记录的行为。
   *
   * <p>这个回调只能**同步**发出（卸载阶段没有 await 的机会），因此传输方式由调用方决定：
   * `navigator.sendBeacon`，或 `fetch(..., { keepalive: true })`。
   * 返回 `true` 表示已交给浏览器接管；返回 `false` 会把条目退回缓冲。
   */
  sendOnUnload?: (body: B, items: T[]) => boolean;
}

const EMPTY_STATE: BatchChannelState = {
  pending: 0,
  sent: 0,
  rejected: 0,
  failedBatches: 0,
  dropped: 0,
  lastError: undefined,
  backoffMs: 0,
};

/** 默认参数。集中在一处，便于 README 与实现不脱节 */
export const BATCH_DEFAULTS = {
  batchSize: 20,
  flushIntervalMs: 3000,
  maxBuffer: 500,
  maxBackoffMs: 30_000,
} as const;

export class BatchChannel<T, B, R = unknown> {
  private buffer: T[] = [];

  private timer: ReturnType<typeof setTimeout> | undefined;

  private state: BatchChannelState = { ...EMPTY_STATE };

  private consecutiveFailures = 0;

  private running = false;

  private flushing = false;

  private readonly batchSize: number;

  private readonly flushIntervalMs: number;

  private readonly maxBuffer: number;

  private readonly maxBackoffMs: number;

  constructor(private readonly options: BatchChannelOptions<T, B, R>) {
    this.batchSize = options.batchSize ?? BATCH_DEFAULTS.batchSize;
    this.flushIntervalMs = options.flushIntervalMs ?? BATCH_DEFAULTS.flushIntervalMs;
    this.maxBuffer = options.maxBuffer ?? BATCH_DEFAULTS.maxBuffer;
    this.maxBackoffMs = options.maxBackoffMs ?? BATCH_DEFAULTS.maxBackoffMs;
  }

  getState(): BatchChannelState {
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

  push(item: T): void {
    // 未 start 时不缓冲：页面还没准备好就开始攒数据，只会在正式启动前先攒出一批
    // 来源可疑的条目
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
    const body = this.options.buildBody(batch);

    try {
      const result = await this.options.send(body);
      const counts = this.options.resolveCounts?.(result, batch) ?? {
        accepted: batch.length,
        rejected: 0,
      };
      this.state.sent += counts.accepted;
      this.state.rejected += counts.rejected;
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

  /**
   * 把缓冲里**全部**条目一次性交给卸载兜底发送。
   *
   * <p>与 {@link flush} 的区别：这里不受 `batchSize` 限制 —— 页面都要没了，
   * 分批发等于只发首批。也因此只能同步，不能 await。
   */
  flushOnUnload(): void {
    if (!this.options.sendOnUnload || this.buffer.length === 0) return;

    const items = this.buffer.splice(0, this.buffer.length);
    const body = this.options.buildBody(items);

    // 不预置 `= false`：两个分支都会赋值，预置的初值永远读不到
    let handedOff: boolean;
    try {
      handedOff = this.options.sendOnUnload(body, items);
    } catch {
      // 兜底发送自身失败也只能咽下：此刻抛异常会打断卸载流程，
      // 而卸载流程属于页面，不属于这条通道
      handedOff = false;
    }

    if (!handedOff) {
      // 交出去失败就放回缓冲。若确实正在卸载，这些条目会随页面一起消失 ——
      // 这是"尽力而为"通道的既定代价，记在状态里而不是抛出去
      this.buffer.unshift(...items);
      this.state.dropped += items.length;
    }
    this.emit();
  }

  /**
   * 绑定页面卸载兜底。
   *
   * <p>同时监听 `pagehide` 与 `visibilitychange`：**不能只依赖 `beforeunload`** ——
   * 移动端浏览器在切后台时可能直接冻结页面，`beforeunload` 根本不会触发，
   * 而"切后台"在实际使用里比"关页签"频繁得多。
   *
   * @returns 解绑函数
   */
  bindUnload(target: EventTarget = globalThis as unknown as EventTarget): () => void {
    const onPageHide = (): void => this.flushOnUnload();
    const onVisibilityChange = (): void => {
      const doc = (globalThis as { document?: { visibilityState?: string } }).document;
      if (doc?.visibilityState === 'hidden') this.flushOnUnload();
    };

    target.addEventListener('pagehide', onPageHide);
    target.addEventListener('visibilitychange', onVisibilityChange);
    return () => {
      target.removeEventListener('pagehide', onPageHide);
      target.removeEventListener('visibilitychange', onVisibilityChange);
    };
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
