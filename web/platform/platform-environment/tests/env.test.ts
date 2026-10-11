import { describe, expect, it } from 'vitest';
import {
  DEFAULT_ENVIRONMENT,
  advanceEnvironment,
  formatClock,
  formatDate,
  normalizeEnvironment,
  sampleEnvironment,
  solarPosition,
  sunDirection,
  toDegrees,
  toRadians,
  environmentLook,
  type EnvironmentState,
  type SolarPosition,
} from '@/scene';

/**
 * 环境内核的断言。
 *
 * 这一层之所以值得逐值对拍，是因为它是**整个大气系统的唯一真相**：
 * 画面里那个太阳、天穹的颜色、雾的远近、夜里的月光，全部由它推导出来。
 * 它错了，画面只会"看起来还行但不对"，而那种错误在浏览器里根本抓不住。
 *
 * 下面对拍的是**公开天文数据**（北京，UTC+8）：
 *
 * | 日期 | 正午高度角 | 日出 | 日落 |
 * | --- | --- | --- | --- |
 * | 夏至 | 73.5° | 04:46 | 19:46 |
 * | 冬至 | 26.6° | 07:32 | 16:53 |
 *
 * 简化算法允许 1° / 2 分钟的偏差 —— 超出就说明公式或约定错了，而不是"精度不够"。
 */

/** 北京 */
const BEIJING = { latitude: 39.9042, longitude: 116.4074, timezone: 8 };

/** 一年中的几个特征日 */
const SUMMER_SOLSTICE = 172;
const WINTER_SOLSTICE = 355;
const VERNAL_EQUINOX = 80;

function altitudeAt(dayOfYear: number, localHours: number): number {
  return toDegrees(solarPosition({ ...BEIJING, dayOfYear, localHours }).altitude);
}

/** 扫描一天，找出高度角上穿 / 下穿 -0.833°（天文日出日落定义）的时刻（小时） */
function horizonCrossings(dayOfYear: number): { rise: number; set: number } {
  const EDGE = -0.833;
  let rise = Number.NaN;
  let set = Number.NaN;
  for (let minute = 0; minute < 1440; minute += 1) {
    const before = altitudeAt(dayOfYear, minute / 60);
    const after = altitudeAt(dayOfYear, (minute + 1) / 60);
    if (Number.isNaN(rise) && before < EDGE && after >= EDGE) {
      rise = minute / 60;
    }
    if (!Number.isNaN(rise) && before >= EDGE && after < EDGE) {
      set = minute / 60;
    }
  }
  return { rise, set };
}

describe('太阳位置', () => {
  it('夏至正午高度角对拍天文值（北京 ≈ 73.5°）', () => {
    expect(altitudeAt(SUMMER_SOLSTICE, 12)).toBeCloseTo(73.5, -1);
  });

  it('冬至正午高度角对拍天文值（北京 ≈ 26.6°）', () => {
    expect(altitudeAt(WINTER_SOLSTICE, 12)).toBeCloseTo(26.6, 0);
  });

  it('春秋分正午高度角 ≈ 90° − 纬度（90 − 39.9 = 50.1°）', () => {
    expect(altitudeAt(VERNAL_EQUINOX, 12)).toBeCloseTo(50.1, 0);
  });

  it('夏至日出日落对拍天文值（04:46 / 19:46，允许 5 分钟）', () => {
    const { rise, set } = horizonCrossings(SUMMER_SOLSTICE);

    expect(Math.abs(rise * 60 - (4 * 60 + 46))).toBeLessThan(5);
    expect(Math.abs(set * 60 - (19 * 60 + 46))).toBeLessThan(5);
  });

  it('冬至日出日落对拍天文值（07:32 / 16:53，允许 5 分钟）', () => {
    const { rise, set } = horizonCrossings(WINTER_SOLSTICE);

    expect(Math.abs(rise * 60 - (7 * 60 + 32))).toBeLessThan(5);
    expect(Math.abs(set * 60 - (16 * 60 + 53))).toBeLessThan(5);
  });

  it('夏至白天远比冬至长 —— 这条不成立就说明赤纬方向反了', () => {
    const summer = horizonCrossings(SUMMER_SOLSTICE);
    const winter = horizonCrossings(WINTER_SOLSTICE);

    expect(summer.set - summer.rise).toBeGreaterThan(winter.set - winter.rise + 5);
  });

  it('正午的太阳在南方，早晨在东方，傍晚在西方', () => {
    const noon = toDegrees(solarPosition({ ...BEIJING, dayOfYear: SUMMER_SOLSTICE, localHours: 12 }).azimuth);
    const morning = toDegrees(
      solarPosition({ ...BEIJING, dayOfYear: SUMMER_SOLSTICE, localHours: 6 }).azimuth,
    );
    const evening = toDegrees(
      solarPosition({ ...BEIJING, dayOfYear: SUMMER_SOLSTICE, localHours: 18 }).azimuth,
    );

    // 方位角从正北起、顺时针：东 90°、南 180°、西 270°
    expect(morning).toBeLessThan(90);
    expect(noon).toBeGreaterThan(160);
    expect(noon).toBeLessThan(200);
    expect(evening).toBeGreaterThan(250);
  });

  it('赤道春分的正午接近天顶（≈ 90°）', () => {
    const altitude = toDegrees(
      solarPosition({ latitude: 0, longitude: 0, timezone: 0, dayOfYear: VERNAL_EQUINOX, localHours: 12 })
        .altitude,
    );

    expect(altitude).toBeGreaterThan(87);
    expect(altitude).toBeLessThanOrEqual(90);
  });

  it('高度角与方位角始终落在合法区间内（一天 1440 个采样点）', () => {
    for (let minute = 0; minute < 1440; minute += 1) {
      const position = solarPosition({
        ...BEIJING,
        dayOfYear: WINTER_SOLSTICE,
        localHours: minute / 60,
      });

      expect(position.altitude).toBeGreaterThanOrEqual(-Math.PI / 2);
      expect(position.altitude).toBeLessThanOrEqual(Math.PI / 2);
      expect(position.azimuth).toBeGreaterThanOrEqual(0);
      expect(position.azimuth).toBeLessThan(Math.PI * 2);
    }
  });
});

