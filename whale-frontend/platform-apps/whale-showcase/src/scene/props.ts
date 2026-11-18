import * as THREE from 'three';
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js';
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
const DOCK_TOP = WATER_LEVEL + 0.95;

function dockX(): number {
  return -10.5;
}

function buildDock(b: Builder): void {
  const x = dockX();
  // 平台
  b.box('wood', 8.6, 0.36, 5.6, x, DOCK_TOP - 0.18, DOCK_Z);
  // 桩
  for (const dx of [-3.6, 0, 3.6]) {
    for (const dz of [-2.2, 2.2]) {
      b.cyl('woodDark', 0.22, 0.26, 3.4, x + dx, DOCK_TOP - 1.9, DOCK_Z + dz, 6);
    }
  }
  // 岸上引道（三级台阶）
  for (let i = 0; i < 3; i += 1) {
    b.box('stone', 3.2, 0.34, 1.4, x + 5.4 + i * 0.2, DOCK_TOP + 0.17 + i * 0.34, DOCK_Z, {
      z: -0.06 * i,
    });
  }
}

function buildBoat(b: Builder, x: number, z: number, rotY: number): void {
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
    new THREE.Vector3(x, WATER_LEVEL + 0.02, z),
    new THREE.Quaternion().setFromEuler(new THREE.Euler(0, rotY, 0)),
    new THREE.Vector3(1.0, 1.0, 2.8),
  );
  b.add('woodDark', hull, m);

  // 船舷与横档
  const ringM = new THREE.Matrix4().compose(
    new THREE.Vector3(x, WATER_LEVEL + 0.92, z),
    new THREE.Quaternion().setFromEuler(new THREE.Euler(0, rotY, 0)),
    new THREE.Vector3(1.04, 1, 2.8),
  );
  b.add('wood', new THREE.TorusGeometry(0.96, 0.07, 5, 18), ringM);

  // 桅杆
  b.cyl('wood', 0.07, 0.09, 3.2, x, WATER_LEVEL + 2.2, z, 6);
}

/* ------------------------------------------------------------------ 树 */

function buildTrees(b: Builder): void {
  let seed = 20261118;
  const rand = () => {
    seed = (seed * 1664525 + 1013904223) % 4294967296;
    return seed / 4294967296;
  };

  let placed = 0;
  let guard = 0;
  while (placed < 52 && guard < 800) {
    guard += 1;
    const x = (rand() - 0.5) * 150;
    const z = (rand() - 0.5) * 240;
    const d = Math.abs(x - Math.sin(z * 0.013) * 6.5 - Math.sin(z * 0.0047 + 1.3) * 4.2);
    if (d < 12) continue; // 河道里不长树
    if (Math.abs(z - BRIDGE_Z) < 24 && d < 26) continue; // 桥头广场留白
    if (x > 12 && x < 34 && z > -46 && z < 8) continue; // 聚落区留给房子

    const y = heightAt(x, z);
    if (y < WATER_LEVEL + 0.4) continue;

    const s = 0.78 + rand() * 0.6;
    b.cyl('woodDark', 0.22 * s, 0.38 * s, 3.0 * s, x, y + 1.5 * s, z, 6);
    b.add(
      'foliage',
      new THREE.IcosahedronGeometry(2.05 * s, 0),
      new THREE.Matrix4().makeTranslation(x, y + 4.3 * s, z),
    );
    b.add(
      'foliage',
      new THREE.IcosahedronGeometry(1.35 * s, 0),
      new THREE.Matrix4().makeTranslation(x + 0.8 * s, y + 5.4 * s, z - 0.6 * s),
    );
    placed += 1;
  }
}

/* ------------------------------------------------------------------ 灯笼 */

