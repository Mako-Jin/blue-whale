/**
 * 城门楼地标（S5 / T5.10）—— 网关在城里的化身。
 *
 * <p>皇城九殿各有一块屏风，能进殿看数；城门楼不一样：它是**通道**，没有内页。
 * 总纲给的定位就是"仅地标"，而这个定位不是偷懒——网关既没有业务表，也没有
 * {@code /<服务>/overview} 契约（它是无状态代理，只有路由/鉴权/限流）。
 * 要在门楼上挂实时数字，就得让网关自己开一个业务端点并进白名单；
 * 而白名单与兜底路由每改一次都要重建镜像、重启入口——为了在地标上多一个数字，
 * 去动全城唯一入口的暴露面，不划算。所以这里**只做"被认出"，不造数据**：</p>
 *
 * <ul>
 *   <li><b>场景</b>：走到门洞前，提示条写清"通道，不是殿"——否则玩家会以为漏了一座殿；</li>
 *   <li><b>按 E</b>：仍然"过门"（开传送门列表，这是进城/出城的动作），
 *       但补一句解释，让"门洞里弹出一张殿宇清单"读起来是过门而不是进殿；</li>
 *   <li><b>城图</b>：四门留缺口、标门楼，让"城有四门"在俯视图上也读得出来。</li>
 * </ul>
 *
 * <p><b>坐标不新增来源</b>：门楼位置就是 {@code CITY_GEOMETRY} 的城墙半径与
 * {@code gateHalfWidth} 的交点，全部现算。{@code minimap.ts} 曾经把外城写死成
 * 92（v1 旧值），扩城到 110 之后整圈城墙跑到画布外——同一套数字写两处，
 * 迟早有一处不跟着改。</p>
 *
 * <p>没有登记为 {@code sc_scene_node} 节点：它不是可进入的建筑，2D 城图里它是
 * 城墙的一段；给它一个节点会让布局校验（{@code check-city-layout.mjs}）与
 * Flyway 迁移都要跟着加一版，而玩家感知不到区别。</p>
 *
 * <p>内城的两座门楼（{@code 0, ±innerWall}）只作图与提示，不进"四门"之名：
 * 它们没有匾额，也不是 {@code gateway} 的化身——按 {@code city.ts} 的建法，
 * 内城门楼的 {@code name} 传 {@code null}，本来就不挂牌。</p>
 */

import { CITY_GEOMETRY, GATE_HALF } from './layout';

/** 地标所属的城圈 */
export type GateTier = 'outer' | 'inner';

/** 一座城门楼 */
export interface GateTowerSpec {
  /** 与场景一致的稳定 key（e2e 与探针用它，不从名字反推） */
  nodeKey: string;
  /** 门名（外城四门有匾额；内城门楼无名，按方位取名只供提示与城图） */
  name: string;
  /** 提示条副标：一句话说清"这里是什么" */
  short: string;
  /** 按 E 过门时的说明（只在有匾额的四门给，因为只有它们是"城门楼"） */
  duty: string;
  tier: GateTier;
  /** 门楼中心（城墙线上） */
  x: number;
  z: number;
  /** 门外朝向（度，0 = 门外朝 +z，与 city.ts 建门楼的 yaw 同口径） */
  yawDeg: number;
  /** 门洞半宽（城图留缺口、碰撞分段共用同一组数字） */
  gateHalf: number;
  /** 是否有匾额：只有四座外城门楼算"地标"，内城门洞只留缺口 */
  landmark: boolean;
}

/** 四座外城门楼的职责说明（同一句话，别在六处各写一遍再慢慢走样） */
const GATE_DUTY = '通道，不是殿：全城的请求都从这道门入城——鉴权、限流、路由都在这儿。';

/** 外城门楼（南 +z、北 -z、东 +x、西 -x，与 city.ts 的 buildGateTower 调用一一对应） */
const OUTER_TIERS: Array<Omit<GateTowerSpec, 'tier' | 'gateHalf' | 'landmark' | 'x' | 'z'>> = [
  { nodeKey: 'gate-nanxun', name: '南薰門', short: '正南 · 御道入口', duty: GATE_DUTY, yawDeg: 0 },
  { nodeKey: 'gate-wangjiang', name: '望江門', short: '正北 · 临江', duty: GATE_DUTY, yawDeg: 180 },
  { nodeKey: 'gate-chaoyang', name: '朝陽門', short: '正东 · 日出', duty: GATE_DUTY, yawDeg: 90 },
  { nodeKey: 'gate-jingjin', name: '鯨津門', short: '正西 · 津渡', duty: GATE_DUTY, yawDeg: -90 },
];

/**
 * 城门楼地标表（座标全部现算）。
 *
 * <p>顺序固定为 南 / 北 / 东 / 西（外城），便于 e2e 与城图按同一顺序读。</p>
 */
