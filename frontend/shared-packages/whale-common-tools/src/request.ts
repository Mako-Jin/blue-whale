// 请求工具占位 - 后续封装 fetch / axios
// TODO: 根据项目实际需求实现请求拦截、错误处理、token 注入等

export interface RequestConfig extends RequestInit {
  params?: Record<string, unknown>;
  timeout?: number;
}

export async function request<T = unknown>(url: string, config: RequestConfig = {}): Promise<T> {
  // 占位实现
  const { params, timeout = 10000, ...rest } = config;

  const queryString = params
    ? '?' + new URLSearchParams(params as Record<string, string>).toString()
    : '';

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);

  try {
    const response = await fetch(url + queryString, {
      ...rest,
      signal: controller.signal,
    });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    return (await response.json()) as T;
  } finally {
    clearTimeout(timer);
  }
}

export const http = {
  get: <T = unknown>(url: string, config?: RequestConfig) =>
    request<T>(url, { ...config, method: 'GET' }),
  post: <T = unknown>(url: string, body?: unknown, config?: RequestConfig) =>
    request<T>(url, { ...config, method: 'POST', body: body ? JSON.stringify(body) : undefined }),
  put: <T = unknown>(url: string, body?: unknown, config?: RequestConfig) =>
    request<T>(url, { ...config, method: 'PUT', body: body ? JSON.stringify(body) : undefined }),
  delete: <T = unknown>(url: string, config?: RequestConfig) =>
    request<T>(url, { ...config, method: 'DELETE' }),
};
