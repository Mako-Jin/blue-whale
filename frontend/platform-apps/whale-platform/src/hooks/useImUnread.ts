// IM 未读汇总：菜单角标数据源
// 30s 轮询 + 页面聚焦立即刷新；无权限/请求失败时静默为 0，不打扰用户

import { useEffect, useState } from 'react';
import { get } from '../api/client';

/** 会话列表中仅需未读数的字段（与 whale-im ConversationVO 对齐） */
interface ConversationUnread {
  unread?: number;
}

const POLL_INTERVAL_MS = 30_000;

/**
 * 汇总 IM 未读消息数。
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
    const refresh = async () => {
      try {
        const list = await get<ConversationUnread[]>('/im/conversations');
        if (disposed) return;
        const total = (list ?? []).reduce((sum, item) => sum + (item.unread ?? 0), 0);
        setUnread(total);
      } catch {
        if (!disposed) setUnread(0);
      }
    };
    void refresh();
    const timer = window.setInterval(refresh, POLL_INTERVAL_MS);
    const onFocus = () => void refresh();
    window.addEventListener('focus', onFocus);
    return () => {
      disposed = true;
      window.clearInterval(timer);
      window.removeEventListener('focus', onFocus);
    };
  }, [enabled]);

  return unread;
}