export const GATE_TOWERS: GateTowerSpec[] = [
  ...OUTER_TIERS.map((gate) => {
    // yaw 0 → 门外朝 +z（南）；90 → 门外朝 +x（东）。方向与 city.ts 一致
    const rad = (gate.yawDeg * Math.PI) / 180;
    return {
      ...gate,
      tier: 'outer' as const,
      x: Number((Math.sin(rad) * CITY_GEOMETRY.outerWall).toFixed(3)),
      z: Number((Math.cos(rad) * CITY_GEOMETRY.outerWall).toFixed(3)),
      gateHalf: GATE_HALF.outer,
      landmark: true,
    };
  }),
  {
    nodeKey: 'gate-inner-south',
    name: '内城南门',
    short: '内城 · 南',
    duty: '',
    tier: 'inner',
    x: 0,
    z: CITY_GEOMETRY.innerWall,
    yawDeg: 0,
    gateHalf: GATE_HALF.inner,
    landmark: false,
  },
  {
    nodeKey: 'gate-inner-north',
    name: '内城北门',
    short: '内城 · 北',
    duty: '',
    tier: 'inner',
    x: 0,
    z: -CITY_GEOMETRY.innerWall,
    yawDeg: 180,
    gateHalf: GATE_HALF.inner,
    landmark: false,
  },
];

/** 有匾额的四门（"城门楼"作为地标只指这四座） */
export const LANDMARK_GATES: GateTowerSpec[] = GATE_TOWERS.filter((gate) => gate.landmark);

/**
 * 提示触发半径（米）。
 *
 * <p>取 11：门楼台基底边外扩约 4.5m，站在门洞里（距门楼中心 0–6m）与刚走到
 * 门洞外（8–10m）都要能读到；再远就与传送门提示（7m）重叠得不讲道理了。
 * 出生点 (0, 66) 距内城南门 8m 落在圈内——那里本来就站在内城门口，
 * 提示"按 E 过门"是对的。</p>
 */
export const GATE_NOTICE_RADIUS = 11;

/** 最近城门楼的查询结果 */
export interface NearestGate {
  spec: GateTowerSpec;
  distance: number;
}

/**
 * 找最近的**地标**门楼（只有四座有匾额的外城门楼参与）。
 *
 * <p>内城门洞不参与：它们没有匾额、也不是网关的化身，走过去弹一句
 * "内城南门 · 通道"只会让"城门楼 = 网关"这件事变模糊。</p>
 *
 * @param x     玩家 x
 * @param z     玩家 z
 * @param radius 触发半径；不传则用 {@link GATE_NOTICE_RADIUS}
 * @returns 半径内最近的一座；没有则 {@code null}
 */
export function nearestGate(x: number, z: number, radius = GATE_NOTICE_RADIUS): NearestGate | null {
  let best: NearestGate | null = null;
  for (const spec of LANDMARK_GATES) {
    const distance = Math.hypot(x - spec.x, z - spec.z);
    if (distance > radius) {
      continue;
    }
    if (!best || distance < best.distance) {
      best = { spec, distance };
    }
  }
  return best;
}

/**
 * 提示条文案（`<b>` 与 `<span>` 由调用方拼，这里只给两段纯文本）。
 *
 * <p>刻意**不写"按 E 进入"**：门楼没有内页，写"进入"就是在骗人。
 * 写"按 E 过门"——按下去是传送门列表，也就是真的过门。</p>
 */
export function gateHintText(spec: GateTowerSpec): { lead: string; name: string } {
  return { lead: '按 <b>E</b> 过门', name: `${spec.name} · 通道非殿` };
}

/**
 * 内城与皇城墙上的门洞（城图留缺口用）。
 *
 * <p>皇城四门（{@code ±royalWall}）没有门楼、也不是地标，但城图上不开口
 * 会让"御道穿城而过"看起来像撞墙，所以一并留缺口。</p>
 */
export const WALL_OPENINGS: Array<{ x: number; z: number; along: 'x' | 'z'; half: number }> = [
  ...GATE_TOWERS.map((gate) => ({
    x: gate.x,
    z: gate.z,
    // 门楼在 z 方向那面墙上时，缺口沿 x 展开，反之亦然
    along: (gate.x === 0 ? 'x' : 'z') as 'x' | 'z',
    half: gate.gateHalf,
  })),
  ...[
    { x: 0, z: CITY_GEOMETRY.royalWall },
    { x: 0, z: -CITY_GEOMETRY.royalWall },
    { x: CITY_GEOMETRY.royalWall, z: 0 },
    { x: -CITY_GEOMETRY.royalWall, z: 0 },
  ].map((gate) => ({
    x: gate.x,
    z: gate.z,
    along: (gate.x === 0 ? 'x' : 'z') as 'x' | 'z',
    half: GATE_HALF.royal,
  })),
];
