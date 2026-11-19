import * as THREE from 'three';
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js';
import type { HouseVariant, SceneAssets, TemplateParts, TreeVariant } from './loader';
import { heightAt, riverCenter, slopeAt, WATER_LEVEL } from './terrain';
import { attachPlaqueSigns } from './plaque';
import {
  applyBrickMap,
  applyPlasterMap,
  applyStoneMap,
  BRICK_METERS,
  isPlaster,
  PLASTER_METERS,
} from './textures';
import { PALETTE, clamp } from './util';

/**
 * 聚落（S0 占位版）。
 *
 * <p>这一版全部是**代码搭的简模**，用来先立住"体量关系与构图"，S3 会逐件换成 GLB。
 * 之所以还是认真做，是因为 S3 换件时只替换几何、位置与尺度关系不动——体量错了，
 * 后面再好的模型也摆不出这张图。</p>
 *
 * <p>硬约定：所有部件在构建时<b>底面贴 y=0</b>，放置时直接
 * {@code position.y = heightAt(x, z)}。不要靠"原点大致对"来落地——那是文章里
 * "木箱看着贴地、实际悬空"的成因。</p>
 */

type MatKey =
  | 'stone'
  | 'stoneDark'
  | 'brick'
  | 'wood'
  | 'woodDark'
  | 'tile'
  | 'plaster'
  | 'red'
  | 'gold'
  | 'glow'
  | 'window'
  | 'foliage'
  | 'shrub'
  | 'crop'
  | 'field'
  | 'path'
  | 'wet'
  | 'water';

const MAT_DEF: Record<MatKey, THREE.MeshStandardMaterialParameters> = {
  stone: { color: PALETTE.stone, roughness: 0.92, metalness: 0.02 },
  stoneDark: { color: PALETTE.stoneDark, roughness: 0.95 },
  // 与 wall.py 的 wallBrick 同色：敌台是城墙的端头，两者必须像同一次砌上去的
  brick: { color: 0x6b6659, roughness: 0.95 },
  wood: { color: PALETTE.wood, roughness: 0.88 },
  woodDark: { color: PALETTE.woodDark, roughness: 0.9 },
  tile: { color: PALETTE.tile, roughness: 0.62 },
  plaster: { color: 0xd9d2c2, roughness: 0.96 },
  red: { color: 0x8f3a2c, roughness: 0.84 },
  gold: { color: PALETTE.lantern, roughness: 0.38, metalness: 0.62 },
  glow: { color: 0xffb070, emissive: 0xff6a2a, emissiveIntensity: 2.2, roughness: 0.5 },
  window: { color: 0xffcf94, emissive: 0xffa050, emissiveIntensity: 1.5, roughness: 0.6 },
  foliage: { color: 0x3d4a2a, roughness: 0.95 },
  shrub: { color: 0x566a38, roughness: 0.94 },
  crop: { color: 0x5d6a32, roughness: 0.95 },
  field: { color: 0x4f5c2d, roughness: 1 },
  path: { color: 0x7d7059, roughness: 0.98 },
  // 沟底常年积水积泥：暗、湿、有点反光。粗糙度是这里唯一的"湿"信号
  wet: { color: 0x2f3532, roughness: 0.22, metalness: 0.04 },
  water: { color: 0x2c3a38, roughness: 0.12, metalness: 0.3 },
};

/**
 * 逐构件色差幅度。
 *
 * <p>程序化拼出来的街面为什么"假"？不是缺细节，是<b>每一块石板、每一段墙都一模一样</b>：
 * 现实里石料是分批采的，同一堵墙上相邻两块的颜色差 5% 就足够让眼睛读出"这是一块块
 * 石头"。这一层用顶点色（每个构件一个均匀色）实现，<b>不增加任何 drawcall</b>，
 * 也不需要贴图。</p>
 *
 * <p>幅度按材料给：石头最杂（0.12）、木构次之、白墙最收敛（0.05，再大就成脏墙）。</p>
 */
const VARY: Partial<Record<MatKey, number>> = {
  stone: 0.11,
  stoneDark: 0.13,
  wood: 0.06,
  woodDark: 0.07,
  plaster: 0.045,
  tile: 0.05,
  path: 0.09,
  field: 0.05,
  crop: 0.09,
  foliage: 0.07,
  shrub: 0.1,
};

/** 一个构件的均匀色偏写进顶点色（合并后所有构件共用一套材质） */
function paintVariation(g: THREE.BufferGeometry, key: MatKey, dark = 1): void {
  const amp = VARY[key] ?? 0;
  const count = g.attributes.position.count;
  const colors = new Float32Array(count * 3);
  let r = 1;
  let gg = 1;
  let b = 1;
  if (amp > 0) {
    g.computeBoundingBox();
    const c = (g.boundingBox as THREE.Box3).getCenter(new THREE.Vector3());
    // 位置哈希：同一位置跑两遍得到同一个色，重建场景不会"跳色"
    const h = Math.sin(c.x * 12.9898 + c.z * 78.233 + c.y * 37.719) * 43758.5453;
    const t = h - Math.floor(h);
    const v = 1 + (t - 0.5) * 2 * amp;
    // 带一点冷暖偏移：纯明度差看着像"脏"，冷/暖差才像不同批次的石料
    const warm = (t - 0.5) * amp * 0.6;
    r = Math.max(0.05, v + warm);
    gg = Math.max(0.05, v + warm * 0.2);
    b = Math.max(0.05, v - warm * 0.5);
  }
  for (let i = 0; i < count; i += 1) {
    colors[i * 3] = r * dark;
    colors[i * 3 + 1] = gg * dark;
    colors[i * 3 + 2] = b * dark;
  }
  g.setAttribute('color', new THREE.BufferAttribute(colors, 3));
}

/**
 * 按米重算盒式投影 UV。
 *
 * <p>`BoxGeometry` 自带的 UV 是"每个面各占 0..1"，同一张白墙贴图铺到巷子的
 * 0.32×2.7m 山墙面上，雨痕被压成一片<b>细密条纹布</b>（出图一眼假），铺到 7m
 * 的正墙上又放大成色块。所以所有 plaster 几何在入组前统一按世界坐标投影：
 * 一个 UV 单位 = 3.2m，与 `house.py` 的 `box_uv(scale=3.2)` 同一把尺。</p>
 *
 * <p>石材同理：一块 3.4m 的石板用默认 UV 会把 512 的图铺满整块板，颗粒被放大到
 * 20cm，看起来是"塑料板上的花纹"。建筑也用同一把尺——顺手省一张常量。</p>
 */
function boxUvWorld(g: THREE.BufferGeometry, scale: number): void {
  const pos = g.attributes.position as THREE.BufferAttribute;
  const nor = g.attributes.normal as THREE.BufferAttribute | undefined;
  const uv = g.attributes.uv as THREE.BufferAttribute | undefined;
  if (!nor || !uv) return;
  for (let i = 0; i < pos.count; i += 1) {
    const ax = Math.abs(nor.getX(i));
    const ay = Math.abs(nor.getY(i));
    const az = Math.abs(nor.getZ(i));
    const x = pos.getX(i);
    const y = pos.getY(i);
    const z = pos.getZ(i);
    // 主轴决定用哪两个世界轴做平面：朝上/下的面用 XZ，其余用"水平轴 + 竖向 Y"。
    // v 必须是 Y（世界向上），否则雨痕会变成横纹。
    if (ax >= ay && ax >= az) uv.setXY(i, y / scale, z / scale);
    else if (ay >= az) uv.setXY(i, x / scale, z / scale);
    else uv.setXY(i, x / scale, y / scale);
  }
  uv.needsUpdate = true;
}

/** 把同材质的部件合并成一个几何：3200 行 city.ts 那种"一物一 mesh"的写法，drawcall 会失控 */
class Builder {
  private readonly parts = new Map<MatKey, THREE.BufferGeometry[]>();
  private darkFactor = 1;

  /**
   * 接下来这批构件压暗多少（1 = 不压）。
   *
   * <p>用来做"假环境光遮蔽"：巷子里的地面和照壁被两侧山墙挡掉大半天光，而我们的
   * IBL 是一张无遮挡的天空贴图——不压的话，2.8m 宽的巷子亮得像街上，纵深全没了。</p>
   */
  darken(k: number): this {
    this.darkFactor = k;
    return this;
  }

  add(key: MatKey, geo: THREE.BufferGeometry, matrix?: THREE.Matrix4): void {
    const g = geo.clone();
    if (matrix) g.applyMatrix4(matrix);
    // mergeGeometries 要求"要么都有 index，要么都没有"。IcosahedronGeometry（树冠）
    // 是非索引的，Box/Cylinder/Lathe 是索引的——不补齐的话整组合并失败，
    // 表现为"某一类构件整体消失"，且只在 console 里留一行警告。
    if (!g.index) {
      const count = g.attributes.position.count;
      const idx = new Uint32Array(count);
      for (let i = 0; i < count; i += 1) idx[i] = i;
      g.setIndex(new THREE.BufferAttribute(idx, 1));
    }
    paintVariation(g, key, this.darkFactor);
    if (key === 'plaster' || key === 'stone' || key === 'stoneDark') boxUvWorld(g, PLASTER_METERS);
    const list = this.parts.get(key) ?? [];
    list.push(g);
    this.parts.set(key, list);
  }

  /** 轴对齐盒：以 (x, y, z) 为中心 */
  box(
    key: MatKey,
    w: number,
    h: number,
    d: number,
    x: number,
    y: number,
    z: number,
    rot?: { x?: number; y?: number; z?: number },
  ): void {
    const m = new THREE.Matrix4().compose(
      new THREE.Vector3(x, y, z),
      new THREE.Quaternion().setFromEuler(new THREE.Euler(rot?.x ?? 0, rot?.y ?? 0, rot?.z ?? 0)),
      new THREE.Vector3(1, 1, 1),
    );
    this.add(key, new THREE.BoxGeometry(w, h, d), m);
  }

  cyl(
    key: MatKey,
    rTop: number,
    rBot: number,
    h: number,
    x: number,
    y: number,
    z: number,
    seg = 8,
    rot?: { x?: number; y?: number; z?: number },
  ): void {
    const geo = new THREE.CylinderGeometry(rTop, rBot, h, seg);
    const m = new THREE.Matrix4().compose(
      new THREE.Vector3(x, y, z),
      new THREE.Quaternion().setFromEuler(new THREE.Euler(rot?.x ?? 0, rot?.y ?? 0, rot?.z ?? 0)),
      new THREE.Vector3(1, 1, 1),
    );
    this.add(key, geo, m);
  }

  build(): THREE.Group {
    const group = new THREE.Group();
    group.name = 'settlement';
    for (const [key, list] of this.parts) {
      if (list.length === 0) continue;
      const merged = mergeGeometries(list, false);
      for (const g of list) g.dispose();
      if (!merged) continue;
      merged.computeVertexNormals();
      const mesh = new THREE.Mesh(
        merged,
        new THREE.MeshStandardMaterial({ ...MAT_DEF[key], vertexColors: true }),
      );
      // 院墙/巷门这些程序化白墙和 GLB 民居必须是同一张底图，否则街上的两种白
      // 一个"有灰"一个"发亮"，转角处一眼能看出是两套资产
      if (key === 'plaster') applyPlasterMap(mesh.material as THREE.MeshStandardMaterial);
      if (key === 'stone' || key === 'stoneDark') applyStoneMap(mesh.material);
      if (key === 'brick') {
        applyBrickMap(mesh.material as THREE.MeshStandardMaterial);
        // 与 GLB 城墙同一把尺：贴图按米投影，否则敌台上的砖会与墙上的砖不是一个尺寸
        boxUvLocal(merged, BRICK_METERS);
      }
      mesh.castShadow = true;
      mesh.receiveShadow = true;
      mesh.name = `settlement-${key}`;
      group.add(mesh);
    }
    return group;
  }
}

/* ------------------------------------------------------------------ 桥 */

const BRIDGE_HALF = 14;
const BRIDGE_DECK_TOP = 4.6;
const BRIDGE_END_Y = 1.5;
const BRIDGE_WIDTH = 4.6;
const BRIDGE_Z = 2;

/** 桥面高度：抛物线，两端落在广场平面 */
function deckY(x: number): number {
  const t = clamp(x / BRIDGE_HALF, -1, 1);
  return BRIDGE_END_Y + (BRIDGE_DECK_TOP - BRIDGE_END_Y) * (1 - t * t);
}

