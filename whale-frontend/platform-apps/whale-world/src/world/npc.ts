/* ==========================================================================
 * 城内 NPC —— 巡游 + 身份台词
 *
 * 现在是"人设驱动"的 NPC：按航点巡逻，靠近时说出身份台词；按 F 搭话则由
 * `brain.ts` 接 whale-agent 真答（问户部主事今天有多少异常工单，他会去查库）。
 * 台词（lines）不再只是装饰，同时充当 AI 未接入时的兜底。
 * ========================================================================== */

import * as THREE from 'three';
import { DEFAULT_AVATAR, createAvatar } from './avatar';
import type { AvatarHandle, AvatarParams } from './avatar';
import type { NpcChatPersona } from './npcchat';

export interface NpcDef {
  id: string;
  name: string;
  role: string;
  /** 驻守/管辖的地点，写进人设（如「皇城·御道」） */
  place?: string;
  /** 职责范围：决定这名 NPC 被允许答什么（写进人设前缀，防止满城 NPC 都说一样的话） */
  brief: string;
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
  /** 与玩家对话中：停步，不再推进航点（否则气泡跟着人跑） */
  hold: boolean;
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
    place: '皇城·御道',
    brief: '掌城门钥与宵禁、巡防布岗、门禁与告警记录；能查告警中心的未处理事件、访客与设备在线情况。',
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
    place: '内城·户部',
    brief: '掌钱粮账目：订单、合同、应收应付、库存与工单；能查 ERP/CRM 的经营数据与异常单据。',
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
    place: '外城·市集',
    brief: '走南闯北的买卖人：商城的商品、价格、活动与订单履约；能查上架状态、库存与近期成交。',
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
    place: '皇城·典籍阁',
    brief: '掌典籍与文书：制度文档、知识库、审批流与会议纪要；能查文档内容、流程走到哪一步、谁的待办。',
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
    place: '外城·更楼',
    brief: '掌时辰更鼓：日程、值班、定时任务与系统运行状态；能查几点该做什么、哪些任务昨夜没跑成。',
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
    place: '外城·正门',
    brief: '掌城门与驿道：八方宫署各管什么、传送门通向哪个 2D 系统、路线怎么走；是城里的活地图。',
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
      hold: false,
    });
  }

  return {
    group,
    list: runtimes,
    update(dt, groundAt) {
      for (const npc of runtimes) {
        // 对话中：原地停步（仍要贴地），否则玩家一路追着气泡跑
        if (npc.hold) {
          npc.speed = Math.max(0, npc.speed - dt * 8);
          npc.pos.y = groundAt(npc.pos.x, npc.pos.z);
          npc.handle.group.position.copy(npc.pos);
          npc.handle.update(dt, npc.speed);
          continue;
        }
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

/** NPC 人设（送进问话面板与 AI 首轮前缀）：台词第一位当开场白，其余留作离线兜底 */
export function npcPersona(def: NpcDef): NpcChatPersona {
  return {
    id: def.id,
    name: def.name,
    role: def.role,
    place: def.place,
    brief: def.brief,
    greeting: def.lines[0],
    lines: def.lines,
  };
}

/** 小鲸宝：不打烊的引路灵兽，全城通识 + 能替主公查各系统的数 */
export const PET_PERSONA: NpcChatPersona = {
  id: 'pet-whale',
  name: '小鲸宝',
  role: '引路灵兽',
  place: '全城',
  brief:
    '主公的随身灵兽：认得全城每一条路与每一座宫署，也知道平台各系统管什么；' +
    '能替主公查工单、订单、文档、告警、设备这类实情，答不上来会直说。',
  greeting: '主公想问什么？城里的账、路上的人、殿里的文书，我都能去看一眼。',
  lines: [
    '我游得快，主公稍等，我去各殿打听一圈。',
    '这条路我熟——御道直走，尽头就是数枢殿。',
    '眼下连不上中枢，我先陪主公走一走。',
  ],
};
