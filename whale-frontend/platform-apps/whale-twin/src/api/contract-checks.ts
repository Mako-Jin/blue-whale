// ============================================================
// 契约接线检查（whale-twin）
//
// 把手写的孪生 VO 钉在 openapi-typescript 生成的契约上：
//   · 后端改字段类型（如 Long→string）→ 这里编译报错
//   · 后端删字段 / 改名 → 这里编译报错
// 契约由 scripts/gen-openapi-contracts.py 生成，勿手工改。
//
// 与 admin 的 contract-checks 差异：孪生侧 VO 允许 `| null`（表单留空语义），
// 故先用 DropNull 剥掉 null 再做结构比较。
// ============================================================

import type { components as Twin } from './generated/twin-schema';

import type {
  EntityVO,
  EntitySaveRequest,
  BindingSaveRequest,
  MarkerSaveRequest,
  SceneSaveRequest,
  InsightRequest,
} from './twin';

// ---- 类型工具 ---------------------------------------------------------------

/** 断言为 true，否则编译失败（export 仅为通过 noUnusedLocals） */
export type Assert<T extends true> = T;

/** 手写类型是否与后端 schema 完全等价（双向） */
export type Equal<X, Y> =
  (<T>() => T extends X ? 1 : 2) extends <T>() => T extends Y ? 1 : 2
    ? true
    : false;

/** 递归剥掉可空语义（孪生 VO 的 children 是自引用，需递归到子节点） */
type DropNull<T> = T extends null | undefined
  ? T
  : T extends (infer U)[]
    ? DropNull<U>[]
    : T extends object
      ? { [K in keyof T]-?: DropNull<Exclude<T[K], null>> }
      : T;

/** 手写类型（忽略 null）是否仍是后端 schema 的结构子集 */
export type SubsetOf<Hand, Schema> =
  DropNull<Hand> extends DropNull<Schema> ? true : false;

type Schemas = Twin['schemas'];

// ---- 场景 / 实体 ------------------------------------------------------------

export type AssertEntityVO = Assert<SubsetOf<EntityVO, Schemas['EntityVO']>>;
export type AssertEntitySave = Assert<
  SubsetOf<EntitySaveRequest, Schemas['EntitySaveRequest']>
>;
export type AssertSceneSave = Assert<
  SubsetOf<SceneSaveRequest, Schemas['SceneSaveRequest']>
>;

// ---- 绑定 / 标注 ------------------------------------------------------------

export type AssertBindingSave = Assert<
  SubsetOf<BindingSaveRequest, Schemas['BindingSaveRequest']>
>;
export type AssertMarkerSave = Assert<
  SubsetOf<MarkerSaveRequest, Schemas['MarkerSaveRequest']>
>;

// ---- AI 解读 ----------------------------------------------------------------

export type AssertInsightRequest = Assert<
  SubsetOf<InsightRequest, Schemas['InsightRequest']>
>;

// ---- 关键口径钉死：雪花 ID 一律 string（防手工改回 number） -------------------

export type AssertEntityIdIsString = Assert<Equal<EntityVO['id'], string>>;
export type AssertEntitySceneIdIsString = Assert<Equal<EntityVO['sceneId'], string>>;
export type AssertEntityParentIdIsString = Assert<
  Equal<NonNullable<EntityVO['parentId']>, string>
>;
export type AssertBindingEntityIdIsString = Assert<
  Equal<BindingSaveRequest['entityId'], string>
>;
export type AssertMarkerEntityIdIsString = Assert<
  Equal<NonNullable<MarkerSaveRequest['entityId']>, string>
>;

// eslint 不检查纯类型，防止被误清空：导出一个运行时常量占位
export const TWIN_CONTRACT_CHECKS = true;
