import type { HttpClient } from 'common-tools/http';

import type { ApiSchemas, BodyOf, DataOf, PathOf, QueryOf } from './types';
import type { paths } from './generated/schema';

/**
 * 权限域的**实体管理**接线（用户 / 租户）。
 *
 * <h3>为什么与 `auth.ts` 分两个文件</h3>
 *
 * `auth.ts` 管的是**认证**（登录 / 刷新 / 登出 / 取当前身份），它的消费方是
 * 会话通道 `createAuthChannel`；本文件管的是**权限域实体的增删改查**，
 * 消费方是管理界面（`platform-admin`）。两者的调用时机、权限要求、失败含义都不同：
 * 认证失败意味着"你没登录"，管理接口失败可能是"你登录了但没有这个权限"（403）。
 * 放在一起就会让"谁在什么时候需要什么权限"变得模糊。
 *
 * <h3>为什么叫 access</h3>
 *
 * 后端把这一域叫 `auth`（认证 + 授权合称），路径也都在 `/api/auth/*` 下。
 * 前端若沿用同名，就会与"认证"混在一起 —— 而**授权管理**（谁能做什么）
 * 与认证是两回事，`docs/decisions/0016` 特意把它们的语义分开（401 与 403 不同）。
 * 因此这里用 `access`（访问控制）：权限域的实体管理面。
 *
 * <h3>边界</h3>
 *
 * 与 `log.ts` / `events.ts` 一样，本文件**只做接线**：路径与类型全部从契约推导，
 * 手写的只有"哪个方法对应哪条路径"以及路径参数怎么拼。
 */

/* ------------------------------------------------------------------ 操作定义 */

type UserPageOp = paths['/api/auth/users']['get'];
type UserCreateOp = paths['/api/auth/users']['post'];
type UserDetailOp = paths['/api/auth/users/{id}']['get'];
type UserResetPasswordOp = paths['/api/auth/users/{id}/password']['post'];

type TenantPageOp = paths['/api/auth/tenants']['get'];
type TenantCreateOp = paths['/api/auth/tenants']['post'];
type TenantDetailOp = paths['/api/auth/tenants/{id}']['get'];

/* ------------------------------------------------------------------ 用户 */

/**
 * 用户出参。
 *
 * <p>直接取契约模型：它与后端 `UserVO` 一样**不含任何口令字段**，
 * 因此这里不需要像日志那样手工收紧字段 —— 收紧的前提是"契约里的类型不合适"，
 * 而不是"想改一改"。
 */
export type AccessUser = ApiSchemas['UserVO'];

/** 用户分页条件（含继承自 `PageQuery` 的 `page` / `size`） */
export type AccessUserQuery = QueryOf<UserPageOp>;

/** 用户分页结果 */
export type AccessUserPage = NonNullable<DataOf<UserPageOp>>;

/** 用户新增 / 修改入参。**修改时 `password` 留空表示不改口令** */
export type AccessUserSaveRequest = BodyOf<UserCreateOp>;

/** 重置口令入参 */
export type AccessPasswordResetRequest = BodyOf<UserResetPasswordOp>;

/** 用户主键 */
export type AccessUserId = PathOf<UserDetailOp>['id'];

export interface AccessUserApi {
  /** 分页查询；不传 `tenantId` 时服务端限定为当前登录用户的租户 */
  page(query?: AccessUserQuery): Promise<AccessUserPage>;
  detail(id: AccessUserId): Promise<AccessUser>;
  create(body: AccessUserSaveRequest): Promise<AccessUser>;
  update(id: AccessUserId, body: AccessUserSaveRequest): Promise<AccessUser>;
  /** 管理员重置他人口令；重置后该用户下次登录须改密 */
  resetPassword(id: AccessUserId, body: AccessPasswordResetRequest): Promise<void>;
  /**
   * 删除用户。
   *
   * <p>方法名不叫 `delete`：`delete` 在这里虽然合法（它是属性名不是运算符），
   * 但在调用点 `api.users.delete(...)` 读起来像在删一个叫 `users` 的东西 ——
   * 管理界面的代码会被读很多次，这里优先选不歧义的写法。
   */
  remove(id: AccessUserId): Promise<void>;
}

