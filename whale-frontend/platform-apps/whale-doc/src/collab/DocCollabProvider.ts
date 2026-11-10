// 文档实时协作自定义 provider（JSON 协议）
//
// 与后端 /doc/ws 对应：服务端只中继 base64 Yjs 更新（update/awareness），不解析正文；
// 连接建立时下发 Redis 更新日志，日志为空时由首个客户端用 REST 正文 seed 全量状态；
// leader 客户端每 snapshotSeconds 秒提交完整正文快照，REST 保存由 40902 兜底拦截。
//
// 选择自定义 provider 而非 y-websocket 标准二进制协议，是为了复用现有 JWT/ticket、
// JSON 帧与后端跨实例广播，同时保持与 y-prosemirror / TipTap Collaboration 兼容。

import * as Y from 'yjs';
import { Awareness, applyAwarenessUpdate, encodeAwarenessUpdate } from 'y-protocols/awareness';
import { prosemirrorJSONToYDoc } from 'y-prosemirror';
import type { Schema } from '@tiptap/pm/model';
import type { JSONContent } from '@tiptap/react';
import { buildCollabWsUrl, collabApi } from '../api/collab';

/** 远端更新来源标识 */
const REMOTE_ORIGIN = 'doc-collab-remote';

/** seed 更新来源标识 */
const SEED_ORIGIN = 'doc-collab-seed';

/** 标题写入来源标识 */
const TITLE_ORIGIN = 'doc-collab-title';

/** 心跳间隔 */
const HEARTBEAT_MS = 25_000;

/** 无消息判定断线阈值 */
const STALE_MS = 75_000;

/** 最大重连次数（超过后回退 M1 自动保存） */
const MAX_RECONNECT = 3;

/** 在线用户（对齐后端 DocPresenceUser） */
export interface CollabUser {
  clientId: string;
  userId: number;
  name: string;
  color: string;
  lastSeen: number;
}

/** ready 帧（对齐后端 DocCollabFrames.ready） */
export interface CollabReadyPayload {
  pageId: number;
  clientId: string;
  username?: string;
  title: string;
  revision: number;
  status?: string;
  contentJson?: string | null;
  updates: string[];
  leader: boolean;
  snapshotSeconds: number;
  users: CollabUser[];
  /** 是否重连后的 ready */
  isReconnect: boolean;
}

/** 协作连接状态 */
export type CollabStatus = 'connecting' | 'connected' | 'reconnecting' | 'failed' | 'closed';

/** provider 回调 */
export interface CollabCallbacks {
  onStatus?: (status: CollabStatus) => void;
  onReady?: (payload: CollabReadyPayload) => void;
  onPresence?: (users: CollabUser[]) => void;
  onLeader?: (leader: boolean) => void;
  onSnapshotAck?: (revision: number, savedAt: string | null, skipped: boolean) => void;
  /** 标题随共享 Y.Text 变化（含远端改名） */
  onTitle?: (title: string) => void;
  /** 收到其他客户端的实时 Yjs 更新（不含 ready 重放），用于 leader 标记待快照 */
  onRemoteUpdate?: () => void;
  onError?: (message: string) => void;
}

/** base64 → Uint8Array（非法输入返回 null） */
function decodeBase64(value: string): Uint8Array | null {
  try {
    const binary = atob(value);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) {
      bytes[index] = binary.charCodeAt(index);
    }
    return bytes;
  } catch {
    return null;
  }
}

/** Uint8Array → base64（分块避免大数组参数栈溢出） */
function encodeBase64(bytes: Uint8Array): string {
  let binary = '';
  const chunk = 0x8000;
  for (let index = 0; index < bytes.length; index += chunk) {
    binary += String.fromCharCode(...bytes.subarray(index, index + chunk));
  }
  return btoa(binary);
}