describe('太阳方向与世界坐标的约定', () => {
  /**
   * 罗盘上 **-Z 是北**、+X 是东（与 `player/motor.ts` 统一）。
   * 这组断言专门钉死符号：一旦反了，症状是"日出的太阳出现在西南"，
   * 而那种错误肉眼很难在第一眼发现。
   */
  it('方位角 90°（东）落在 +X 上', () => {
    const [x, y, z] = sunDirection(0, Math.PI / 2);

    expect(x).toBeCloseTo(1, 6);
    expect(y).toBeCloseTo(0, 6);
    expect(z).toBeCloseTo(0, 6);
  });

  it('方位角 180°（南）落在 +Z 上', () => {
    const [x, , z] = sunDirection(0, Math.PI);

    expect(x).toBeCloseTo(0, 6);
    expect(z).toBeCloseTo(1, 6);
  });

  it('方位角 0°（北）落在 -Z 上', () => {
    const [, , z] = sunDirection(0, 0);

    expect(z).toBeCloseTo(-1, 6);
  });

  it('高度角就是 y 分量：正午的太阳在天顶方向', () => {
    const [, y] = sunDirection(Math.PI / 2, 0);

    expect(y).toBeCloseTo(1, 6);
  });
});

describe('高度角 → 画面参数', () => {
  /** 直接构造一个"高度角已知"的太阳，绕过日期 —— 这一层只该关心高度角 */
  function lookAt(altitudeDegrees: number) {
    const solar: SolarPosition = {
      altitude: toRadians(altitudeDegrees),
      azimuth: 0,
      declination: 0,
      equationOfTime: 0,
      solarHours: 12,
    };
    return environmentLook(solar);
  }

  it('夜里的主光极弱，白昼的强', () => {
    expect(lookAt(-18).keyIntensity).toBeLessThan(0.1);
    expect(lookAt(45).keyIntensity).toBeGreaterThan(2);
    expect(lookAt(-18).night).toBe(true);
    expect(lookAt(10).night).toBe(false);
  });

  it('白昼系数从夜到昼单调上升', () => {
    expect(lookAt(-18).daylight).toBe(0);
    expect(lookAt(45).daylight).toBeCloseTo(1, 5);
    expect(lookAt(0).daylight).toBeGreaterThan(lookAt(-4).daylight);
    expect(lookAt(-4).daylight).toBeGreaterThan(lookAt(-10).daylight);
  });

  it('日落的地平线泛红 —— 暖色占比高于正午（这一点是算出来的，不是调出来的）', () => {
    /** 红蓝比：越大越暖 */
    const warmth = (color: number) => {
      const red = (color >> 16) & 0xff;
      const blue = color & 0xff;
      return red / Math.max(1, blue);
    };

    expect(warmth(lookAt(1).horizonColor)).toBeGreaterThan(warmth(lookAt(45).horizonColor) * 2);
    expect(lookAt(-18).keyColor & 0xff).toBeGreaterThan((lookAt(-18).keyColor >> 16) & 0xff);
  });

  it('关键帧两端不外推：更深的夜与更高的正午都取端点值', () => {
    // 外推会让夜色出现"负亮度"，画面直接变成黑块
    expect(lookAt(-90).keyIntensity).toBe(lookAt(-18).keyIntensity);
    expect(lookAt(-90).horizonColor).toBe(lookAt(-18).horizonColor);
    // 上端同理：插值到 89.99° 必须与 90° 一致（90° 就是端点本身）
    expect(lookAt(89.99).keyIntensity).toBeCloseTo(lookAt(90).keyIntensity, 3);
  });

  it('天穹散射随高度角变化：日落时的瑞利系数高于正午', () => {
    expect(lookAt(-0.833).sky.rayleigh).toBeGreaterThan(lookAt(45).sky.rayleigh);
    expect(lookAt(-0.833).sky.turbidity).toBeGreaterThan(lookAt(45).sky.turbidity);
  });

  it('夜间能见度下降：雾距系数小于白天', () => {
    expect(lookAt(-18).fogFarScale).toBeLessThan(lookAt(45).fogFarScale);
    expect(lookAt(-18).fogNearScale).toBeLessThan(lookAt(45).fogNearScale);
  });

  it('时刻本身不进 look：同一高度角得到同一份画面参数', () => {
    // 反过来说：日出那天的日落与日落那天的日出，画面参数相同 —— 这正是"只用高度角"的含义
    expect(lookAt(10)).toEqual(lookAt(10));
  });
});