function buildBridge(b: Builder): void {
  /*
   * 石拱桥必须是一个**连续实体**：以前用"桥面板 + 两根圆环当拱 + 方墩"拼，
   * 三者在空间里互不相连，出图就是一条悬空的薄带加一个水里的方盒（实测很难看）。
   *
   * 现在改成：把桥的侧立面画成一条带两个半圆洞的轮廓，直接挤出成体。
   * 一次成形，桥面、拱券、桥台、河床以下是同一块石头，不存在"接不上"。
   */
  const deckBottom = (x: number) => deckY(x) - 0.62;
  const riverBedY = -2.4;

  const shape = new THREE.Shape();
  shape.moveTo(-BRIDGE_HALF, riverBedY);
  const N = 48;
  for (let i = 0; i <= N; i += 1) {
    const x = -BRIDGE_HALF + (BRIDGE_HALF * 2 * i) / N;
    shape.lineTo(x, deckBottom(x));
  }
  shape.lineTo(BRIDGE_HALF, riverBedY);
  shape.lineTo(-BRIDGE_HALF, riverBedY);

  /*
   * 三孔拱洞：中孔大、两侧小（石拱桥的常规形制）。
   *
   * 拱脚必须抬到水面附近（springY≈-0.5），不能落在河床上——落在河床的话拱洞有
   * 2.4m 埋在水下，露出水面的开口只剩 1.3m，整座桥看上去实心，像一道拦河坝。
   * 拱顶再按"离桥面留 0.6 以上"反推半径，避免拱顶穿出桥面。
   */
  const springY = -0.5;
  const arches: Array<[number, number]> = [
    [0, 3.6],
    [-7.6, 2.2],
    [7.6, 2.2],
  ];
  for (const [ax, r] of arches) {
    const hole = new THREE.Path();
    hole.moveTo(ax - r, riverBedY);
    hole.lineTo(ax - r, springY);
    // clockwise=true 才走"上半圆"（角度从 π 递减经 π/2 到 0），false 会挖到河床以下
    hole.absarc(ax, springY, r, Math.PI, 0, true);
    hole.lineTo(ax + r, riverBedY);
    shape.holes.push(hole);
  }

  const geo = new THREE.ExtrudeGeometry(shape, {
    depth: BRIDGE_WIDTH,
    bevelEnabled: false,
    curveSegments: 20,
  });
  // ExtrudeGeometry 从 z=0 往 +z 挤，桥宽中心要落在 BRIDGE_Z 上
  geo.translate(0, 0, BRIDGE_Z - BRIDGE_WIDTH / 2);
  b.add('stoneDark', geo);

  // 桥面铺一层浅色石板，把"可走的面"和"结构体"区分开（也是唯一的亮色线索）
  const segs = 18;
  const step = (BRIDGE_HALF * 2) / segs;
  for (let i = 0; i < segs; i += 1) {
    const x0 = -BRIDGE_HALF + step * (i + 0.5);
    const y = deckY(x0);
    const slope = deckY(x0 + 0.5) - deckY(x0 - 0.5);
    const ang = Math.atan(slope);
    b.box('stone', step * 1.04, 0.16, BRIDGE_WIDTH - 0.5, x0, y - 0.08, BRIDGE_Z, { z: ang });
    // 两侧栏板（沿弧分段，跟着斜率转）
    for (const zs of [-1, 1]) {
      b.box('stoneDark', step * 1.02, 0.9, 0.26, x0, y + 0.45, BRIDGE_Z + zs * (BRIDGE_WIDTH / 2 - 0.13), {
        z: ang,
      });
    }
  }
  // 栏杆望柱：每 2.3m 一根，否则长栏板看着像一堵墙
  for (let x = -BRIDGE_HALF + 1.2; x <= BRIDGE_HALF - 1.2; x += 2.3) {
    const y = deckY(x);
    for (const zs of [-1, 1]) {
      b.box('stone', 0.34, 1.16, 0.34, x, y + 0.58, BRIDGE_Z + zs * (BRIDGE_WIDTH / 2 - 0.13));
    }
  }
}

/* ------------------------------------------------------------------ 牌坊 */

function buildGate(b: Builder, x: number, z: number): void {
  const baseY = heightAt(x, z);
  const halfZ = 3.3;
  for (const dz of [-halfZ, halfZ]) {
    for (const dx of [-1.5, 1.5]) {
      b.box('stoneDark', 1.0, 0.34, 1.0, x + dx, baseY + 0.17, z + dz); // 柱础
      b.box('red', 0.62, 6.2, 0.62, x + dx, baseY + 3.1, z + dz); // 立柱
    }
  }
  // 两道横梁（沿 z 向跨过街道）
  b.box('red', 0.75, 0.6, halfZ * 2 + 1.6, x, baseY + 4.55, z);
  b.box('red', 0.66, 0.52, halfZ * 2 + 0.6, x, baseY + 5.55, z);
  // 匾额
  b.box('gold', 0.36, 1.15, 3.1, x - 0.1, baseY + 6.35, z);
  // 檐顶两级
  b.box('tile', 3.4, 0.4, halfZ * 2 + 3.0, x, baseY + 7.05, z);
  b.box('tile', 2.6, 0.34, halfZ * 2 + 2.0, x, baseY + 7.45, z);
  // 檐角
  for (const dz of [-1, 1]) {
    b.box('tile', 3.0, 0.26, 1.0, x, baseY + 7.62, z + dz * (halfZ + 1.4), { x: dz * 0.14 });
  }
}

/* ------------------------------------------------------------------ 码头与小船 */

const DOCK_Z = -14;
/** 平台面高度（GLB 里 z=+0.95，这里必须跟它一致，台阶与灯位都按这个值算） */
const DOCK_TOP = WATER_LEVEL + 0.95;

/**
 * 码头中心 X。
 *
 * <p>旧值 -10.5 是<b>整个平台都落在岸上</b>：河槽西岸在 x≈-6.2（`RIVER_HALF=9`，
 * 河心随 z 摆到 2.8 附近），8.6m 的平台从 -14.8 到 -6.2 正好止于岸线，
 * 一根桩都没沾到水——出图就是"岸上摆了一张大木桌"。</p>
 *
 * <p>现在 -4.6：平台 -8.9…-0.3，西端 2.6m 搭岸、其余 6m 悬在水上（东端水深约
 * 2.4m，桩长 3.9m 能扎到河床）。-7.0 时仍有 60% 的板面压在草地上，
 * 出图是"岸上摆了一张大木桌"。</p>
 */
function dockX(): number {
  return -4.6;
}

/** 码头平台长度（沿 X），GLB 里也是 8.6 */
const DOCK_LEN = 8.6;

function buildDock(b: Builder): void {
  const x = dockX();
  // 平台
  b.box('wood', DOCK_LEN, 0.36, 5.6, x, DOCK_TOP - 0.18, DOCK_Z);
  // 桩
  for (const dx of [-3.6, 0, 3.6]) {
    for (const dz of [-2.2, 2.2]) {
      b.cyl('woodDark', 0.22, 0.26, 3.4, x + dx, DOCK_TOP - 1.9, DOCK_Z + dz, 6);
    }
  }
}

/**
 * 上岸台阶（已废弃，见 buildSettlement 里的 S15 注）。
 *
 * <p>留这段历史是因为它记录了一个四次返工的教训：这段岸坡与码头面齐平，
 * 任何"强造高差"的构件（三级台阶 / 旗石埠头）都会读作"摔在草坪上的混凝土板"；
 * 而旗石贴地又会整个被地形网格吞掉——地形是连续面，平板只要一角低于地面
 * 就被啃成碎片。<b>地形在说什么就听什么</b>：岸与码头齐平，草地包边即是原样。</p>
 *
 * <p>方向备忘：码头西端搭岸、东端伸进深水，任何上岸构件只能往 -X 铺。</p>
 */


export interface BoatPlace {
  x: number;
  z: number;
  rotY: number;
}

/**
 * 船位。
 *
 * <p>吃水 0.45m，所以必须落在<b>水深 0.5 以上</b>的位置。旧值（dockX-3.4 /
 * dockX-1.2，即 x≈-13.9 / -11.7）在岸上，船是"停在草地上"的——把码头往东挪
 * 之后必须一并重算，靠判 {@code heightAt} 找不到落点。</p>
 */
export function boatSpots(): BoatPlace[] {
  return [
    // 船身 5.4m，两条同侧摆必然首尾相插（x 区间重叠 1.7m 时出图就是"一堆木头"）。
    // 一南一北夹着码头，也是渡口真实的停法。
    { x: -2.4, z: -18.5, rotY: 0.1 }, // 南侧
    { x: 0.6, z: -10.4, rotY: -0.14 }, // 北侧
  ];
}

function buildBoat(b: Builder, p: BoatPlace): void {
  // 船体：旋转剖面拉长，比"盒子斜切"更像船
  const profile: THREE.Vector2[] = [
    new THREE.Vector2(0.02, 0),
    new THREE.Vector2(0.55, 0.06),
    new THREE.Vector2(0.82, 0.34),
    new THREE.Vector2(0.95, 0.68),
    new THREE.Vector2(1.0, 0.92),
    new THREE.Vector2(0.94, 1.0),
  ];
  const hull = new THREE.LatheGeometry(profile, 14);
  const m = new THREE.Matrix4().compose(
    new THREE.Vector3(p.x, WATER_LEVEL + 0.02, p.z),
    new THREE.Quaternion().setFromEuler(new THREE.Euler(0, p.rotY, 0)),
    new THREE.Vector3(1.0, 1.0, 2.8),
  );
  b.add('woodDark', hull, m);
  b.cyl('wood', 0.07, 0.09, 3.2, p.x, WATER_LEVEL + 2.2, p.z, 6);
}

/* ------------------------------------------------------------------ 树 */

export interface TreeSpot {
  x: number;
  z: number;
  y: number;
  scale: number;
  rotY: number;
  variant: TreeVariant;
}

/**
 * 树位。程序化占位件与 GLB 两条路径**共用**这套落点：换资产时构图不变，
 * 否则每次换模型都要重新调一遍画面。
 *
 * <p>靠河 22m 内优先种垂柳（水边柳是江南的默认配置），远处用圆冠 / 高瘦交替，
 * 避免整片树一个样。</p>
 */
export function treeSpots(count = 54): TreeSpot[] {
  let seed = 20261118;
  const rand = () => {
    seed = (seed * 1664525 + 1013904223) % 4294967296;
    return seed / 4294967296;
  };

  const out: TreeSpot[] = [];
  let guard = 0;
  while (out.length < count && guard < 2000) {
    guard += 1;
    const x = (rand() - 0.5) * 150;
    const z = (rand() - 0.5) * 240;
    const d = Math.abs(x - Math.sin(z * 0.013) * 6.5 - Math.sin(z * 0.0047 + 1.3) * 4.2);
    if (d < 12) continue; // 河道里不长树
    if (Math.abs(z - BRIDGE_Z) < 24 && d < 26) continue; // 桥头广场留白
    // 聚落占位区：x 覆盖街面到城墙，z 覆盖整条街（-38..38）并向两端各留 6m。
    // 早先只挡到 z<8，结果 z>8 的街心会长出树来（桥头机位实测有一棵树穿过牌坊屋顶）
    if (x > 11.5 && x < 36 && z > -46 && z < 44) continue;
    // 码头及其上岸台阶：x -14…-2.7、z -17…-11。不排除的话会有树长在平台上
    if (x > -14.5 && x < -2.5 && z > -18 && z < -10) continue;
    // 田垄与西岸小村：树长在垄上不像话，也会把田垄切得七零八落
    let onField = false;
    for (const [fx0, fz0, fx1, fz1] of FIELD_BLOCKS) {
      if (x > fx0 - 1.5 && x < fx1 + 1.5 && z > fz0 - 1.5 && z < fz1 + 1.5) onField = true;
    }
    if (onField) continue;
    if (x > -36 && x < -18 && z > -10 && z < 12) continue;

    const y = heightAt(x, z);
    if (y < WATER_LEVEL + 0.4) continue;

    const r = rand();
    const variant: TreeVariant = d < 22 ? 'willow' : r < 0.62 ? 'round' : 'tall';
    out.push({
      x,
      z,
      y,
      scale: variant === 'willow' ? 0.9 + rand() * 0.3 : 0.85 + rand() * 0.45,
      rotY: rand() * Math.PI * 2,
      variant,
    });
  }
  return out;
}

function buildTreesPlaceholder(b: Builder, spots: TreeSpot[]): void {
  for (const sp of spots) {
    const s = sp.scale * 1.1;
    b.cyl('woodDark', 0.22 * s, 0.38 * s, 3.0 * s, sp.x, sp.y + 1.5 * s, sp.z, 6);
    b.add(
      'foliage',
      new THREE.IcosahedronGeometry(2.05 * s, 0),
      new THREE.Matrix4().makeTranslation(sp.x, sp.y + 4.3 * s, sp.z),
    );
    b.add(
      'foliage',
      new THREE.IcosahedronGeometry(1.35 * s, 0),
      new THREE.Matrix4().makeTranslation(sp.x + 0.8 * s, sp.y + 5.4 * s, sp.z - 0.6 * s),
    );
  }
}

/**
 * 用 InstancedMesh 铺树。
 *
 * <p>54 棵 × 2 个 primitive × 一物一 mesh 的话是 108 个 drawcall；按 (变体, 部件)
 * 归并成 InstancedMesh 之后是 6 个。三角面不变，但 drawcall 与材质切换全省了。</p>
 */
