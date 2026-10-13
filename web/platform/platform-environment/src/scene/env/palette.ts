import { sunDirection, toDegrees, type SolarPosition } from './solar';

/**
 * 高度角 → 画面参数（纯内核）。
 *
 * 这是"现实世界的映射"的**翻译层**：左边是物理量（太阳高度角），
 * 右边是渲染量（主光强度与色温、环境光、雾、天穹散射参数、曝光）。
 *
 * 三条刻意的取舍：
 *
 * 1. **只用高度角做自变量。** 它一个量就同时编码了"几点、什么季节、什么纬度"——
 *    比"按小时查表"高明的地方在于：换到漠河或赤道，同一套曲线照样成立。
 * 2. **关键帧之间线性插值，不用指数或样条。** 曲线要能被打印出来看懂，
 *    才能被讨论和修改；拟合得很漂亮但没人敢动的曲线是没有价值的。
 * 3. **不返回绝对雾距，返回系数。** 各场景的雾距基调差着数量级
 *    （丛林 9~46、写字楼 46~130），环境只该说"今天能见度是几成"，
 *    不该把丛林也拉成写字楼那样通透。
 */

/** 一对"颜色 + 强度"的关键帧值。 */
type LightLevel = { color: number; intensity: number };
type Keyframe<T> = { at: number; value: T };
type Keyframes<T> = readonly Keyframe<T>[];

/** 天穹散射参数，直接对应 three 的 `Sky` 着色器 uniforms。 */
export type SkyScatter = {
  turbidity: number;
  rayleigh: number;
  mieCoefficient: number;
  mieDirectionalG: number;
};

/** 环境算出的**画面指令**。渲染层照此执行，不做二次判断。 */
export type EnvironmentLook = {
  /** 主光方向（单位向量，从原点指向光源）。夜间它取反成"月亮"，见 `rig.ts` */
  keyDirection: readonly [number, number, number];
  keyColor: number;
  keyIntensity: number;
  ambientColor: number;
  ambientIntensity: number;
  /** 由高度角算出的**地平线色** —— 天光的本体，也是"远处该融进什么颜色"的答案 */
  horizonColor: number;
  /**
   * 建议把场景基色混向 `horizonColor` 的权重（0 = 保留场景基色，1 = 完全用天光）。
   *
   * 给**建议值**而不是强制值：丛林要绿、太空要黑、实验室要冷蓝 ——
   * 各场景的气质差异极大，把"要多像真实天空"的决定权留给场景自己，
   * 环境只负责说"此刻天光是什么颜色"。
   */
  backgroundWeight: number;
  /** 雾距系数。夜间能见度下降，雾就该更近 */
  fogNearScale: number;
  fogFarScale: number;
  /** 色调映射曝光 */
  exposure: number;
  /** 天穹散射参数 */
  sky: SkyScatter;
  /** 白昼强度 0~1（晨昏线附近平滑过渡）。二期给云量、降水强度当权重用 */
  daylight: number;
  /** 太阳是否已落到地平线以下（`altitude < -4°`，即民用暮光结束之后） */
  night: boolean;
};

const DEG_TO_RAD = Math.PI / 180;

/**
 * 关键帧按 **altitude（度）** 排布。
 *
 * 取值不是凭空捏的，三条锚点是真实的：
 * - `-0.833°` 是天文上的日出日落定义（太阳圆面 + 大气折射）；
 * - `-6°` / `-18°` 分别是民用暮光与天文暮光的边界；
 * - 白天的主光强度约 3.1、环境光约 0.55 —— 与场景此前手调的常数量级一致，
 *   所以接上环境之后画面不会突然换一个样子。
 */
