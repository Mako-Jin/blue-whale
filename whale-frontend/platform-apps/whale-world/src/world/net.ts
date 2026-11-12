/* ==========================================================================
 * 世界频道 —— 多人同屏的客户端骨架
 *
 * 传输层是**可替换**的：P1 用本地模拟（mode: 'local'）把「同屏有多少人、怎么
 * 插值、怎么显示」全部跑通；P2 接 whale-im 的 world 帧协议时，只要实现同样
 * 的 WorldChannel 接口，场景层一行不改。
 *
 * 规划中的协议（whale-im WebSocket，复用其握手 ticket 鉴权与连接注册表）：
 *   上行 world.frame   { x, z, yaw, speed, t }       10Hz，仅位置与朝向
 *   上行 world.join    { profileId, sceneId }        进场景时一次
 *   下行 world.snapshot{ sceneId, peers: PeerState[] } 兴趣区内全量（默认 120m）
 *   下行 world.delta   { id, x, z, yaw, speed }      5Hz，其余人增量
 *   下行 world.leave   { id }
 * 服务端只做「按场景 + 距离裁剪」的转发，不落库（位置不是业务数据）。
 * ========================================================================== */

export interface PeerState {
  id: string;
  name: string;
  /** 衣袍主色，客户端据此即时生成形象（避免传模型） */
  robe: string;
  x: number;
  z: number;
  yaw: number;
  speed: number;
}

export interface WorldChannel {
  readonly mode: 'local' | 'ws';
  readonly peers: PeerState[];
  /** 每帧推进（本地模拟）/ 应用服务端快照（ws 模式） */
  update: (dt: number) => void;
  dispose: () => void;
}

interface CirclePeer extends PeerState {
  kind: 'circle';
  radius: number;
  angle: number;
  omega: number;
}

interface LinePeer extends PeerState {
  kind: 'line';
  from: [number, number];
  to: [number, number];
  progress: number;
  direction: 1 | -1;
}

type LocalPeer = CirclePeer | LinePeer;

/** 本地模拟的"其他玩家"：分布在环路、内环与中轴，速度略有差异 */
function seedPeers(): LocalPeer[] {
  return [
    {
      id: 'peer-yun', name: '云中客', robe: '#2b4a63',
      kind: 'circle', radius: 42, angle: 0.6, omega: 0.028,
      x: 0, z: 0, yaw: 0, speed: 2.6,
    },
    {
      id: 'peer-aRUO', name: '阿箬', robe: '#a3435a',
      kind: 'circle', radius: 31, angle: 3.4, omega: -0.036,
      x: 0, z: 0, yaw: 0, speed: 2.2,
    },
    {
      id: 'peer-nanfeng', name: '南风', robe: '#8f3a2c',
      kind: 'line', from: [3.4, 78], to: [3.4, 36], progress: 0.3, direction: 1,
      x: 0, z: 0, yaw: 0, speed: 3.0,
    },
    {
      id: 'peer-changle', name: '长乐', robe: '#3f6b57',
      kind: 'circle', radius: 85, angle: 5.1, omega: -0.02,
      x: 0, z: 0, yaw: 0, speed: 2.4,
    },
  ];
}

export function createWorldChannel(): WorldChannel {
  const peers = seedPeers();

  const applyCircle = (p: CirclePeer, dt: number) => {
    p.angle += p.omega * dt * 8;
    p.x = Math.sin(p.angle) * p.radius;
    p.z = Math.cos(p.angle) * p.radius;
    // 圆周运动的切向朝向
    p.yaw = p.angle + (p.omega > 0 ? Math.PI / 2 : -Math.PI / 2);
  };

  const applyLine = (p: LinePeer, dt: number) => {
    const dx = p.to[0] - p.from[0];
    const dz = p.to[1] - p.from[1];
    const len = Math.hypot(dx, dz) || 1;
    p.progress += (p.speed * dt * p.direction) / len;
    if (p.progress > 1) {
      p.progress = 1;
      p.direction = -1;
    } else if (p.progress < 0) {
      p.progress = 0;
      p.direction = 1;
    }
    p.x = p.from[0] + dx * p.progress;
    p.z = p.from[1] + dz * p.progress;
    p.yaw = Math.atan2(dx * p.direction, dz * p.direction);
  };

  for (const p of peers) {
    if (p.kind === 'circle') {
      applyCircle(p, 0);
    } else {
      applyLine(p, 0);
    }
  }

  return {
    mode: 'local',
    peers,
    update(dt) {
      for (const p of peers) {
        if (p.kind === 'circle') {
          applyCircle(p, dt);
        } else {
          applyLine(p, dt);
        }
      }
    },
    dispose() {
      peers.length = 0;
    },
  };
}