function buildTreesFromGlb(
  group: THREE.Group,
  assets: SceneAssets,
  spots: TreeSpot[],
): void {
  const byVariant = new Map<TreeVariant, TreeSpot[]>();
  for (const sp of spots) {
    const list = byVariant.get(sp.variant) ?? [];
    list.push(sp);
    byVariant.set(sp.variant, list);
  }

  const m = new THREE.Matrix4();
  const q = new THREE.Quaternion();
  const pos = new THREE.Vector3();
  const scl = new THREE.Vector3();

  for (const [variant, list] of byVariant) {
    const parts = assets.trees[variant];
    if (!parts.length) continue;
    for (const part of parts) {
      const im = new THREE.InstancedMesh(part.geometry, part.material, list.length);
      im.castShadow = true;
      im.receiveShadow = true;
      im.frustumCulled = false; // 实例分布在 150m 范围，整体包围盒没意义
      list.forEach((sp, i) => {
        q.setFromAxisAngle(new THREE.Vector3(0, 1, 0), sp.rotY);
        pos.set(sp.x, sp.y, sp.z);
        scl.set(sp.scale, sp.scale, sp.scale);
        m.compose(pos, q, scl);
        im.setMatrixAt(i, m);
      });
      im.instanceMatrix.needsUpdate = true;
      im.name = `trees-${variant}`;
      group.add(im);
    }
  }
}

/* ------------------------------------------------------------------ 灯笼 */

export interface LanternSpot {
  x: number;
  z: number;
  y: number;
  rotY: number;
}

/**
 * 街灯落点。
 *
 * <p>灯挑向<b>街心</b>：西侧一排朝 +X、东侧一排朝 -X。GLB 里灯体挂在 Blender 的 -Y
 * 一侧，导出后对应 three.js 的 +Z，所以绕 Y 转 +90° 得到 +X、-90° 得到 -X。</p>
 */
export function lanternSpots(): LanternSpot[] {
  const rows = [
    { x: 14.9, rotY: Math.PI / 2 },
    { x: 19.4, rotY: -Math.PI / 2 },
  ];
  const out: LanternSpot[] = [];
  const alleys = alleySpots();
  for (const row of rows) {
    for (let z = -22; z <= 26; z += 4.4) {
      if (Math.abs(z - BRIDGE_Z) < 5) continue;
      /*
       * 巷口正前方不立灯。
       *
       * <p>巷子只有 2.8m 宽，一根 2.6m 高的灯杆立在口子上，正好把 6m 深的巷子
       * 从巷口机位整个挡住——第一版出图就是"一根灯笼怼在镜头前，后面一道黑缝"。
       * 巷子自己有一盏壁灯，不需要街灯补光。</p>
       */
      if (alleys.some((a) => Math.abs(z - a.z) < 2.6)) continue;
      out.push({ x: row.x, z, y: heightAt(row.x, z), rotY: row.rotY });
    }
  }

  /*
   * 渡口灯：码头靠岸一侧两根立杆灯，灯挑向水面（+X）。
   *
   * <p>不加这两盏，码头在夜里是一团纯黑——离它最近的街灯在 20m 外，而点光源的
   * 衰减距离只有 14m，实测相机抬到 3.6m 高度也看不清平台在哪。更要紧的是
   * "灯影摇红"：灯高 2.7m、相机高 3.6m 时，倒影点落在 x≈-2.9 / z≈-20.3，
   * 正好在河面内——这是整个场景里唯一能看到灯笼倒影的位置。</p>
   */
  for (const z of [-16.6, -11.4]) {
    out.push({ x: -9.2, z, y: heightAt(-9.2, z), rotY: Math.PI / 2 });
  }
  return out;
}

function buildLanternsFromGlb(group: THREE.Group, assets: SceneAssets): void {
  const spots = lanternSpots();
  if (!spots.length || !assets.lantern.length) return;
  const m = new THREE.Matrix4();
  const q = new THREE.Quaternion();
  const up = new THREE.Vector3(0, 1, 0);
  const pos = new THREE.Vector3();
  const one = new THREE.Vector3(1, 1, 1);
  for (const part of assets.lantern) {
    const im = new THREE.InstancedMesh(part.geometry, part.material, spots.length);
    im.castShadow = true;
    im.receiveShadow = true;
    im.frustumCulled = false;
    spots.forEach((sp, i) => {
      q.setFromAxisAngle(up, sp.rotY);
      pos.set(sp.x, sp.y, sp.z);
      m.compose(pos, q, one);
      im.setMatrixAt(i, m);
    });
    im.instanceMatrix.needsUpdate = true;
    im.name = 'lanterns';
    group.add(im);
  }
}

function buildLanterns(b: Builder): void {
  for (const sp of lanternSpots()) {
    const dir = sp.rotY > 0 ? 1 : -1;
    b.cyl('woodDark', 0.055, 0.065, 2.6, sp.x, sp.y + 1.3, sp.z, 6);
    b.box('woodDark', 0.6, 0.07, 0.07, sp.x + dir * 0.22, sp.y + 2.58, sp.z);
    b.cyl('glow', 0.21, 0.21, 0.56, sp.x + dir * 0.46, sp.y + 2.26, sp.z, 8);
    b.cyl('tile', 0.27, 0.27, 0.06, sp.x + dir * 0.46, sp.y + 2.56, sp.z, 8);
  }
}

/* ------------------------------------------------------------------ 街面 */

/** 石板街：房子不能直接坐草地上。分段铺，段间高差当"分台"处理 */
/** 主街中心线：民居一律放在 STREET_X + 5 以东，街面本身不许压房子 */
const STREET_X = 17;
/** 排水明沟：贴着街西沿（13.53）的一条，宽 0.46（一锹宽） */
const GUTTER_X = 13.76;
const GUTTER_W = 0.46;

function buildStreet(b: Builder): void {
  /*
   * 石板街。
   *
   * <p>一整块 7×3.7 的浅色平板是"低模感"最直接的来源——没有一条缝，光一打就是
   * 一块水泥地。这里每 3.6m 一段、段间留 7cm 缝，段内再按中轴错成两排
   * （沿 X 各半、错位 0.9m），远看就有"石板一铺到底"的节奏。</p>
   *
   * <p>全部走 Builder 合并，缝再碎也还是 1 个 drawcall。</p>
   */
  /*
   * 垫层：先铺一整块通长的条石，再在上面铺"一块块石板"。
   *
   * <p>不铺垫层时，3.6m 一段之间的 10cm 缝下面是<b>草地</b>——沿街看过去，这些
   * 横向缝在透视里连成一条绿线，像街心长了一条草。垫层只多 12 个三角面，
   * 缝里看到的就是石头。</p>
   */
  b.box('stoneDark', 7.3, 0.2, 87, STREET_X, 1.18 - 0.06, -2.5);

  let seed = 5150;
  const rand = () => {
    seed = (seed * 1103515245 + 12345) % 2147483648;
    return seed / 2147483648;
  };
  /*
   * 高低不平 + 修补过的石板。
   *
   * <p>「街面像刚扫过」的第二个原因：所有石板的顶面在同一个高度上。老石板的
   * 沉陷是<b>厘米级</b>的——0.03 的起伏在掠射角下就是一条条明暗线，读起来是"铺装"
   * 而不是"贴图"。另外按 7% 的比例把几块换成浅一档的石料（`stone`），
   * 那些是几十年里陆续换过的板子，整条街因此有了"时间"。</p>
   *
   * <p>试过在石板上再叠"车辙"与"门前踏石"两条 6mm 的薄板，两次都失败：
   * 薄板在街面上渲染出来的亮度<b>与它的反照率无关</b>（改到近黑仍然比街面亮），
   * 出图是两条白漆标线。这类"贴在表面上的一层薄皮"这个场景里不要再用。</p>
   */
  for (let z = -46; z < 41; z += 3.6) {
    // 东半幅：17.05…20.47，一整块
    const zcE = z + 0.9 + (rand() - 0.5) * 0.12;
    const sinkE = (rand() - 0.5) * 0.055;
    const kE: MatKey = rand() < 0.07 ? 'stone' : 'stoneDark';
    b.box(kE, 3.42 - rand() * 0.1, 0.24, 3.5, STREET_X + 1.76, heightAt(STREET_X, zcE) + 0.05 + sinkE, zcE);

    // 西半幅：13.99…16.95。13.53…13.99 那一条让给排水明沟（见下）
    const zcW = z - 0.9 + (rand() - 0.5) * 0.12;
    const sinkW = (rand() - 0.5) * 0.055;
    const kW: MatKey = rand() < 0.07 ? 'stone' : 'stoneDark';
    b.box(kW, 2.96 - rand() * 0.08, 0.24, 3.5, 15.47, heightAt(STREET_X, zcW) + 0.05 + sinkW, zcW);
  }

  /*
   * 排水明沟。
   *
   * <p>「街面像刚扫过」的原因不是没有缝，是<b>没有一个比街面低的东西</b>：
   * 石板的顶面全在同一个高度上，光一打就是一块平板。老街的排水明沟贴着街沿走，
   * 沟底比街面低 10cm，常年积水积泥发暗——这一条暗线在透视里往远处收，
   * 是街景机位里唯一能读出"路面有多长"的东西。</p>
   *
   * <p>做法不是"在石板上再叠一块"，而是<b>把西半幅的石板往里让 0.46</b>，
   * 露出下面的垫层当沟底。叠上去的沟底会是一条凸起的暗带，看着像路缘。</p>
   */
  const GY = heightAt(STREET_X, -2.5) + 0.04; // 垫层顶面（1.22）
  b.box('wet', GUTTER_W - 0.05, 0.02, 87, GUTTER_X, GY, -2.5);
  // 内侧牙石：比街面高 2cm 的一道小棱，低角度光会在这里打出一条长直线
  b.box('stone', 0.16, 0.15, 87, GUTTER_X + GUTTER_W / 2 - 0.02, GY + 0.075, -2.5);
  // 沟底每 7m 一道跌水坎：长直沟里没有任何横向信息，远看就是一根黑线
  for (let z = -44; z < 40; z += 7) {
    b.box('stoneDark', GUTTER_W - 0.06, 0.09, 0.14, GUTTER_X, GY + 0.045, z);
  }



  /*
   * 街沿石：街面东沿（20.5）到临街立面（21.2）之间那条 0.7m 的缝。
   *
   * <p>房子退到 FRONT_X 之后，街面与墙脚之间会留一条长草的窄缝——远看像街上
   * 多了一条绿化带。补一条贴着墙脚的条石，顺便把台基与街面的高差收口。</p>
   */
  for (let z = -46; z < 41; z += 2.4) {
    const zc = z + 1.2;
    const y = heightAt(FRONT_X - 0.4, zc);
    b.box('stoneDark', 1.5, 0.3, 2.44, FRONT_X - 0.45, y + 0.04, zc);
  }
}

/* ------------------------------------------------------------------ 驳岸 */

/**
 * 石砌驳岸。
 *
 * <p>镇区平台（terrain.ts 的 townMask）把街面压到 1.18m，而水边在 x≈12.4：平台西沿
 * 到街面之间那条 0.6m 的土坡，加上平台外 0.5m 的陡岸，在出图里是一条<b>长草的斜坡
 * 直接跌进河里</b>——临河老街最不该缺的就是这道石驳岸。</p>
 *
 * <p>做法：沿 z 每 2.4m 一段（与街面石板同节奏），从平台顶往下砌到水下 1.6m，
 * 顶上加一条压面石。桥头让开，其余整条连通。</p>
 */
function buildEmbankment(b: Builder): void {
  const X = 13.5; // 岸线就压在街面西沿（13.53）上：平台边缘与街沿齐平，不留土坡
  for (let z = -50; z < 44; z += 2.4) {
    if (Math.abs(z - BRIDGE_Z) < 3.4) continue; // 桥台占位
    const zc = z + 1.2;
    const top = heightAt(X + 0.4, zc);
    const h = top + 1.75; // 压到水下 1.75m
    b.box('stoneDark', 1.15, h, 2.42, X, top - h / 2 + 0.04, zc);
    // 压面石：比墙面挑出一点，形成一条影线
    b.box('stone', 1.34, 0.16, 2.44, X + 0.04, top + 0.10, zc);
  }
}

/* ------------------------------------------------------------------ 院墙与门楼 */

/**
 * 院墙 + 门楼。
 *
 * <p>只做独门独院的宅子（{@code courtyard}）。院墙不是装饰：它把"街"和"家"分成
 * 两个层次，街立面因此有了<b>前景（墙）与背景（屋顶）</b>。全是贴街的房子，
 * 再怎么做细节也是一排货架。</p>
 *
 * <p>墙必须逐段取地形高度：9m 长的墙在坡上用一个高度，一头会埋进土里、
 * 一头悬空。每段 2.2m，段顶随地形走，压顶瓦跟着走。</p>
 */
