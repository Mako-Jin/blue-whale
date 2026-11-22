/* ==========================================================================
 * 城池寻路（右键自动寻路的算路层）
 *
 * <p>为什么不用「直线 + 撞墙滑动」：城里有三层城墙，门洞只有四条边中点。
 * 从外城去皇城，直线必然撞在内墙上；靠推挤滑动会卡在墙角来回抖，
 * 而这类缺陷**编译不报错、运行不崩溃**，只是"点了没反应"——正是本项目
 * 最高频的静默失效类型。所以这里做真正的网格 A*。</p>
 *
 * <p>为什么要膨胀半径：{@code collision.collide} 是按零半径推出的，而实际
 * 角色的肩膀有宽度。若寻路按零半径算，路径会贴着墙角走，一帧一帧被推出、
 * 再走回去，看起来就是"卡了"。这里按 {@link DEFAULT_RADIUS} 膨胀障碍，
 * 保证算出来的路在推挤下依然能走完。</p>
 *
 * <p>精度取舍：1m 一格，全城约 176×176 格，一次 A* 几十毫秒（点一下算一次）。
 * 最窄的三道门洞净宽 11m / 13m / 17m，1m 网格不会把门判死。</p>
 *
 * <p><b>旋转盒必须按旋转算，不能拿 hx/hz 当轴对齐盒用。</b>内城十二坊的坊墙是斜着
 * 摆的：把 {@code hx/hz} 直接当轴对齐盒，斜墙在格网里既会"少挡"（墙的两端被抹平，
 * 路被规划进真墙里，角色顶着墙推不进去、几秒后放弃在路中间）也会"多挡"（墙的
 * 中段被摊平，本该能走的街道变成死路）。两种错都不报错，只表现为"点了没反应"或
 * "走到一半停下"。所以格网与拉直两处都按盒的局部坐标系判。</p>
 * ========================================================================== */

import type { ColliderSpec } from './layout';

/** 带朝向的碰撞盒（与 {@code collision.Blocker} 同形；坊墙是旋转盒） */
export type NavBlocker = ColliderSpec & { yaw?: number };

/** 格子边长（米） */
const CELL = 1;

/** 角色碰撞半径（米）：障碍按此膨胀，比视觉肩宽略窄，留一点容错 */
const DEFAULT_RADIUS = 0.55;

/**
 * 拉直线时的额外余量（米）。
 *
 * <p>格网判定只保证"格子中心不撞"，但角色实际是「速度插值 + 转向插值」在走，
 * 拐弯时会甩出去半米。若简化后的直线贴着墙角，就会一帧一帧被推回来，
 * 看着像卡住。<b>实测：把余量给到 0.35 米后，斜穿全城的用例才稳定走完。</b></p>
 */
const CLEAR_PADDING = 0.35;

/** 8 邻域偏移：前 4 个正交，后 4 个对角 */
const NX = [1, -1, 0, 0, 1, 1, -1, -1] as const;
const NZ = [0, 0, 1, -1, 1, -1, 1, -1] as const;

/** 正交移动代价 */
const COST_STRAIGHT = 1;
/** 对角移动代价（√2） */
const SQRT2 = Math.SQRT2;

/** 世界坐标点 */
export interface NavPoint {
  x: number;
  z: number;
}

/** 一次寻路的结果 */
export interface NavPath {
  /** 起点之后的拐点序列（已按视线拉直），最后一点即落点 */
  points: NavPoint[];
  /** 实际落点（终点不可站时已修正到最近可站处） */
  goal: NavPoint;
  /** 落点是否被修正过（UI 据此提示"改道"） */
  adjusted: boolean;
}

export interface NavGridOptions {
  /** 可走的绝对边界：|x|、|z| 都必须小于它（默认取自外层城墙） */
  bound: number;
  /** 障碍膨胀半径（米） */
  radius?: number;
  /** 网格边长（米） */
  cell?: number;
}

