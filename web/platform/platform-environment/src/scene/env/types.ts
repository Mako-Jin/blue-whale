/**
 * 环境（天文与气象）的**数据层**。
 *
 * 这一层刻意全是纯数字：不碰 three、不碰 DOM、不碰渲染。理由是"现实世界的映射"
 * 最怕变成一堆手调魔数 —— 一旦"日落该多亮"写在某个场景文件里，
 * 就再也没法回答"那冬至的日落呢"，也没法为它写一行断言。
 *
 * 所以拆成两半：**这里只回答"物理量是多少"**，
 * `scene/atmosphere/` 只回答"这些物理量画出来是什么样"。判断只有一处。
 */

/**
 * 使用者可控的环境参数。
 *
 * 与 `SceneOptions` 同级但**刻意分开**：选项随场景切换重置（网格开关对丛林没意义），
 * 而时刻与地理位置是**跨场景延续**的 —— 切一圈场景回来，天还是那个天。
 */
export type EnvironmentState = {
  /** 年积日（1 = 1 月 1 日）。它决定太阳赤纬，也就是季节 */
  dayOfYear: number;
  /** 当地钟表时间（小时，可含小数）。0 = 午夜 */
  localHours: number;
  /** 纬度（度，北正） */
  latitude: number;
  /** 经度（度，东正） */
  longitude: number;
  /** 时区（小时，东正） */
  timezone: number;
  /**
   * 时间流逝倍速（相对真实时间的倍数）。**0 = 暂停，且暂停是默认值。**
   *
   * 暂停当默认值是有意的：演示要可复现 —— 画面自己一直走，
   * 回头就找不到刚才那一帧了。这与场景散布用 `createPrng(seed)` 拒绝
   * `Math.random()` 是同一条思路。
   */
  flow: number;
};

/**
 * 默认环境：**北京 · 夏至 · 下午两点半 · 暂停**。
 *
 * 选夏至而不是"随便一天"，是因为这一天的太阳轨迹最饱满：
 * 高度角能爬到 73.5°，日落也最晚，滑块从 00:00 拖到 24:00 能看到最完整的一段。
 */
export const DEFAULT_ENVIRONMENT: EnvironmentState = {
  dayOfYear: 172,
  localHours: 14.5,
  latitude: 39.9042,
  longitude: 116.4074,
  timezone: 8,
  flow: 0,
};

/** 时间流逝的档位（倍速）。0 = 暂停。 */
export const FLOW_PRESETS: readonly { label: string; value: number }[] = [
  { label: '暂停', value: 0 },
  { label: '×600', value: 600 },
  { label: '×3600', value: 3600 },
];