function buildCourtWalls(b: Builder, spots: HouseSpot[]): void {
  for (const sp of spots) {
    if (!sp.courtyard) continue;
    const half = sp.courtyard.halfZ;
    const wallH = 2.15;
    const seg = 2.2;
    const z0 = sp.z - half;
    const z1 = sp.z + half;
    for (let z = z0; z < z1 - 0.01; z += seg) {
      const len = Math.min(seg, z1 - z);
      const zc = z + len / 2;
      const y = heightAt(FRONT_X, zc);
      b.box('plaster', 0.32, wallH + 0.5, len + 0.02, FRONT_X, y + wallH / 2 - 0.15, zc);
      // 压顶瓦：白墙没有这道深色檐口就是一道白栅栏
      b.box('tile', 0.52, 0.12, len + 0.16, FRONT_X, y + wallH + 0.06, zc);
    }

    // 门楼：两垛砖墩 + 门楣 + 小瓦顶。位置在墙中央，正对宅门
    const gy = heightAt(FRONT_X, sp.z);
    for (const dz of [-1.05, 1.05]) {
      b.box('plaster', 0.44, 2.75, 0.44, FRONT_X, gy + 1.22, sp.z + dz);
      b.box('tile', 0.58, 0.1, 0.58, FRONT_X, gy + 2.62, sp.z + dz);
    }
    // 门洞：深色门板凹进 0.1，门框用木色
    b.box('woodDark', 0.16, 2.06, 1.66, FRONT_X - 0.04, gy + 1.03, sp.z);
    b.box('wood', 0.12, 2.2, 1.9, FRONT_X - 0.10, gy + 1.10, sp.z);
    b.box('woodDark', 0.2, 2.02, 1.6, FRONT_X - 0.16, gy + 1.01, sp.z);
    // 门楼顶：一块 1.5m 进深的小坡瓦顶（前后各挑 0.35），比墙头高一档
    b.box('tile', 0.92, 0.12, 2.7, FRONT_X - 0.40, gy + 2.83, sp.z, { z: 0.30 });
    b.box('tile', 0.92, 0.12, 2.7, FRONT_X + 0.40, gy + 2.83, sp.z, { z: -0.30 });
    b.box('tile', 0.2, 0.12, 2.78, FRONT_X, gy + 2.96, sp.z);
    // 门前踏跺
    b.box('stoneDark', 0.5, 0.18, 2.0, FRONT_X - 0.42, gy + 0.09, sp.z);
  }
}

/* ------------------------------------------------------------------ 巷口 */

/**
 * 巷口与巷内。
 *
 * <p>俯视图里整片城镇是"一整块屋顶"，缺的不是细节而是<b>缝隙</b>：真实的江南小镇
 * 由一条条 2~3m 的巷子切成一进进的院落，屋顶因此分成一块块，各自有脊、有山墙。
 * 这里把两处铺面之间让出 2.8m，用铺地 + 巷门 + 照壁把它做"实"——只留一条空缝
 * 在俯视时是两块屋顶之间的一道黑边，看不出来是巷子。</p>
 *
 * <p>两侧的山墙不用另建：铺面的山墙本来就是整进深落地、高过屋面的。</p>
 */
function buildAlleys(b: Builder, spots: AlleySpot[]): void {
  for (const sp of spots) {
    const zc = sp.z;
    const half = sp.w / 2;
    const x0 = FRONT_X;
    const y = heightAt(x0 + 2.0, zc);

    // 巷内铺地：先一段平的，靠里抬一级（镇区平台在 x>26 开始交回自然地形）
    // 压暗 22%：两侧山墙挡掉大半天光，而 IBL 是无遮挡的天空贴图，不压就是"街面延伸"
    b.darken(0.78);
    b.box('stoneDark', 4.8, 0.22, sp.w - 0.06, x0 + 2.4, y + 0.02, zc);
    b.box('stone', 1.5, 0.22, sp.w - 0.06, x0 + 5.55, y + 0.18, zc);
    b.box('stoneDark', 0.3, 0.2, sp.w - 0.06, x0 + 4.85, y + 0.07, zc);
    b.darken(1);

    // 巷门：两垛砖墩 + 门楣 + 小瓦顶。它是街立面上的一个"洞"，
    // 也是唯一能在街景机位里看出"这里有条巷子"的东西。
    for (const dz of [-1, 1]) {
      b.box('plaster', 0.42, 2.6, 0.4, x0 + 0.06, y + 1.3, zc + dz * (half - 0.2));
      b.box('tile', 0.54, 0.1, 0.52, x0 + 0.06, y + 2.6, zc + dz * (half - 0.2));
    }
    b.box('woodDark', 0.34, 0.26, sp.w + 0.1, x0 + 0.06, y + 2.43, zc);
    // 门头瓦顶：前后两坡，前坡挑出 0.55。
    // 0.12 厚的瓦片从街面平视只看得见一条边——出图是"门楣上浮着一块黑板"；
    // 加厚到 0.2 并把前坡往外挑，才看得见瓦面。
    b.box('tile', 1.15, 0.2, sp.w + 0.5, x0 - 0.34, y + 2.70, zc, { z: 0.30 });
    b.box('tile', 1.15, 0.2, sp.w + 0.5, x0 + 0.34, y + 2.70, zc, { z: -0.30 });
    b.box('tile', 0.2, 0.22, sp.w + 0.62, x0 + 0.02, y + 2.86, zc);
    // 巷名匾：一小块深色木牌。不能用 gold——金属材质在 1m 内的壁灯下会烧成一块橙红
    // 色发光板，比巷子本身还抢眼。
    b.box('woodDark', 0.06, 0.34, 1.05, x0 - 0.16, y + 2.16, zc);
    b.box('wood', 0.04, 0.26, 0.9, x0 - 0.19, y + 2.16, zc);

    /*
     * 巷尾：不是照壁，是一户人家的后门。
     *
     * <p>第一版做成一堵白墙，出图是"巷子尽头一堵大白板"——眼睛需要一个落点，
     * 一堵亮墙只会把 6m 的纵深压成 1m 的壁龛。改成一扇深色板门 + 石门框 +
     * 门罩（小披檐）：暗色的门在亮墙上是全巷唯一的高对比物，纵深立刻成立。</p>
     */
    b.darken(0.82);
    b.box('plaster', 0.34, 2.75, sp.w + 0.1, x0 + ALLEY_D - 0.1, y + 1.55, zc);
    b.darken(1);
    b.box('tile', 0.52, 0.14, sp.w + 0.3, x0 + ALLEY_D - 0.1, y + 2.98, zc);
    // 石门框 + 双扇板门
    b.box('stoneDark', 0.12, 2.3, 1.7, x0 + ALLEY_D - 0.30, y + 1.15, zc);
    b.box('woodDark', 0.1, 2.06, 1.34, x0 + ALLEY_D - 0.34, y + 1.03, zc);
    b.box('wood', 0.06, 1.96, 0.06, x0 + ALLEY_D - 0.38, y + 1.0, zc);
    // 门罩：门头上一道小披檐，把"门"和"墙"分开
    b.box('tile', 0.62, 0.1, 2.2, x0 + ALLEY_D - 0.46, y + 2.42, zc, { z: 0.34 });
    // 水缸：巷子最里端放一口缸是防火的常规做法
    b.cyl('stoneDark', 0.3, 0.26, 0.56, x0 + ALLEY_D - 1.15, y + 0.44, zc + 0.72, 12);
    b.cyl('stone', 0.33, 0.33, 0.07, x0 + ALLEY_D - 1.15, y + 0.71, zc + 0.72, 12);

    // 墙上壁灯：巷子里没有灯就是一条黑洞，这也是一处暖光斑
    b.box('woodDark', 0.16, 0.08, 0.2, x0 + 2.3, y + 2.34, zc - half + 0.08);
    b.box('glow', 0.2, 0.34, 0.26, x0 + 2.3, y + 2.12, zc - half + 0.1);
  }
}

/* ------------------------------------------------------------------ 后巷墙 */

/**
 * 后巷围墙。
 *
 * <p>俯视时铺面屋顶之后到城墙根之间是一大片空地，城镇因此"只有一层皮"。
 * 补一道 1.85m 的后墙（每 2.4m 一段、逐段取地形高度），墙外留出 1m 后巷——
 * 屋顶、后墙、城墙形成三条前后错开的水平线，纵深就出来了。</p>
 */
function buildBackWalls(b: Builder): void {
  const X = 28.6;
  const runs: Array<[number, number]> = [
    [-27.6, -11.2],
    [9.0, 32.9],
  ];
  for (const [z0, z1] of runs) {
    for (let z = z0; z < z1 - 0.01; z += 2.4) {
      const len = Math.min(2.4, z1 - z);
      const zc = z + len / 2;
      const y = heightAt(X, zc);
      b.box('plaster', 0.3, 2.0, len + 0.02, X, y + 0.85, zc);
      b.box('tile', 0.5, 0.11, len + 0.14, X, y + 1.88, zc);
    }
  }
}

/* ------------------------------------------------------------------ 门前小品 */

/**
 * 门口的水缸、石磨、柴垛。
 *
 * <p>光有房子没有"生活痕迹"，街还是布景。这三样最便宜、辨识度最高：
 * 水缸（防火，江南老街几乎家家一口）、石磨、柴垛。</p>
 */
function buildStreetProps(b: Builder, spots: HouseSpot[]): void {
  let seed = 20261121;
  const rand = () => {
    seed = (seed * 1103515245 + 12345) % 2147483648;
    return seed / 2147483648;
  };

  for (const sp of spots) {
    if (sp.x < 0) continue; // 只做东岸的街
    const x = FRONT_X - 0.75;
    // 水缸：缸身 + 缸沿 + 水面
    if (rand() < 0.6) {
      const z = sp.z + (rand() - 0.5) * 4.4;
      const y = heightAt(x, z);
      // cyl(key, rTop, rBot, h, x, y, z, seg)：六个数字一个都不能少。
      // 少传 h 时 h 会吃掉 x（20.75m），出图是一根顶到天上去的细柱。
      b.cyl('stoneDark', 0.34, 0.30, 0.62, x, y + 0.30, z, 12);
      b.cyl('stone', 0.37, 0.37, 0.08, x, y + 0.60, z, 12);
      b.cyl('water', 0.29, 0.29, 0.03, x, y + 0.56, z, 12);
    }
    // 柴垛：三四根横放的木柴叠起来
    if (rand() < 0.4) {
      const z = sp.z + (rand() - 0.5) * 5.2;
      const y = heightAt(x, z);
      for (let i = 0; i < 4; i += 1) {
        b.cyl('wood', 0.09, 0.09, 0.9, x + (i % 2) * 0.16, y + 0.09 + Math.floor(i / 2) * 0.16, z + (i % 2) * 0.02, 6, {
          x: Math.PI / 2,
        });
      }
    }
  }
}

/* ------------------------------------------------------------------ 城墙 */

const WALL_X = 31.5;
/** GLB 标准段长度（沿 Z）。运行时按实际段长沿 Z 缩放，把区间精确填满 */
const WALL_SEG = 8;

/** 城墙只修两段，中间让给桥头广场（真实城郭也是这样——南门进、北门出） */
const WALL_RUNS: Array<[number, number]> = [
  [-56, -8],
  [12, 44],
];

export interface WallSeg {
  z: number;
  y: number;
  /** 本段沿 Z 的实际长度（≈8m，因要整除区间而略有变化） */
  len: number;
  /** 本段俯仰角（弧度）。整个 run 同一个值，让墙顶连成一条斜线 */
  pitch: number;
}

/**
 * 城墙分段落点。
 *
 * <p>为什么是"段"而不是"一道墙"：城墙必须顺着地形起伏，而地形在这 44m 跨度里
 * 有近 9m 高差。<b>每段单独取地面高度</b>，段与段之间的高差就形成阶梯状下跌——
 * 那正是山城城墙的真实形态。整道墙只取一次高度（旧写法）的后果是一排悬空方块。</p>
 *
 * <p>段数由区间长度除以 8 四舍五入，再把区间均分——<b>两个区间长度都是 8 的整数倍</b>
 * （48m / 32m），所以段长正好 8m、缩放恒为 1。这一点对砖纹是硬要求：UV 在
 * Blender 里按米算（1 UV = 4m），一旦逐段缩放，砖缝宽度与相位都会跟着变，
 * 每 8m 边界就多出一条错缝，等于给"格子感"描了边。</p>
 */
export function wallSegments(): WallSeg[] {
  const out: WallSeg[] = [];
  for (const [z0, z1] of WALL_RUNS) {
    const span = z1 - z0;
    const n = Math.max(1, Math.round(span / WALL_SEG));
    const len = span / n;
    // 整段区间用**一个**俯仰角（首尾地面高差 / 区间长）。逐段取坡度的话，
    // 相邻段端面不平行，8m 的段长会把 1° 的角度差放大成 14cm 的楔形缝。
    const pitch = -Math.atan((heightAt(WALL_X, z1) - heightAt(WALL_X, z0)) / span);
    for (let i = 0; i < n; i += 1) {
      const zc = z0 + len * (i + 0.5);
      // 取段内 5 点平均：只看中心点的话，8m 段在坡上一端悬空、一端埋没
      let sum = 0;
      for (let k = 0; k < 5; k += 1) sum += heightAt(WALL_X, zc + (k - 2) * (len / 5));
      out.push({ z: zc, y: sum / 5, len, pitch });
    }
  }
  return out;
}

