import * as THREE from 'three';
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js';
import type { HouseVariant, SceneAssets, TemplateParts, TreeVariant } from './loader';
import { heightAt, WATER_LEVEL } from './terrain';
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
  | 'wood'
  | 'woodDark'
  | 'tile'
  | 'plaster'
  | 'red'
  | 'gold'
  | 'glow'
  | 'window'
  | 'foliage';

const MAT_DEF: Record<MatKey, THREE.MeshStandardMaterialParameters> = {
  stone: { color: PALETTE.stone, roughness: 0.92, metalness: 0.02 },
  stoneDark: { color: PALETTE.stoneDark, roughness: 0.95 },
  wood: { color: PALETTE.wood, roughness: 0.88 },
  woodDark: { color: PALETTE.woodDark, roughness: 0.9 },
  tile: { color: PALETTE.tile, roughness: 0.62 },
  plaster: { color: 0xd9d2c2, roughness: 0.96 },
  red: { color: 0x8f3a2c, roughness: 0.84 },
  gold: { color: PALETTE.lantern, roughness: 0.38, metalness: 0.62 },
  glow: { color: 0xffb070, emissive: 0xff6a2a, emissiveIntensity: 2.2, roughness: 0.5 },
  window: { color: 0xffcf94, emissive: 0xffa050, emissiveIntensity: 1.5, roughness: 0.6 },
  foliage: { color: 0x3d4a2a, roughness: 0.95 },
};

/** 把同材质的部件合并成一个几何：3200 行 city.ts 那种"一物一 mesh"的写法，drawcall 会失控 */
class Builder {
  private readonly parts = new Map<MatKey, THREE.BufferGeometry[]>();

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

