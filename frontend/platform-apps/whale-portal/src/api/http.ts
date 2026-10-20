// http.ts —— 极简 fetch 封装（门户子应用，不引入额外依赖）
//
// 后端统一返回 `Result` 信封：{ code: number; message: string; data: T }
//   - HTTP 状态恒 200，业务码在 body.code；
//   - code === 0 表示成功（取 body.data），否则 throw new Error(body.message)。
// token 从 localStorage 读取（key 与基座 whale-platform 保持一致：whale_token），
// 拼到 `Authorization: Bearer <token>` 头。
// 前端统一使用相对路径请求（如 `/workflow/task/page`），
// 由 dev server / 生产 Nginx 经网关 8080 代理到对应后端服务。

/** 基座 token 存储 key（对齐 frontend/platform-apps/whale-platform/src/api/auth.ts） */
const TOKEN_STORAGE_KEY = 'whale_token';

/** 后端统一响应信封 */
export interface Result<T = unknown> {
  code: number;
  message: string;
  data: T;
}

/** 读取当前登录 token（未登录时为 null） */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY);
}

/** 发起请求并解包 Result 信封：HTTP 异常或业务码非 0 时抛错。 */
export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const token = getToken();
  const response = await fetch(url, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init?.headers ?? {}),
    },
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  const body = (await response.json()) as Result<T>;
  if (body.code !== 0) {
    throw new Error(body.message || `业务错误 code=${body.code}`);
  }
  return body.data;
}

/** 常用方法快捷封装 */
export const http = {
  get: <T>(url: string) => request<T>(url),
  post: <T>(url: string, body?: unknown) =>
    request<T>(url, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(url: string, body?: unknown) =>
    request<T>(url, { method: 'PUT', body: JSON.stringify(body) }),
  del: <T>(url: string) => request<T>(url, { method: 'DELETE' }),
};
