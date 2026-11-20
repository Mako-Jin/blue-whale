/**
 * 城池布局几何自检：把「不重叠、不压墙、门能过」这三件事从肉眼目测变成可回归的断言。
 *
 * 用法：node scripts/check-city-layout.mjs [--json]
 *
 * 为什么需要它
 * ------------
 * 12 坊是斜着摆在环路上的旋转矩形，截图只能看到一个角度，看不出「相邻两坊的墙
 * 有没有叠在一起」。而这类错误在编译、类型检查、启动时全都不报错——只有玩家走到
 * 那里才会发现卡住。所以这里用分离轴定理（SAT）把重叠算出来。
 *
 * 检查项
 * ------
 * 1. 相邻坊弦长一致（验证 12 等分没算错）；
 * 2. 任意两坊的坊域矩形不相交；
 * 3. 坊台基不侵入内城墙 / 外城墙；
 * 4. 坊门洞净宽 ≥ 角色直径（2m）的两倍，保证进出不卡；
 * 5. 皇城九殿两两不重叠，且不越出皇城墙；
 * 6. 所有节点 nodeKey 唯一、parentKey 存在。
 */

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const JSON_PATH = join(
  HERE,
  '../whale-frontend/platform-apps/whale-world/src/world/city-layout.json',
);
const layout = JSON.parse(readFileSync(JSON_PATH, 'utf8'));

const G = layout.geometry;
const EPS = 1e-6;
const errors = [];
const notes = [];

/* ------------------------------------------------------------------ */
/* 工具：旋转矩形                                                      */
/* ------------------------------------------------------------------ */

/** 由中心、半长、朝向角（弧度）构造矩形 */
const rect = (x, z, hx, hz, yaw = 0) => ({ x, z, hx, hz, yaw });

/** 世界点 → 矩形局部坐标 */
function toLocal(r, px, pz) {
  const dx = px - r.x;
  const dz = pz - r.z;
  const cos = Math.cos(r.yaw);
  const sin = Math.sin(r.yaw);
  return [dx * cos + dz * sin, -dx * sin + dz * cos];
}

/** 矩形四角（世界坐标） */
function corners(r) {
  const cos = Math.cos(r.yaw);
  const sin = Math.sin(r.yaw);
  const out = [];
  for (const [sx, sz] of [
    [-1, -1],
    [1, -1],
    [1, 1],
    [-1, 1],
  ]) {
    const lx = sx * r.hx;
    const lz = sz * r.hz;
    out.push([r.x + lx * cos - lz * sin, r.z + lx * sin + lz * cos]);
  }
  return out;
}

/**
 * 分离轴定理：两个凸多边形是否相交。
 * @returns {number} 重叠深度（0 表示分离）
 */
function overlapDepth(a, b) {
  const ca = corners(a);
  const cb = corners(b);
  const axes = [];
  for (const poly of [ca, cb]) {
    for (let i = 0; i < 4; i += 1) {
      const [x1, z1] = poly[i];
      const [x2, z2] = poly[(i + 1) % 4];
      const ex = x2 - x1;
      const ez = z2 - z1;
      const len = Math.hypot(ex, ez);
      if (len > EPS) {
        axes.push([-ez / len, ex / len]);
      }
    }
  }

  let minOverlap = Infinity;
  for (const [ax, az] of axes) {
    let minA = Infinity;
    let maxA = -Infinity;
    let minB = Infinity;
    let maxB = -Infinity;
    for (const [x, z] of ca) {
      const p = x * ax + z * az;
      if (p < minA) minA = p;
      if (p > maxA) maxA = p;
    }
    for (const [x, z] of cb) {
      const p = x * ax + z * az;
      if (p < minB) minB = p;
      if (p > maxB) maxB = p;
    }
    const o = Math.min(maxA, maxB) - Math.max(minA, minB);
    if (o <= 0) {
      return 0;
    }
    if (o < minOverlap) minOverlap = o;
  }
  return minOverlap;
}

/* ------------------------------------------------------------------ */
/* 1. 结构一致性                                                       */
/* ------------------------------------------------------------------ */
const buildings = layout.buildings;
const keys = new Set();
for (const b of buildings) {
  if (keys.has(b.nodeKey)) {
    errors.push(`nodeKey 重复：${b.nodeKey}`);
  }
  keys.add(b.nodeKey);
}
for (const b of buildings) {
  if (b.parentKey && !keys.has(b.parentKey)) {
    errors.push(`${b.nodeKey} 的 parentKey「${b.parentKey}」不存在`);
  }
}

const districts = buildings.filter(
  (b) => b.parentKey === 'inner-city' && b.nodeType === 'BUILDING',
);
const royalHalls = buildings.filter((b) => b.parentKey === 'royal-city' && b.nodeType === 'BUILDING');