function buildLanterns(b: Builder): void {
  const rows = [
    { x: 15.6, z0: -20, z1: 24 },
    { x: 22.4, z0: -20, z1: 24 },
  ];
  for (const row of rows) {
    for (let z = row.z0; z <= row.z1; z += 4.2) {
      if (Math.abs(z - BRIDGE_Z) < 5) continue;
      const y = heightAt(row.x, z);
      b.cyl('woodDark', 0.055, 0.065, 2.6, row.x, y + 1.3, z, 6);
      b.box('woodDark', 0.6, 0.07, 0.07, row.x + 0.22, y + 2.58, z); // 挑臂
      b.cyl('glow', 0.21, 0.21, 0.56, row.x + 0.46, y + 2.26, z, 8);
      b.cyl('tile', 0.27, 0.27, 0.06, row.x + 0.46, y + 2.56, z, 8);
    }
  }
}

/* ------------------------------------------------------------------ 街面 */

/** 石板街：房子不能直接坐草地上。分段铺，段间高差当"分台"处理 */
/** 主街中心线：民居一律放在 STREET_X + 5 以东，街面本身不许压房子 */
const STREET_X = 17;

function buildStreet(b: Builder): void {
  for (let z = -34; z < 30; z += 3.6) {
    const y = heightAt(STREET_X, z);
    b.box('stoneDark', 7.0, 0.26, 3.7, STREET_X, y + 0.06, z);
  }
}

/* ------------------------------------------------------------------ 城墙 */

function buildWall(b: Builder): void {
  const x = 31.5;
  const segments = [
    { z0: -52, z1: -8 },
    { z0: 12, z1: 46 },
  ];
  for (const seg of segments) {
    /*
     * 必须按 4m 分段取地面高度。整段只取一次 heightAt 的话（旧写法），墙身在
     * 44m 跨度里既不随地形起伏也不随地形下沉——北端地形抬升 9m，墙就整段飘在
     * 半空；远看是一排悬空的方块，很像阶梯。
     */
    const step = 4;
    for (let z = seg.z0; z < seg.z1; z += step) {
      const zc = z + step / 2;
      const y = heightAt(x, zc);
      const bottom = y - 1.4; // 埋深 1.4m，坡地上不会露出墙根
      b.box('stoneDark', 1.3, y + 4.0 - bottom, step + 0.02, x, (y + 4.0 + bottom) / 2, zc);
      // 垛口与墙体咬合（同一段 y），避免出现"墙顶飘着一排小方块"
      b.box('stone', 1.5, 0.9, 1.1, x, y + 4.45, zc);
    }
  }
}

/* ------------------------------------------------------------------ 民居 */

function buildHouses(b: Builder): void {
  let seed = 77881122;
  const rand = () => {
    seed = (seed * 1103515245 + 12345) % 2147483648;
    return seed / 2147483648;
  };

  const spots: Array<{ x: number; z: number }> = [];
  let guard = 0;
  while (spots.length < 9 && guard < 400) {
    guard += 1;
    // 只落在街面以东：以前 x 从 15.5 起，房子会直接盖在街上，
    // 街景机位就卡在墙体内部（实测整幅图被一堵白墙糊住）
    const x = STREET_X + 5.4 + rand() * 5.4;
    const z = -26 + rand() * 46;
    if (Math.abs(z - BRIDGE_Z) < 7) continue;
    if (spots.some((s) => Math.abs(s.x - x) < 6.5 && Math.abs(s.z - z) < 7)) continue;
    spots.push({ x, z });
  }

  for (const s of spots) {
    const y = heightAt(s.x, s.z);
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

export function buildSettlement(): THREE.Group {
  const b = new Builder();
  buildBridge(b);
  buildGate(b, 16.5, BRIDGE_Z);
  buildDock(b);
  buildBoat(b, dockX() - 3.4, DOCK_Z - 4.6, 0.35);
  buildBoat(b, dockX() - 1.2, DOCK_Z - 9.2, -0.2);
  buildTrees(b);
  buildStreet(b);
  buildLanterns(b);
  buildWall(b);
  buildHouses(b);
  return b.build();
}