export interface NavGrid {
  /** 格子边长 */
  readonly cell: number;
  /** 可走边界 */
  readonly bound: number;
  /** 是否可站（世界坐标） */
  walkable: (x: number, z: number) => boolean;
  /** 找最近可站点（不可站时按环向外扩搜索） */
  nearest: (x: number, z: number, maxRing?: number) => NavPoint | null;
  /** 算路；完全不可达返回 null */
  findPath: (from: NavPoint, to: NavPoint) => NavPath | null;
  /** 两点之间是否无遮挡（已含膨胀），供外部做落点/连线预判 */
  clearLine: (a: NavPoint, b: NavPoint) => boolean;
}

/** 膨胀后的盒（带朝向；yaw 为 0 时就是普通轴对齐盒） */
interface PadBox {
  x: number;
  z: number;
  hx: number;
  hz: number;
  /** 绕 Y 轴旋转（弧度），0 表示轴对齐 */
  yaw: number;
  /** cos(-yaw)：世界位移转盒内坐标用，预先算好避免每格重算 */
  cos: number;
  /** sin(-yaw) */
  sin: number;
}

/**
 * 盒是否包含某点。
 *
 * <p>闭区间取向：贴边即算撞——与 {@code collide} 的推挤边界一致，宁可绕一点。</p>
 *
 * @param box 膨胀盒
 * @param x   世界 x
 * @param z   世界 z
 * @returns 落在盒内返回 {@code true}
 */
function boxContains(box: PadBox, x: number, z: number): boolean {
  if (box.yaw === 0) {
    return Math.abs(x - box.x) < box.hx && Math.abs(z - box.z) < box.hz;
  }
  const dx = x - box.x;
  const dz = z - box.z;
  const lx = dx * box.cos - dz * box.sin;
  const lz = dx * box.sin + dz * box.cos;
  return Math.abs(lx) < box.hx && Math.abs(lz) < box.hz;
}

/**
 * 线段是否穿过盒子（2D slab 法）。
 *
 * <p>这里判的是**闭区间相交**：线段擦着盒边也算撞上。宁可绕一点，
 * 也不要让角色沿着墙角擦过去被推挤。</p>
 *
 * @param pad 额外的安全余量（米）：拉直时要比格网判定更保守，见 {@link CLEAR_PADDING}
 */
function segmentHitsBox(
  ax: number,
  az: number,
  bx: number,
  bz: number,
  box: PadBox,
  pad: number,
): boolean {
  /* 旋转盒：把线段两端转到盒内坐标，盒就退化成以原点为中心、半边 ±hx/±hz 的
     轴对齐盒。直线在旋转下仍是直线，slab 法照样成立。 */
  let x0 = ax;
  let z0 = az;
  let x1 = bx;
  let z1 = bz;
  let cx = box.x;
  let cz = box.z;
  if (box.yaw !== 0) {
    const ax0 = ax - box.x;
    const az0 = az - box.z;
    x0 = ax0 * box.cos - az0 * box.sin;
    z0 = ax0 * box.sin + az0 * box.cos;
    const bx0 = bx - box.x;
    const bz0 = bz - box.z;
    x1 = bx0 * box.cos - bz0 * box.sin;
    z1 = bx0 * box.sin + bz0 * box.cos;
    cx = 0;
    cz = 0;
  }
  const dx = x1 - x0;
  const dz = z1 - z0;
  let tmin = 0;
  let tmax = 1;

  const slab = (origin: number, delta: number, min: number, max: number): boolean => {
    if (Math.abs(delta) < 1e-9) {
      // 平行于该轴：起点必须落在区间内，否则整段都在盒外
      return origin >= min && origin <= max;
    }
    let t1 = (min - origin) / delta;
    let t2 = (max - origin) / delta;
    if (t1 > t2) {
      const tmp = t1;
      t1 = t2;
      t2 = tmp;
    }
    if (t1 > tmin) tmin = t1;
    if (t2 < tmax) tmax = t2;
    return tmin <= tmax;
  };

  if (!slab(x0, dx, cx - box.hx - pad, cx + box.hx + pad)) {
    return false;
  }
  return slab(z0, dz, cz - box.hz - pad, cz + box.hz + pad);
}

