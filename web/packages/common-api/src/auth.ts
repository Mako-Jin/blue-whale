import type { HttpClient } from 'common-tools/http';
import type { SessionTokens, SessionUser } from 'common-tools/session';

import type { BodyOf, DataOf } from './types';
import type { paths } from './generated/schema';

/**
 * 认证域的契约接线。
 *
 * <p>本文件只做两件事：**把路径与类型从契约里取出来**，以及**把响应体收敛成会话身份**。
 * 会话怎么存、401 怎么恢复、界面怎么跳，都不在这里（分别在 `common-tools/session`、
 * `common-tools/http` 与 `channel.ts`）—— 归属见 `docs/decisions/0018`。
 */

type LoginOp = paths['/api/auth/login']['post'];
type RefreshOp = paths['/api/auth/refresh']['post'];
type LogoutOp = paths['/api/auth/logout']['post'];
type MeOp = paths['/api/auth/me']['get'];
type ChangePasswordOp = paths['/api/auth/password']['put'];

/** 登录请求体：租户编码 + 登录名 + 口令 */
export type AuthLoginRequest = BodyOf<LoginOp>;

/** 刷新 / 登出请求体：只要刷新令牌 */
export type AuthRefreshRequest = BodyOf<RefreshOp>;

/** 自助改密请求体：旧口令（证明是本人）+ 新口令 */
export type AuthPasswordChangeRequest = BodyOf<ChangePasswordOp>;

/**
 * 登出请求体（与刷新同形，都是刷新令牌）。
 *
 * <p><b>它曾经是个特例。</b>契约里那份请求体一度是**可选**的，因为"不传"曾表示
 * 另一件事（撤销该用户全部令牌），可选属性推不出请求体类型，于是这里只能从操作的
 * `requestBody` 上手工取模型 —— 一个类型定义被业务语义逼出来的形状。
 * 两条语义拆成两条路径之后（见 `docs/decisions/0047`），它回到了最普通的
 * {@link BodyOf}：契约说必填，类型就必填。
 *
 * <p>留着这段沿革不是怀旧：`BodyOf` 与"手工取 requestBody"的差别是
 * **可选性有没有被表达出来**，而这里曾经恰恰是"可选性被当成了语义"。
 */
export type AuthLogoutRequest = BodyOf<LogoutOp>;

/**
 * 登录 / 刷新返回体。
 *
 * <p>**它同时携带身份与权限集合**（`userId` / `roles` / `permissions` 等）——
 * 这就是"刷新页面后不必再调 `/me`"的原因：一次刷新即完成会话恢复。
 */
export type AuthLoginResult = NonNullable<DataOf<LoginOp>>;

/** 当前用户信息，身份部分与 {@link AuthLoginResult} 一致 */
export type AuthMe = NonNullable<DataOf<MeOp>>;

/* ------------------------------------------------------------------ 路径常量 */

// `satisfies keyof paths`：路径拼错在编译期就报错，而不是等到运行时 404
const PATH_LOGIN = '/api/auth/login' satisfies keyof paths;
const PATH_REFRESH = '/api/auth/refresh' satisfies keyof paths;
const PATH_LOGOUT = '/api/auth/logout' satisfies keyof paths;
/*
 * 这一条是六个路径里**唯一没有配套 Op 类型别名**的：它没有请求体，响应体也是空的
 * `R<Void>`，取不出任何有意义的类型 —— 别名写在那里就是一行"声明了没人用"的死代码
 * （`tsc` 的 `noUnusedLocals` 会直接报出来，本仓正是这么发现它的）。
 * "这条路真的在契约里"由上面的 `satisfies keyof paths` 保证，不需要别名代劳。
 */
const PATH_LOGOUT_ALL = '/api/auth/logout-all' satisfies keyof paths;
const PATH_ME = '/api/auth/me' satisfies keyof paths;
const PATH_PASSWORD = '/api/auth/password' satisfies keyof paths;

/**
 * 公开接口的请求选项：不带令牌、也不参与 401 自动恢复。
 *
 * <p>`skipAuthRetry` 在刷新接口上是**必须**的 —— 否则刷新失败拿到 401 会触发再次刷新，
 * 递归下去表现为刷新接口被自己打出一片 401（见 `docs/decisions/0018` 第 7 条）。
 */
const PUBLIC_REQUEST = { skipAuth: true, skipAuthRetry: true } as const;

/** 登出带上已有令牌（便于服务端审计"谁登出了"），但**不参与** 401 自动恢复 */
const LOGOUT_REQUEST = { skipAuthRetry: true } as const;

/* ------------------------------------------------------------------ 接口 */

