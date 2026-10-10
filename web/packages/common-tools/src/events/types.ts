import type { JsonObject } from '../json';

/**
 * 行为事件信封。
 *
 * <p><b>这是前后端之间的契约，不是"随手记一条"。</b>字段一旦上报过就不能改名 ——
 * 改名不会报错，只会让历史数据的含义在某一刻断裂，而这种断裂要到月底看报表时才发现。
 * 因此本结构刻意收得比日志紧：日志可以自由加字段，事件不行。
 *
 * <p>与日志 {@link LogRecord} 的关系：两者都在"前端产生 → 服务端落库"，
 * 但数据性质完全不同 —— 日志是技术切片（回答"系统在干什么"），
 * 事件是业务事实（回答"用户做了什么"）。保留期、采样策略、消费方、索引组合
 * 逐项不同，因此是**两条独立管道**，只在传输层复用。详见 ADR-0011。
 */
export interface EventEnvelope {
  /** 事件唯一 id（幂等键），客户端生成。重发时保持不变，供消费侧去重 */
  eventId: string;
  /**
   * 事件名，形如 `order.submit`。
   *
   * <p>**永久契约**：一旦上报过就不能改名。必须满足 {@link EVENT_NAME_PATTERN} ——
   * 小写、点分、至少两段。校验前移到客户端，是为了让拼错的名字在开发时就暴露，
   * 而不是等到接口返回 50000 才发现。
   */
  eventName: string;
  /**
   * 行为发生时间（毫秒）。**客户端时钟不可信**（用户会改系统时间、时区可能错），
   * 因此它只用于展示与归因参考；排序、分页、时间窗过滤一律用服务端接收时间。
   */
  clientTime: number;
  /** 会话标识 */
  sessionId: string;
  /** 登录用户标识；未登录时为空 */
  userId?: string;
  /**
   * 匿名设备标识。
   *
   * <p>登录前只有它，登录后才有 `userId`。两者都带上，分析侧才能把
   * "匿名期的浏览"与"登录后的下单"缝成同一个人的一条链路 ——
   * 缺了它，转化漏斗的漏斗底永远对不上漏斗口。
   */
  deviceId?: string;
  /** 触发事件时的路由 */
  route?: string;
  /** 应用版本 / 构建号。分析时用来切开"改版前 / 改版后" */
  release?: string;
  /**
   * 事件属性。已由 {@link sanitize} 修剪为可序列化形态
   *
   * <p><b>刻意没有 `traceId`。</b>日志管道有它（用于排查一次失败请求的完整链路），
   * 但事件的关联维度是**人**（漏斗、留存、分群），靠 `userId` / `deviceId` / `sessionId`
   * 就够。更关键的是：契约没有这一列，加了它就是一条"发出去也没人接"的假字段 ——
   * 而字段一旦写进契约就是永久的，宁可不加。
   * 真需要"事件与日志对得上"时，按 (sessionId, serverTime) 关联通常已经够用；
   * 若要精确到次，是在后端加一列而不是现在猜。
   */
  props?: JsonObject;
  /**
   * 属性结构版本。
   *
   * <p>props 增删字段或改变字段语义时**必须递增**。否则同一列里会同时躺着
   * 两套含义不同的结构，消费方无差别解读会得到静默错误的结果（而不是一个报错）。
   */
  propsVersion?: number;
}

/** 事件属性入参。接受任意值，由内核负责修剪成 {@link JsonObject} */
export type EventPropsInput = Record<string, unknown>;

/** 单次 {@link Tracker.track} 的可选项 */
export interface TrackOptions {
  /**
   * 属性结构版本，缺省为 1。
   *
   * <p>props 增删字段或改变字段语义时**必须递增**。
   *
   * <p>做成**按次传**而不是全局配置：版本属于"某个事件的结构"，
   * 不同事件在同一个版本号上没有任何共同含义 —— 一个全局的 `propsVersion = 3`
   * 会让"这个 3 到底指哪件事"变成无解的追问。
   */
  propsVersion?: number;
}

