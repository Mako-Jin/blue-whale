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
  HALL_COLLIDERS,
  PALACE_COLLIDERS,
  WALL_RINGS,
  type ColliderSpec,
} from './layout';

/** 碰撞盒（轴对齐矩形，hx/hz 为半长） */
export interface Blocker extends ColliderSpec {
  /** 语义标签，便于调试与后续按区域裁剪 */
  kind: 'wall' | 'hall' | 'palace';
}

/** 相机避让缓冲（米）：进入该范围即视为遮挡 */
const CAM_PADDING = 2;

/**
 * 生成全部碰撞盒：三层城墙（按门洞切段） + 皇城建筑 + 八方宫殿。
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

  return blockers;
}

/** 玩家可活动的绝对边界（略小于外城墙，防止从门洞走出去后无限外扩） */
export const WORLD_BOUND = CITY_GEOMETRY.outerWall - 1.5;

/**
 * 把位置从所有碰撞盒里推出来。
 *
 * <p>多次迭代：推出后可能又落进相邻盒（例如门洞两侧墙段夹角），两轮即可收敛。</p>
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
      const px = Math.abs(dx) - b.hx;
      const pz = Math.abs(dz) - b.hz;
      if (px < 0 && pz < 0) {
        // 沿最小穿透方向推出
        if (px > pz) {
          pos.x = b.x + Math.sign(dx || 1) * b.hx;
        } else {
          pos.z = b.z + Math.sign(dz || 1) * b.hz;
        }
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
 * @param blockers 碰撞盒
 * @param x        相机落点 x
 * @param z        相机落点 z
 * @returns 落入任一盒的缓冲范围返回 true
 */
export function camBlocked(blockers: Blocker[], x: number, z: number): boolean {
  for (const b of blockers) {
    if (Math.abs(x - b.x) < b.hx + CAM_PADDING && Math.abs(z - b.z) < b.hz + CAM_PADDING) {
      return true;
    }
  }
  return false;
}