/** 认证接口 */
export interface AuthApi {
  /** 登录，返回令牌对与身份、权限 */
  login(body: AuthLoginRequest): Promise<AuthLoginResult>;
  /** 刷新令牌；旧刷新令牌随之作废（服务端轮换），返回新的令牌对与身份 */
  refresh(body: AuthRefreshRequest): Promise<AuthLoginResult>;
  /** 登出**这一台设备**：撤销请求体里那一个刷新令牌（要撤销全部用 {@link AuthApi.logoutAll}） */
  logout(body: AuthLogoutRequest): Promise<void>;
  /**
   * 退出所有设备：撤销当前用户的全部刷新令牌（含本次会话手上这一个）。
   *
   * <p><b>它必须带令牌，而且走默认请求选项（带令牌、401 时刷新重试）。</b>
   * 与登出相反 —— 登出要撤的那一个会话由请求体自己指明，因此不需要身份；
   * 而"全部"是**按当前用户**算的，服务端得先知道你是谁。
   *
   * <p>也正因为如此，它不能像登出那样 `skipAuthRetry`：最常见的调用时机恰恰是
   * "访问令牌已经过期、我怀疑账号被人登着"，此时跳过刷新等于让这个动作在
   * 最需要它的时候必然失败。
   */
  logoutAll(): Promise<void>;
  /** 当前登录用户（要求已认证，但不要求任何权限） */
  me(): Promise<AuthMe>;
  /**
   * 自助改密：本人修改自己的口令。
   *
   * <p><b>不挂权限码</b> —— 能登录就该能改自己的口令，否则用户会被一个自己都进不去的
   * 口令锁在门外。"证明操作者是本人"因此只能靠**旧口令**（请求体里的 `oldPassword`）。
   *
   * <p><b>成功后服务端会撤销该账号的全部刷新令牌，包括本次会话手上那一个。</b>
   * 调用方不要把它当成一次普通写操作 —— 唯一的正确写法在
   * {@link AuthChannel.changePassword}（它把"调接口"与"结束本地会话"绑在了一起，
   * 免得每个界面各自记得这件事）。
   */
  changePassword(body: AuthPasswordChangeRequest): Promise<void>;
}

export function createAuthApi(http: HttpClient): AuthApi {
  return {
    login: (body) => http.post<AuthLoginResult>(PATH_LOGIN, body, PUBLIC_REQUEST),
    refresh: (body) => http.post<AuthLoginResult>(PATH_REFRESH, body, PUBLIC_REQUEST),
    logout: (body) => http.post<void>(PATH_LOGOUT, body, LOGOUT_REQUEST),
    /*
     * 走默认请求选项，理由与 changePassword 那条注解同源：本仓约定
     * **业务失败返回 HTTP 200**，所以"未认证"（40104）在传输层也是 200，
     * 内核不会把它误当成令牌失效去刷新重试。真到了需要刷新的时候
     * （访问令牌过期），那次刷新恰恰是这条动作能成功的前提。
     */
    logoutAll: () => http.post<void>(PATH_LOGOUT_ALL),
    me: () => http.get<AuthMe>(PATH_ME),
    /*
     * 走**默认**请求选项（带令牌、401 时刷新重试），这条看起来平淡，但它成立
     * 依赖后端的一条约定：**业务失败返回 HTTP 200**（本仓 `GlobalExceptionHandler`
     * 的所有处理器都返回 `R.fail(...)`）。
     *
     * 换句话说，"旧口令不对"（`40101`）在传输层是 200，内核不会把它误当成令牌失效。
     * 如果哪天有人把这条约定改成"按业务码回真实状态码"，那这里就会变成
     * "用户打错一个字 → 内核当成令牌失效 → 刷新、重试、清会话跳登录页"，
     * 而界面会把它显示成"改密成功，请重新登录"。到那时本请求必须加上
     * `skipAuthRetry`（`LOGOUT_REQUEST` 就是那个写法）。
     */
    changePassword: (body) => http.put<void>(PATH_PASSWORD, body),
  };
}

/* ------------------------------------------------------------------ 身份映射 */

/**
 * 把契约返回体收敛成会话里的身份。
 *
 * <p>只在**这一处**做映射：令牌形态由服务端定义、权限标识由服务端定义内容，
 * 前端只负责搬过来。任何"顺手拼一个权限码"的做法都会在这里露出来。
 */
export function toSessionUser(source: AuthLoginResult | AuthMe): SessionUser {
  return {
    // 契约里这些字段都可缺席（生成器按非必填处理），但服务端一定会给；
    // 兜一个空值是为了不让"某个字段没值"把整次登录变成失败
    userId: source.userId ?? '',
    username: source.username ?? '',
    displayName: source.displayName,
    tenantId: source.tenantId,
    tenantCode: source.tenantCode,
    roles: source.roles ?? [],
    permissions: source.permissions ?? [],
    // 后端给的是 0/1，会话里存成判定用的布尔值。**只有等于 1 才算待改密** ——
    // 写成 `!== 0` 的话，字段缺席（`undefined`）会被当成"要改密"，
    // 于是所有老版本后端都会让用户看到一个无事可做的引导。
    mustChangePassword: source.mustChangePassword === 1,
  };
}

/**
 * 把"有效期秒数"换算成到期时刻。
 *
 * <p>算不出来时返回 `null` —— 由使用方决定是否需要主动续期，
 * 而不是把"不知道"伪装成一个具体时刻。
 */
export function toExpiresAt(source: AuthLoginResult): number | null {
  const seconds = source.expiresIn;
  return typeof seconds === 'number' && seconds > 0 ? Date.now() + seconds * 1000 : null;
}

/** 从返回体提取令牌对 */
export function toSessionTokens(source: AuthLoginResult): SessionTokens {
  return {
    accessToken: source.accessToken ?? null,
    refreshToken: source.refreshToken ?? null,
    expiresAt: toExpiresAt(source),
  };
}