/** 极简二叉堆（A* 开放集），比每轮 sort 快一个数量级 */
class MinHeap {
  private items: number[] = [];
  private scores: Float32Array;

  constructor(scores: Float32Array) {
    this.scores = scores;
  }

  get size(): number {
    return this.items.length;
  }

  clear(): void {
    this.items.length = 0;
  }

  push(node: number): void {
    const items = this.items;
    items.push(node);
    let i = items.length - 1;
    while (i > 0) {
      const parent = (i - 1) >> 1;
      if (this.scores[items[parent]] <= this.scores[items[i]]) break;
      const tmp = items[parent];
      items[parent] = items[i];
      items[i] = tmp;
      i = parent;
    }
  }

  pop(): number {
    const items = this.items;
    const top = items[0];
    const last = items.pop() as number;
    if (items.length > 0) {
      items[0] = last;
      let i = 0;
      for (;;) {
        const l = i * 2 + 1;
        const r = l + 1;
        let best = i;
        if (l < items.length && this.scores[items[l]] < this.scores[items[best]]) best = l;
        if (r < items.length && this.scores[items[r]] < this.scores[items[best]]) best = r;
        if (best === i) break;
        const tmp = items[best];
        items[best] = items[i];
        items[i] = tmp;
        i = best;
      }
    }
    return top;
  }
}

/**
 * 建一张寻路网格。
 *
 * @param blockers 碰撞盒（与 {@code collision.createBlockers()} 同源，保证"能走"与"不撞墙"是同一套几何）
 * @param options  边界与膨胀半径
 */
