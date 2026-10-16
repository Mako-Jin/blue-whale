// Mock 认证 API - 后端未就绪，本地模拟登录
// 约定账号：admin / 123456

const TOKEN_STORAGE_KEY = 'whale_token';
const USER_STORAGE_KEY = 'whale_user';

export interface UserInfo {
  username: string;
  nickname: string;
  roles: string[];
}

export interface LoginResult {
  token: string;
  user: UserInfo;
}

const MOCK_ACCOUNT = { username: 'admin', password: '123456' };

const MOCK_USER: UserInfo = {
  username: 'admin',
  nickname: '系统管理员',
  roles: ['admin'],
};

// base64url 编码（兼容中文等非 ASCII 字符）
function base64UrlEncode(value: unknown): string {
  const bytes = new TextEncoder().encode(JSON.stringify(value));
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

// 生成带 exp 的假 JWT（仅供 mock 登录流程使用，无真实签名）
function createFakeJwt(user: UserInfo): string {
  const nowSeconds = Math.floor(Date.now() / 1000);
  const header = { alg: 'HS256', typ: 'JWT' };
  const payload = {
    sub: user.username,
    nickname: user.nickname,
    roles: user.roles,
    iat: nowSeconds,
    exp: nowSeconds + 24 * 60 * 60,
  };
  return [base64UrlEncode(header), base64UrlEncode(payload), base64UrlEncode('bluewhale-mock-signature')].join('.');
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY);
}

export function getCurrentUser(): UserInfo | null {
  const raw = localStorage.getItem(USER_STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as UserInfo;
  } catch {
    return null;
  }
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_STORAGE_KEY);
  localStorage.removeItem(USER_STORAGE_KEY);
}

export function logout(): void {
  clearAuth();
}

export async function login(username: string, password: string): Promise<LoginResult> {
  // 模拟网络请求延迟
  await new Promise((resolve) => setTimeout(resolve, 600));

  if (username !== MOCK_ACCOUNT.username || password !== MOCK_ACCOUNT.password) {
    throw new Error('用户名或密码错误');
  }

  const result: LoginResult = { token: createFakeJwt(MOCK_USER), user: MOCK_USER };
  localStorage.setItem(TOKEN_STORAGE_KEY, result.token);
  localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(result.user));
  return result;
}