  cyl(key: MatKey, rTop: number, rBot: number, h: number, x: number, y: number, z: number, seg = 8): void {
    const geo = new THREE.CylinderGeometry(rTop, rBot, h, seg);
    const m = new THREE.Matrix4().makeTranslation(x, y, z);
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
      const mesh = new THREE.Mesh(merged, new THREE.MeshStandardMaterial(MAT_DEF[key]));
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
const DOCK_HALF = DOCK_LEN / 2;

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
 * 上岸台阶。
 *
 * <p>这一截**必须按地形实时算**，不能写死高度。码头平台面固定 0.95，而岸坡在
 * 这段是 0.6~0.9 并沿 X 变化——写死三级"往上抬"的台阶（旧写法
 * {@code DOCK_TOP + 0.17 + i*0.34}）会一路抬到 1.97m，比岸高 1.3m，
 * 出图是从码头爬上一堵墙。</p>
 *
 * <p>方向也要注意：码头是西端搭岸、东端伸进深水，台阶只能往<b>-X</b> 铺。
 * 往 +X 铺的话三级台全立在河心里。</p>
 */
function buildApron(b: Builder): void {
  const x0 = dockX() - DOCK_HALF; // 平台西沿（靠岸这一侧）
  const steps = 3;
  const depth = 0.7;
  for (let i = 0; i < steps; i += 1) {
    const xc = x0 - depth * (i + 0.5);
    const ground = heightAt(xc, DOCK_Z);
    // 台阶顶面从平台面降到地面；i 越大越靠岸、越低
    const top = DOCK_TOP + (Math.min(ground, DOCK_TOP) - DOCK_TOP) * ((i + 1) / steps);
    const h = Math.max(0.34, top - ground + 0.6); // 再往下吃 0.6m，坡地上不悬空
    b.box('stoneDark', depth + 0.02, h, 3.2, xc, top - h / 2, DOCK_Z);
  }
}

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
  for (const row of rows) {
    for (let z = -22; z <= 26; z += 4.4) {
      if (Math.abs(z - BRIDGE_Z) < 5) continue;
      out.push({ x: row.x, z, y: heightAt(row.x, z), rotY: row.rotY });
    }
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
  let seed = 5150;
  const rand = () => {
    seed = (seed * 1103515245 + 12345) % 2147483648;
    return seed / 2147483648;
  };
  for (let z = -34; z < 30; z += 3.6) {
    for (const side of [-1, 1]) {
      // 左右两排各让 0.9m 的错位：整条街的石板不可能齐缝
      const zc = z + side * 0.9 + (rand() - 0.5) * 0.12;
      const y = heightAt(STREET_X, zc);
      const sink = (rand() - 0.5) * 0.03; // 石板厚薄不一是最便宜的"手工感"
      b.box('stoneDark', 3.42 - rand() * 0.1, 0.24, 3.5, STREET_X + side * 1.76, y + 0.05 + sink, zc);
    }
  }
}

/* ------------------------------------------------------------------ 城墙 */

const WALL_X = 31.5;
/** GLB 标准段长度（沿 Z）。运行时按实际段长沿 Z 缩放，把区间精确填满 */
const WALL_SEG = 8;

/** 城墙只修两段，中间让给桥头广场（真实城郭也是这样——南门进、北门出） */
const WALL_RUNS: Array<[number, number]> = [
  [-52, -8],
  [12, 46],
];

export interface WallSeg {
  z: number;
  y: number;
  /** 本段沿 Z 的实际长度（≈8m，因要整除区间而略有变化） */
  len: number;
}

/**
 * 城墙分段落点。
 *
 * <p>为什么是"段"而不是"一道墙"：城墙必须顺着地形起伏，而地形在这 44m 跨度里
 * 有近 9m 高差。<b>每段单独取地面高度</b>，段与段之间的高差就形成阶梯状下跌——
 * 那正是山城城墙的真实形态。整道墙只取一次高度（旧写法）的后果是一排悬空方块。</p>
 *
 * <p>段数由区间长度除以 8 四舍五入，再把区间均分——这样区间一定被填满，
 * 代价是段长在 7~9m 之间浮动，靠缩放吸收（8m 段缩 12% 以内，垛口宽窄看不出来）。</p>
 */
export function wallSegments(): WallSeg[] {
  const out: WallSeg[] = [];
  for (const [z0, z1] of WALL_RUNS) {
    const span = z1 - z0;
    const n = Math.max(1, Math.round(span / WALL_SEG));
    const len = span / n;
    for (let i = 0; i < n; i += 1) {
      const zc = z0 + len * (i + 0.5);
      // 取段内 5 点平均：只看中心点的话，8m 段在坡上一端悬空、一端埋没
      let sum = 0;
      for (let k = 0; k < 5; k += 1) sum += heightAt(WALL_X, zc + (k - 2) * (len / 5));
      out.push({ z: zc, y: sum / 5, len });
    }
  }
  return out;
}

/** 占位城墙：分层收分 + 垛口，体量与 GLB 对齐（换件时构图不动） */
function buildWall(b: Builder, segs: WallSeg[]): void {
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

/* ------------------------------------------------------------------ 民居 */

export interface HouseSpot {
  x: number;
  z: number;
  y: number;
  scale: number;
  rotY: number;
  variant: HouseVariant;
}

/**
 * 民居落点。
 *
 * <p>用<b>固定 z 槽位</b>而不是纯随机撒点：房子正面朝 -X，此时"面宽 6.2~7.4"是沿
 * Z 排布的，而旧代码的防重叠判据只按 6.5×7 的盒子算，相邻两栋必然互相插进去
 * （出图就是一排糊在一起的屋顶）。槽距按最大面宽 + 走道给到 9m。</p>
 */
export function houseSpots(): HouseSpot[] {
  let seed = 77881122;
  const rand = () => {
    seed = (seed * 1103515245 + 12345) % 2147483648;
    return seed / 2147483648;
  };

  const out: HouseSpot[] = [];
  for (let z = -38; z <= 38; z += 9) {
    if (Math.abs(z - BRIDGE_Z) < 8) continue; // 桥头广场留空
    const zz = z + (rand() - 0.5) * 1.2;
    const x = STREET_X + 6.2 + rand() * 1.1;
    out.push({
      x,
      z: zz,
      y: heightAt(x, zz),
      scale: 0.92 + rand() * 0.18,
      // 正面朝 -X（街道在西侧）；再抖一点角度，一排房完全对齐最假
      rotY: -Math.PI / 2 + (rand() - 0.5) * 0.1,
      variant: rand() < 0.4 ? 'big' : 'small',
    });
  }
  return out;
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
      list.forEach((sp, i) => {
        q.setFromAxisAngle(up, sp.rotY);
        pos.set(sp.x, sp.y, sp.z);
        scl.set(sp.scale, sp.scale, sp.scale);
        m.compose(pos, q, scl);
        im.setMatrixAt(i, m);
      });
      im.instanceMatrix.needsUpdate = true;
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

const GATE_X = 16.5;

/** 实例摆放：位置 + 朝向 + 非等比缩放（城墙段要沿 Z 缩放到实际段长） */
interface Placement {
  x: number;
  y: number;
  z: number;
  rotY: number;
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
  const pos = new THREE.Vector3();
  const scl = new THREE.Vector3();
  const up = new THREE.Vector3(0, 1, 0);
  for (const part of parts) {
    const im = new THREE.InstancedMesh(part.geometry, part.material, list.length);
    im.castShadow = true;
    im.receiveShadow = true;
    im.frustumCulled = false; // 实例横跨几十米，整体包围盒没意义
    list.forEach((p, i) => {
      q.setFromAxisAngle(up, p.rotY);
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
  // 台阶永远按地形现算——它跟岸边高度绑定，跟用哪版码头无关
  buildApron(b);

  const boats = boatSpots();
  const hasBoat = !!assets && assets.boat.length > 0;
  if (!hasBoat) for (const p of boats) buildBoat(b, p);

  buildStreet(b);
  const hasLanterns = !!assets && assets.lantern.length > 0;
  if (!hasLanterns) buildLanterns(b);

  const segs = wallSegments();
  const hasWall = !!assets && assets.wall.length > 0;
  if (!hasWall) buildWall(b, segs);

  const hasHouses = !!assets && (['small', 'big'] as HouseVariant[]).some(
    (k) => (assets.houses[k] ?? []).length > 0,
  );
  if (!hasHouses) buildHouses(b);

  const group = b.build();

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
    instanceParts(
      group,
      assets.wall,
      segs.map((sg) => ({ x: WALL_X, y: sg.y, z: sg.z, rotY: 0, sz: sg.len / WALL_SEG })),
      'wall',
    );
  }

  if (hasTrees && assets) buildTreesFromGlb(group, assets, spots);
  if (hasHouses && assets) buildHousesFromGlb(group, assets, houseSpots());
  if (hasLanterns && assets) buildLanternsFromGlb(group, assets);

  return group;
}