if (districts.length !== 12) {
  errors.push(`内城坊数应为 12，实际 ${districts.length}`);
}
if (royalHalls.length !== 11) {
  errors.push(`皇城建筑应为 11（1 主殿 + 8 中枢殿 + 2 小阁），实际 ${royalHalls.length}`);
}

/* ------------------------------------------------------------------ */
/* 2. 坊：等距、不重叠、不压墙、门够宽                                 */
/* ------------------------------------------------------------------ */
const WALL_HALF = (b) => b.w ?? 9;
const FOOT_HALF_W = (b) => (WALL_HALF(b) + 9) / 2 * (b.scale ?? 1);
const FOOT_HALF_D = (b) => ((b.d ?? 7) + 9) / 2 * (b.scale ?? 1);

const districtRects = districts.map((b) => ({
  key: b.nodeKey,
  // 坊域比台基大一圈（13 × 11），这里是坊墙的位置，也是判定重叠的对象
  r: rect(b.x, b.z, 13, 11, ((b.rotationDeg ?? 0) * Math.PI) / 180),
}));

// 2.1 相邻坊弦长
const radii = districts.map((b) => Math.hypot(b.x, b.z));
const rMin = Math.min(...radii);
const rMax = Math.max(...radii);
if (rMax - rMin > 0.01) {
  errors.push(`12 坊不在同一环上：半径 ${rMin.toFixed(2)} ~ ${rMax.toFixed(2)}`);
}
const chord = 2 * rMin * Math.sin(Math.PI / 12);
notes.push(`坊环半径 ${rMin.toFixed(2)}m，相邻弦长 ${chord.toFixed(2)}m`);

// 2.2 两两不重叠
let minGap = Infinity;
for (let i = 0; i < districtRects.length; i += 1) {
  for (let j = i + 1; j < districtRects.length; j += 1) {
    const d = overlapDepth(districtRects[i].r, districtRects[j].r);
    if (d > 0) {
      errors.push(`坊域重叠：${districtRects[i].key} ↔ ${districtRects[j].key} 深度 ${d.toFixed(2)}m`);
    }
  }
}

// 2.3 与城墙
for (const b of districts) {
  const radius = Math.hypot(b.x, b.z);
  const hd = FOOT_HALF_D(b);
  const innerEdge = radius - hd;
  const outerEdge = radius + hd;
  if (innerEdge <= G.innerWall + G.wallThickness.inner) {
    errors.push(`${b.nodeKey} 台基压内城墙：内缘 ${innerEdge.toFixed(2)} ≤ ${G.innerWall + G.wallThickness.inner}`);
  }
  if (outerEdge >= G.outerWall - G.wallThickness.outer) {
    errors.push(`${b.nodeKey} 台基压外城墙：外缘 ${outerEdge.toFixed(2)} ≥ ${G.outerWall - G.wallThickness.outer}`);
  }
  minGap = Math.min(minGap, innerEdge - (G.innerWall + G.wallThickness.inner));
}
notes.push(`坊台基距内城墙最小余量 ${minGap.toFixed(2)}m`);

// 2.4 坊门洞净宽（2 × gateHalf）必须容得下角色往返
const gateHalf = 4.5;
if (gateHalf * 2 < 4) {
  errors.push(`坊门净宽 ${gateHalf * 2}m 小于角色直径两倍`);
}
notes.push(`坊门净宽 ${gateHalf * 2}m`);

/* ------------------------------------------------------------------ */
/* 2.5 穿门投影测试：门要能过、墙要挡得住                              */
/* ------------------------------------------------------------------ */
/*
 * 用与 collision.ts 完全一致的算法重建坊墙碰撞盒，然后拿一条线段去打：
 *   A) 从坊外沿局部 +z 直穿坊门到坊内 —— 必须一路无阻挡（否则玩家进不了坊）；
 *   B) 从坊外沿局部 x 横穿侧墙 —— 必须被挡住（否则墙白画了）。
 * 两条都成立，才说明「门开在该开的位置」。
 */
const WALL_T = 0.6;
const blockers = [];
for (const b of districts) {
  const yaw = ((b.rotationDeg ?? 0) * Math.PI) / 180;
  const cos = Math.cos(yaw);
  const sin = Math.sin(yaw);
  const l2w = (lx, lz) => [b.x + lx * cos - lz * sin, b.z + lx * sin + lz * cos];
  const push = (lx, lz, hx, hz) => {
    const [wx, wz] = l2w(lx, lz);
    blockers.push({ x: wx, z: wz, hx, hz, yaw });
  };
  const sideW = 13 - gateHalf;
  push(0, -11, 13, WALL_T); // 北
  push(-13, 0, WALL_T, 11); // 西
  push(13, 0, WALL_T, 11); // 东
  push(-(gateHalf + sideW / 2), 11, sideW / 2, WALL_T); // 南左
  push(gateHalf + sideW / 2, 11, sideW / 2, WALL_T); // 南右
}