describe('环境状态的推进与归一化', () => {
  it('暂停（flow = 0）时原样返回同一个对象 —— 暂停在调用处就是零成本', () => {
    const paused: EnvironmentState = { ...DEFAULT_ENVIRONMENT, flow: 0 };

    expect(advanceEnvironment(paused, 10)).toBe(paused);
  });

  it('flow 是"相对真实时间的倍数"：×3600 时 1 秒推进 1 小时', () => {
    const state: EnvironmentState = { ...DEFAULT_ENVIRONMENT, localHours: 6, flow: 3600 };
    const next = advanceEnvironment(state, 1);

    expect(next.localHours).toBeCloseTo(7, 5);
  });

  it('时间跑过午夜会进到第二天（否则季节会锁死）', () => {
    const state: EnvironmentState = { ...DEFAULT_ENVIRONMENT, localHours: 23.5, flow: 3600 };
    const next = advanceEnvironment(state, 1);

    expect(next.localHours).toBeCloseTo(0.5, 5);
    expect(next.dayOfYear).toBe(DEFAULT_ENVIRONMENT.dayOfYear + 1);
  });

  it('年末回卷到 1 月 1 日', () => {
    const state: EnvironmentState = {
      ...DEFAULT_ENVIRONMENT,
      dayOfYear: 365,
      localHours: 23.9,
      flow: 3600,
    };
    const next = advanceEnvironment(state, 1);

    expect(next.dayOfYear).toBe(1);
    expect(next.localHours).toBeCloseTo(0.9, 5);
  });

  it('归一化把 26.3 小时收成次日 2.3 小时', () => {
    const next = normalizeEnvironment({ ...DEFAULT_ENVIRONMENT, localHours: 26.3 });

    expect(next.localHours).toBeCloseTo(2.3, 5);
    expect(next.dayOfYear).toBe(DEFAULT_ENVIRONMENT.dayOfYear + 1);
  });

  it('归一化是幂等的：已经合法的状态原样返回', () => {
    const state: EnvironmentState = { ...DEFAULT_ENVIRONMENT, localHours: 12 };

    expect(normalizeEnvironment(state)).toBe(state);
  });
});

describe('可复现性', () => {
  it('同一个 state 两次采样逐值一致 —— 演示才能被复现', () => {
    const first = sampleEnvironment(DEFAULT_ENVIRONMENT);
    const second = sampleEnvironment(DEFAULT_ENVIRONMENT);

    expect(second.solar.altitude).toBe(first.solar.altitude);
    expect(second.look.keyColor).toBe(first.look.keyColor);
    expect(second.look.keyDirection).toEqual(first.look.keyDirection);
  });

  it('"推进到某时刻"与"直接给定该时刻"结果完全一致', () => {
    const advanced = advanceEnvironment({ ...DEFAULT_ENVIRONMENT, localHours: 6, flow: 3600 }, 4.5);
    const direct = { ...DEFAULT_ENVIRONMENT, localHours: 10.5, flow: 3600 };

    expect(sampleEnvironment(advanced).solar.altitude).toBeCloseTo(
      sampleEnvironment(direct).solar.altitude,
      10,
    );
  });
});

describe('格式化', () => {
  it('时钟补零到分钟', () => {
    expect(formatClock(0)).toBe('00:00');
    expect(formatClock(14.5)).toBe('14:30');
    expect(formatClock(9.02)).toBe('09:01');
  });

  it('年积日换成月日', () => {
    expect(formatDate(1)).toBe('1月1日');
    expect(formatDate(172)).toBe('6月21日');
    expect(formatDate(365)).toBe('12月31日');
  });
});
