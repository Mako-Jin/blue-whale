// IM WebSocket 客户端：一次性 ticket 握手、心跳、指数退避重连、帧分发
//
// 安全口径：JWT 严禁进入 WS URL；每次建连前调用 POST /im/v1/ws-ticket 换取 60s 一次性 ticket。

import { createLogger } from '@bluewhale/whale-common-tools';

const logger = createLogger('whale-im › ws');

const BASE_BACKOFF_MS = 1000;
const MAX_BACKOFF_MS = 30000;
const DEFAULT_HEARTBEAT_SECONDS = 30;

/** 服务端帧（type 为帧类型，其余字段随类型变化） */
export interface ImFrame {
  type: string;
  [key: string]: unknown;
}

export type SocketState = 'connecting' | 'open' | 'closed';

export interface ImSocketOptions {
  /** 换取一次性 ticket */
  getTicket: () => Promise<string>;
  /** 帧回调 */
  onFrame: (frame: ImFrame) => void;
  /** 连接状态变化 */
  onStateChange?: (state: SocketState) => void;
  /** 重连成功（收到 ready 帧）后回调，用于按 lastSeq 补拉 */
  onReconnect?: () => void;
}

export class ImSocket {
  private readonly options: ImSocketOptions;

  private ws: WebSocket | null = null;

  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;

  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  private attempts = 0;

  private closedByUser = false;

  private connectedOnce = false;

  constructor(options: ImSocketOptions) {
    this.options = options;
  }

  /** 建立连接（幂等：已连接时忽略） */
  connect(): void {
    if (this.ws) {
      return;
    }
    this.closedByUser = false;
    void this.open();
  }

  /** 主动关闭：不再自动重连 */
  close(): void {
    this.closedByUser = true;
    this.clearHeartbeat();
    this.clearReconnect();
    const socket = this.ws;
    this.ws = null;
    socket?.close();
    this.options.onStateChange?.('closed');
  }

  /** 发送文本帧（心跳） */
  private async open(): Promise<void> {
    if (this.closedByUser) {
      return;
    }
    this.options.onStateChange?.('connecting');
    let ticket: string;
    try {
      ticket = await this.options.getTicket();
    } catch (error) {
      logger.warn('WS ticket 获取失败', error);
      this.scheduleReconnect();
      return;
    }
    if (this.closedByUser) {
      return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = `${protocol}//${window.location.host}/im/ws?ticket=${encodeURIComponent(ticket)}`;
    let socket: WebSocket;
    try {
      socket = new WebSocket(url);
    } catch (error) {
      logger.warn('WS 建连失败', error);
      this.scheduleReconnect();
      return;
    }
    this.ws = socket;

    socket.onmessage = (event) => this.handleMessage(event.data);
    socket.onopen = () => {
      this.attempts = 0;
      this.options.onStateChange?.('open');
    };
    socket.onerror = () => {
      logger.warn('WS 传输异常');
    };
    socket.onclose = () => {
      this.ws = null;
      this.clearHeartbeat();
      this.options.onStateChange?.('closed');
      if (!this.closedByUser) {
        this.scheduleReconnect();
      }
    };
  }

  /** 解析并分发帧；ready 帧启动心跳并触发重连补拉 */
  private handleMessage(raw: unknown): void {
    if (typeof raw !== 'string') {
      return;
    }
    let frame: ImFrame;
    try {
      frame = JSON.parse(raw) as ImFrame;
    } catch {
      logger.warn('WS 帧解析失败');
      return;
    }
    if (frame.type === 'ready') {
      const heartbeatSeconds =
        typeof frame.heartbeatSeconds === 'number' && frame.heartbeatSeconds > 0
          ? frame.heartbeatSeconds
          : DEFAULT_HEARTBEAT_SECONDS;
      this.startHeartbeat(Math.max(5, Math.floor(heartbeatSeconds / 2)));
      if (this.connectedOnce) {
        this.options.onReconnect?.();
      }
      this.connectedOnce = true;
    }
    this.options.onFrame(frame);
  }

  /** 按服务端建议间隔发送 ping */
  private startHeartbeat(intervalSeconds: number): void {
    this.clearHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'ping' }));
      }
    }, intervalSeconds * 1000);
  }

  /** 退避重连：1s、2s、4s…最大 30s */
  private scheduleReconnect(): void {
    if (this.closedByUser || this.reconnectTimer) {
      return;
    }
    const delay = Math.min(MAX_BACKOFF_MS, BASE_BACKOFF_MS * 2 ** this.attempts);
    this.attempts += 1;
    logger.warn(`WS 断开，${delay}ms 后重连（第 ${this.attempts} 次）`);
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      void this.open();
    }, delay);
  }

  private clearHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private clearReconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }
}