/* ------------------------------------------------------------------ 租户 */

/** 租户出参 */
export type AccessTenant = ApiSchemas['TenantVO'];

/** 租户分页条件 */
export type AccessTenantQuery = QueryOf<TenantPageOp>;

/** 租户分页结果 */
export type AccessTenantPage = NonNullable<DataOf<TenantPageOp>>;

/** 租户新增 / 修改入参。**修改时 `tenantCode` 不生效** */
export type AccessTenantSaveRequest = BodyOf<TenantCreateOp>;

/** 租户主键 */
export type AccessTenantId = PathOf<TenantDetailOp>['id'];

export interface AccessTenantApi {
  page(query?: AccessTenantQuery): Promise<AccessTenantPage>;
  detail(id: AccessTenantId): Promise<AccessTenant>;
  create(body: AccessTenantSaveRequest): Promise<AccessTenant>;
  update(id: AccessTenantId, body: AccessTenantSaveRequest): Promise<AccessTenant>;
  /** 删除租户；存在子租户时服务端拒绝 */
  remove(id: AccessTenantId): Promise<void>;
}

/* ------------------------------------------------------------------ 接口 */

/** 权限域实体管理接口 */
export interface AccessApi {
  readonly users: AccessUserApi;
  readonly tenants: AccessTenantApi;
}

/** 路径常量。`satisfies keyof paths` 让拼错的路径在编译期就暴露，而不是运行时 404 */
const PATH_USERS = '/api/auth/users' satisfies keyof paths;
const PATH_TENANTS = '/api/auth/tenants' satisfies keyof paths;

export function createAccessApi(http: HttpClient): AccessApi {
  return {
    users: {
      page: (query) => http.get<AccessUserPage>(PATH_USERS, { query }),
      detail: (id) => http.get<AccessUser>(`${PATH_USERS}/${id}`),
      create: (body) => http.post<AccessUser>(PATH_USERS, body),
      update: (id, body) => http.put<AccessUser>(`${PATH_USERS}/${id}`, body),
      resetPassword: (id, body) => http.post<void>(`${PATH_USERS}/${id}/password`, body),
      remove: (id) => http.delete<void>(`${PATH_USERS}/${id}`),
    },
    tenants: {
      page: (query) => http.get<AccessTenantPage>(PATH_TENANTS, { query }),
      detail: (id) => http.get<AccessTenant>(`${PATH_TENANTS}/${id}`),
      create: (body) => http.post<AccessTenant>(PATH_TENANTS, body),
      update: (id, body) => http.put<AccessTenant>(`${PATH_TENANTS}/${id}`, body),
      remove: (id) => http.delete<void>(`${PATH_TENANTS}/${id}`),
    },
  };
}

/* ------------------------------------------------------------------ 权限标识 */

/**
 * 权限标识。
 *
 * <p><b>与后端 `AuthPermissions` 一一对应，前端不得拼接或自造。</b>
 * 后端已经把"散写字面量"的风险收成常量（`docs/decisions/0016`），
 * 前端再拼一次等于把风险搬回来：`auth:user:create` 拼成 `auth:user:ceate` 不会报错，
 * 只会让按钮永远不出现，而排查方向会指向"权限没下发"。
 *
 * <p>这里**只列前端真的用到的**：`AuthPermissions.ALL` 里没有被消费的标识
 * （例如由后端拦截器自行判断的那些）不需要在前端出现 ——
 * 抄一份全量表，等于给出一份"看起来前端在管权限"的假象。
 */
export const AccessPermissions = {
  USER_READ: 'auth:user:read',
  USER_CREATE: 'auth:user:create',
  USER_UPDATE: 'auth:user:update',
  USER_DELETE: 'auth:user:delete',
  TENANT_READ: 'auth:tenant:read',
  TENANT_CREATE: 'auth:tenant:create',
  TENANT_UPDATE: 'auth:tenant:update',
  TENANT_DELETE: 'auth:tenant:delete',
} as const;

/** 权限标识的字面量联合类型 */
export type AccessPermission = (typeof AccessPermissions)[keyof typeof AccessPermissions];
