/**
 * 登录态共享类型（以 admin 全量版为准，字段尽量可选以兼容各应用子集）。
 *
 * 各应用此前在 src/api/token.ts 内逐份复制这些接口（审计 P1），现统一收口；
 * 个别应用需要更严格字段时可在本地派生自己的类型。
 */

/** 后端 MenuVO：GET /auth/me 返回的可见菜单树节点 */
export interface MenuNode {
  id: number;
  parentId?: number;
  name: string;
  path?: string;
  component?: string;
  icon?: string;
  sort?: number;
  menuType?: string;
  permissionCode?: string;
  visible?: number;
  status?: number;
  children?: MenuNode[];
}

/** 当前登录用户（localStorage 持久化结构，仅非敏感信息） */
export interface UserInfo {
  userId?: number | string;
  /** 所属租户 ID（由登录响应与 /auth/me 回写，供顶栏展示与请求头注入） */
  tenantId?: number | string;
  /** 登录时填写的租户编码（仅展示用，服务端以 JWT 为准） */
  tenantCode?: string;
  username: string;
  nickname: string;
  roles: string[];
  permissions?: string[];
  menus?: MenuNode[];
}

/**
 * 登录/刷新接口响应。
 *
 * V-03：refreshToken 改为 HttpOnly Cookie（whale_refresh_token）下发，
 * 响应体不再包含 refreshToken；CSRF 令牌经非 HttpOnly Cookie whale_csrf 下发。
 */
export interface LoginResponse {
  accessToken: string;
  tokenType?: string;
  expiresIn?: number;
  userId?: number | string;
  tenantId?: number | string;
  username: string;
  nickname: string;
  roles: string[];
  /** 首次登录/管理员重置密码后强制改密标记（T-03） */
  mustChangePassword?: boolean;
}

/** GET /auth/me 响应 */
export interface AuthMeResponse {
  tenantId?: number | string;
  userId: number | string;
  username: string;
  nickname: string;
  roles: string[];
  permissions: string[];
  menus: MenuNode[];
}
