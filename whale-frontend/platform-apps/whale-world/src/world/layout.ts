/**
 * 蓝鲸城三维布局（数据驱动）。
 *
 * <p>布局的唯一数据源是同目录的 {@code city-layout.json}：三维场景、后端 V4 迁移脚本
 * （{@code scripts/gen-world-layout-sql.py} 生成）都从它派生，避免「前端坐标」与
 * 「库里坐标」两套数字各改各的——那正是本项目历史上最常见的静默失效来源。</p>
 *
 * <p>单位约定：世界单位近似米，x 向东、z 向南、y 向上；旋转角为绕 Y 轴角度（度），
 * 0 表示朝北（-z），与 three.js 的 {@code rotation.y} 一致（弧度 = 度 × π / 180）。</p>
 */

import raw from './city-layout.json';

/** 节点类型（与 sc_scene_node.node_type 同构） */
export type NodeType = 'REGION' | 'BUILDING' | 'ENTRANCE' | 'DISTRICT';

/** 二维入口绑定（与 sc_node_binding 同构） */
export interface EntryLayout {
  type: 'SYSTEM' | 'PAGE' | 'KNOWLEDGE' | 'EXTERNAL';
  path: string;
  permission: string | null;
}

/** 传送门（与 sc_node_world.portal_* 同构） */
export interface PortalLayout {
  x: number;
  z: number;
  color: string;
  label: string;
  subLabel: string;
  main?: boolean;
}

/** 单栋建筑 / 区域（与 sc_scene_node + sc_node_world 合并视图同构） */
export interface BuildingLayout {
  nodeKey: string;
  parentKey: string | null;
  nodeType: NodeType;
  name: string;
  sub?: string;
  icon?: string;
  x: number;
  z: number;
  y?: number;
  w?: number;
  d?: number;
  scale?: number;
  rotationDeg?: number;
  roofColor?: string;
  bodyColor?: string;
  modelKey: string;
  collider?: { halfX: number; halfZ: number };
  sortNo: number;
  entry?: EntryLayout;
  portal?: PortalLayout;
}

/** 布局根结构 */
export interface CityLayoutData {
  scene: { key: string; name: string; description: string; version: number };
  scale: { planePerUnit: number; planeOrigin: number };
  geometry: {
    outerWall: number;
    innerWall: number;
    royalWall: number;
    ringRoadRadius: number;
    gateHalfWidth: { outer: number; inner: number; royal: number };
    wallThickness: { outer: number; inner: number; royal: number };
  };
  buildings: BuildingLayout[];
}

/** 布局原始数据（只读，勿就地修改） */
export const CITY_LAYOUT = raw as unknown as CityLayoutData;

/** 城郭尺度：外城 / 内城 / 皇城 三层城墙半径与环城路半径 */
export const CITY_GEOMETRY = CITY_LAYOUT.geometry;

/** 门洞半宽（城墙碰撞按此分段留门，避免整面墙把门堵死） */
export const GATE_HALF = CITY_GEOMETRY.gateHalfWidth;

/** {@code #rrggbb} → three.js 需要的十进制颜色 */
export function hexColor(value: string | undefined, fallback = 0xffffff): number {
  if (!value) {
    return fallback;
  }
  return Number.parseInt(value.replace('#', ''), 16);
}

const toRad = (deg: number) => (deg * Math.PI) / 180;

/** 三维宫殿（含对应业务子应用路由） */
export interface PalaceSpec {
  nodeKey: string;
  name: string;
  sub: string;
  route: string;
  permission: string | null;
  x: number;
  z: number;
  yaw: number;
  w: number;
  d: number;
  scale: number;
  roof: number;
  body: number;
  /** 碰撞半长（来自 city-layout.json，缺省 10×9 沿用旧手感） */
  collider: { halfX: number; halfZ: number };
  portal: { x: number; z: number; color: number };
}

/** 皇城建筑（主殿与配殿） */
export interface HallSpec {
  nodeKey: string;
  name: string;
  x: number;
  z: number;
  w: number;
  d: number;
  scale: number;
  yaw: number;
  roof: number;
  body: number;
  /** 碰撞半长；缺省时按台基外扩量估算 */
  collider: { halfX: number; halfZ: number };
}

const buildings = CITY_LAYOUT.buildings;

/** 有入口绑定且有传送门的建筑即「宫殿」（主传送门单列，不参与环状排布） */
const palaceNodes = buildings.filter((b) => b.entry && b.portal && !b.portal.main);

