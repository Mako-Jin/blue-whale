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

/** 当前登录用户（localStorage 持久化结构） */
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

/** 登录/刷新接口响应 */
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  tenantId?: number | string;
  username: string;
  nickname: string;
  roles: string[];
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
