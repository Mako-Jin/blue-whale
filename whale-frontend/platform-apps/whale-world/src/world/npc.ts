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
  /** 雨强 0..1：撑伞（伞面淡入）/ 收伞。0 时整把伞不参与绘制 */
  setWet: (amount: number) => void;
  /** 当前伞的透明度（0 = 收着）：给探针断言用，正常逻辑不该读它 */
  wetOpacity: () => number;
  update: (dt: number, groundAt: (x: number, z: number) => number) => void;
  dispose: () => void;
}

export const NPCS: NpcDef[] = [
  {
    id: 'guard-wei',
    name: '卫临',
    role: '御前侍卫',
    place: '皇城·御道',
    brief:
      '掌城门钥与宵禁、巡防布岗、门禁与告警的规矩；能查告警、门禁相关的接口清单、配置与制度文档。',
    route: [
      [-3.2, 80],
      [-3.2, 34],
    ],
    speed: 2.2,
    lines: ['御道清静，主公慢行。', '夜里巡城，灯影最配这城墙。', '城门钥在我这儿，要出城说一声。'],
    appearance: {
      robe: '#3f5f78',
      hairStyle: 2,
      accessory: 2,
      beard: true,
      height: 1.06,
      build: 1.06,
    },
  },
  {
    id: 'clerk-shen',
    name: '沈文昭',
    role: '户部主事',
    place: '内城·户部',
    brief:
      '掌钱粮账目：订单、合同、应收应付、库存与工单的章程；能查 ERP/CRM 的接口、表结构与业务文档。',
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
    appearance: {
      robe: '#7a5a2e',
      hairStyle: 0,
      accessory: 1,
      beard: true,
      height: 0.98,
      build: 0.98,
    },
  },
  {
    id: 'peddler-chen',
    name: '陈九',
    role: '货郎',
    place: '外城·市集',
    brief:
      '走南闯北的买卖人：商城的商品、价格、活动与履约流程；能查商城的接口、配置与上架规则文档。',
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
    brief: '掌典籍与文书：制度文档、知识库、审批流与会议纪要；能查文档正文、流程定义与待办规则。',
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
    appearance: {
      robe: '#a3435a',
      hairStyle: 1,
      accessory: 1,
      height: 0.95,
      build: 0.92,
      faceWidth: 0.96,
    },
  },
  {
    id: 'watcher-luan',
    name: '老栾',
    role: '更夫',
    place: '外城·更楼',
    brief: '掌时辰更鼓：定时任务、值班与系统运行；能查任务清单、调度配置与服务端口。',
    route: [
      [40, 0],
      [0, 40],
      [-40, 0],
      [0, -40],
    ],
    speed: 1.3,
    lines: ['天干物燥，小心火烛。', '更鼓敲过三巡，主公早些歇息。', '冬夜风硬，城头的旗都冻住了。'],
    appearance: {
      robe: '#5a4a7a',
      hairStyle: 0,
      accessory: 0,
      beard: true,
      height: 0.93,
      build: 1.12,
    },
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

/**
 * 造一把伞。
 *
 * <p>不建骨骼、不做收放动画：伞面直接淡入淡出，走起来跟着人平移就够——
 * 这个距离下没人会盯伞骨。真正要避免的是"雨天城里七个人全在淋雨"。</p>
 *
 * <p>高度按 avatar 的头部位置定（头顶约 2.9m，伞面中心 2.62m 正好罩住肩以上）。</p>
 */
function makeUmbrella(): { group: THREE.Group; mats: THREE.MeshStandardMaterial[] } {
  const group = new THREE.Group();
  const canopyMat = new THREE.MeshStandardMaterial({
    color: 0x24313d,
    roughness: 0.68,
    metalness: 0.08,
    transparent: true,
    opacity: 0,
    side: THREE.DoubleSide,
    depthWrite: false,
  });
  const shaftMat = new THREE.MeshStandardMaterial({
    color: 0x6b4e2e,
    roughness: 0.85,
    transparent: true,
    opacity: 0,
    depthWrite: false,
  });
  const canopy = new THREE.Mesh(new THREE.ConeGeometry(1.04, 0.46, 14, 1, true), canopyMat);
  canopy.position.y = 2.62;
  const shaft = new THREE.Mesh(new THREE.CylinderGeometry(0.028, 0.028, 1.5, 6), shaftMat);
  shaft.position.y = 1.92;
  group.add(canopy, shaft);
  group.visible = false;
  return { group, mats: [canopyMat, shaftMat] };
}

export function createNpcs(list: NpcDef[] = NPCS): NpcSystem {
  const group = new THREE.Group();
  const runtimes: NpcRuntime[] = [];
  const umbrellas: Array<{ group: THREE.Group; mats: THREE.MeshStandardMaterial[] }> = [];
  /** 最近一次 setWet 的值；探针读它，避免去翻场景图判断"伞有没有撑开" */
  let wetAmount = 0;

  for (const def of list) {
    const params: AvatarParams = { ...DEFAULT_AVATAR, name: def.name, ...def.appearance };
    const handle = createAvatar(params);
    const start = def.route[0];
    const pos = new THREE.Vector3(start[0], 0, start[1]);
    handle.group.position.copy(pos);
    // 伞挂在人物 group 上：人物转身、走路，伞跟着，不必单独维护朝向
    const umbrella = makeUmbrella();
    handle.group.add(umbrella.group);
    umbrellas.push(umbrella);
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
    setWet(amount) {
      const a = Math.max(0, Math.min(1, amount));
      wetAmount = a;
      // 透明度为 0 的物体仍会走一遍绘制调用，低于阈值直接整把藏起来
      const visible = a > 0.02;
      for (const umbrella of umbrellas) {
        umbrella.group.visible = visible;
        for (const mat of umbrella.mats) {
          mat.opacity = a;
        }
      }
    },
    wetOpacity: () => wetAmount,
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
      for (const umbrella of umbrellas) {
        umbrella.group.removeFromParent();
        for (const mat of umbrella.mats) {
          mat.dispose();
        }
        for (const child of [...umbrella.group.children]) {
          if (child instanceof THREE.Mesh) {
            child.geometry.dispose();
          }
        }
      }
      umbrellas.length = 0;
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
    '能替主公翻文档、查接口与配置，说不清的会直说「这个我查不到」。',
  greeting: '主公想问什么？城里的账、路上的人、殿里的文书，我都能去看一眼。',
  lines: [
    '我游得快，主公稍等，我去各殿打听一圈。',
    '这条路我熟——御道直走，尽头就是数枢殿。',
    '眼下连不上中枢，我先陪主公走一走。',
  ],
};
