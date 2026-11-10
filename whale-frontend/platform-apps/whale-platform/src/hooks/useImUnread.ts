// IM 未读汇总：菜单角标数据源
// WS 全局未读通道（message.new / unread.changed）+ 首连/重连/页面聚焦时 REST 校准一次，不再定时轮询

import { useEffect, useState } from 'react';
import { createLogger } from '@bluewhale/whale-common-tools';
import { get, post } from '../api/client';
import { getCurrentUser } from '../api/token';

const logger = createLogger('whale-platform › im-unread');

const BASE_BACKOFF_MS = 1_000;
const MAX_BACKOFF_MS = 30_000;
const DEFAULT_HEARTBEAT_SECONDS = 30;
const MIN_HEARTBEAT_SECONDS = 5;

/** 会话列表中仅需未读数的字段（与 whale-im ConversationVO 对齐） */
interface ConversationUnread {
  id: string;
  unread?: number;
}

interface WsTicketVO {
  ticket: string;
}

interface MessageFramePayload {
  conversationId?: string;
  senderId?: string;
}

interface ImFrame {
  type: string;
  [key: string]: unknown;
}

/**
 * 汇总 IM 未读消息数。
 *
 * 通过 `/im/ws` 订阅 `message.new`（收到他人消息即 +1）与 `unread.changed`
 * （本人已读后由服务端下发最新未读数）；断线按指数退避重连，并在首次连接、
 * 重连成功与窗口重新聚焦时各做一次 REST 校准，补回断线期间漏掉的帧。
 *
 * @param enabled 是否具备 `sys:im:chat` 权限
 * @returns 未读总数
 */
export function useImUnread(enabled: boolean): number {
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    if (!enabled) {
      setUnread(0);
      return;
    }

    let disposed = false;
    let socket: WebSocket | null = null;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
    let attempts = 0;
    const currentUserId = getCurrentUser()?.userId;
    /** 会话ID → 未读数（未知会话按增量累积，服务端下发时覆盖） */
    const counts = new Map<string, number>();

    const publish = () => {
      if (disposed) {
        return;
      }
      let total = 0;
      counts.forEach((value) => {
        total += value;
      });
      setUnread(total);
    };

    /** 全量校准：以服务端会话列表为准重建计数 */
    const reconcile = async () => {
      try {
        const list = await get<ConversationUnread[]>('/im/conversations');
        if (disposed) {
          return;
        }
        counts.clear();
        (list ?? []).forEach((item) => counts.set(item.id, item.unread ?? 0));
        publish();
      } catch (error) {
        logger.warn('未读校准失败，保留本地计数', error);
      }
    };

    const clearHeartbeat = () => {
      if (heartbeatTimer) {
        clearInterval(heartbeatTimer);
        heartbeatTimer = null;
      }
    };

    const startHeartbeat = (seconds: number) => {
      clearHeartbeat();
      heartbeatTimer = setInterval(() => {
        if (socket?.readyState === WebSocket.OPEN) {
          socket.send(JSON.stringify({ type: 'ping' }));
        }
      }, seconds * 1000);
    };

    function scheduleReconnect(): void {
      if (disposed || reconnectTimer) {
        return;
      }
      const delay = Math.min(MAX_BACKOFF_MS, BASE_BACKOFF_MS * 2 ** attempts);
      attempts += 1;
      reconnectTimer = setTimeout(() => {
        reconnectTimer = null;
        void open();
      }, delay);
    }

    const handleFrame = (frame: ImFrame) => {
      switch (frame.type) {
        case 'ready': {
          const seconds =
            typeof frame.heartbeatSeconds === 'number' && frame.heartbeatSeconds > 0
              ? Math.max(MIN_HEARTBEAT_SECONDS, Math.floor(frame.heartbeatSeconds / 2))
              : DEFAULT_HEARTBEAT_SECONDS;
          startHeartbeat(seconds);
          // 首次连接与重连都校准一次，补回断线期间漏掉的未读帧
          void reconcile();
          break;
        }
        case 'message.new': {
          const message = frame.message as MessageFramePayload | undefined;
          const conversationId = String(message?.conversationId ?? '');
          const senderId = String(message?.senderId ?? '');
          if (!conversationId || senderId === String(currentUserId ?? '')) {
            return;
          }
          counts.set(conversationId, (counts.get(conversationId) ?? 0) + 1);
          publish();
          break;
        }
        case 'unread.changed': {
          const conversationId = String(frame.conversationId ?? '');
          if (!conversationId) {
            return;
          }
          counts.set(conversationId, typeof frame.unread === 'number' ? frame.unread : 0);
          publish();
          break;
        }
        default:
          break;
      }
    };

    async function open(): Promise<void> {
      if (disposed) {
        return;
      }
      let ticket: string;
      try {
        // JWT 严禁进入 WS URL；每次建连前换取 60s 一次性 ticket
        ticket = (await post<WsTicketVO>('/im/ws-ticket')).ticket;
      } catch (error) {
        logger.warn('WS ticket 获取失败', error);
        scheduleReconnect();
        return;
      }
      if (disposed || !ticket) {
        if (!disposed) {
          scheduleReconnect();
        }
        return;
      }

      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const url = `${protocol}//${window.location.host}/im/ws?ticket=${encodeURIComponent(ticket)}`;
      let ws: WebSocket;
      try {
        ws = new WebSocket(url);
      } catch (error) {
        logger.warn('WS 建连失败', error);
        scheduleReconnect();
        return;
      }
      socket = ws;
      ws.onmessage = (event) => {
        if (typeof event.data !== 'string') {
          return;
        }
        let frame: ImFrame;
        try {
          frame = JSON.parse(event.data) as ImFrame;
        } catch {
          logger.warn('WS 帧解析失败');
          return;
        }
        handleFrame(frame);
      };
      ws.onopen = () => {
        attempts = 0;
      };
      ws.onerror = () => {
        logger.warn('WS 传输异常');
      };
      ws.onclose = () => {
        socket = null;
        clearHeartbeat();
        scheduleReconnect();
      };
    }

    // 页面回到前台时校准一次：后台标签页可能错过推送
    const onFocus = () => {
      void reconcile();
    };
    window.addEventListener('focus', onFocus);

    void reconcile();
    void open();

    return () => {
      disposed = true;
      window.removeEventListener('focus', onFocus);
      clearHeartbeat();
      if (reconnectTimer) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
      }
      const current = socket;
      socket = null;
      current?.close();
    };
  }, [enabled]);

  return unread;
}
