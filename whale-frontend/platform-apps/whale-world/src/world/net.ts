/* ==========================================================================
 * 世界频道 —— 多人同屏的客户端
 *
 * 传输层是**可替换**的，且默认安全降级：
 *   1) 起手用本地模拟（mode: 'local'）把「同屏有几个人、怎么插值、怎么显示」跑通，
 *      这样单机开发、没登录、网关没起时画面依然有人，不会白屏；
 *   2) 一旦拿到 token 就换一次性 ticket 连 `/im/world`，收到全量快照即切到 mode: 'ws'，
 *      peers 直接来自服务端，本地模拟自动停用；
 *   3) 连接失败按退避重试，重试期间继续用本地模拟，不阻塞用户。
 *
 * 协议（与 whale-im `WorldFrames` 一一对应）：
 *   上行 world.join    { type, sceneKey, name, robe, x, z, yaw }   进场景一次
 *   上行 world.frame   { type, sceneKey, x, z, yaw, speed }        10Hz，仅位置朝向
 *   下行 world.snapshot{ sceneKey, selfId, peers[] }               兴趣区内全量（120m）
 *   下行 world.delta   { peers[] }                                 服务端 5Hz 聚合后的增量
 *   下行 world.leave   { ids[] }                                   离场
 * 服务端只做「按场景 + 距离裁剪」的转发，不落库。
 *
 * 安全口径：JWT 不进 WS URL —— 沿用 IM 的做法，先 POST /im/ws-ticket 换 60 秒一次性
 * ticket，再用 `?ticket=` 升级连接。
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

/** 一句同城喊话 */
export interface WorldSay {
  /** 发言人 id（本地模拟是 peer-xxx，联机是用户 id） */
  from: string;
  name: string;
  text: string;
  /** 发言时的世界坐标：气泡挂在这里，不跟着人飘 */
  x: number;
  z: number;
  at: number;
  /** 是不是自己说的（自己说的话气泡用另一种颜色） */
  mine: boolean;
}

export interface WorldChannel {
  /** local：本地模拟；ws：已连上服务端 */
  readonly mode: 'local' | 'ws';
  readonly peers: PeerState[];
  /** 每帧推进（本地模拟）/ 应用服务端快照（ws 模式） */
  update: (dt: number) => void;
  /** 上报自身位置（内部按 10Hz 节流，ws 模式下才真正发送） */
  report: (x: number, z: number, yaw: number, speed: number) => void;
  /** 改动自身档案（改名/换衣）：已在场景内时重新 join 一次，让别人看到新形象 */
  setSelf: (self: { name?: string; robe?: string }) => void;
  /**
   * 说一句（同城喊话）。
   *
   * @param text 内容
   * @returns 是否已发出（本地模拟恒为 true；联机未连上时为 false，由调用方决定是否本地回显）
   */
  say: (text: string) => boolean;
  dispose: () => void;
}