const KEY_LIGHT: Keyframes<LightLevel> = [
  { at: -18, value: { color: 0x8fa6d8, intensity: 0.05 } },
  { at: -6, value: { color: 0xff9a6b, intensity: 0.16 } },
  { at: -0.833, value: { color: 0xff8b45, intensity: 0.5 } },
  { at: 6, value: { color: 0xffb066, intensity: 1.25 } },
  { at: 20, value: { color: 0xffdcae, intensity: 2.3 } },
  { at: 45, value: { color: 0xfff2dc, intensity: 3.1 } },
  { at: 90, value: { color: 0xffffff, intensity: 3.4 } },
];

const AMBIENT: Keyframes<LightLevel> = [
  { at: -18, value: { color: 0x1b2440, intensity: 0.1 } },
  { at: -6, value: { color: 0x2c3760, intensity: 0.18 } },
  { at: -0.833, value: { color: 0x3f4a75, intensity: 0.26 } },
  { at: 6, value: { color: 0x5d6892, intensity: 0.36 } },
  { at: 20, value: { color: 0x7a86b8, intensity: 0.48 } },
  { at: 45, value: { color: 0x8f9ec4, intensity: 0.56 } },
  { at: 90, value: { color: 0x9aa8cc, intensity: 0.6 } },
];

/**
 * 地平线色。
 *
 * 日落那一段给到 `0xc9703f`（橙红）不是调出来的"好看"——
 * 它是大气散射的物理结果：太阳低角度时，直射光在大气里的路径长度
 * 是正午的几十倍，短波被散射殆尽，只剩下长波。这就是"日落泛红"。
 * 而正午的地平线是淡蓝白（瑞利散射把整个天顶的蓝推到地平线附近）。
 */
const HORIZON: Keyframes<number> = [
  { at: -18, value: 0x0a0e1c },
  { at: -6, value: 0x1d2440 },
  { at: -0.833, value: 0x6b3a4e },
  { at: 3, value: 0xc9703f },
  { at: 8, value: 0x9fa8c9 },
  { at: 25, value: 0xa8b6d6 },
  { at: 60, value: 0xb6c4e2 },
];

const BACKGROUND_WEIGHT: Keyframes<number> = [
  { at: -18, value: 0.55 },
  { at: -6, value: 0.7 },
  { at: 6, value: 0.8 },
  { at: 45, value: 0.8 },
];

const FOG_NEAR_SCALE: Keyframes<number> = [
  { at: -18, value: 0.55 },
  { at: -6, value: 0.75 },
  { at: 10, value: 1 },
  { at: 45, value: 1.05 },
];

const FOG_FAR_SCALE: Keyframes<number> = [
  { at: -18, value: 0.6 },
  { at: -6, value: 0.8 },
  { at: 10, value: 1 },
  { at: 45, value: 1.05 },
];

const EXPOSURE: Keyframes<number> = [
  { at: -18, value: 1.2 },
  { at: -0.833, value: 1.12 },
  { at: 10, value: 1.05 },
  { at: 90, value: 1.05 },
];

/**
 * 天穹散射（Preetham 模型）。
 *
 * `rayleigh` 是"天空有多蓝"：正午 1.1、日落拉到 2.6 —— 后者正是霞光的来源。
 * `turbidity` 是大气浑浊度（气溶胶）：日落时给 8，云霞才有"厚"的感觉。
 */
const SKY: Keyframes<SkyScatter> = [
  { at: -18, value: { turbidity: 2, rayleigh: 0.3, mieCoefficient: 0.02, mieDirectionalG: 0.7 } },
  { at: -6, value: { turbidity: 4, rayleigh: 1.4, mieCoefficient: 0.012, mieDirectionalG: 0.75 } },
  {
    at: -0.833,
    value: { turbidity: 8, rayleigh: 2.6, mieCoefficient: 0.008, mieDirectionalG: 0.8 },
  },
  { at: 10, value: { turbidity: 6, rayleigh: 1.8, mieCoefficient: 0.006, mieDirectionalG: 0.8 } },
  { at: 45, value: { turbidity: 4, rayleigh: 1.1, mieCoefficient: 0.004, mieDirectionalG: 0.8 } },
  { at: 90, value: { turbidity: 3, rayleigh: 0.9, mieCoefficient: 0.003, mieDirectionalG: 0.8 } },
];

