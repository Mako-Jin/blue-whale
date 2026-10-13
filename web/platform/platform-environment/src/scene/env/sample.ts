import { environmentLook, type EnvironmentLook } from './palette';
import { solarPosition, type SolarPosition } from './solar';
import type { EnvironmentState } from './types';

/**
 * 把**状态**算成**一帧的画面指令**。
 *
 * 这里是内核的出口：`sampleEnvironment` 是纯函数，同一个 state 永远得到同一份 sample。
 * 于是"时间流逝"这件事可以由任何一方推进 —— 舞台按帧推进、测试按固定步长推进、
 * 界面上按滑块直接给定 —— 三者看到的结果**逐值一致**，不存在第二份真相。
 */

const HOURS_PER_DAY = 24;
const DAYS_PER_YEAR = 365;
/** 秒 → 小时 */
const SECONDS_TO_HOURS = 1 / 3600;

export type EnvironmentSample = {
  /** 归一化之后的 state（`localHours` 已收进 `[0, 24)`，跨日时 `dayOfYear` 已进位） */
  state: EnvironmentState;
  solar: SolarPosition;
  look: EnvironmentLook;
};

/**
 * 把 `localHours` 收进 `[0, 24)`，超出的部分进到 `dayOfYear` 上。
 *
 * 不做这一步的话，时间流逝跑过午夜会得到一个 26.3 小时的"钟表时间"：
 * 太阳位置照样算得出来（公式是周期的），但界面上会显示 `26:18`，
 * 而且年积日永远不前进 —— 季节就锁死了。
 */
export function normalizeEnvironment(state: EnvironmentState): EnvironmentState {
  const dayShift = Math.floor(state.localHours / HOURS_PER_DAY);
  if (dayShift === 0 && state.localHours >= 0) {
    return state;
  }
  const dayOfYear =
    ((((state.dayOfYear - 1 + dayShift) % DAYS_PER_YEAR) + DAYS_PER_YEAR) % DAYS_PER_YEAR) + 1;
  return {
    ...state,
    dayOfYear,
    localHours: state.localHours - dayShift * HOURS_PER_DAY,
  };
}

/**
 * 推进 `deltaSeconds` 秒（真实时间）。`flow` 为 0 时原样返回 —— 连对象都不新建，
 * 让"暂停"在调用处就是零成本。
 */
export function advanceEnvironment(
  state: EnvironmentState,
  deltaSeconds: number,
): EnvironmentState {
  if (state.flow <= 0 || deltaSeconds <= 0) {
    return state;
  }
  return normalizeEnvironment({
    ...state,
    localHours: state.localHours + deltaSeconds * state.flow * SECONDS_TO_HOURS,
  });
}

export function sampleEnvironment(state: EnvironmentState): EnvironmentSample {
  const normalized = normalizeEnvironment(state);
  const solar = solarPosition({
    dayOfYear: normalized.dayOfYear,
    localHours: normalized.localHours,
    latitude: normalized.latitude,
    longitude: normalized.longitude,
    timezone: normalized.timezone,
  });
  return { state: normalized, solar, look: environmentLook(solar) };
}

/** 把小时数格式化成 `14:32`。跨日的进位在这里不体现 —— 那是日历的事，不是时钟的事。 */
export function formatClock(localHours: number): string {
  const totalMinutes = Math.round(localHours * 60);
  const hours = Math.floor(totalMinutes / 60) % HOURS_PER_DAY;
  const minutes = totalMinutes % 60;
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
}

/** 平年的月长。年积日 → 月日的换算只需要它 */
const MONTH_DAYS = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31] as const;

/**
 * 把年积日格式化成 `6月21日`。
 *
 * 界面上显示它而不是直接显示 `172`：人对"第 172 天"没有直觉，
 * 但对"6 月 21 日"有 —— 一眼就知道这是夏至，太阳该最靠北。
 */
export function formatDate(dayOfYear: number): string {
  let remaining = Math.round(dayOfYear);
  for (let month = 0; month < MONTH_DAYS.length; month += 1) {
    if (remaining <= MONTH_DAYS[month]) {
      return `${month + 1}月${remaining}日`;
    }
    remaining -= MONTH_DAYS[month];
  }
  return '12月31日';
}