export interface WorldChannelOptions {
  /** 读取 accessToken（基座通过 qiankun props 注入）；缺省则只跑本地模拟 */
  getToken?: () => string | null;
  /** 场景标识，对应库里 sc_scene.scene_key */
  sceneKey?: string;
  /** 自身显示名与衣色（别人看到的样子） */
  self?: { name?: string; robe?: string };
  /** 上行帧率（Hz），默认 10 */
  hz?: number;
  /** 收到喊话（含自己说的回显） */
  onSay?: (message: WorldSay) => void;
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

/** 下行帧的最小结构（多余字段忽略，缺失即视为无操作） */
interface InboundFrame {
  type?: string;
  peers?: unknown;
  ids?: unknown;
  code?: string;
  message?: string;
  from?: unknown;
  name?: unknown;
  text?: unknown;
  x?: unknown;
  z?: unknown;
  at?: unknown;
  selfId?: unknown;
}

/** 上行帧间隔（毫秒） */
const DEFAULT_HZ = 10;

/** 离场兜底：超过该时长没有增量的远端玩家从画面移除（服务端断开时自愈） */
const REMOTE_TIMEOUT = 15_000;

/** 重连退避（毫秒） */
const RETRY_DELAYS = [1500, 4000, 9000];

/** 本地模拟的"路人闲话"：单机时也有声气，否则"多人同屏"看着像空城 */
const LOCAL_SAY_LINES = [
  '主公万安。',
  '今日城里热闹得很。',
  '听说户部在盘账，走一趟看看？',
  '数枢殿的灯还亮着呢。',
  '这雨要下到几时…',
  '刚从市舶司回来，货都出清了。',
  '兵部那边好像响了号子。',
  '御道上风大，主公慢行。',
];

/** 本地模拟的"其他玩家"：分布在环路、内环与中轴，速度略有差异 */
function seedPeers(): LocalPeer[] {
  return [
    {
      id: 'peer-yun',
      name: '云中客',
      robe: '#2b4a63',
      kind: 'circle',
      radius: 42,
      angle: 0.6,
      omega: 0.028,
      x: 0,
      z: 0,
      yaw: 0,
      speed: 2.6,
    },
    {
      id: 'peer-aRUO',
      name: '阿箬',
      robe: '#a3435a',
      kind: 'circle',
      radius: 31,
      angle: 3.4,
      omega: -0.036,
      x: 0,
      z: 0,
      yaw: 0,
      speed: 2.2,
    },
    {
      id: 'peer-nanfeng',
      name: '南风',
      robe: '#8f3a2c',
      kind: 'line',
      from: [3.4, 78],
      to: [3.4, 36],
      progress: 0.3,
      direction: 1,
      x: 0,
      z: 0,
      yaw: 0,
      speed: 3.0,
    },
    {
      id: 'peer-changle',
      name: '长乐',
      robe: '#3f6b57',
      kind: 'circle',
      radius: 85,
      angle: 5.1,
      omega: -0.02,
      x: 0,
      z: 0,
      yaw: 0,
      speed: 2.4,
    },
  ];
}

/**
 * 建立世界频道。
 *
 * @param opts 可选配置（token 读取、场景、自身档案、上行帧率）
 * @returns 世界频道句柄
 */
export function createWorldChannel(opts: WorldChannelOptions = {}): WorldChannel {
  const sceneKey = opts.sceneKey ?? 'bluewhale-city-3d';
  const hz = Math.min(30, Math.max(1, opts.hz ?? DEFAULT_HZ));
  const sendInterval = 1000 / hz;

  /* ---------------- 本地模拟 ---------------- */
  const localPeers = seedPeers();

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

  for (const p of localPeers) {
    if (p.kind === 'circle') {
      applyCircle(p, 0);
    } else {
      applyLine(p, 0);
    }
  }

  /* ---------------- 联机状态 ---------------- */
  let mode: 'local' | 'ws' = 'local';
  let socket: WebSocket | null = null;
  let closed = false;
  let retryIndex = 0;
  let retryTimer = 0;
  let connecting = false;
  let lastSend = 0;

  /** 远端玩家：current 用于平滑，target 来自服务端最新增量 */
  const remote = new Map<
    string,
    {
      peer: PeerState;
      tx: number;
      tz: number;
      tyaw: number;
      seen: number;
    }
  >();

  const now = () => Date.now();

  /** 上报缓存：服务端没连上时也记录，连上后立即带上最新位置 */
  const self = {
    x: 0,
    z: 0,
    yaw: 0,
    speed: 0,
    name: opts.self?.name ?? '行者',
    robe: opts.self?.robe ?? '#2b4a63',
  };

  /** 自己在服务端的用户 id（snapshot 下发），用于区分"自己说的"与"别人说的" */
  let selfId = '';

  /** 本地模拟的路人闲话倒计时（毫秒） */
  let localSayTimer = 14_000 + Math.random() * 12_000;

  const emitSay = (message: WorldSay) => {
    opts.onSay?.(message);
  };

  /**
   * 换一次性 ticket。
   *
   * <p>用裸 fetch 而不是共享 http 客户端：whale-world 刻意不引入后端请求层，
   * 唯一的网络交互就是这一个换票接口，保持 3D 子应用与 2D 的 API 面完全隔离。</p>
   */
  const fetchTicket = async (): Promise<string | null> => {
    const token = opts.getToken?.();
    if (!token) {
      return null;
    }
    try {
      const response = await fetch('/im/v1/ws-ticket', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      });
      if (!response.ok) {
        return null;
      }
      const payload = (await response.json()) as { code?: number; data?: { ticket?: string } };
      if (payload.code !== 0 || !payload.data?.ticket) {
        return null;
      }
      return payload.data.ticket;
    } catch {
      return null;
    }
  };