/** 皇城内的建筑（父节点为 royal-city） */
const royalHalls = buildings.filter(
  (b) => b.nodeType === 'BUILDING' && b.parentKey === 'royal-city' && !b.portal,
);

/**
 * 内城十二坊（每坊对应一个业务子系统），顺序即绕城顺时针一圈。
 *
 * <p>v2 从 8 坊扩到 12 坊（每象限 3 坊），四个正方位空出来给御道。顺序同时决定
 * 导览路线与传送门列表的展示次序，改动前先看 {@code docs/plans/2026-11-19-3d-capital-master-plan.md}。</p>
 */
export const PALACES: PalaceSpec[] = palaceNodes.map((b) => ({
  nodeKey: b.nodeKey,
  name: b.name,
  sub: b.sub ?? '',
  route: b.entry?.path ?? '/',
  permission: b.entry?.permission ?? null,
  x: b.x,
  z: b.z,
  yaw: toRad(b.rotationDeg ?? 0),
  w: b.w ?? 9,
  d: b.d ?? 7,
  scale: b.scale ?? 0.95,
  roof: hexColor(b.roofColor, 0x2f6b5f),
  body: hexColor(b.bodyColor, 0x8f3a2c),
  collider: {
    halfX: b.collider?.halfX ?? 10,
    halfZ: b.collider?.halfZ ?? 9,
  },
  portal: {
    x: b.portal?.x ?? b.x,
    z: b.portal?.z ?? b.z,
    color: hexColor(b.portal?.color, 0x6fe3ff),
  },
}));

/**
 * 坊域尺寸（局部坐标：{@code halfW} 沿街巷切向、{@code halfD} 沿径向）。
 *
 * <p>12 坊在 r=76 的环路上均分，相邻中心弦长约 39.3m，坊台基 18m 宽 × 16m 深。
 * 取 13×11 的半长，相邻坊之间留约 3m 巷道；径向内缘 76−11=65，仍在内城墙 58 之外。</p>
 */
export const DISTRICT_FOOTPRINT = { halfW: 13, halfD: 11 };

/**
 * 坊墙几何参数（渲染与碰撞共用，避免「墙画在 A 处、碰撞在 B 处」）。
 *
 * <p>{@code gateHalf} 是坊门洞的半宽；坊门开在朝圆心那一面（局部 +z），
 * 四个正方位留给御道，坊不四面开口。</p>
 */
export const DISTRICT_WALL = { height: 3.0, thick: 0.6, gateHalf: 4.5 };

/** 坊（街区）定义：供坊墙、坊门、匾额与坊内落位使用 */
export interface DistrictSpec {
  nodeKey: string;
  name: string;
  sub: string;
  x: number;
  z: number;
  yaw: number;
  halfW: number;
  halfD: number;
  portal: { x: number; z: number; color: number };
}

/**
 * 内城十二坊（带坊域范围；**外城诸司不生成坊墙**）。
 *
 * <p><b>为什么外城不算"坊"。</b>坊墙是内城的组织方式：十二坊挤在 r=76 的环路上，
 * 相邻中心弦长 39.3 米、坊台基 18 米宽，只有围出"墙—门—墙"才有街区感。
 * 外城诸司（S7 九处）在 r≈94 的外城带，本来就散在景观里，再各围一圈墙会把
 * 内外城之间的通行带切成互不连通的小块——实测外城带被这些墙切成若干封闭区，
 * 自动寻路从城内到乐坊/影戏院一律报"过不去那道墙"，玩家只能靠运气绕。
 * 所以：坊墙只为 {@code parentKey === 'inner-city'} 的建筑生成，外城建筑只留
 * 自己的台基与建筑碰撞。</p>
 */
export const DISTRICTS: DistrictSpec[] = PALACES.filter((p) => {
  const node = buildings.find((b) => b.nodeKey === p.nodeKey);
  return node?.parentKey === 'inner-city';
}).map((p) => ({
  nodeKey: p.nodeKey,
  name: p.name,
  sub: p.sub,
  x: p.x,
  z: p.z,
  yaw: p.yaw,
  halfW: DISTRICT_FOOTPRINT.halfW,
  halfD: DISTRICT_FOOTPRINT.halfD,
  portal: p.portal,
}));