/**
 * 随每个事件自动附带的上下文。
 *
 * <p>与"事件属性"的区别：属性属于**这一次操作**（订单号、金额），
 * 上下文属于**当时的会话状态**（谁、在哪、哪个版本）。上下文由应用统一维护，
 * 业务代码不必每次重复传递。
 */
export interface EventContext {
  sessionId: string;
  userId?: string;
  deviceId?: string;
  route?: string;
  release?: string;
}

/** 事件订阅者 */
export type EventListener = (event: EventEnvelope) => void;

/**
 * 事件中间件。
 *
 * <p>拿到事件与 `next`，可以改写事件、也可以返回 `null` 把它丢掉（脱敏、采样、屏蔽）。
 * 返回 `null` 即**终止整条链**，后续中间件与订阅者都不会看到它。
 */
export type EventMiddleware = (
  event: EventEnvelope,
  next: (event: EventEnvelope) => EventEnvelope | null,
) => EventEnvelope | null;

export interface TrackerOptions {
  /** 初始上下文 */
  context?: Partial<EventContext>;
  /**
   * 上下文提供者，每次发布时求值。
   *
   * <p>做成"每次求值"而不是"启动时快照"：用户登录、路由跳转都发生在运行期，
   * 快照会让登录后的事件仍然带着登录前的身份 —— 而且不会有任何报错。
   */
  contextProvider?: () => Partial<EventContext>;
  /** 当前时间来源，便于测试注入固定时钟 */
  now?: () => number;
  /** 事件 id 生成器 */
  idFactory?: () => string;
  /** 中间件，先注册的在最外层 */
  middlewares?: EventMiddleware[];
  /** 初始订阅者 */
  listeners?: EventListener[];
  /**
   * 异常出口。埋点链路上的任何异常都不向外抛，一律送到这里。
   *
   * <p>缺省静默：埋点失败的严重性远低于它可能打断的业务逻辑。
   * 应用通常接上自己的日志器，这样"埋点坏了"是可观测的，而不是无声无息。
   */
  onError?: (error: unknown, stage: string) => void;
}

/** 事件追踪器：业务侧唯一需要认识的入口 */
export interface Tracker {
  /**
   * 发布一个事件。
   *
   * <p>业务代码只需要认识这一个方法 —— 它之后的一切（补齐上下文、修剪属性、
   * 交给谁、怎么发）都与业务无关。
   *
   * @returns 最终发布出去的信封；被中间件丢弃或内部异常时返回 `null`
   */
  track(eventName: string, props?: EventPropsInput, options?: TrackOptions): EventEnvelope | null;
  /** 订阅事件。返回取消订阅函数 */
  subscribe(listener: EventListener): () => void;
  /** 追加中间件。返回移除函数 */
  use(middleware: EventMiddleware): () => void;
  /** 合并式更新上下文（登录、跳路由时调用） */
  updateContext(patch: Partial<EventContext>): void;
  /** 读取当前上下文快照 */
  getContext(): Readonly<EventContext>;
  /**
   * 清除登录身份，保留会话与设备标识。
   *
   * <p>单独开一个方法而不是让调用方 `updateContext({ userId: undefined })`：
   * 登出是最容易漏掉的一步，漏了的后果是"登出后的事件仍记在上一个人名下"，
   * 而这在有共享设备的场景下就是数据事故。
   */
  clearUser(): void;
}

/**
 * 事件名格式：小写字母开头，点分至少两段，形如 `order.submit` / `page.view` / `log.console.generate`。
 *
 * <p>与后端 `ClientEventService` 的校验规则**必须完全一致**。两边各写一套的后果是
 * 客户端放行、服务端逐条拒绝，表现为"接口返回成功但一条也没落库"。
 */
export const EVENT_NAME_PATTERN = /^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/;

/** 事件名是否合法 */
export function isValidEventName(name: string): boolean {
  return EVENT_NAME_PATTERN.test(name);
}