  const wsUrl = (ticket: string) => {
    const configured = import.meta.env.VITE_WORLD_WS_URL as string | undefined;
    const base =
      configured && configured.length > 0
        ? configured
        : `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/im/world`;
    return `${base}?ticket=${encodeURIComponent(ticket)}`;
  };

  const send = (payload: Record<string, unknown>) => {
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      return;
    }
    socket.send(JSON.stringify(payload));
  };

  const applyPeers = (list: unknown) => {
    if (!Array.isArray(list)) {
      return;
    }
    const stamp = now();
    for (const raw of list) {
      if (!raw || typeof raw !== 'object') {
        continue;
      }
      const item = raw as Record<string, unknown>;
      const id = String(item.id ?? '');
      if (!id) {
        continue;
      }
      const x = Number(item.x ?? 0);
      const z = Number(item.z ?? 0);
      const yaw = Number(item.yaw ?? 0);
      const speed = Number(item.speed ?? 0);
      const existing = remote.get(id);
      if (existing) {
        existing.tx = x;
        existing.tz = z;
        existing.tyaw = yaw;
        existing.peer.speed = speed;
        existing.seen = stamp;
        continue;
      }
      remote.set(id, {
        // 新玩家从目标点出生，避免从原点"飞"过来
        peer: {
          id,
          name: String(item.name ?? '过客'),
          robe: String(item.robe ?? '#2b4a63'),
          x,
          z,
          yaw,
          speed,
        },
        tx: x,
        tz: z,
        tyaw: yaw,
        seen: stamp,
      });
    }
  };

  const dropPeers = (ids: unknown) => {
    if (!Array.isArray(ids)) {
      return;
    }
    for (const id of ids) {
      remote.delete(String(id));
    }
  };

  const scheduleRetry = () => {
    if (closed) {
      return;
    }
    const delay = RETRY_DELAYS[Math.min(retryIndex, RETRY_DELAYS.length - 1)];
    retryIndex += 1;
    retryTimer = window.setTimeout(() => {
      void connect();
    }, delay);
  };

  const connect = async () => {
    if (closed || connecting || socket) {
      return;
    }
    connecting = true;
    const ticket = await fetchTicket();
    if (!ticket) {
      connecting = false;
      // 没登录 / 网关没起：继续本地模拟，别刷屏重试
      return;
    }
    let ws: WebSocket;
    try {
      ws = new WebSocket(wsUrl(ticket));
    } catch {
      connecting = false;
      scheduleRetry();
      return;
    }
    socket = ws;

    ws.onopen = () => {
      connecting = false;
      retryIndex = 0;
      send({
        type: 'world.join',
        sceneKey,
        name: self.name,
        robe: self.robe,
        x: self.x,
        z: self.z,
        yaw: self.yaw,
      });
    };

    ws.onmessage = (event) => {
      let frame: InboundFrame | null = null;
      try {
        frame = JSON.parse(String(event.data)) as InboundFrame;
      } catch {
        return;
      }
      if (!frame) {
        return;
      }
      if (frame.type === 'world.snapshot') {
        // 全量：先按快照重置，再切模式（避免旧模拟玩家残留）
        remote.clear();
        applyPeers(frame.peers);
        selfId = String(frame.selfId ?? '');
        mode = 'ws';
        return;
      }
      if (frame.type === 'world.delta') {
        applyPeers(frame.peers);
        return;
      }
      if (frame.type === 'world.say') {
        const from = String(frame.from ?? '');
        const text = String(frame.text ?? '');
        if (!from || !text) {
          return;
        }
        emitSay({
          from,
          name: String(frame.name ?? '过客'),
          text,
          x: Number(frame.x ?? 0),
          z: Number(frame.z ?? 0),
          at: Number(frame.at ?? now()),
          mine: from === selfId,
        });
        return;
      }
      if (frame.type === 'world.leave') {
        dropPeers(frame.ids);
        return;
      }
      // world.error：协议错误只影响联机，不影响本地画面，保留日志便于排查
      if (frame.type === 'world.error') {
        console.warn('[world] 服务端拒绝帧', frame);
      }
    };

    ws.onclose = () => {
      socket = null;
      connecting = false;
      if (mode === 'ws') {
        // 掉线退回本地模拟，画面不至于空场
        mode = 'local';
        remote.clear();
      }
      scheduleRetry();
    };

    ws.onerror = () => {
      // onclose 会紧随其后，统一在那里处理重连
    };
  };

  void connect();

  return {
    get mode() {
      return mode;
    },
    get peers(): PeerState[] {
      if (mode === 'local') {
        return localPeers;
      }
      return Array.from(remote.values(), (item) => item.peer);
    },
    update(dt) {
      if (mode === 'local') {
        for (const p of localPeers) {
          if (p.kind === 'circle') {
            applyCircle(p, dt);
          } else {
            applyLine(p, dt);
          }
        }
        // 路人偶尔说一句：单机开发时也要有"城里有人"的感觉，
        // 间隔给得比较长（20–40 秒），否则一进场就吵得像菜市场
        localSayTimer -= dt * 1_000;
        if (localSayTimer <= 0 && localPeers.length > 0) {
          localSayTimer = 20_000 + Math.random() * 20_000;
          const speaker = localPeers[Math.floor(Math.random() * localPeers.length)];
          emitSay({
            from: speaker.id,
            name: speaker.name,
            text: LOCAL_SAY_LINES[Math.floor(Math.random() * LOCAL_SAY_LINES.length)],
            x: speaker.x,
            z: speaker.z,
            at: now(),
            mine: false,
          });
        }
        return;
      }
      // 远端玩家做线性插值：服务端 5Hz 下发，客户端 60fps 渲染
      const stamp = now();
      const k = Math.min(1, dt * 8);
      for (const [id, item] of remote) {
        if (stamp - item.seen > REMOTE_TIMEOUT) {
          remote.delete(id);
          continue;
        }
        const peer = item.peer;
        peer.x += (item.tx - peer.x) * k;
        peer.z += (item.tz - peer.z) * k;
        // 朝向走最短弧，避免从 -π 到 π 时整个人原地旋转
        let delta = item.tyaw - peer.yaw;
        while (delta > Math.PI) {
          delta -= Math.PI * 2;
        }
        while (delta < -Math.PI) {
          delta += Math.PI * 2;
        }
        peer.yaw += delta * k;
      }
    },
    report(x, z, yaw, speed) {
      self.x = x;
      self.z = z;
      self.yaw = yaw;
      self.speed = speed;
      if (mode !== 'ws') {
        return;
      }
      const stamp = now();
      if (stamp - lastSend < sendInterval) {
        return;
      }
      lastSend = stamp;
      send({ type: 'world.frame', sceneKey, x, z, yaw, speed });
    },
    setSelf(next) {
      if (next.name) {
        self.name = next.name;
      }
      if (next.robe) {
        self.robe = next.robe;
      }
      if (mode === 'ws') {
        send({
          type: 'world.join',
          sceneKey,
          name: self.name,
          robe: self.robe,
          x: self.x,
          z: self.z,
          yaw: self.yaw,
        });
      }
    },
    say(text) {
      const content = text.trim();
      if (!content) {
        return false;
      }
      if (mode === 'ws') {
        // 由服务端回显：本地先显示会让"说了但别人没听见"看不出来
        send({ type: 'world.say', sceneKey, text: content });
        return true;
      }
      emitSay({
        from: 'self',
        name: self.name,
        text: content,
        x: self.x,
        z: self.z,
        at: now(),
        mine: true,
      });
      return true;
    },
    dispose() {
      closed = true;
      window.clearTimeout(retryTimer);
      if (socket) {
        socket.onclose = null;
        socket.onmessage = null;
        socket.close();
        socket = null;
      }
      remote.clear();
      localPeers.length = 0;
    },
  };
}