/** 占位城墙：分层收分 + 垛口，体量与 GLB 对齐（换件时构图不动） */function buildWall(b: Builder, segs: WallSeg[]): void {
  for (const sg of segs) {
    const y = sg.y;
    // 三层退台：底 2.75 → 1.55m 处 2.62 → 3.15m 处 2.34 → 4.6m 处 2.05
    b.box('stoneDark', 2.68, 4.7, sg.len + 0.02, WALL_X, y + 1.6, sg.z);
    b.box('stoneDark', 2.48, 1.9, sg.len + 0.02, WALL_X, y + 4.0, sg.z);
    // 外侧垛墙 + 四个垛
    for (let i = 0; i < 4; i += 1) {
      const dz = -sg.len / 2 + sg.len / 8 + i * (sg.len / 4);
      b.box('stone', 0.46, 1.18, sg.len / 4 - 0.1, WALL_X + 0.8, y + 5.2, sg.z + dz);
    }
    // 内侧女墙
    b.box('stone', 0.36, 0.74, sg.len, WALL_X - 0.85, y + 4.97, sg.z);
  }
}

/**
 * 敌台：城墙端头的收头。
 *
 * <p>城墙是两个区间拼起来的，端点在 z=-56 / -8 / 12 / 44 处直接截断——墙体本身
 * 有厚度有收分，可一旦被"切"断，断面是一块光板：俯视机位一眼就看见四个平口，
 * 像四根被锯断的方木。真实城郭不会让墙凭空结束，要么拐角、要么收进山体、
 * 要么以敌台（马面上的方形墩台）收头。</p>
 *
 * <p>选敌台是因为它三层都能兑现：<b>加宽</b>（比墙体宽 1.5m，端面被吃进去）、
 * <b>加高</b>（马道 5.3m，比墙顶 4.6m 高一级，俯视时形成节奏）、<b>四面垛口</b>
 * （端头有了自己的轮廓，不再依赖墙身）。全部程序化生成，与 GLB 城墙共用
 * 同一张砖纹、同一把尺（{@link BRICK_METERS}），接缝处不会出现两套砖。</p>
 */
function buildWallBastions(b: Builder): void {
  const W = 4.2; // 进深（X）：墙体 2.75，两侧各出挑 0.7
  const D = 5.6; // 沿墙长（Z）：把端面整个包进去
  const UW = 3.7; // 上层收分后
  const UD = 4.9;
  const DECK = 5.1; // 敌台马道面，比墙顶 4.6 略高一档（再高就成了孤零零的黑塔）
  const PARA = 1.18;

  for (const [z0, z1] of WALL_RUNS) {
    for (const zc of [z0, z1]) {
      const y = heightAt(WALL_X, zc);
      // 基座：比墙身还宽一圈，向下埋 0.9m——坡地上端头最容易露墙根
      b.box('brick', W + 0.7, 1.5, D + 0.7, WALL_X, y - 0.15, zc);
      b.box('brick', W, 3.5, D, WALL_X, y + 2.35, zc);
      b.box('brick', UW, DECK - 4.1, UD, WALL_X, y + (4.1 + DECK) / 2, zc);

      // 四面垛口：每个面先一道 0.34 高的连续矮墙，上面再按块分垛。
      // 垛口不是"墙上的洞"，而是矮墙上方缺一块——缺口底就是马道。
      for (const sx of [-1, 1]) {
        const px = WALL_X + sx * (UW / 2 - 0.21);
        b.box('brick', 0.42, 0.34, UD, px, y + DECK + 0.17, zc);
        for (let i = 0; i < 3; i += 1) {
          const dz = -UD / 2 + UD / 6 + i * (UD / 3);
          b.box('brick', 0.42, PARA, UD / 3 - 0.5, px, y + DECK + PARA / 2, zc + dz);
          b.box('stoneDark', 0.52, 0.09, UD / 3 - 0.4, px, y + DECK + PARA + 0.045, zc + dz);
        }
      }
      for (const sz of [-1, 1]) {
        const pz = zc + sz * (UD / 2 - 0.21);
        b.box('brick', UW - 0.42, 0.34, 0.42, WALL_X, y + DECK + 0.17, pz);
        for (let i = 0; i < 2; i += 1) {
          const dx = -UW / 2 + UW / 4 + i * (UW / 2);
          b.box('brick', UW / 2 - 0.5, PARA, 0.42, WALL_X + dx, y + DECK + PARA / 2, pz);
          b.box('stoneDark', UW / 2 - 0.4, 0.09, 0.52, WALL_X + dx, y + DECK + PARA + 0.045, pz);
        }
      }
    }
  }
}

/* ------------------------------------------------------------------ 西岸：田垄 */

/**
 * 田垄区块 [x0, z0, x1, z1]。
 *
 * <p>左岸（西岸）原本只有草地和几棵散树，全景里是一大片空绿——构图半边全空。
 * 补一片垄作田比补建筑便宜得多：一行行 0.5m 高的作物贴地铺开，远看就是
 * 江南的田垄肌理，且完全跟着地形走，不会出现"浮在坡上的地毯"。</p>
 *
 * <p>两块地的位置刻意分开：一块在西南河湾外，一块在桥头正对岸——前者补全景
 * 左下，后者补桥头机位的背景。</p>
 */
const FIELD_BLOCKS: Array<[number, number, number, number]> = [
  [-38, -47, -16, -25],
  [-36, 10, -18, 31],
];

/**
 * 田块底面。
 *
 * <p>只有垄没有"田"是错的：远看一垄一垄的高对比条带会在 1.5m 的垄距上产生摩尔纹，
 * 黄昏逆光下整片田变成一格一格的亮点（实测像一片地灯）。先铺一层跟地形贴合的
 * 田面（暗绿），垄再压成低对比的浅色条纹——远看是一块"颜色不同的地"，
 * 近看才是垄。</p>
 *
 * <p>田面必须逐顶点取 heightAt：整块取一个高度的平面，在 3.6m 的高差上会一半
 * 埋进坡里、一半浮在空中。</p>
 */
function buildFieldGround(b: Builder): void {
  for (const [x0, z0, x1, z1] of FIELD_BLOCKS) {
    const cx = (x0 + x1) / 2;
    const cz = (z0 + z1) / 2;
    const geo = new THREE.PlaneGeometry(x1 - x0, z1 - z0, Math.round((x1 - x0) / 2.5), Math.round((z1 - z0) / 2.5));
    geo.rotateX(-Math.PI / 2);
    const pos = geo.attributes.position as THREE.BufferAttribute;
    for (let i = 0; i < pos.count; i += 1) {
      const wx = pos.getX(i) + cx;
      const wz = pos.getZ(i) + cz;
      pos.setY(i, Math.max(heightAt(wx, wz), WATER_LEVEL + 0.25) + 0.09);
    }
    pos.needsUpdate = true;
    geo.translate(cx, 0, cz);
    b.add('field', geo);
  }
}

function buildFields(b: Builder): void {
  buildFieldGround(b);
  let seed = 20261119;
  const rand = () => {
    seed = (seed * 1103515245 + 12345) % 2147483648;
    return seed / 2147483648;
  };
  for (const [x0, z0, x1, z1] of FIELD_BLOCKS) {
    // 垄距 1.55、株距 0.98：块长 0.84 让一垄几乎连成线，但留有断口
    for (let x = x0; x <= x1; x += 1.55) {
      for (let z = z0; z <= z1; z += 0.98) {
        const xc = x + (rand() - 0.5) * 0.12;
        const y = heightAt(xc, z);
        if (y < WATER_LEVEL + 0.45) continue; // 水里不种
        // 地头不能是一条齐边：真田是随地形和地界走的，四边各让出一圈不规则缺口
        const edge = Math.min(x - x0, x1 - x, z - z0, z1 - z);
        if (edge < 2.6 && rand() < (2.6 - edge) / 3.2) continue;
        b.box('crop', 0.56 + rand() * 0.1, 0.12 + rand() * 0.06, 0.84, xc, y + 0.09, z);
      }
    }
  }
}

/* ------------------------------------------------------------------ 西岸：小路与水埠 */

/**
 * 小路：从西岸小村下到河边水埠。
 *
 * <p>一块田加几栋房子，中间没有"连接"就是三个孤立的物件。一条踩出来的土路
 * 把村、田、水连起来，是这个尺度上最便宜、也最有说服力的"有人住"的证据。</p>
 *
 * <p>路必须逐段取地形高度并按切线转向：整条用一个高度会一半埋进坡里；
 * 不转向则每段之间露出锯齿。</p>
 */
const PATH_PTS: Array<[number, number]> = [
  [-23.5, 12.0],
  [-17.0, 11.2],
  [-11.0, 8.6],
  [-6.4, 6.2],
];

function buildWestPath(b: Builder): void {
  const curve = new THREE.CatmullRomCurve3(PATH_PTS.map(([x, z]) => new THREE.Vector3(x, 0, z)));
  const n = 40;
  for (let i = 0; i < n; i += 1) {
    const t = (i + 0.5) / n;
    const p = curve.getPoint(t);
    const tan = curve.getTangent(t).normalize();
    const y = heightAt(p.x, p.z);
    if (y < WATER_LEVEL + 0.1) continue;
    // 段长 ×1.1 让相邻两段叠一点，转弯处不会露缝
    b.box('path', 2.0 * 1.1, 0.14, 1.05, p.x, y + 0.02, p.z, { y: Math.atan2(-tan.z, tan.x) });
  }
}

/**
 * 水埠：四级石阶从岸上落进水里。
 *
 * <p>方位在 x≈-6.4 / z≈6.2，正好是西岸岸线的位置（该处河心 4.5、河槽半宽 9）。
 * 台阶往 +X 走、逐级降低，最后一级没进水面以下——真实的河埠头就是这样的，
 * 水涨水落都够得着。</p>
 */
function buildWestWharf(b: Builder): void {
  const z = 6.2;
  const steps = 4;
  for (let i = 0; i < steps; i += 1) {
    const x = -6.4 + i * 0.62;
    const y = heightAt(-6.4, z) - i * 0.26;
    b.box('stoneDark', 0.64, 1.5, 2.4, x, y - 0.75, z);
  }
  // 岸侧的驳岸石：把土坡跟台阶咬住，否则台阶像"插在草里的三块板"
  for (const dz of [-1.35, 1.35]) {
    b.box('stoneDark', 1.5, 0.5, 0.34, -7.2, heightAt(-7.2, z + dz) + 0.12, z + dz);
  }
}

/* ------------------------------------------------------------------ 水线：芦苇 */

export interface ReedSpot {
  x: number;
  y: number;
  z: number;
  rotY: number;
  s: number;
}

/**
 * 芦苇丛落点。
 *
 * <p>沿水线撒：距河心 7.9~10.5m（正是岸坡与水交界那一条），且地面高度在
 * -0.55~0.85 之间——低于这个区间是深水，高于就是干草地，芦苇不长在干草地上。
 * 这一步的意义是打散"草地与水面之间那条刀切一样的直线"：原来两岸的岸线是
 * 一条笔直的色块分界，是全景里最假的地方之一。</p>
 */
export function reedSpots(count = 140): ReedSpot[] {
  let seed = 515072;
  const rand = () => {
    seed = (seed * 1103515245 + 12345) % 2147483648;
    return seed / 2147483648;
  };
  const out: ReedSpot[] = [];
  let guard = 0;
  while (out.length < count && guard < 4000) {
    guard += 1;
    const z = (rand() - 0.5) * 300;
    const side = rand() < 0.5 ? -1 : 1;
    const x = riverCenter(z) + side * (7.9 + rand() * 2.6);
    const y = heightAt(x, z);
    if (y < -0.55 || y > 0.85) continue;
    if (Math.abs(z - BRIDGE_Z) < 4.5) continue; // 桥洞两侧不长（会被桥台压住）
    if (x > -10 && x < 1 && z > -18 && z < -10) continue; // 码头
    /*
     * 东岸在城区段不能长芦苇。
     *
     * <p>河槽半宽 9、河心约 4.2，东岸水线到 x≈13.2，而街面从 13.5 起——芦苇撒点
     * 半径 7.9~10.5 正好落在街上，出图是"街心长着一丛芦苇"。城区段东岸是石砌
     * 驳岸，本来也不长。</p>
     */
    if (x > 11.5 && z > -46 && z < 40) continue;
    out.push({ x, y, z, rotY: rand() * Math.PI * 2, s: 0.75 + rand() * 0.55 });
  }
  return out;
}

