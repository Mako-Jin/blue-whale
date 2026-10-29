// 实时协作 WS ticket：POST /doc/ws-ticket
//
// 浏览器 WebSocket 无法携带 Authorization 头，因此先用标准 JWT 换取 60 秒一次性 ticket，
// 再以 `/doc/ws?ticket=...&clientId=...` 升级连接；JWT 不出现在 URL 中。

import { http } from './client';

/** WS ticket 响应（对齐后端 DocWsTicketVO） */
export interface DocWsTicket {
  ticket: string;
  expiresIn: number;
}

export const collabApi = {
  /** 申请页面协作一次性凭证 */
  ticket: (pageId: number) => http.post<DocWsTicket>('/doc/ws-ticket', { pageId }),
};

/** 构造协作 WS 地址（与网关 /doc/ws 路由一致） */
export function buildCollabWsUrl(ticket: string, clientId: string): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const query = `ticket=${encodeURIComponent(ticket)}&clientId=${encodeURIComponent(clientId)}`;
  return `${protocol}//${window.location.host}/doc/ws?${query}`;
}