export function createNavGrid(blockers: NavBlocker[], options: NavGridOptions): NavGrid {
  const cell = options.cell ?? CELL;
  const radius = options.radius ?? DEFAULT_RADIUS;
  const bound = options.bound;
  const origin = -bound;
  const size = Math.max(2, Math.ceil((bound * 2) / cell));
  const total = size * size;

  const boxes: PadBox[] = blockers.map((b) => {
    const yaw = b.yaw ?? 0;
    return {
      x: b.x,
      z: b.z,
      hx: b.hx + radius,
      hz: b.hz + radius,
      yaw,
      cos: Math.cos(-yaw),
      sin: Math.sin(-yaw),
    };
  });

  /** 逐格标记：格子中心落在任一膨胀盒内即不可走 */
  const blocked = new Uint8Array(total);
  for (let iz = 0; iz < size; iz += 1) {
    const cz = origin + (iz + 0.5) * cell;
    for (let ix = 0; ix < size; ix += 1) {
      const cx = origin + (ix + 0.5) * cell;
      let hit = 0;
      for (const b of boxes) {
        if (boxContains(b, cx, cz)) {
          hit = 1;
          break;
        }
      }
      blocked[iz * size + ix] = hit;
    }
  }

  /* A* 的工作数组只分配一次，用 epoch 标记代替 fill，避免点一次鼠标就造几百 KB 垃圾。
     注意 closed 也必须是带 epoch 的数组：它若只清一次，第二轮算路会把上一轮的
     "已访问"当成自己的，路径会莫名其妙地绕远甚至判为不可达。 */
  const gScore = new Float32Array(total);
  const fScore = new Float32Array(total);
  const parent = new Int32Array(total);
  const stamp = new Uint32Array(total);
  const closedStamp = new Uint32Array(total);
  let epoch = 0;
  const heap = new MinHeap(fScore);

  const centerX = (ix: number) => origin + (ix + 0.5) * cell;
  const centerZ = (iz: number) => origin + (iz + 0.5) * cell;
  const ixOf = (x: number) => Math.floor((x - origin) / cell);
  const izOf = (z: number) => Math.floor((z - origin) / cell);

  const inside = (ix: number, iz: number) => ix >= 0 && iz >= 0 && ix < size && iz < size;
  const passable = (ix: number, iz: number) => inside(ix, iz) && blocked[iz * size + ix] === 0;

  const walkable = (x: number, z: number): boolean => {
    if (Math.abs(x) >= bound || Math.abs(z) >= bound) {
      return false;
    }
    return passable(ixOf(x), izOf(z));
  };

  const clearLine = (a: NavPoint, b: NavPoint): boolean => {
    for (const box of boxes) {
      if (segmentHitsBox(a.x, a.z, b.x, b.z, box, CLEAR_PADDING)) {
        return false;
      }
    }
    return true;
  };

  /**
   * 环形外扩找可站格，按距离升序返回前 {@code count} 个。
   *
   * <p>为什么要多个候选：右键点在宫殿顶上时，最近的可站格可能落在殿**背面**，
   * 从玩家那边根本绕不过去（A* 只能返回不可达）。多备几个候选，前面被围死的
   * 跳过、后面那个在殿前的就能走通——否则用户看到的就是"点了没反应"。</p>
   */
  const nearestCandidates = (x: number, z: number, count: number, maxRing = 14): NavPoint[] => {
    const ix0 = Math.min(size - 1, Math.max(0, ixOf(x)));
    const iz0 = Math.min(size - 1, Math.max(0, izOf(z)));
    const found: Array<NavPoint & { d: number }> = [];
    if (passable(ix0, iz0)) {
      found.push({ x: centerX(ix0), z: centerZ(iz0), d: 0 });
    }
    for (let r = 1; r <= maxRing; r += 1) {
      for (let iz = iz0 - r; iz <= iz0 + r; iz += 1) {
        for (let ix = ix0 - r; ix <= ix0 + r; ix += 1) {
          // 只看这一圈的边框，内部上一轮已经看过
          if (Math.abs(ix - ix0) !== r && Math.abs(iz - iz0) !== r) continue;
          if (!passable(ix, iz)) continue;
          const cx = centerX(ix);
          const cz = centerZ(iz);
          found.push({ x: cx, z: cz, d: (cx - x) ** 2 + (cz - z) ** 2 });
        }
      }
      // 凑够就停：圈数只增不减，再往外不会出现"近得多"的点
      if (found.length >= count) {
        break;
      }
    }
    found.sort((a, b) => a.d - b.d);
    return found.slice(0, count).map((p) => ({ x: p.x, z: p.z }));
  };

  const nearest = (x: number, z: number, maxRing = 14): NavPoint | null =>
    nearestCandidates(x, z, 1, maxRing)[0] ?? null;

  const reconstruct = (endIdx: number, startIdx: number): NavPoint[] => {
    const raw: NavPoint[] = [];
    for (let idx = endIdx; idx !== startIdx && idx >= 0; idx = parent[idx]) {
      raw.push({ x: centerX(idx % size), z: centerZ(Math.floor(idx / size)) });
    }
    raw.reverse();
    return raw;
  };

  /**
   * 视线拉直：从当前锚点贪心跳到"最远仍无遮挡"的点。
   *
   * <p>网格路径是锯齿状的，直接按点走会一顿一顿。拉直后只剩真正的拐点，
   * 转向平滑、也不会沿墙一格一格蹭。</p>
   */
  const simplify = (from: NavPoint, points: NavPoint[]): NavPoint[] => {
    if (points.length <= 1) {
      return points;
    }
    const out: NavPoint[] = [];
    let anchor = from;
    let i = 0;
    while (i < points.length) {
      let furthest = i;
      for (let j = points.length - 1; j > i; j -= 1) {
        if (clearLine(anchor, points[j])) {
          furthest = j;
          break;
        }
      }
      out.push(points[furthest]);
      anchor = points[furthest];
      i = furthest + 1;
    }
    return out;
  };

  /** 单次 A*：成功返回格路径（含终点格，不含起点格）与总代价，失败返回 null */
  const search = (startIdx: number, goalIdx: number): { raw: NavPoint[]; cost: number } | null => {
    epoch += 1;
    heap.clear();
    const goalIx = goalIdx % size;
    const goalIz = Math.floor(goalIdx / size);

    const heuristic = (ix: number, iz: number): number => {
      const dx = Math.abs(ix - goalIx);
      const dz = Math.abs(iz - goalIz);
      // octile：允许对角，代价比直线便宜
      return (dx + dz + (SQRT2 - 2) * Math.min(dx, dz)) * cell;
    };

    const startIx = startIdx % size;
    const startIz = Math.floor(startIdx / size);
    gScore[startIdx] = 0;
    fScore[startIdx] = heuristic(startIx, startIz);
    stamp[startIdx] = epoch;
    parent[startIdx] = -1;
    heap.push(startIdx);

    let found = false;
    let guard = 0;
    while (heap.size > 0 && guard < total) {
      guard += 1;
      const cur = heap.pop();
      if (closedStamp[cur] === epoch) continue;
      closedStamp[cur] = epoch;
      if (cur === goalIdx) {
        found = true;
        break;
      }
      const cx = cur % size;
      const cz = Math.floor(cur / size);
      for (let k = 0; k < 8; k += 1) {
        const nx = cx + NX[k];
        const nz = cz + NZ[k];
        if (!passable(nx, nz)) continue;
        const diagonal = k >= 4;
        // 禁止贴角斜切：两侧正交格都得通，否则角色会从墙角挤过去被推回来
        if (diagonal && (!passable(cx + NX[k], cz) || !passable(cx, cz + NZ[k]))) {
          continue;
        }
        const next = nz * size + nx;
        if (closedStamp[next] === epoch) continue;
        const step = diagonal ? SQRT2 * cell : COST_STRAIGHT * cell;
        const tentative = gScore[cur] + step;
        if (stamp[next] === epoch && tentative >= gScore[next]) continue;
        stamp[next] = epoch;
        gScore[next] = tentative;
        fScore[next] = tentative + heuristic(nx, nz);
        parent[next] = cur;
        heap.push(next);
      }
    }

    if (!found) {
      return null;
    }
    return { raw: reconstruct(goalIdx, startIdx), cost: gScore[goalIdx] };
  };

  const findPath = (from: NavPoint, to: NavPoint): NavPath | null => {
    const startPoint = nearest(from.x, from.z, 4);
    if (!startPoint) {
      return null;
    }
    const startIdx = izOf(startPoint.z) * size + ixOf(startPoint.x);

    // 终点按"离点击点由近到远"依次尝试：前面被建筑围死的候选跳过
    const candidates = nearestCandidates(to.x, to.z, 5);
    if (candidates.length === 0) {
      return null;
    }

    let best: NavPath | null = null;
    for (const goalPoint of candidates) {
      // 落点一定落在格中心，与点击点天然差最多半格（约 0.7m）。
      // 只有明显被挪走（比如点进了建筑里）才算"改道"，否则每次都会提示一次，纯噪音。
      const drift = Math.hypot(goalPoint.x - to.x, goalPoint.z - to.z);
      const adjusted = drift > 1.5;
      const goalIdx = izOf(goalPoint.z) * size + ixOf(goalPoint.x);
      if (goalIdx === startIdx) {
        best = { points: [{ ...goalPoint }], goal: goalPoint, adjusted };
        break;
      }
      const result = search(startIdx, goalIdx);
      if (!result) {
        continue;
      }
      best = { points: simplify(startPoint, result.raw), goal: goalPoint, adjusted };
      break;
    }
    return best;
  };

  return { cell, bound, walkable, nearest, findPath, clearLine };
}
