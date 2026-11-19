// 统一 HTTP 客户端：基于 @bluewhale/http，注入本应用登录态（401 单飞续期）
// 生产由 Nginx 同源反代；开发由 vite proxy 转发，故默认同源相对路径

import { createHttpClient } from '@bluewhale/http';
import { clearAuth, getCurrentUser, getToken, saveLogin, type LoginResponse } from './token';

const API_BASE: string = import.meta.env.VITE_API_BASE_URL ?? '';

// 二次认证弹窗由 main.tsx 注入（避免 client ↔ reauth 的模块循环依赖）
let reauthPrompt: (() => Promise<string | null>) | null = null;

/**
 * 注册二次认证弹窗（Wave 6.22）：危险操作返回 428/42801 时调用
 *
 * @param prompt 返回 step-up 凭证；用户取消返回 null
 */
export function setReauthPrompt(prompt: () => Promise<string | null>): void {
  reauthPrompt = prompt;
}

const client = createHttpClient<LoginResponse>({
  baseUrl: API_BASE,
  getToken,
  getTenantId: () => getCurrentUser()?.tenantId,
  saveLogin,
  clearAuth,
  onReauthRequired: () => (reauthPrompt ? reauthPrompt() : Promise.resolve(null)),
});

export { ApiError } from '@bluewhale/http';
export type { HttpOptions, PageResult, Result } from '@bluewhale/http';

export const { http, get, post, put, del, downloadBlob, refreshSession } = client;
