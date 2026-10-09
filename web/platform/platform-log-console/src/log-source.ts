import { createLogger } from 'common-tools';
import type { LogLevel, LogRecord, Logger } from 'common-tools';

/** 可选命名空间，贴近真实项目的层级写法 */
export const NAMESPACES = ['app › auth', 'app › order', 'app › payment', 'app › render'] as const;

const SAMPLE_MESSAGES: Record<LogLevel, readonly string[]> = {
  trace: ['进入组件渲染', '开始计算派生状态', '命中本地缓存'],
  debug: ['请求参数已组装', '开关 featureFlag 取值', '表单校验通过'],
  perf: ['首屏渲染耗时', '接口往返耗时', '列表 diff 耗时'],
  info: ['用户进入订单页', '提交结算请求', '拉取用户信息'],
  success: ['登录成功', '订单创建成功', '支付回调已受理'],
  warn: ['接口首次失败，正在重试', '优惠券即将过期，已自动替换', '本地缓存写入被拒绝'],
  error: ['下游库存服务超时', '支付回调验签失败', '接口返回数据格式异常'],
};

const SAMPLE_DATA: readonly unknown[][] = [
  [],
  [{ orderId: 'SO-2026-0001' }],
  [{ sku: 'BW-001', qty: 2 }],
  [{ items: 3, amount: 268.5 }],
];

export interface LogSourceOptions {
  sessionId: string;
  release: string;
  /** 每条记录产生时回调 —— 用于页面展示与送入上报缓冲 */
  onRecord: (record: LogRecord) => void;
}

/**
 * 日志产生器。
 *
 * <p>它不是"随机打印字符串"，而是真的走一遍 `common-tools` 的 logger：
 * 级别过滤、命名空间层级、`onRecord` 钩子都是真实路径。
 * 因此这个演示页同时也在验证日志包本身 —— 如果 `child()` 的命名空间拼错了，
 * 页面上立刻能看出来。
 */
export class LogSource {
  private readonly root: Logger;

  private readonly cache = new Map<string, Logger>();

  private seq = 0;

  constructor(options: LogSourceOptions) {
    this.root = createLogger('console', {
      // 演示页需要看到全部级别，因此把阈值放到最低
      level: 'trace',
      showLevel: true,
      showNamespace: true,
      onRecord: (record) => options.onRecord(record),
      sessionId: options.sessionId,
      release: options.release,
    });
  }

  /** 取（并缓存）某个命名空间的子 logger，走的是日志包的层级能力 */
  private loggerFor(namespace: string): Logger {
    const cached = this.cache.get(namespace);
    if (cached) return cached;
    const created = namespace ? this.root.child(namespace) : this.root;
    this.cache.set(namespace, created);
    return created;
  }

  /**
   * 产生一条指定级别与命名空间的日志。
   *
   * <p><b>为什么是 switch 而不是 `logger[level](message, ...data)`：</b>
   * `perf` 的签名是 `(label, value: number, unit?)`，与其余六个级别的
   * `(message, ...data)` 不是一回事。按级别名统一调用会把性能数据点写成
   * `首屏渲染耗时 {…} undefined` —— 数值落进了消息位置，级别特有的语义被抹平。
   * 这里显式分派，让每个级别都以它自己的签名被调用。
   */
  emit(level: LogLevel, namespace: string, message: string, data: unknown[] = []): void {
    const logger = this.loggerFor(namespace);

    switch (level) {
      case 'trace':
        logger.trace(message, ...data);
        break;
      case 'debug':
        logger.debug(message, ...data);
        break;
      case 'info':
        logger.info(message, ...data);
        break;
      case 'success':
        logger.success(message, ...data);
        break;
      case 'warn':
        logger.warn(message, ...data);
        break;
      case 'error':
        logger.error(message, ...data);
        break;
      case 'perf': {
        const [value, unit] = data;
        logger.perf(
          message,
          typeof value === 'number' ? value : 0,
          typeof unit === 'string' ? unit : 'ms',
        );
        break;
      }
    }
  }

  /** 随机产生一条日志 */
  random(): void {
    const level = LEVELS[Math.floor(Math.random() * LEVELS.length)];
    const namespace = NAMESPACES[Math.floor(Math.random() * NAMESPACES.length)];
    const pool = SAMPLE_MESSAGES[level];
    const message = pool[Math.floor(Math.random() * pool.length)];

    // perf 的数据形态是"数值 + 单位"，与其余级别的"附带对象"不同，单独取样
    if (level === 'perf') {
      this.emit(level, namespace, message, [Math.round(20 + Math.random() * 380), 'ms']);
      return;
    }

    const data = SAMPLE_DATA[Math.floor(Math.random() * SAMPLE_DATA.length)];
    this.emit(level, namespace, message, data);
  }

  /** 一次性产生若干条 */
  burst(count: number): void {
    for (let i = 0; i < count; i += 1) this.random();
  }

  /** 带 Error 对象的日志 —— 用来验证错误堆栈在记录里是否完整保留 */
  raiseError(): void {
    const logger = this.loggerFor('app › payment');
    logger.error(new Error('模拟异常：支付回调验签失败'), { orderId: 'SO-2026-0002' });
  }

  /**
   * 模拟一次成功链路。
   *
   * <p>整条链路共用一个 `traceId`，这是"前端日志与后端日志能串起来"的关键：
   * 服务端会用同一个 id 记录这次请求的处理过程。
   *
   * @returns 本次链路的 traceId
   */
  simulateSuccessChain(): string {
    const traceId = this.nextTraceId();
    const flow = this.root.child('app › order', { traceId });

    flow.debug('提交订单请求已发出', { orderId: 'SO-2026-0001' });
    flow.info('进入订单创建流程');
    flow.perf('库存服务耗时', 138, 'ms');
    flow.info('库存扣减成功', { sku: 'BW-001' });
    flow.success('订单创建成功', { orderNo: 'SO-2026-0001' });

    return traceId;
  }

  /** 模拟一次失败链路，用于演示"按 traceId 追整条链路" */
  simulateFailureChain(): string {
    const traceId = this.nextTraceId();
    const flow = this.root.child('app › payment', { traceId });

    flow.info('发起支付', { amount: 268.5 });
    flow.debug('支付渠道已选择', { channel: 'wechat' });
    flow.warn('首次回调未收到，等待重试');
    flow.error(new Error('支付回调验签失败'), { traceId, attempt: 2 });

    return traceId;
  }

  private nextTraceId(): string {
    this.seq += 1;
    return `trace-${Date.now().toString(36)}-${this.seq.toString(36)}`;
  }
}

const LEVELS: readonly LogLevel[] = ['trace', 'debug', 'perf', 'info', 'success', 'warn', 'error'];
