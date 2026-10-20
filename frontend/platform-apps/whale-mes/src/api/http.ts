// http.ts —— 极简 fetch 封装骨架（供后续接入后端接口，不引入额外依赖）
//
// 后端统一返回 `Result` 信封：{ code: number; message: string; data: T }，
//   code === 0 表示成功，其余为业务错误码；解包后直接返回 data。
// 前端统一使用相对路径请求（如 `/mes/work-order/list`），
//   由 dev server / 生产 Nginx 经网关 8080 代理到对应后端服务。
// TODO: 后续接入时补充 token 注入（Authorization 头）与统一错误提示。

/** 后端统一响应信封 */
export interface Result<T = unknown> {
  code: number;
  message: string;
  data: T;
}

/** 发起请求并解包 Result 信封：成功返回 data，HTTP 异常或业务码非 0 时抛错。 */
export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
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