/** 生成客户端ID */
function newClientId(): string {
  return crypto.randomUUID
    ? crypto.randomUUID()
    : `doc-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/** 文档协作 provider：Y.Doc + Awareness + WebSocket JSON 帧 */
export class DocCollabProvider {
  readonly doc = new Y.Doc();

  readonly awareness: Awareness;

  /** 共享标题（页面标题通过 Y.Text 同步，避免 leader 快照覆盖他人改名） */
  private readonly titleText: Y.Text;

  readonly clientId = newClientId();

  private ws: WebSocket | null = null;

  private status: CollabStatus = 'closed';

  private synced = false;

  private seedSent = false;

  private destroyed = false;

  private leader = false;

  private revision = 0;

  private snapshotInterval = 12;

  private reconnectAttempt = 0;

  private reconnectTimer: number | null = null;

  private heartbeatTimer: number | null = null;

  private lastMessageAt = Date.now();

  private failedNotified = false;

  constructor(
    private readonly pageId: number,
    private readonly user: { name: string; color: string },
    private readonly callbacks: CollabCallbacks,
  ) {
    this.awareness = new Awareness(this.doc);
    this.awareness.setLocalStateField('user', { name: user.name, color: user.color });
    this.titleText = this.doc.getText('title');
    this.titleText.observe(this.handleTitleChange);
    this.doc.on('update', this.handleDocUpdate);
    this.awareness.on('update', this.handleAwarenessUpdate);
  }

  /** 建立连接（申请 ticket 后升级 WS） */
  async connect(): Promise<void> {
    if (this.destroyed) {
      return;
    }
    this.setState(this.reconnectAttempt > 0 ? 'reconnecting' : 'connecting');
    try {
      const { ticket } = await collabApi.ticket(this.pageId);
      this.openSocket(ticket);
    } catch (error) {
      this.fail(error instanceof Error ? error.message : '实时协作暂不可用');
    }
  }

  /** 当前是否 leader（负责周期快照） */
  isLeader(): boolean {
    return this.leader;
  }

  /** 服务端建议的快照间隔（秒） */
  getSnapshotSeconds(): number {
    return this.snapshotInterval;
  }

  /** 最近一次快照后的 revision */
  getRevision(): number {
    return this.revision;
  }

  /** 当前是否已完成首次 sync */
  isSynced(): boolean {
    return this.synced;
  }

  /**
   * 用 REST 正文首次初始化 Y.Doc，并回传全量 seed 更新。
   *
   * @param schema  TipTap schema
   * @param content TipTap JSON
   * @param title   初始标题
   */
  seed(schema: Schema, content: JSONContent, title: string): void {
    if (this.seedSent || this.destroyed) {
      return;
    }
    const seedDoc = prosemirrorJSONToYDoc(schema, content, 'default');
    const seedTitle = seedDoc.getText('title');
    if (title) {
      seedTitle.insert(0, title);
    }
    const update = Y.encodeStateAsUpdate(seedDoc);
    Y.applyUpdate(this.doc, update, SEED_ORIGIN);
    this.seedSent = true;
    this.send({ type: 'seed', update: encodeBase64(update) });
  }

  /** 读取共享标题（未初始化时返回空串） */
  getTitle(): string {
    return this.titleText.toString();
  }

  /** 写入共享标题（空标题时清空；远端会同步到所有客户端） */
  setTitle(title: string): void {
    if (this.destroyed || this.titleText.toString() === title) {
      return;
    }
    this.doc.transact(() => {
      this.titleText.delete(0, this.titleText.length);
      if (title) {
        this.titleText.insert(0, title);
      }
    }, TITLE_ORIGIN);
  }

  /**
   * 作为 leader 提交完整正文快照。
   *
   * @param title 标题
   * @param contentJson TipTap JSON
   */
  sendSnapshot(title: string, contentJson: JSONContent): void {
    if (!this.synced || this.destroyed) {
      return;
    }
    this.send({ type: 'snapshot', title, contentJson, revision: this.revision });
  }

  /** 主动关闭并释放资源 */
  destroy(): void {
    this.destroyed = true;
    this.stopTimers();
    this.setState('closed');
    this.titleText.unobserve(this.handleTitleChange);
    this.doc.off('update', this.handleDocUpdate);
    this.awareness.off('update', this.handleAwarenessUpdate);
    this.awareness.destroy();
    this.doc.destroy();
    if (this.ws) {
      this.ws.onclose = null;
      this.ws.close();
      this.ws = null;
    }
  }

  /** 创建 WebSocket 并绑定事件 */
  private openSocket(ticket: string): void {
    const socket = new WebSocket(buildCollabWsUrl(ticket, this.clientId));
    this.ws = socket;
    socket.onopen = () => {
      this.reconnectAttempt = 0;
      this.lastMessageAt = Date.now();
      this.setState('connected');
      this.startHeartbeat();
      this.send({ type: 'presence', name: this.user.name, color: this.user.color });
    };
    socket.onmessage = (event) => {
      this.lastMessageAt = Date.now();
      this.handleMessage(String(event.data));
    };
    socket.onerror = () => {
      // 具体原因由 onclose / error 帧补充，避免重复提示
    };
    socket.onclose = () => {
      this.stopHeartbeat();
      if (this.destroyed) {
        return;
      }
      this.scheduleReconnect();
    };
  }

  /** 处理服务端帧 */
  private handleMessage(raw: string): void {
    let frame: Record<string, unknown>;
    try {
      frame = JSON.parse(raw) as Record<string, unknown>;
    } catch {
      return;
    }
    const type = typeof frame.type === 'string' ? frame.type : '';
    switch (type) {
      case 'ready':
        this.handleReady(frame);
        break;
      case 'update':
        this.applyRemoteUpdate(frame.update);
        this.callbacks.onRemoteUpdate?.();
        break;
      case 'awareness':
        this.applyRemoteAwareness(frame.update);
        break;
      case 'presence':
        this.callbacks.onPresence?.((frame.users as CollabUser[]) ?? []);
        break;
      case 'leader':
        this.handleLeader(frame);
        break;
      case 'snapshot-ack':
        this.handleSnapshotAck(frame);
        break;
      case 'error':
        this.callbacks.onError?.(
          typeof frame.message === 'string' ? frame.message : '协作服务返回错误',
        );
        break;
      case 'pong':
        break;
      default:
        break;
    }
  }

  /** 处理 ready：应用更新日志、广播状态、重连时回传本地全量状态 */
  private handleReady(frame: Record<string, unknown>): void {
    const wasSynced = this.synced;
    const updates = Array.isArray(frame.updates)
      ? frame.updates.filter((item): item is string => typeof item === 'string')
      : [];
    updates.forEach((update) => this.applyRemoteUpdate(update));
    this.synced = true;
    this.revision = typeof frame.revision === 'number' ? frame.revision : 0;
    this.snapshotInterval =
      typeof frame.snapshotSeconds === 'number' && frame.snapshotSeconds > 0
        ? frame.snapshotSeconds
        : 12;
    this.leader = Boolean(frame.leader);
    if (wasSynced) {
      // 重连：把本端离线期间的 Yjs 状态合并回共享文档（Yjs 更新幂等，不会覆盖他人删除）
      const fullState = Y.encodeStateAsUpdate(this.doc);
      this.send({ type: 'update', update: encodeBase64(fullState) });
    }
    const payload: CollabReadyPayload = {
      pageId: typeof frame.pageId === 'number' ? frame.pageId : this.pageId,
      clientId: this.clientId,
      username: typeof frame.username === 'string' ? frame.username : this.user.name,
      title: typeof frame.title === 'string' ? frame.title : '',
      revision: this.revision,
      status: typeof frame.status === 'string' ? frame.status : undefined,
      contentJson: typeof frame.contentJson === 'string' ? frame.contentJson : null,
      updates,
      leader: this.leader,
      snapshotSeconds: this.snapshotInterval,
      users: Array.isArray(frame.users) ? (frame.users as CollabUser[]) : [],
      isReconnect: wasSynced,
    };
    this.callbacks.onReady?.(payload);
    this.callbacks.onLeader?.(this.leader);
    this.callbacks.onPresence?.(payload.users);
  }

  /** 应用远端 Yjs 更新 */
  private applyRemoteUpdate(value: unknown): void {
    if (typeof value !== 'string') {
      return;
    }
    const update = decodeBase64(value);
    if (!update) {
      return;
    }
    Y.applyUpdate(this.doc, update, REMOTE_ORIGIN);
  }

  /** 应用远端 awareness 更新 */
  private applyRemoteAwareness(value: unknown): void {
    if (typeof value !== 'string') {
      return;
    }
    const update = decodeBase64(value);
    if (!update) {
      return;
    }
    applyAwarenessUpdate(this.awareness, update, REMOTE_ORIGIN);
  }

  /** 处理 leader 帧：只关心本客户端 */
  private handleLeader(frame: Record<string, unknown>): void {
    if (frame.clientId !== this.clientId) {
      return;
    }
    this.leader = Boolean(frame.leader);
    this.callbacks.onLeader?.(this.leader);
  }

  /** 处理快照确认 */
  private handleSnapshotAck(frame: Record<string, unknown>): void {
    if (typeof frame.revision === 'number') {
      this.revision = frame.revision;
    }
    const savedAt = typeof frame.savedAt === 'string' ? frame.savedAt : null;
    this.callbacks.onSnapshotAck?.(this.revision, savedAt, Boolean(frame.skipped));
  }

  /** 共享标题变化（本地或远端） */
  private handleTitleChange = (): void => {
    this.callbacks.onTitle?.(this.getTitle());
  };

  /** Y.Doc 本地更新上行 */
  private handleDocUpdate = (update: Uint8Array, origin: unknown): void => {
    if (origin === REMOTE_ORIGIN || origin === SEED_ORIGIN || this.destroyed) {
      return;
    }
    this.send({ type: 'update', update: encodeBase64(update) });
  };

  /** Awareness 本地更新上行 */
  private handleAwarenessUpdate = (
    changes: { added: number[]; updated: number[]; removed: number[] },
    origin: unknown,
  ): void => {
    if (origin === REMOTE_ORIGIN || this.destroyed) {
      return;
    }
    const clients = [...changes.added, ...changes.updated, ...changes.removed];
    if (clients.length === 0) {
      return;
    }
    this.send({
      type: 'awareness',
      update: encodeBase64(encodeAwarenessUpdate(this.awareness, clients)),
    });
  };

  /** 心跳：刷新 presence、续期 leader、补位、断线检测 */
  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.heartbeatTimer = window.setInterval(() => {
      if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
        return;
      }
      if (Date.now() - this.lastMessageAt > STALE_MS) {
        this.ws.close();
        return;
      }
      this.send({ type: 'ping' });
      if (!this.leader) {
        this.send({ type: 'leader-claim' });
      }
    }, HEARTBEAT_MS);
  }

  /** 停止心跳 */
  private stopHeartbeat(): void {
    if (this.heartbeatTimer !== null) {
      window.clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  /** 停止全部定时器 */
  private stopTimers(): void {
    this.stopHeartbeat();
    if (this.reconnectTimer !== null) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  /** 断线重连，超过上限回退 M1 自动保存 */
  private scheduleReconnect(): void {
    if (this.destroyed || this.failedNotified) {
      return;
    }
    if (this.reconnectAttempt >= MAX_RECONNECT) {
      this.fail('实时协作连接中断，已回退自动保存');
      return;
    }
    this.reconnectAttempt += 1;
    this.setState('reconnecting');
    const delay = Math.min(1000 * 2 ** (this.reconnectAttempt - 1), 15_000);
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null;
      void this.connect();
    }, delay);
  }

  /** 标记协作失败（由 DocEditor 回退 M1 编辑器） */
  private fail(message: string): void {
    this.stopTimers();
    this.ws?.close();
    this.ws = null;
    this.setState('failed');
    if (!this.failedNotified) {
      this.failedNotified = true;
      this.callbacks.onError?.(message);
    }
  }

  /** 更新连接状态并回调 */
  private setState(status: CollabStatus): void {
    if (this.status === status) {
      return;
    }
    this.status = status;
    this.callbacks.onStatus?.(status);
  }

  /** 发送 JSON 帧（未连接时静默丢弃） */
  private send(payload: Record<string, unknown>): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      return;
    }
    this.ws.send(JSON.stringify(payload));
  }
}