/** 一丛芦苇：9 根斜出的苇秆 + 3 个穗。合并成一个几何，供 InstancedMesh 复用 */
function createReedClump(): THREE.BufferGeometry {
  const parts: THREE.BufferGeometry[] = [];
  for (let i = 0; i < 9; i += 1) {
    const a = (i / 9) * Math.PI * 2 + 0.4;
    const h = 1.05 + ((i * 37) % 11) / 26;
    const g = new THREE.CylinderGeometry(0.005, 0.021, h, 4, 1, true);
    g.translate(0, h / 2, 0);
    // 向外倒一点点：一丛芦苇是散开的，不是一束筷子
    g.rotateZ(Math.cos(a) * 0.15);
    g.rotateX(Math.sin(a) * 0.15);
    g.translate(Math.cos(a) * (0.07 + (i % 3) * 0.05), 0, Math.sin(a) * (0.07 + (i % 3) * 0.05));
    parts.push(g);
  }
  for (let i = 0; i < 3; i += 1) {
    const a = (i / 3) * Math.PI * 2 + 1.1;
    const g = new THREE.CylinderGeometry(0.007, 0.032, 0.24, 4, 1, true);
    g.translate(Math.cos(a) * 0.11, 1.12 + i * 0.05, Math.sin(a) * 0.11);
    parts.push(g);
  }
  const merged = mergeGeometries(parts, false);
  for (const g of parts) g.dispose();
  return merged!;
}

function buildReeds(group: THREE.Group): void {
  const spots = reedSpots();
  if (!spots.length) return;
  const geo = createReedClump();
  // 双面：苇秆是开口圆管，单面会在背面看穿
  const mat = new THREE.MeshStandardMaterial({ color: 0x8d8a53, roughness: 0.96, side: THREE.DoubleSide });
  const im = new THREE.InstancedMesh(geo, mat, spots.length);
  im.castShadow = false; // 几百根细秆投影只会让阴影贴图变脏，收益接近零
  im.receiveShadow = true;
  im.frustumCulled = false;
  const m = new THREE.Matrix4();
  const q = new THREE.Quaternion();
  const pos = new THREE.Vector3();
  const scl = new THREE.Vector3();
  const up = new THREE.Vector3(0, 1, 0);
  spots.forEach((sp, i) => {
    q.setFromAxisAngle(up, sp.rotY);
    pos.set(sp.x, sp.y, sp.z);
    scl.set(sp.s, sp.s * 0.9 + 0.2, sp.s);
    m.compose(pos, q, scl);
    im.setMatrixAt(i, m);
  });
  im.instanceMatrix.needsUpdate = true;
  im.name = 'reeds';
  group.add(im);
}

/* ------------------------------------------------------------------ 地被 */

/**
 * 草簇 / 灌木 / 滩石。
 *
 * <p><b>为什么必须单独做一层</b>：地形是 2.3m 一格的网格，顶点色最多做到"大色块"，
 * 于是草地永远是一整片平色。码头与街口这两个近景机位里，草地占了画面下半部，
 * 是"低模感"最集中的地方——而人眼判断地面的尺度<b>靠边缘的碎度</b>，
 * 不靠颜色。一片纯色草地无论调成什么绿都像塑料，加了草簇之后同样的绿色就"是草"了。</p>
 *
 * <p>三个约束：</p>
 * <ul>
 *   <li>全部走 InstancedMesh：草 1.1 万丛也只 1 个 drawcall；</li>
 *   <li>不投影：几万根 0.5m 高的细叶投出来的只是阴影贴图上的噪点，还会把
 *       2048 的阴影图吃干净；</li>
 *   <li>不用 alpha 贴图：这个项目没有透明材质的排序管线，一张透明 PNG 在 Bloom
 *       和阴影下会露方边。实体叶片贵一点，但没有排序问题，还能吃逐顶点色。</li>
 * </ul>
 */

interface GroundSpot {
  x: number;
  y: number;
  z: number;
  rotY: number;
  s: number;
}

/** 点到西岸小路中心线的距离（用来在路上留出空白） */
function distToPath(x: number, z: number): number {
  let best = Infinity;
  for (let i = 0; i < PATH_PTS.length - 1; i += 1) {
    const [x0, z0] = PATH_PTS[i];
    const [x1, z1] = PATH_PTS[i + 1];
    const dx = x1 - x0;
    const dz = z1 - z0;
    const t = clamp(((x - x0) * dx + (z - z0) * dz) / (dx * dx + dz * dz), 0, 1);
    const px = x0 + dx * t;
    const pz = z0 + dz * t;
    best = Math.min(best, Math.hypot(x - px, z - pz));
  }
  return best;
}

/**
 * 铺装区：这些地方长草是硬伤（草会从石板缝里冒出来、从房子里穿出来）。
 *
 * <p>规则要写死而不是"靠地形高度判断"——镇区平台被压平到 1.18，跟河滩草地
 * 高度差不多，靠高度分不出来。</p>
 */
function blockedByPaving(x: number, z: number): boolean {
  // 镇区：街面 / 台基 / 院墙 / 后巷 / 城墙内外（与 terrain.ts 的 townMask 同一范围）
  if (x > 13.2 && x < 34 && z > -50 && z < 45) return true;
  // 田垄：整块排除。让田与草地的边界是一条干净的直线，草只长在地头外
  for (const [x0, z0, x1, z1] of FIELD_BLOCKS) {
    if (x > x0 - 0.8 && x < x1 + 0.8 && z > z0 - 0.8 && z < z1 + 0.8) return true;
  }
  // 西岸小路（含路肩）
  if (distToPath(x, z) < 2.6) return true;
  // 码头平台与上岸台阶
  if (x > -15.5 && x < -1.5 && z > -19 && z < -9) return true;
  // 西岸水埠
  if (x > -8.8 && x < -3.4 && z > 3.8 && z < 8.8) return true;
  // 西岸小村的房子（含墙脚一圈）
  for (const [hx, hz, sc] of WEST_VILLAGE) {
    if (Math.abs(x - hx) < 4.6 * sc && Math.abs(z - hz) < 3.8 * sc) return true;
  }
  return false;
}

/**
 * 一丛草：6 片叶，每片 2 段（基部四边形 + 梢头三角），18 个三角面。
 *
 * <p>顶点色做"根暗梢亮"（0.62 → 1.25）：草叶的透光让梢头比根部亮一档，
 * 这是草看起来"薄"而不是"一片塑料"的原因。逐实例再乘一个冷暖偏色，
 * 近水偏绿、上坡偏黄。</p>
 */
function createGrassTuft(): THREE.BufferGeometry {
  const positions: number[] = [];
  const colors: number[] = [];
  const indices: number[] = [];
  const blades = 6;

  for (let i = 0; i < blades; i += 1) {
    const a = (i / blades) * Math.PI * 2 + 0.55;
    const h = 0.20 + ((i * 37) % 11) / 42; // 0.20 … 0.44
    const w = 0.03;
    const lean = 0.11 + ((i * 23) % 9) / 46;
    const dirX = Math.cos(a);
    const dirZ = Math.sin(a);
    const sideX = -dirZ;
    const sideZ = dirX;
    const bx = dirX * 0.07;
    const bz = dirZ * 0.07;
    const base = positions.length / 3;

    // 两层各两个顶点（基部 / 中段），梢头收成一个点
    for (const t of [0, 0.55]) {
      const half = (w * (1 - 0.8 * t)) / 2;
      const k = Math.pow(t, 1.6) * lean;
      const cx = bx + dirX * k;
      const cz = bz + dirZ * k;
      const y = h * t;
      positions.push(cx - sideX * half, y, cz - sideZ * half);
      positions.push(cx + sideX * half, y, cz + sideZ * half);
      const c = 0.72 + t * 0.44;
      colors.push(c, c, c, c, c, c);
    }
    const kt = lean;
    positions.push(bx + dirX * kt, h, bz + dirZ * kt);
    colors.push(1.16, 1.16, 1.16);

    indices.push(base, base + 1, base + 3, base, base + 3, base + 2, base + 2, base + 3, base + 4);
  }

  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  geo.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3));
  geo.setIndex(indices);
  geo.computeVertexNormals();

  /*
   * 法线朝上偏。
   *
   * <p>草叶是竖直的薄片，几何法线是水平的——低角度的黄昏光打上去，一半叶片
   * 全黑一半全亮，出图是一丛丛"刀片"。把法线往 +Y 掰（保留 45% 的朝向信息），
   * 草就跟着地面一起被照亮，只在叶片朝向差异上留一点明暗——这才是草。</p>
   */
  const nrm = geo.attributes.normal as THREE.BufferAttribute;
  for (let i = 0; i < nrm.count; i += 1) {
    const nx = nrm.getX(i) * 0.45;
    const ny = nrm.getY(i) * 0.45 + 0.78;
    const nz = nrm.getZ(i) * 0.45;
    const len = Math.hypot(nx, ny, nz) || 1;
    nrm.setXYZ(i, nx / len, ny / len, nz / len);
  }
  return geo;
}

/** 草簇落点。两遍采样：全岸带一遍 + 码头近景一遍（那一块是画面下半部） */
function grassSpots(count = 13000): GroundSpot[] {
  let seed = 20261121;
  const rand = () => {
    seed = (seed * 1103515245 + 12345) % 2147483648;
    return seed / 2147483648;
  };

  const out: GroundSpot[] = [];
  const push = (x: number, z: number, sMin = 0.85, sMax = 1.45): boolean => {
    const y = heightAt(x, z);
    if (y < WATER_LEVEL + 0.28 || y > 8.2) return false;
    if (slopeAt(x, z) > 0.85) return false;
    if (blockedByPaving(x, z)) return false;
    out.push({ x, y, z, rotY: rand() * Math.PI * 2, s: sMin + rand() * (sMax - sMin) });
    return true;
  };

  // 全岸带：距河心 9.4…39，靠近水线密、往内陆稀（pow 让分布偏向近岸）
  let guard = 0;
  while (out.length < count && guard < count * 26) {
    guard += 1;
    const z = (rand() - 0.5) * 265;
    const side = rand() < 0.62 ? -1 : 1; // 西岸多给一点：主视点在西南
    const d = 9.4 + Math.pow(rand(), 0.72) * 30;
    push(riverCenter(z) + side * d, z);
  }

  // 码头近景：机位在 3.9m 高、镜头朝岸，这一块 16×26m 的草地要密到能看出"草"
  for (let i = 0; i < 3200; i += 1) {
    push(-25 + rand() * 23, -30 + rand() * 30, 0.8, 1.3);
  }
  return out;
}

/** 灌木落点：树根周围 + 岸带零散。灌木层是"草地 / 树"之间缺的中间层 */
function shrubSpots(trees: TreeSpot[]): GroundSpot[] {
  let seed = 90911;
  const rand = () => {
    seed = (seed * 1103515245 + 12345) % 2147483648;
    return seed / 2147483648;
  };
  const out: GroundSpot[] = [];

  /*
   * 树根周围的下木。
   *
   * <p>第一版是"每棵树必挂 2~3 丛、半径 0.8~2.3m"，出图每棵树脚下都长着
   * 一模一样的一摊深绿——灌木的<b>均匀性</b>比它的稀疏更假。现在只有六成的树有，
   * 数量 1~3、半径 1.2~3.4m，尺寸也拉开，其中一部分被推到树冠投影之外。</p>
   */
  for (const t of trees) {
    if (rand() > 0.58) continue;
    const n = 1 + Math.floor(rand() * 3);
    for (let i = 0; i < n; i += 1) {
      const a = rand() * Math.PI * 2;
      const r = 1.2 + Math.pow(rand(), 0.7) * 2.2;
      const x = t.x + Math.cos(a) * r;
      const z = t.z + Math.sin(a) * r;
      if (blockedByPaving(x, z)) continue;
      out.push({ x, y: heightAt(x, z), z, rotY: rand() * Math.PI * 2, s: 0.42 + rand() * 0.72 });
    }
  }

  for (let i = 0; i < 110; i += 1) {
    const z = (rand() - 0.5) * 240;
    const side = rand() < 0.6 ? -1 : 1;
    const d = 9.6 + Math.pow(rand(), 0.8) * 22;
    const x = riverCenter(z) + side * d;
    if (blockedByPaving(x, z)) continue;
    const y = heightAt(x, z);
    if (y < WATER_LEVEL + 0.35 || slopeAt(x, z) > 0.8) continue;
    out.push({ x, y, z, rotY: rand() * Math.PI * 2, s: 0.5 + rand() * 0.55 });
  }
  return out;
}

/** 一丛灌木：4 个压扁的多面体，高低错开。20 面 × 4 = 80 个三角面 */
function createShrub(): THREE.BufferGeometry {
  const parts: THREE.BufferGeometry[] = [];
  const lumps: Array<[number, number, number, number]> = [
    [0, 0.38, 0, 0.6],
    [0.44, 0.26, 0.2, 0.44],
    [-0.4, 0.24, -0.16, 0.4],
    [0.04, 0.56, -0.26, 0.38],
  ];
  for (const [x, y, z, r] of lumps) {
    // 细分一级：20 面的二十面体在 10m 内是一块"岩石"，80 面才像一堆叶子
    const g = new THREE.IcosahedronGeometry(r, 1);
    g.scale(1, 0.72, 1);
    g.translate(x, y, z);
    parts.push(g);
  }
  const merged = mergeGeometries(parts, false);
  for (const g of parts) g.dispose();
  if (!merged) return new THREE.IcosahedronGeometry(0.5, 0);
  merged.computeVertexNormals();
  return merged;
}