/**
 * 皇城内不带传送门的建筑：8 座中枢配殿（枢密 / 度支 / 藏书 / 时辰 / 政事 / 考功 /
 * 通政 / 起居）与南广场两角的典籍阁、玉衡阁。
 *
 * <p>v2 里 8 中枢殿东西两列各 4 座，典籍阁与玉衡阁缩成小阁退到 (±10.5, 20)，
 * 给中枢殿让位。它们都没有 2D 入口——S5 接入时才补 entry/portal。</p>
 */
export const SIDE_HALLS: HallSpec[] = royalHalls.map((b) => ({
  nodeKey: b.nodeKey,
  name: b.name,
  x: b.x,
  z: b.z,
  w: b.w ?? 6,
  d: b.d ?? 5,
  scale: b.scale ?? 0.72,
  yaw: toRad(b.rotationDeg ?? 0),
  roof: hexColor(b.roofColor, 0x3c6b5a),
  body: hexColor(b.bodyColor, 0x8a4a30),
  collider: {
    halfX: b.collider?.halfX ?? (((b.w ?? 6) + 9) / 2) * (b.scale ?? 0.72),
    halfZ: b.collider?.halfZ ?? (((b.d ?? 5) + 9) / 2) * (b.scale ?? 0.72),
  },
}));

const mainNode = buildings.find((b) => b.portal?.main);
if (!mainNode) {
  throw new Error('city-layout.json 缺少主传送门（portal.main）定义');
}

/** 皇城主殿（数枢殿，核心数据入口） */
export const MAIN_HALL: HallSpec = {
  nodeKey: mainNode.nodeKey,
  name: mainNode.name,
  x: mainNode.x,
  z: mainNode.z,
  w: mainNode.w ?? 17,
  d: mainNode.d ?? 12,
  scale: mainNode.scale ?? 1.22,
  yaw: toRad(mainNode.rotationDeg ?? 0),
  roof: hexColor(mainNode.roofColor, 0xc9a13a),
  body: hexColor(mainNode.bodyColor, 0x8f2f24),
  collider: {
    halfX: mainNode.collider?.halfX ?? (((mainNode.w ?? 14) + 9) / 2) * (mainNode.scale ?? 1),
    halfZ: mainNode.collider?.halfZ ?? (((mainNode.d ?? 11) + 9) / 2) * (mainNode.scale ?? 1),
  },
};

/** 主传送门（皇城内，正对御道） */
export const MAIN_PORTAL = {
  nodeKey: mainNode.nodeKey,
  name: mainNode.portal?.label ?? mainNode.name,
  sub: mainNode.portal?.subLabel ?? '',
  x: mainNode.portal?.x ?? 0,
  z: mainNode.portal?.z ?? 30,
  route: mainNode.entry?.path ?? '/portal',
  color: hexColor(mainNode.portal?.color, 0xffd166),
};

/** 建筑碰撞盒的参数化定义（实际几何在 collision.ts 生成） */
export interface ColliderSpec {
  x: number;
  z: number;
  hx: number;
  hz: number;
}

/** 主殿与配殿的碰撞盒（尺寸来自 city-layout.json 的 collider，缺省按台基外扩量兜底） */
export const HALL_COLLIDERS: ColliderSpec[] = [MAIN_HALL, ...SIDE_HALLS].map((hall) => ({
  x: hall.x,
  z: hall.z,
  hx: hall.collider.halfX,
  hz: hall.collider.halfZ,
}));

/** 十二坊的碰撞盒（与 sc_node_world.collider_half_* 同源） */
export const PALACE_COLLIDERS: ColliderSpec[] = PALACES.map((pal) => ({
  x: pal.x,
  z: pal.z,
  hx: pal.collider.halfX,
  hz: pal.collider.halfZ,
}));

/** 三层城墙的几何参数（半边长 / 墙厚 / 门洞半宽） */
export const WALL_RINGS = [
  {
    half: CITY_GEOMETRY.outerWall,
    thick: CITY_GEOMETRY.wallThickness.outer,
    gate: GATE_HALF.outer,
  },
  {
    half: CITY_GEOMETRY.innerWall,
    thick: CITY_GEOMETRY.wallThickness.inner,
    gate: GATE_HALF.inner,
  },
  {
    half: CITY_GEOMETRY.royalWall,
    thick: CITY_GEOMETRY.wallThickness.royal,
    gate: GATE_HALF.royal,
  },
] as const;
