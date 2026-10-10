/**
 * 行为事件内核。
 *
 * <p>与框架无关：不依赖 React / Vue，不依赖任何运行时第三方库。
 *
 * <p>三层职责，边界刻意划清：
 * <ul>
 *   <li>{@link createTracker} —— 语义化 API 与内部总线（补齐上下文、中间件、多订阅者）</li>
 *   <li>{@link BatchChannel}（见 `common-tools/report`）—— 传输通道：批量 / 有界 / 退避 / 卸载兜底</li>
 *   <li>{@link sanitize}（见 `common-tools/json`）—— 把任意值修剪成可序列化形态</li>
 * </ul>
 *
 * <p><b>本模块只管"产生事件"，不管"怎么送出去"。</b>送出去的方式（走哪个接口、
 * 用不用 beacon）由应用接线 —— 与日志管道完全一致的分工，
 * 也让内核保持对具体接口零依赖，可以直接用于构建脚本或 Node 工具链。
 */

export { createTracker } from './tracker';

export { EVENT_NAME_PATTERN, isValidEventName } from './types';

export type {
  EventContext,
  EventEnvelope,
  EventListener,
  EventMiddleware,
  EventPropsInput,
  TrackOptions,
  Tracker,
  TrackerOptions,
} from './types';