/** 地面散石：靠水线的滩石，扁、圆、半埋 */
function stoneSpots(count = 300): GroundSpot[] {
  let seed = 33113;
  const rand = () => {
    seed = (seed * 1103515245 + 12345) % 2147483648;
    return seed / 2147483648;
  };
  const out: GroundSpot[] = [];
  let guard = 0;
  while (out.length < count && guard < count * 12) {
    guard += 1;
    const z = (rand() - 0.5) * 280;
    const side = rand() < 0.5 ? -1 : 1;
    const d = 8.5 + Math.pow(rand(), 1.4) * 9;
    const x = riverCenter(z) + side * d;
    if (blockedByPaving(x, z)) continue;
    if (x > 11.5 && z > -46 && z < 40) continue;
    const y = heightAt(x, z);
    if (y < WATER_LEVEL - 0.5 || y > 1.6) continue;
    out.push({ x, y, z, rotY: rand() * Math.PI * 2, s: 0.35 + rand() * 0.8 });
  }
  return out;
}

/** 通用：一批落点铺成一个 InstancedMesh（可带逐实例色） */
function instanceScatter(
  group: THREE.Group,
  geo: THREE.BufferGeometry,
  mat: THREE.Material,
  spots: GroundSpot[],
  name: string,
  colorAt?: (i: number, s: GroundSpot) => THREE.Color,
): THREE.InstancedMesh | null {
  if (!spots.length) return null;
  const im = new THREE.InstancedMesh(geo, mat, spots.length);
  im.castShadow = false;
  im.receiveShadow = true;
  im.frustumCulled = false;
  const m = new THREE.Matrix4();
  const q = new THREE.Quaternion();
  const pos = new THREE.Vector3();
  const scl = new THREE.Vector3();
  const up = new THREE.Vector3(0, 1, 0);
  spots.forEach((sp, i) => {
    q.setFromAxisAngle(up, sp.rotY);
    pos.set(sp.x, sp.y, sp.z);
    scl.set(sp.s, sp.s, sp.s);
    m.compose(pos, q, scl);
    im.setMatrixAt(i, m);
    if (colorAt) im.setColorAt(i, colorAt(i, sp));
  });
  im.instanceMatrix.needsUpdate = true;
  if (im.instanceColor) im.instanceColor.needsUpdate = true;
  im.name = name;
  group.add(im);
  return im;
}

function buildGroundCover(group: THREE.Group, trees: TreeSpot[]): void {
  const grass = grassSpots();
  const tuft = createGrassTuft();
  instanceScatter(
    group,
    tuft,
    new THREE.MeshStandardMaterial({
      // 0x7b8a4c → 0x767f4e：白天提亮后草簇的绿读着发"艳"。去饱和的关键是
      // 压 G 通道而不是压亮度——亮度一压草丛就从草地里"沉"下去了。
      color: 0x767f4e,
      vertexColors: true,
      side: THREE.DoubleSide,
      roughness: 0.9,
      metalness: 0,
    }),
    grass,
    'grass',
    (_i, sp) => {
      /*
       * 近水偏冷绿、上坡偏黄枯，再叠一层逐丛抖动。
       *
       * <p>整片草一个颜色，比"没有草"更假——远看是一片荧光绿的颗粒。<b>干湿差</b>
       * 才是草地的信息：靠水的青、坡上的黄、背阴的深。所以这里用高度定干湿，
       * 用位置哈希定每丛的随机偏移（哈希而不是 Math.random，重建场景不跳色）。</p>
       */
      const h = Math.sin(sp.x * 12.9898 + sp.z * 78.233) * 43758.5453;
      const t = h - Math.floor(h);
      const dry = clamp((sp.y - 1.0) / 4.5, 0, 1) * 0.5 + t * 0.5;
      return new THREE.Color().setRGB(0.86 + dry * 0.26, 0.96 + dry * 0.02, 0.82 - dry * 0.14);
    },
  );

  const shrub = createShrub();
  instanceScatter(
    group,
    shrub,
    new THREE.MeshStandardMaterial({ color: 0x566a38, roughness: 0.94 }),
    shrubSpots(trees),
    'shrubs',
  );

  const stones = stoneSpots();
  if (stones.length) {
    // 压到 0.62 而不是 0.5：0.5 时是一张 1.15m 宽的"薄饼"，在近景像一块纸板。
    // 石头的可信度在"埋在土里"——露出一半、边缘被草挡住，比形状本身重要
    const rock = new THREE.IcosahedronGeometry(0.5, 0);
    rock.scale(1.0, 0.62, 1.0);
    instanceScatter(
      group,
      rock,
      new THREE.MeshStandardMaterial({ color: 0x7c7364, roughness: 0.96, flatShading: true }),
      stones,
      'bank-stones',
    );
  }
}

/* ------------------------------------------------------------------ 民居 */

export interface HouseSpot {
  x: number;
  z: number;
  y: number;
  scale: number;
  rotY: number;
  variant: HouseVariant;
  /** 院落：前面砌院墙 + 门楼，房子退到墙后（临街面不补齐，就会出现"墙比门还退"的坑） */
  courtyard?: { halfZ: number };
}

/**
 * 临街立面线。所有临街的房子、院墙、门楼都对齐到这一条 x。
 *
 * <p>不是"每栋各自随机 x"：街这种东西的力量全在<b>立面连续</b>上。随机 x 出来
 * 的是七栋各自独立的房子，对齐之后才是一条街。</p>
 */
const FRONT_X = 21.2;

/** 三款的进深（Blender 里 D 的一半是前脸到中心的距离） */
const HOUSE_D: Record<HouseVariant, number> = { small: 4.4, big: 5.2, shop: 6.0 };

/**
 * 街段编排。
 *
 * <p>不是等距撒点。等距撒点必然得到"一排等宽等深的房子"——这就是"货架感"的来源。
 * 真实的老街是几种东西交替：<b>联排铺面</b>（贴着街、深檐廊、共用山墙）、
 * <b>独门独院的门面</b>（墙退半步）、<b>院墙 + 门楼</b>（房子退到墙后）。
 * 三种交替出现，街立面才有节奏。</p>
 *
 * <p>{@code alley} 不是房子，是<b>一条让出来的缝</b>：两栋铺面的山墙本来就整进深落地，
 * 中间留 2.8m，巷子的两侧墙天然就有了。俯视时这一条暗缝是"城镇"与"一排房子"
 * 的分界线（§16.8 第 1 条）。</p>
 */
const STREET_PLOTS: Array<{
  z: number;
  kind: 'shop' | 'front' | 'court' | 'alley';
  variant?: 'small' | 'big';
  setback?: number;
  halfZ?: number;
}> = [
  { z: -41.0, kind: 'court', variant: 'big', setback: 3.6, halfZ: 4.6 },
  { z: -32.0, kind: 'front', variant: 'small' },
  { z: -24.0, kind: 'shop' },
  { z: -19.2, kind: 'alley' },
  { z: -14.4, kind: 'shop' },
  // 桥头广场：z -11…9 留空（桥从 z=2 进来，广场不能被房子压住）
  { z: 12.4, kind: 'shop' },
  { z: 17.2, kind: 'alley' },
  { z: 22.0, kind: 'shop' },
  { z: 29.6, kind: 'front', variant: 'small' },
  { z: 37.0, kind: 'court', variant: 'small', setback: 3.0, halfZ: 4.0 },
];

/** 巷宽。2.8m 是"两人抬轿能过"的尺度；再窄俯视就看不见，再宽就不是巷了。 */
const ALLEY_W = 2.8;
/** 巷深：与铺面进深对齐（6.0）再留 0.2 的照壁厚度 */
const ALLEY_D = 6.2;

export interface AlleySpot {
  z: number;
  w: number;
}

/** 巷口落点（从 STREET_PLOTS 里挑 alley 项，房子数量与巷子数量由同一张表决定） */
export function alleySpots(): AlleySpot[] {
  return STREET_PLOTS.filter((p) => p.kind === 'alley').map((p) => ({ z: p.z, w: ALLEY_W }));
}

/**
 * 西岸小村（x, z, 缩放）。
 *
 * <p>提到模块级是为了让地被（{@link blockedByPaving}）也能读到：房子脚下不能长草，
 * 否则草簇会从墙里穿出来。两处共用同一份坐标，改一处不会漏另一处。</p>
 */
const WEST_VILLAGE: Array<[number, number, number]> = [
  [-21.5, 3.0, 0.9],
  [-25.8, 7.5, 1.0],
  [-22.8, 12.4, 0.86],
  [-28.5, 1.6, 0.94],
];

/**
 * 民居落点。
 *
 * <p>面宽沿 Z、进深沿 X（正面朝 -X）。临街面统一对齐到 {@link FRONT_X}：
 * 中心 x = FRONT_X + 进深/2（院落再加退让）。旧写法直接给"STREET_X + 6.2 + 随机"，
 * 于是三款不同进深的房子前脸各差半米，远看是一排参差的白墙。</p>
 */
export function houseSpots(): HouseSpot[] {
  let seed = 77881122;
  const rand = () => {
    seed = (seed * 1103515245 + 12345) % 2147483648;
    return seed / 2147483648;
  };

  const out: HouseSpot[] = [];
  for (const plot of STREET_PLOTS) {
    if (plot.kind === 'alley') continue; // 巷子不是房子，几何在 buildAlleys 里单独铺
    const variant: HouseVariant = plot.kind === 'shop' ? 'shop' : (plot.variant ?? 'small');
    const depth = HOUSE_D[variant];
    const setback = plot.setback ?? 0;
    const x = FRONT_X + setback + depth / 2;
    const z = plot.z + (plot.kind === 'shop' ? 0 : (rand() - 0.5) * 0.8);
    out.push({
      x,
      z,
      y: heightAt(x, z),
      // 铺面是联排的，缩放会让共用的山墙错开；另两款允许 8% 的差异
      scale: plot.kind === 'shop' ? 1 : 0.94 + rand() * 0.12,
      rotY: -Math.PI / 2,
      variant,
      ...(plot.kind === 'court' ? { courtyard: { halfZ: plot.halfZ ?? 4.6 } } : {}),
    });
  }

  /*
   * 西岸小村：四栋零零散散，正面朝东（对着河）。
   *
   * <p>没有它，全景左半边只有草地——"河这边一座城、河那边一片空草地"，
   * 构图是偏的。房子必须<b>散</b>：间距 6~9m 不等、朝向各偏几度，聚成一排
   * 就又变成东岸那条货架街了。</p>
   */
  for (const [x, z, sc] of WEST_VILLAGE) {
    out.push({
      x,
      z,
      y: heightAt(x, z),
      scale: sc,
      rotY: Math.PI / 2 + (rand() - 0.5) * 0.26,
      variant: rand() < 0.5 ? 'big' : 'small',
    });
  }
  return out;
}

/**
 * 屋面色差。
 *
 * <p>整片屋顶同色同亮是"一整块"的第二个成因（第一个是没有缝）。真实的江南屋顶是
 * 几十年里东补一块、西换一垄积起来的：青瓦会从青灰偏到暖灰，长苔的地方发绿。
 * 用 {@code InstancedMesh.setColorAt} 逐实例乘一个色偏，<b>不加 drawcall</b>——
 * 比给每栋换材质便宜得多。</p>
 *
 * <p>幅度必须小（0.82~1.12）：再大就成了拼布被子。</p>
 */
const TILE_TINTS = [0xffffff, 0xf6f2ea, 0xe2e6e2, 0xece6da, 0xd2d6d2, 0xf0ece4];

function tileTint(seed: number): THREE.Color {
  // 线性空间乘数：这里给的是 sRGB 十六进制，THREE.Color 会转成线性
  // seed 可能为负（sp.z 有负数），取模必须落在正区间，否则取到 undefined
  const i = ((seed % TILE_TINTS.length) + TILE_TINTS.length) % TILE_TINTS.length;
  return new THREE.Color(TILE_TINTS[i]);
}

/**
 * 民居也走 InstancedMesh。
 *
 * <p>一栋房在 GLB 里拆成 6 个 primitive（白墙/背墙/瓦/木/石/窗纸各一），
 * 9 栋各建一个 Group 就是 54 个 drawcall；按 (变体, 部件) 归并后只剩 12 个。</p>
 */
function buildHousesFromGlb(group: THREE.Group, assets: SceneAssets, spots: HouseSpot[]): void {
  const byVariant = new Map<HouseVariant, HouseSpot[]>();
  for (const sp of spots) {
    const list = byVariant.get(sp.variant) ?? [];
    list.push(sp);
    byVariant.set(sp.variant, list);
  }

  const m = new THREE.Matrix4();
  const q = new THREE.Quaternion();
  const pos = new THREE.Vector3();
  const scl = new THREE.Vector3();
  const up = new THREE.Vector3(0, 1, 0);

  for (const [variant, list] of byVariant) {
    const parts = assets.houses[variant];
    if (!parts.length || !list.length) continue;
    for (const part of parts) {
      const im = new THREE.InstancedMesh(part.geometry, part.material, list.length);
      im.castShadow = true;
      im.receiveShadow = true;
      im.frustumCulled = false;
      const tintTiles = /tile/i.test(part.material.name);
      if (isPlaster(part.material.name)) applyPlasterMap(part.material);
      list.forEach((sp, i) => {
        q.setFromAxisAngle(up, sp.rotY);
        pos.set(sp.x, sp.y, sp.z);
        scl.set(sp.scale, sp.scale, sp.scale);
        m.compose(pos, q, scl);
        im.setMatrixAt(i, m);
        // 只给瓦面做色差：白墙也跟着变就成了"九种白"
        // TINT-EXPERIMENT
        if (tintTiles) im.setColorAt(i, tileTint(i * 3 + Math.round(sp.z)));
      });
      im.instanceMatrix.needsUpdate = true;
      if (im.instanceColor) im.instanceColor.needsUpdate = true;
      im.name = `houses-${variant}`;
      group.add(im);
    }
  }
}

