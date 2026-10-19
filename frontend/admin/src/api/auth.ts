// whale-auth 真实认证 API 客户端（经网关转发）
// 后端统一响应体：{ code, message, data }，code=0 表示成功
// 本模块是「登出令牌黑名单」前端联调入口（REQ-SEC-003）

const ACCESS_TOKEN_KEY = 'whale_admin_access_token';
const REFRESH_TOKEN_KEY = 'whale_admin_refresh_token';
const USER_KEY = 'whale_admin_user';

// 生产由 Nginx 同源反代；开发由 vite proxy 转发，故默认同源相对路径
const API_BASE: string = import.meta.env.VITE_API_BASE_URL ?? '';

export interface UserInfo {
  userId?: number | string;
  username: string;
  nickname: string;
  roles: string[];
  permissions?: string[];
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  username: string;
  nickname: string;
  roles: string[];
}

export interface AuthMeResponse {
  userId: number | string;
  username: string;
  nickname: string;
  roles: string[];
  permissions: string[];
  menus: unknown[];
}

interface Result<T> {
  code: number;
  message: string;
  data: T;
}

export class ApiError extends Error {
  readonly code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
  }
}

interface FetchOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  auth?: boolean;
}

async function doFetch<T>(path: string, options: FetchOptions = {}): Promise<T> {
  const { body, auth = true, headers, ...rest } = options;
  const finalHeaders = new Headers(headers);
  if (body !== undefined && !finalHeaders.has('Content-Type')) {
    finalHeaders.set('Content-Type', 'application/json');
  }
  if (auth) {
    const token = getToken();
    if (token) {
      finalHeaders.set('Authorization', `Bearer ${token}`);
    }
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...rest,
    headers: finalHeaders,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  const text = await response.text();
  let payload: Result<T> | null = null;
  if (text) {
    try {
      payload = JSON.parse(text) as Result<T>;
    } catch {
      payload = null;
    }
  }

  // 401：令牌缺失/过期/已登出（黑名单命中）→ 清本地态并抛错，由路由守卫重定向登录
  if (response.status === 401) {
    clearAuth();
    throw new ApiError(401, payload?.message ?? '登录已失效，请重新登录');
  }
  if (!response.ok) {
    throw new ApiError(
      payload?.code ?? response.status,
      payload?.message ?? `请求失败（HTTP ${response.status}）`,
    );
  }
  if (payload && typeof payload.code === 'number' && payload.code !== 0) {
    throw new ApiError(payload.code, payload.message ?? '请求失败');
  }
  return (payload ? payload.data : undefined) as T;
}

export function getToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function getCurrentUser(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as UserInfo;
  } catch {
    return null;
  }
}

export function clearAuth(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

function persist(data: LoginResponse): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken);
  const user: UserInfo = {
    username: data.username,
    nickname: data.nickname,
    roles: data.roles ?? [],
  };
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

// 登录：POST /auth/login（网关白名单，免 JWT）
export async function login(username: string, password: string): Promise<LoginResponse> {
  const data = await doFetch<LoginResponse>('/auth/login', {
    method: 'POST',
    body: { username, password },
    auth: false,
  });
  persist(data);
  return data;
}

// 当前登录用户：GET /auth/me（需 accessToken）——用于校验令牌有效性
export async function fetchMe(): Promise<AuthMeResponse> {
  const me = await doFetch<AuthMeResponse>('/auth/me', { method: 'GET' });
  const user = getCurrentUser();
  if (user) {
    const merged: UserInfo = {
      ...user,
      userId: me.userId,
      roles: me.roles,
      permissions: me.permissions,
    };
    localStorage.setItem(USER_KEY, JSON.stringify(merged));
  }
  return me;
}

// 登出：POST /auth/logout（需 accessToken）——后端将 accessToken 与 refreshToken 写入 Redis 黑名单
export async function logout(): Promise<void> {
  try {
    await doFetch<void>('/auth/logout', {
      method: 'POST',
      body: { refreshToken: getRefreshToken() },
    });
  } catch {
    // 登出接口异常（离线/网络故障）不阻断本地登出，保证用户始终能退出
  } finally {
    clearAuth();
  }
}