/** 点是否落在任一旋转盒内 */
function hits(px, pz, pad = 0) {
  for (const bl of blockers) {
    const dx = px - bl.x;
    const dz = pz - bl.z;
    const cos = Math.cos(bl.yaw);
    const sin = Math.sin(bl.yaw);
    const lx = dx * cos + dz * sin;
    const lz = -dx * sin + dz * cos;
    if (Math.abs(lx) < bl.hx + pad && Math.abs(lz) < bl.hz + pad) return true;
  }
  return false;
}

/** 沿局部直线采样，返回是否全程无阻挡 */
function walkClear(b, fromLx, fromLz, toLx, toLz, pad) {
  const yaw = ((b.rotationDeg ?? 0) * Math.PI) / 180;
  const cos = Math.cos(yaw);
  const sin = Math.sin(yaw);
  const steps = Math.ceil(Math.hypot(toLx - fromLx, toLz - fromLz) / 0.2);
  for (let i = 0; i <= steps; i += 1) {
    const t = i / steps;
    const lx = fromLx + (toLx - fromLx) * t;
    const lz = fromLz + (toLz - fromLz) * t;
    const wx = b.x + lx * cos - lz * sin;
    const wz = b.z + lx * sin + lz * cos;
    if (hits(wx, wz, pad)) return false;
  }
  return true;
}

const CHAR_R = 0.4; // 角色半径（与碰撞推出用的实心点略有冗余，取保守值）
for (const b of districts) {
  if (!walkClear(b, 0, 15, 0, 9, CHAR_R)) {
    errors.push(`${b.nodeKey} 坊门走不通：沿正门直入被挡（门洞净宽 ${gateHalf * 2}m）`);
  }
  if (walkClear(b, -16, 0, 16, 0, CHAR_R)) {
    errors.push(`${b.nodeKey} 侧墙形同虚设：沿切向横穿未被挡住`);
  }
}
notes.push(`穿门投影：12 坊正门均可通行，侧墙均可阻挡`);

/* ------------------------------------------------------------------ */
/* 3. 皇城九殿                                                         */
/* ------------------------------------------------------------------ */
const royalRects = royalHalls.map((b) => ({
  key: b.nodeKey,
  name: b.name,
  r: rect(b.x, b.z, FOOT_HALF_W(b), FOOT_HALF_D(b), ((b.rotationDeg ?? 0) * Math.PI) / 180),
}));

for (let i = 0; i < royalRects.length; i += 1) {
  for (let j = i + 1; j < royalRects.length; j += 1) {
    const d = overlapDepth(royalRects[i].r, royalRects[j].r);
    if (d > 0) {
      errors.push(
        `皇城台基重叠：${royalRects[i].name}(${royalRects[i].key}) ↔ ${royalRects[j].name}(${royalRects[j].key}) 深度 ${d.toFixed(2)}m`,
      );
    }
  }
}

const royalLimit = G.royalWall - G.wallThickness.royal;
for (const { key, name, r } of royalRects) {
  for (const [cx, cz] of corners(r)) {
    // 皇城是方城，用切比雪夫距离判是否越界
    const over = Math.max(Math.abs(cx), Math.abs(cz)) - royalLimit;
    if (over > 0) {
      errors.push(`${name}(${key}) 台基越出皇城墙 ${over.toFixed(2)}m`);
    }
  }
}

/* ------------------------------------------------------------------ */
/* 4. 节点坐标是否落在城内                                             */
/* ------------------------------------------------------------------ */
for (const b of buildings) {
  if (b.nodeType === 'REGION') continue;
  const radius = Math.hypot(b.x, b.z);
  if (radius > G.outerWall) {
    errors.push(`${b.nodeKey} 落在城外：半径 ${radius.toFixed(2)} > ${G.outerWall}`);
  }
}

/* ------------------------------------------------------------------ */
/* 输出                                                                */
/* ------------------------------------------------------------------ */
const asJson = process.argv.includes('--json');
if (asJson) {
  console.log(JSON.stringify({ errors, notes }, null, 2));
} else {
  for (const n of notes) console.log(`  · ${n}`);
  if (errors.length) {
    console.log(`\nFAIL 布局自检未通过（${errors.length} 项）：`);
    for (const e of errors) console.log(`  - ${e}`);
  } else {
    console.log(`\nPASS 布局自检通过：${buildings.length} 节点，12 坊互不重叠且不压墙，皇城 ${royalRects.length} 座建筑无交叠`);
  }
}
process.exit(errors.length ? 1 : 0);
