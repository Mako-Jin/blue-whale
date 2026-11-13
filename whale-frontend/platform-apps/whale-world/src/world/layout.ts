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
export type NodeType = 'REGION' | 'BUILDING' | 'ENTRANCE';

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
}

const buildings = CITY_LAYOUT.buildings;

/** 有入口绑定且有传送门的建筑即「宫殿」（主传送门单列，不参与环状排布） */
const palaceNodes = buildings.filter((b) => b.entry && b.portal && !b.portal.main);

/** 皇城内的建筑（父节点为 royal-city） */
const royalHalls = buildings.filter(
  (b) => b.nodeType === 'BUILDING' && b.parentKey === 'royal-city' && !b.portal,
);

/** 八方宫殿（对应 8 个业务子应用），顺序即传送门数字键 2–9 */
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
  portal: {
    x: b.portal?.x ?? b.x,
    z: b.portal?.z ?? b.z,
    color: hexColor(b.portal?.color, 0x6fe3ff),
  },
}));

/** 皇城配殿（典籍阁 / 玉衡阁） */
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

/** 主殿与配殿的碰撞盒（取建筑底面 0.9 倍，与手感调校一致） */
export const HALL_COLLIDERS: ColliderSpec[] = [MAIN_HALL, ...SIDE_HALLS].map((hall) => ({
  x: hall.x,
  z: hall.z,
  hx: hall.w * 0.9,
  hz: hall.d * 0.9,
}));

/** 八方宫殿的碰撞盒（库里 sc_node_world.collider_half_* 同源） */
export const PALACE_COLLIDERS: ColliderSpec[] = PALACES.map((pal) => ({
  x: pal.x,
  z: pal.z,
  hx: 10,
  hz: 9,
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
