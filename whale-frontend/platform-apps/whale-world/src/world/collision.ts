/**
 * 城池碰撞（城墙按门洞分段 + 建筑台基推出）与相机避让。
 *
 * <p>这里的几何全部来自 {@link layout}，不再散落在主场景文件里。两种用法：</p>
 * <ul>
 *   <li>{@link collide} 把角色从墙体/台基里推出来（最小穿透方向）；</li>
 *   <li>{@link camBlocked} 判断相机落点是否落在建筑/墙体缓冲区内，供相机拉远避让。</li>
 * </ul>
 *
 * <p><b>城墙必须按门洞切段</b>：P0 时每面墙是整条碰撞体，门只是画上去的，
 * 玩家永远走不进内城与皇城，只能靠传送门——这种「画了但走不过去」的缺陷
 * 编译与启动都不报错，所以 e2e 必须有正向（能穿门）+ 负向（贴墙被挡）双向断言。</p>
 */

import {
  CITY_GEOMETRY,
  DISTRICTS,
  DISTRICT_WALL,
  HALL_COLLIDERS,
  PALACE_COLLIDERS,
  WALL_RINGS,
  type ColliderSpec,
} from './layout';

/** 碰撞盒（hx/hz 为半长；带 yaw 时为绕中心旋转的盒，否则轴对齐） */
export interface Blocker extends ColliderSpec {
  /** 语义标签，便于调试与后续按区域裁剪 */
  kind: 'wall' | 'hall' | 'palace' | 'district';
  /** 绕 Y 轴旋转（弧度），缺省 0 表示轴对齐 */
  yaw?: number;
}

/** 相机避让缓冲（米）：进入该范围即视为遮挡 */
const CAM_PADDING = 2;

/**
 * 生成全部碰撞盒：三层城墙（按门洞切段） + 皇城建筑 + 十二坊 + 坊墙。
 */
export function createBlockers(): Blocker[] {
  const blockers: Blocker[] = [];

  for (const hall of HALL_COLLIDERS) {
    blockers.push({ ...hall, kind: 'hall' });
  }
  for (const palace of PALACE_COLLIDERS) {
    blockers.push({ ...palace, kind: 'palace' });
  }

  for (const ring of WALL_RINGS) {
    const seg = (ring.half - ring.gate) / 2;
    const off = ring.gate + seg;
    for (const side of [-1, 1]) {
      // 南北墙：沿 x 轴切开
      for (const sx of [-1, 1]) {
        blockers.push({ x: sx * off, z: side * ring.half, hx: seg, hz: ring.thick, kind: 'wall' });
      }
      // 东西墙：沿 z 轴切开
      for (const sz of [-1, 1]) {
        blockers.push({ x: side * ring.half, z: sz * off, hx: ring.thick, hz: seg, kind: 'wall' });
      }
    }
  }

  /* 坊墙：每坊四面墙，南面（朝圆心）按门洞切两段，不封死坊门。
   * 报点时先算局部坐标再旋回世界，语义与 city.ts 的渲染完全一致。 */
  const t = DISTRICT_WALL.thick;
  const gate = DISTRICT_WALL.gateHalf;
  for (const d of DISTRICTS) {
    const hw = d.halfW;
    const hd = d.halfD;
    const sideW = hw - gate;
    const cos = Math.cos(d.yaw);
    const sin = Math.sin(d.yaw);
    /** 局部 (lx, lz) → 世界坐标 */
    const toWorld = (lx: number, lz: number) => ({
      x: d.x + lx * cos - lz * sin,
      z: d.z + lx * sin + lz * cos,
    });

    // 北 / 西 / 东 三面整段
    const north = toWorld(0, -hd);
    blockers.push({ x: north.x, z: north.z, hx: hw, hz: t, kind: 'district', yaw: d.yaw });
    for (const sx of [-1, 1]) {
      const sidePos = toWorld(sx * hw, 0);
      blockers.push({ x: sidePos.x, z: sidePos.z, hx: t, hz: hd, kind: 'district', yaw: d.yaw });
    }
    // 南面两段
    for (const sx of [-1, 1]) {
      const segPos = toWorld(sx * (gate + sideW / 2), hd);
      blockers.push({
        x: segPos.x,
        z: segPos.z,
        hx: sideW / 2,
        hz: t,
        kind: 'district',
        yaw: d.yaw,
      });
    }
  }

  return blockers;
}

/** 玩家可活动的绝对边界（略小于外城墙，防止从门洞走出去后无限外扩） */
export const WORLD_BOUND = CITY_GEOMETRY.outerWall - 1.5;

/**
 * 把位置从所有碰撞盒里推出来。
 *
 * <p>多次迭代：推出后可能又落进相邻盒（例如门洞两侧墙段夹角），两轮即可收敛。</p>
 *
 * <p>带 {@code yaw} 的盒先转到盒的局部坐标系再判穿透与推出，最后转回世界。
 * 坊墙必须是旋转盒：12 坊按方位角斜着摆，用轴对齐包围盒近似会把相邻两坊
 * 的墙连成一片，坊门就被封死了。</p>
 *
 * @param blockers 碰撞盒
 * @param pos      待修正的位置（就地修改 x/z）
 */
export function collide(blockers: Blocker[], pos: { x: number; z: number }): void {
  for (let iter = 0; iter < 2; iter += 1) {
    let moved = false;
    for (const b of blockers) {
      const dx = pos.x - b.x;
      const dz = pos.z - b.z;

      // 世界偏移 → 盒局部坐标（逆旋转）
      const cos = b.yaw ? Math.cos(b.yaw) : 1;
      const sin = b.yaw ? Math.sin(b.yaw) : 0;
      const lx = dx * cos + dz * sin;
      const lz = -dx * sin + dz * cos;

      const px = Math.abs(lx) - b.hx;
      const pz = Math.abs(lz) - b.hz;
      if (px < 0 && pz < 0) {
        // 沿最小穿透方向推出（在局部坐标里推，再旋回世界）
        let nlx = lx;
        let nlz = lz;
        if (px > pz) {
          nlx = Math.sign(lx || 1) * b.hx;
        } else {
          nlz = Math.sign(lz || 1) * b.hz;
        }
        pos.x = b.x + nlx * cos - nlz * sin;
        pos.z = b.z + nlx * sin + nlz * cos;
        moved = true;
      }
    }
    if (!moved) {
      return;
    }
  }
}

/**
 * 相机落点是否被墙体或建筑挡住。
 *
 * <p>统一按盒的局部坐标系判断（带 yaw 的先逆旋），不能对坊墙用外接圆近似：
 * 坊的外接半径约 17m，而相邻坊中心距只有 39m，12 个圆首尾相接会沿环带
 * 连成一圈"隐形墙"，相机在内城环路上会被一路顶到贴脸。</p>
 *
 * @param blockers 碰撞盒
 * @param x        相机落点 x
 * @param z        相机落点 z
 * @returns 落入任一盒的缓冲范围返回 true
 */
export function camBlocked(blockers: Blocker[], x: number, z: number): boolean {
  for (const b of blockers) {
    const dx = x - b.x;
    const dz = z - b.z;
    const cos = b.yaw ? Math.cos(b.yaw) : 1;
    const sin = b.yaw ? Math.sin(b.yaw) : 0;
    const lx = dx * cos + dz * sin;
    const lz = -dx * sin + dz * cos;
    if (Math.abs(lx) < b.hx + CAM_PADDING && Math.abs(lz) < b.hz + CAM_PADDING) {
      return true;
    }
  }
  return false;
}
