/**
 * 太阳位置（纯内核）。
 *
 * 把「年积日 + 经纬度 + 时区 + 钟表时间」换算成**高度角与方位角**。
 * 全程是闭合的解析式：没有迭代、没有查表、没有外部数据文件，
 * 于是它能在无 GPU 环境里逐值断言，也能直接对拍真实天文数据。
 *
 * ⚠️ **精度**：简化算法的赤纬误差 < 0.5°、时刻误差 < 1 分钟。对"日落泛红""夜里该黑"
 * 这类视觉判断绰绰有余；**不适用于天文导航**。
 *
 * 角度约定（与 `player/motor.ts` 的罗盘严格一致）：
 * - 方位角**从正北起、顺时针为正**：东 90°、南 180°、西 270°；
 * - 罗盘上 **-Z 是北**，所以"南"落在世界坐标的 +Z 上。
 */

const DEG_TO_RAD = Math.PI / 180;
const RAD_TO_DEG = 180 / Math.PI;
const MINUTES_PER_DAY = 1440;
/** 一年按 365 天算：闰年带来的 0.24° 赤纬差肉眼不可辨，却会让"日期进位"复杂一倍 */
const DAYS_PER_YEAR = 365;

export type SolarInput = {
  /** 年积日（1 = 1 月 1 日） */
  dayOfYear: number;
  /** 当地钟表时间（小时，可含小数） */
  localHours: number;
  /** 纬度（度，北正） */
  latitude: number;
  /** 经度（度，东正） */
  longitude: number;
  /** 时区（小时，东正） */
  timezone: number;
};

export type SolarPosition = {
  /** 高度角（弧度）。**负值 = 在地平线以下** */
  altitude: number;
  /** 方位角（弧度，`[0, 2π)`）。从正北起、顺时针 */
  azimuth: number;
  /** 太阳赤纬（弧度） */
  declination: number;
  /** 均时差（分钟）：真太阳时与平太阳时之差，年内在 ±16 分钟内摆动 */
  equationOfTime: number;
  /** 地方真太阳时（小时，`[0, 24)`） */
  solarHours: number;
};

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

/** 归一到 `[0, span)`（span 为负值传入时也成立）。 */
function wrap(value: number, span: number): number {
  return ((value % span) + span) % span;
}

/** 归一到 `[0, 2π)`。 */
export function normalizeRadians(angle: number): number {
  return wrap(angle, Math.PI * 2);
}

/** 角度转弧度 / 弧度转角度。写成函数是为了让调用处读起来是"度"，而不是一串魔术数。 */
export function toRadians(degrees: number): number {
  return degrees * DEG_TO_RAD;
}

export function toDegrees(radians: number): number {
  return radians * RAD_TO_DEG;
}

/**
 * 赤纬与均时差 —— Spencer 1971 的傅里叶级数近似。
 *
 * 两项都只是年积日的函数：太阳的位置一年一个循环，与年份无关
 * （严格说每世纪会漂 0.013°，这里不需要）。
 */
function declinationAndEot(dayOfYear: number): {
  declination: number;
  equationOfTime: number;
} {
  const gamma = ((2 * Math.PI) / DAYS_PER_YEAR) * (dayOfYear - 1);
  const declination =
    0.006918 -
    0.399912 * Math.cos(gamma) +
    0.070257 * Math.sin(gamma) -
    0.006758 * Math.cos(2 * gamma) +
    0.000907 * Math.sin(2 * gamma) -
    0.002697 * Math.cos(3 * gamma) +
    0.00148 * Math.sin(3 * gamma);
  const equationOfTime =
    229.18 *
    (0.000075 +
      0.001868 * Math.cos(gamma) -
      0.032077 * Math.sin(gamma) -
      0.014615 * Math.cos(2 * gamma) -
      0.040849 * Math.sin(2 * gamma));
  return { declination, equationOfTime };
}

export function solarPosition(input: SolarInput): SolarPosition {
  const { declination, equationOfTime } = declinationAndEot(input.dayOfYear);

  /**
   * 钟表时间 → 地方真太阳时（分钟）。两项修正各管一件事：
   *
   * - `equationOfTime`：地球轨道偏心率 + 黄赤交角造成的"真太阳时 vs 平太阳时"偏差；
   * - `4 × 经度 − 60 × 时区`：地点偏离时区中央经线的部分（每向东 1° 早 4 分钟）。
   *
   * 少了第二项，乌鲁木齐（东经 87°、时区 +8）的"正午"会被算到下午三点 ——
   * 那是这类简化算法最常见的错法。
   */
  const solarMinutes = wrap(
    input.localHours * 60 + equationOfTime + 4 * input.longitude - 60 * input.timezone,
    MINUTES_PER_DAY,
  );
  /** 时角：正午后为正（太阳在西），正午为 0。1440 分钟 / 4 = 360° */
  const hourAngle = toRadians(solarMinutes / 4 - 180);

  const latitude = toRadians(input.latitude);
  const sinAltitude =
    Math.sin(latitude) * Math.sin(declination) +
    Math.cos(latitude) * Math.cos(declination) * Math.cos(hourAngle);
  const altitude = Math.asin(clamp(sinAltitude, -1, 1));

  /**
   * 方位角用 `atan2` 而不是 `acos`。
   *
   * `acos` 形式的方位角在地平线附近与正午前后导数趋于无穷，
   * 一点浮点噪声就能让它跳半圈 —— 表现是天穹里的太阳"啪"地弹到对面。
   * `atan2` 全程连续，只在极点附近退化（而我们对极点没有需求）。
   */
  const azimuth = normalizeRadians(
    Math.atan2(
      -Math.cos(declination) * Math.sin(hourAngle),
      Math.sin(declination) * Math.cos(latitude) -
        Math.cos(declination) * Math.sin(latitude) * Math.cos(hourAngle),
    ),
  );

  return { altitude, azimuth, declination, equationOfTime, solarHours: solarMinutes / 60 };
}

/**
 * 太阳在**世界坐标**里的方向（单位向量，从原点指向太阳）。
 *
 * 罗盘约定：-Z 是北、+X 是东。于是
 * `x = cos(alt)·sin(az)`、`z = −cos(alt)·cos(az)`、`y = sin(alt)`。
 * 代入方位角 180°（南）得 `z = +cos(alt)` —— 正是世界 +Z，与"南"一致。
 */
export function sunDirection(altitude: number, azimuth: number): readonly [number, number, number] {
  const horizontal = Math.cos(altitude);
  return [horizontal * Math.sin(azimuth), Math.sin(altitude), -horizontal * Math.cos(azimuth)];
}