function buildHouses(b: Builder): void {
  const rand = (() => {
    let seed = 99001122;
    return () => {
      seed = (seed * 1103515245 + 12345) % 2147483648;
      return seed / 2147483648;
    };
  })();

  for (const s of houseSpots()) {
    const y = s.y;
    const w = 4.2 + rand() * 1.6;
    const d = 4.6 + rand() * 1.8;
    const h = 3.0 + rand() * 0.9;

    // 台基：房子落在坡地上时用它吃掉高差，避免一侧悬空
    b.box('stoneDark', w + 0.5, 1.8, d + 0.5, s.x, y - 0.55, s.z);
    b.box('plaster', w, h, d, s.x, y + h / 2, s.z);
    // 窗
    b.box('window', 0.1, 0.7, 1.0, s.x + w / 2, y + h * 0.62, s.z, {});
    b.box('window', 0.1, 0.7, 1.0, s.x - w / 2, y + h * 0.62, s.z, {});
    // 人字顶：两块斜板对扣
    const slope = 0.52;
    const roofLen = (w / 2) / Math.cos(slope) + 0.5;
    b.box('tile', roofLen, 0.24, d + 0.9, s.x - w / 4 - 0.1, y + h + 0.5, s.z, { z: slope });
    b.box('tile', roofLen, 0.24, d + 0.9, s.x + w / 4 + 0.1, y + h + 0.5, s.z, { z: -slope });
    // 屋脊
    b.box('tile', 0.4, 0.3, d + 0.95, s.x, y + h + 1.02, s.z);
  }
}

/* ------------------------------------------------------------------ 出口 */

/**
 * 牌坊中心 X。
 *
 * <p>旧值 16.5 时西侧柱脚落在 <b>桥面上</b>：牌坊跨度 7.5m，柱位 12.75 / 20.25，
 * 而石拱桥的实体是 x -14…14（桥必须够到东岸，河心在 4.2、河槽半宽 9），
 * x=12.75 处桥面已经抬到 2.03m，而广场地面只有 1.15——柱脚埋进桥面 0.88m，
 * 夹杆石整个看不见。往东挪到 17.9 后柱位 14.15 / 21.65，刚好跨在桥头广场上，
 * 东腿落在街沿（街面 13.5…20.5），也是真实牌坊"骑街而立"的摆法。</p>
 */
const GATE_X = 17.9;

/** 实例摆放：位置 + 朝向 + 非等比缩放（城墙段要沿 Z 缩放到实际段长） */
interface Placement {
  x: number;
  y: number;
  z: number;
  rotY: number;
  /** 绕 X 轴的俯仰（弧度）。城墙顺坡用：墙顶连成斜线，而不是每 8m 掉一级台阶 */
  rx?: number;
  sx?: number;
  sy?: number;
  sz?: number;
}

/**
 * 把一组 (几何, 材质) 按同一批变换铺成 InstancedMesh。
 *
 * <p>树/民居/灯笼各自写过一份几乎一样的循环，城墙与船又要用——这里抽一个。</p>
 */
function instanceParts(
  group: THREE.Group,
  parts: TemplateParts[],
  list: Placement[],
  name: string,
): void {
  if (!parts.length || !list.length) return;
  const m = new THREE.Matrix4();
  const q = new THREE.Quaternion();
  const e = new THREE.Euler();
  const pos = new THREE.Vector3();
  const scl = new THREE.Vector3();
  const up = new THREE.Vector3(0, 1, 0);
  for (const part of parts) {
    const im = new THREE.InstancedMesh(part.geometry, part.material, list.length);
    im.castShadow = true;
    im.receiveShadow = true;
    im.frustumCulled = false; // 实例横跨几十米，整体包围盒没意义
    list.forEach((p, i) => {
      if (p.rx) {
        // YXZ：先绕自身 X 俯仰，再绕 Y 转向（城墙只要俯仰）
        e.set(p.rx, p.rotY, 0, 'YXZ');
        q.setFromEuler(e);
      } else {
        q.setFromAxisAngle(up, p.rotY);
      }
      pos.set(p.x, p.y, p.z);
      scl.set(p.sx ?? 1, p.sy ?? 1, p.sz ?? 1);
      m.compose(pos, q, scl);
      im.setMatrixAt(i, m);
    });
    im.instanceMatrix.needsUpdate = true;
    im.name = name;
    group.add(im);
  }
}

/**
 * 按主轴重算"按米"的盒式投影 UV（局部坐标）。
 *
 * <p>与 `wall.py::box_uv` 同一套规则，但**在运行时算**：GLB 里那套 UV 在
 * three 端读出来是 0..1（每段一张图），渲染出来一块砖 1.3m、一层 1.1m，
 * 正好是"贴图被铺满一整段"的样子。而这种错<em>看不出来是错</em>——墙上确实有
 * 砖缝，只是尺度大了 2 倍、看着像砌块墙。所以不赌导出链路，直接按几何重算。</p>
 *
 * <p>轴向与 glTF 一致：段长沿 Z、墙高沿 Y。`scale` 是"一个 UV 单位 = 多少米"，
 * 必须与 `textures.ts::BRICK_METERS` 相等。</p>
 */
function boxUvLocal(g: THREE.BufferGeometry, scale: number): void {
  const pos = g.attributes.position;
  const nor = g.attributes.normal;
  if (!pos || !nor) return;
  const uv = new Float32Array(pos.count * 2);
  for (let i = 0; i < pos.count; i += 1) {
    const ax = Math.abs(nor.getX(i));
    const ay = Math.abs(nor.getY(i));
    const az = Math.abs(nor.getZ(i));
    let u: number;
    let v: number;
    if (ax >= ay && ax >= az) {
      // 内/外墙：段长 × 墙高
      u = pos.getZ(i);
      v = pos.getY(i);
    } else if (ay >= az) {
      // 顶面/底面：进深 × 段长
      u = pos.getX(i);
      v = pos.getZ(i);
    } else {
      // 端面：进深 × 墙高
      u = pos.getX(i);
      v = pos.getY(i);
    }
    uv[i * 2] = u / scale;
    uv[i * 2 + 1] = v / scale;
  }
  g.setAttribute('uv', new THREE.BufferAttribute(uv, 2));
}

export function buildSettlement(assets?: SceneAssets): THREE.Group {
  const b = new Builder();
  buildBridge(b);

  const spots = treeSpots();
  // 树必须在上面的 builder 出组之前决定走哪条路径：占位件是走 Builder 合并的
  const hasTrees = !!assets && (['round', 'tall', 'willow'] as TreeVariant[]).some(
    (k) => (assets.trees[k] ?? []).length > 0,
  );
  if (!hasTrees) buildTreesPlaceholder(b, spots);

  if (!assets?.gate) buildGate(b, GATE_X, BRIDGE_Z);

  const hasDock = !!assets?.dock;
  if (!hasDock) buildDock(b);
  // 上岸处不做任何石构件（S15 结论）：这段岸坡与码头面天然齐平，
  // 草地直接包住甲板边缘就是渡口的原样——强加的台阶/旗石读作"混凝土板"。

  const boats = boatSpots();
  const hasBoat = !!assets && assets.boat.length > 0;
  if (!hasBoat) for (const p of boats) buildBoat(b, p);

  buildFields(b);
  buildWestPath(b);
  buildWestWharf(b);
  buildStreet(b);
  const houseList = houseSpots();
  buildEmbankment(b);
  buildCourtWalls(b, houseList);
  buildAlleys(b, alleySpots());
  buildBackWalls(b);
  buildStreetProps(b, houseList);
  const hasLanterns = !!assets && assets.lantern.length > 0;
  if (!hasLanterns) buildLanterns(b);

  const segs = wallSegments();
  const hasWall = !!assets && assets.wall.length > 0;
  if (!hasWall) buildWall(b, segs);
  // 敌台与"用 GLB 还是占位墙"无关：它是端头的体量，两条路径都要有
  buildWallBastions(b);

  const hasHouses = !!assets && (['small', 'big', 'shop'] as HouseVariant[]).some(
    (k) => (assets.houses[k] ?? []).length > 0,
  );
  if (!hasHouses) buildHouses(b);

  const group = b.build();

  // 敌台与城墙共用**同一份材质实例**。
  // 参数抄得再一样也没用：程序化材质是新建的 MeshStandardMaterial，贴图对象、
  // envMapIntensity、顶点色开关、color 的线性化路径只要差一点，接缝处就是
  // "两种灰"（实测敌台比墙暗近一半，像另砌了一座黑塔）。直接换引用最稳。
  if (assets?.wall?.length) {
    const brickMesh = group.getObjectByName('settlement-brick') as THREE.Mesh | undefined;
    const wallMat = assets.wall.find((p) => /^wallBrick$/i.test(p.material?.name ?? ''))?.material;
    if (brickMesh && wallMat) brickMesh.material = wallMat;
  }

  if (assets?.gate) {
    // 牌坊换真模型：GLB 在 Blender 里就是"沿 X 展开、正面朝 +Z"，
    // 正好横跨沿 Z 的街道，不需要额外旋转。这里只负责贴地。
    const gate = assets.gate.clone(true);
    gate.position.set(GATE_X, heightAt(GATE_X, BRIDGE_Z), BRIDGE_Z);
    gate.traverse((o) => {
      const m = o as THREE.Mesh;
      if (m.isMesh) {
        m.castShadow = true;
        m.receiveShadow = true;
      }
    });
    // 匾额金字：GLB 只带深漆地子，字是挂上去的 canvas 面片（见 plaque.ts）。
    // 注意必须放在 traverse 之后——否则面片会被统一打开 castShadow，
    // 透明镂空的字影是一团噪点。
    attachPlaqueSigns(gate);
    group.add(gate);
  }

  if (hasDock && assets?.dock) {
    // 码头 GLB 的原点在世界坐标的"水面中心"，长边沿 X，正好对上岸的方向。
    // 不 clone：只有一处，直接用（GLB scene 只能挂在场景树的一个位置上）。
    const dock = assets.dock;
    dock.position.set(dockX(), WATER_LEVEL, DOCK_Z);
    dock.traverse((o) => {
      const m = o as THREE.Mesh;
      if (m.isMesh) {
        m.castShadow = true;
        m.receiveShadow = true;
      }
    });
    group.add(dock);
  }

  if (hasBoat && assets) {
    instanceParts(
      group,
      assets.boat,
      boats.map((p) => ({ x: p.x, y: WATER_LEVEL + 0.02, z: p.z, rotY: p.rotY })),
      'boats',
    );
  }

  if (hasWall && assets) {
    // 砖纹只在墙身/基座上：马道铺面（wallDeck）与压顶石（wallCap）是打磨过的
    // 长条石，铺一层砌缝反而变成"墙上贴了砖纸"。
    for (const part of assets.wall) {
      const std = part.material as THREE.MeshStandardMaterial;
      if (!/^wall(Brick|BrickUp|Base)$/i.test(std.name ?? '')) continue;
      applyBrickMap(std);
      // GLB 里那套 UV 在 three 端读出来是"每段一张图"（0..1），渲染出来一块砖
      // 1.3m、一层 1.1m——墙上确实有砖缝，只是尺度大 2 倍，看着像砌块墙。
      // 不赌导出链路，按几何重算（与 wall.py 同一套规则）。
      boxUvLocal(part.geometry, BRICK_METERS);
    }
    instanceParts(
      group,
      assets.wall,
      segs.map((sg) => ({
        x: WALL_X,
        y: sg.y,
        z: sg.z,
        rotY: 0,
        rx: sg.pitch,
        // ① 俯仰让段的水平投影缩短 cos(θ)（12° 就是 8cm/端），不补偿段间会开裂；
        // ② 再多给 6cm：段长正好 8m 且 y 相同的两端面完全共面，会在边界上闪
        //    （z-fighting）。溢出 6cm 让上段压住下段，UV 只错 1.5%，
        //    远小于一块砖的宽度，接缝看起来像正常的错缝砌筑。
        sz: (sg.len + 0.06) / (WALL_SEG * Math.cos(sg.pitch)),
      })),
      'wall',
    );
  }

  buildReeds(group);
  buildGroundCover(group, spots);
  if (hasTrees && assets) buildTreesFromGlb(group, assets, spots);
  if (hasHouses && assets) buildHousesFromGlb(group, assets, houseList);
  if (hasLanterns && assets) buildLanternsFromGlb(group, assets);

  return group;
}
