/**
 * 环境内核（纯函数）。
 *
 * 三层结构，各自只做一件事：
 * - `solar.ts` —— 年积日/经纬度/时刻 → **太阳高度角与方位角**；
 * - `palette.ts` —— 高度角 → **光、雾、天穹、曝光**；
 * - `sample.ts` —— 把两者串起来，并负责"时间流逝"与状态归一化。
 *
 * 全部不碰 three、不碰 DOM，因此可以在无 GPU 环境里逐值断言 ——
 * 这是"现实世界的映射"能被验证的前提：太阳算错了，画面再好看也是错的。
 */

export {
  DEFAULT_ENVIRONMENT,
  FLOW_PRESETS,
  type EnvironmentState,
} from './types';

export {
  normalizeRadians,
  solarPosition,
  sunDirection,
  toDegrees,
  toRadians,
  type SolarInput,
  type SolarPosition,
} from './solar';

export { environmentLook, type EnvironmentLook, type SkyScatter } from './palette';

export {
  advanceEnvironment,
  formatClock,
  formatDate,
  normalizeEnvironment,
  sampleEnvironment,
  type EnvironmentSample,
} from './sample';