function lerp(left: number, right: number, t: number): number {
  return left + (right - left) * t;
}

/** 按通道插值两个 `0xRRGGBB`。在 sRGB 空间直接插值不够"物理"，但胜在可预测且可断言 */
function mixColor(left: number, right: number, t: number): number {
  const red = Math.round(lerp((left >> 16) & 0xff, (right >> 16) & 0xff, t));
  const green = Math.round(lerp((left >> 8) & 0xff, (right >> 8) & 0xff, t));
  const blue = Math.round(lerp(left & 0xff, right & 0xff, t));
  return (red << 16) | (green << 8) | blue;
}

/** 在关键帧之间线性插值。两端之外一律取端点值（不外推 —— 外推会让夜色变成负亮度）。 */
function sampleKeyframes<T>(
  frames: Keyframes<T>,
  x: number,
  blend: (left: T, right: T, t: number) => T,
): T {
  const first = frames[0];
  const last = frames[frames.length - 1];
  if (x <= first.at) {
    return first.value;
  }
  if (x >= last.at) {
    return last.value;
  }
  for (let index = 0; index < frames.length - 1; index += 1) {
    const left = frames[index];
    const right = frames[index + 1];
    if (x >= left.at && x <= right.at) {
      return blend(left.value, right.value, (x - left.at) / (right.at - left.at));
    }
  }
  return last.value;
}

const blendLevel = (left: LightLevel, right: LightLevel, t: number): LightLevel => ({
  color: mixColor(left.color, right.color, t),
  intensity: lerp(left.intensity, right.intensity, t),
});

const blendSky = (left: SkyScatter, right: SkyScatter, t: number): SkyScatter => ({
  turbidity: lerp(left.turbidity, right.turbidity, t),
  rayleigh: lerp(left.rayleigh, right.rayleigh, t),
  mieCoefficient: lerp(left.mieCoefficient, right.mieCoefficient, t),
  mieDirectionalG: lerp(left.mieDirectionalG, right.mieDirectionalG, t),
});

/** 平滑阶跃：把 `[edge0, edge1]` 映射到 `[0, 1]`，两端导数为 0（不会有折角） */
function smoothstep(edge0: number, edge1: number, x: number): number {
  const t = Math.min(1, Math.max(0, (x - edge0) / (edge1 - edge0)));
  return t * t * (3 - 2 * t);
}

/** 民用暮光结束之后就算"夜"：此时主光交给月亮，天穹只剩余晖 */
const NIGHT_ALTITUDE = -4;

export function environmentLook(solar: SolarPosition): EnvironmentLook {
  const altitude = toDegrees(solar.altitude);
  const key = sampleKeyframes(KEY_LIGHT, altitude, blendLevel);
  const ambient = sampleKeyframes(AMBIENT, altitude, blendLevel);

  return {
    keyDirection: sunDirection(solar.altitude, solar.azimuth),
    keyColor: key.color,
    keyIntensity: key.intensity,
    ambientColor: ambient.color,
    ambientIntensity: ambient.intensity,
    horizonColor: sampleKeyframes(HORIZON, altitude, mixColor),
    backgroundWeight: sampleKeyframes(BACKGROUND_WEIGHT, altitude, lerp),
    fogNearScale: sampleKeyframes(FOG_NEAR_SCALE, altitude, lerp),
    fogFarScale: sampleKeyframes(FOG_FAR_SCALE, altitude, lerp),
    exposure: sampleKeyframes(EXPOSURE, altitude, lerp),
    sky: sampleKeyframes(SKY, altitude, blendSky),
    daylight: smoothstep(-6, 6, altitude),
    night: altitude < NIGHT_ALTITUDE,
  };
}

export { DEG_TO_RAD };
