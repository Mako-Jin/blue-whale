/* ==========================================================================
 * 城内 NPC —— 巡游 + 身份台词
 *
 * 当前是"规则驱动的 NPC"：按航点巡逻、靠近时说出身份台词。
 * P3 计划：把 `lines` 换成 whale-agent 的 LLM 人设（带业务数据工具），
 * 即「问户部主事今天有多少异常工单」这类问题能真答——
 * 接口已按 `NpcBrain` 预留，替换实现即可，不动场景层。
 * ========================================================================== */

import * as THREE from 'three';
import { DEFAULT_AVATAR, createAvatar } from './avatar';
import type { AvatarHandle, AvatarParams } from './avatar';

/** P3 用：把 NPC 台词替换为大模型应答（当前由本地台词兜底） */
export interface NpcBrain {
  ask: (npcId: string, question: string) => Promise<string>;
}

export interface NpcDef {
  id: string;
  name: string;
  role: string;
  /** 巡逻航点（世界坐标 x,z），循环 */
  route: Array<[number, number]>;
  /** 移动速度 m/s */
  speed: number;
  lines: string[];
  appearance: Partial<AvatarParams>;
}

export interface NpcRuntime {
  def: NpcDef;
  handle: AvatarHandle;
  pos: THREE.Vector3;
  yaw: number;
  speed: number;
  waypoint: number;
  wait: number;
}

export interface NpcSystem {
  group: THREE.Group;
  list: NpcRuntime[];
  update: (dt: number, groundAt: (x: number, z: number) => number) => void;
  dispose: () => void;
}

export const NPCS: NpcDef[] = [
  {
    id: 'guard-wei',
    name: '卫临',
    role: '御前侍卫',
    route: [
      [-3.2, 80],
      [-3.2, 34],
    ],
    speed: 2.2,
    lines: [
      '御道清静，主公慢行。',
      '夜里巡城，灯影最配这城墙。',
      '城门钥在我这儿，要出城说一声。',
    ],
    appearance: { robe: '#3f5f78', hairStyle: 2, accessory: 2, beard: true, height: 1.06, build: 1.06 },
  },
  {
    id: 'clerk-shen',
    name: '沈文昭',
    role: '户部主事',
    route: [
      [26, 78],
      [8, 78],
      [8, 66],
      [26, 66],
    ],
    speed: 1.7,
    lines: [
      '户部的账已平了三成，剩下的明日再核。',
      '仓里新到的粮，数目对得上。',
      '城里的买卖，如今都走市舶司的账。',
    ],
    appearance: { robe: '#7a5a2e', hairStyle: 0, accessory: 1, beard: true, height: 0.98, build: 0.98 },
  },
  {
    id: 'peddler-chen',
    name: '陈九',
    role: '货郎',
    route: [
      [2.8, -32],
      [2.8, -72],
    ],
    speed: 1.9,
    lines: [
      '南城来的绢，摸一摸就知道好坏。',
      '主公要买什么？我这儿都是实在货。',
      '雪天路滑，担子可不好挑。',
    ],
    appearance: { robe: '#7d8590', hairStyle: 1, accessory: 0, height: 0.94, build: 0.96 },
  },
  {
    id: 'maid-su',
    name: '苏婉',
    role: '女官',
    route: [
      [-12, 8],
      [12, 8],
      [12, 19],
      [-12, 19],
    ],
    speed: 1.5,
    lines: [
      '典籍阁的卷册，我已按序归好了。',
      '皇城里的灯，比别处暖一些。',
      '公主要在数枢殿议事么？我把灯挑亮。',
    ],
    appearance: { robe: '#a3435a', hairStyle: 1, accessory: 1, height: 0.95, build: 0.92, faceWidth: 0.96 },
  },
  {
    id: 'watcher-luan',
    name: '老栾',
    role: '更夫',
    route: [
      [40, 0],
      [0, 40],
      [-40, 0],
      [0, -40],
    ],
    speed: 1.3,
    lines: [
      '天干物燥，小心火烛。',
      '更鼓敲过三巡，主公早些歇息。',
      '冬夜风硬，城头的旗都冻住了。',
    ],
    appearance: { robe: '#5a4a7a', hairStyle: 0, accessory: 0, beard: true, height: 0.93, build: 1.12 },
  },
  {
    id: 'guard-han',
    name: '韩五',
    role: '门吏',
    route: [
      [2.6, 88],
      [2.6, 68],
    ],
    speed: 1.6,
    lines: [
      '外城门申时落锁，主公留意时辰。',
      '传送到八方宫署，比走快得多。',
      '这条御道，直通数枢殿。',
    ],
    appearance: { robe: '#2f5f78', hairStyle: 0, accessory: 2, height: 1.02, build: 1.04 },
  },
];

export function createNpcs(list: NpcDef[] = NPCS): NpcSystem {
  const group = new THREE.Group();
  const runtimes: NpcRuntime[] = [];

  for (const def of list) {
    const params: AvatarParams = { ...DEFAULT_AVATAR, name: def.name, ...def.appearance };
    const handle = createAvatar(params);
    const start = def.route[0];
    const pos = new THREE.Vector3(start[0], 0, start[1]);
    handle.group.position.copy(pos);
    group.add(handle.group);
    runtimes.push({
      def,
      handle,
      pos,
      yaw: 0,
      speed: 0,
      waypoint: 1 % def.route.length,
      wait: Math.random() * 1.5,
    });
  }

  return {
    group,
    list: runtimes,
    update(dt, groundAt) {
      for (const npc of runtimes) {
        const target = npc.def.route[npc.waypoint];

        if (npc.wait > 0) {
          npc.wait -= dt;
          npc.speed = Math.max(0, npc.speed - dt * 6);
        } else {
          const dx = target[0] - npc.pos.x;
          const dz = target[1] - npc.pos.z;
          const dist = Math.hypot(dx, dz);
          if (dist < 0.7) {
            npc.waypoint = (npc.waypoint + 1) % npc.def.route.length;
            npc.wait = 0.8 + Math.random() * 2.4;
            npc.speed = 0;
          } else {
            const desiredYaw = Math.atan2(dx, dz);
            let diff = desiredYaw - npc.yaw;
            while (diff > Math.PI) diff -= Math.PI * 2;
            while (diff < -Math.PI) diff += Math.PI * 2;
            npc.yaw += diff * Math.min(1, dt * 4);
            npc.speed = Math.min(npc.def.speed, npc.speed + dt * 4);
            npc.pos.x += Math.sin(npc.yaw) * npc.speed * dt;
            npc.pos.z += Math.cos(npc.yaw) * npc.speed * dt;
          }
        }

        npc.pos.y = groundAt(npc.pos.x, npc.pos.z);
        npc.handle.group.position.copy(npc.pos);
        npc.handle.group.rotation.y = npc.yaw;
        npc.handle.update(dt, npc.speed);
      }
    },
    dispose() {
      for (const npc of runtimes) {
        npc.handle.dispose();
        group.remove(npc.handle.group);
      }
      runtimes.length = 0;
    },
  };
}
