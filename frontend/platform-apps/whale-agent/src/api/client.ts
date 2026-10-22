// 统一 HTTP 客户端：基于 @bluewhale/http，复用 workflow-designer 配置，并导出 SSE 流式请求
// 生产由 Nginx 同源反代；开发由 vite proxy 转发，故默认同源相对路径

import { createHttpClient } from '@bluewhale/http';
import { clearAuth, getRefreshToken, getToken, saveLogin, type LoginResponse } from './token';

const API_BASE: string = import.meta.env.VITE_API_BASE_URL ?? '';

const client = createHttpClient<LoginResponse>({
  baseUrl: API_BASE,
  getToken,
  getRefreshToken,
  saveLogin,
  clearAuth,
});

export { ApiError } from '@bluewhale/http';
export type { HttpOptions, PageResult, Result, SseEventHandler } from '@bluewhale/http';

export const { http, get, post, put, del, refreshSession, streamSse } = client;
