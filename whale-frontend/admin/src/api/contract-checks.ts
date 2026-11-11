// ============================================================
// 契约接线检查（Wave 6.36）
//
// 目的：把手写 VO「钉」在 openapi-typescript 生成的契约上。
// 断言方向：手写类型必须仍是后端 schema 的结构子集——
//   · 后端把某字段改类型（如 Long→string 口径变化）→ 这里编译报错
//   · 后端删掉/改名某字段 → 这里编译报错
// 生成契约用 `pnpm --filter whale-admin exec openapi-typescript`（或
// scripts/gen-openapi-contracts.py）随文档再生成，本文件无需手工同步。
//
// 注意：schema 侧字段全是 optional，所以子集判定只校验「手写里出现的字段
// 在后端存在且类型兼容」，不校验可选性/必填性——那是后端校验层的职责。
// ============================================================

import type { components as Auth } from './generated/auth-schema';
import type { components as Msg } from './generated/message-schema';

import type {
  MessageTemplateVO,
  MessageTemplateInput,
  AnnouncementVO,
  AnnouncementPublishInput,
} from './message';
import type { MenuVO, MenuPayload } from './menu';
import type { PermissionVO, PermissionPayload } from './permission';
import type {
  RoleVO,
  RoleCreatePayload,
  RoleUpdatePayload,
} from './role';
import type {
  UserVO,
  UserCreatePayload,
  UserUpdatePayload,
  ResetPasswordVO,
} from './user';

// ---- 类型工具 ---------------------------------------------------------------

/** 断言为 true，否则编译失败（export 仅为通过 noUnusedLocals） */
export type Assert<T extends true> = T;

/** 手写类型是否仍是后端 schema 的结构子集 */
export type SubsetOf<Hand, Schema> = Hand extends Schema ? true : false;

/** 手写类型是否与后端 schema 完全等价（双向） */
export type Equal<X, Y> =
  (<T>() => T extends X ? 1 : 2) extends <T>() => T extends Y ? 1 : 2
    ? true
    : false;

type AuthSchemas = Auth['schemas'];
type MsgSchemas = Msg['schemas'];

// ---- 用户（/user/**） -------------------------------------------------------

export type AssertUserVO = Assert<SubsetOf<UserVO, AuthSchemas['UserVO']>>;
export type AssertUserCreate = Assert<
  SubsetOf<UserCreatePayload, AuthSchemas['UserCreateRequest']>
>;
export type AssertUserUpdate = Assert<
  SubsetOf<UserUpdatePayload, AuthSchemas['UserUpdateRequest']>
>;
export type AssertResetPassword = Assert<
  SubsetOf<ResetPasswordVO, AuthSchemas['ResetPasswordVO']>
>;

// ---- 角色（/role/**） -------------------------------------------------------

export type AssertRoleVO = Assert<SubsetOf<RoleVO, AuthSchemas['RoleVO']>>;
export type AssertRoleCreate = Assert<
  SubsetOf<RoleCreatePayload, AuthSchemas['RoleCreateRequest']>
>;
export type AssertRoleUpdate = Assert<
  SubsetOf<RoleUpdatePayload, AuthSchemas['RoleUpdateRequest']>
>;

// ---- 菜单 / 权限点 ----------------------------------------------------------

export type AssertMenuVO = Assert<SubsetOf<MenuVO, AuthSchemas['MenuVO']>>;
export type AssertMenuPayload = Assert<
  SubsetOf<MenuPayload, AuthSchemas['MenuRequest']>
>;
export type AssertPermissionVO = Assert<
  SubsetOf<PermissionVO, AuthSchemas['PermissionVO']>
>;
export type AssertPermissionPayload = Assert<
  SubsetOf<PermissionPayload, AuthSchemas['PermissionRequest']>
>;

// ---- 消息服务（/message/**） ------------------------------------------------

export type AssertMessageTemplateVO = Assert<
  SubsetOf<MessageTemplateVO, MsgSchemas['MessageTemplateVO']>
>;
export type AssertMessageTemplateInput = Assert<
  SubsetOf<MessageTemplateInput, MsgSchemas['MessageTemplateSaveRequest']>
>;
export type AssertAnnouncementVO = Assert<
  SubsetOf<AnnouncementVO, MsgSchemas['AnnouncementVO']>
>;
export type AssertAnnouncementPublish = Assert<
  SubsetOf<AnnouncementPublishInput, MsgSchemas['AnnouncementPublishRequest']>
>;

// ---- 关键口径钉死：Long 一律 string（防手工改回 number） ---------------------

export type AssertUserVOIdIsString = Assert<
  Equal<UserVO['id'], string>
>;
export type AssertRoleVOIdIsString = Assert<
  Equal<RoleVO['id'], string>
>;
export type AssertMenuVOIdIsString = Assert<
  Equal<MenuVO['id'], string>
>;
export type AssertPermissionVOIdIsString = Assert<
  Equal<PermissionVO['id'], string>
>;
export type AssertMessageTemplateIdIsString = Assert<
  Equal<MessageTemplateVO['id'], string>
>;

// eslint 不检查纯类型，防止被误清空：导出一个运行时空对象占位
export const CONTRACT_CHECKS = true;

