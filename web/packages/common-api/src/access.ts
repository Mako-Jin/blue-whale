import type { HttpClient } from 'common-tools/http';

import type { ApiSchemas, BodyOf, DataOf, PathOf, QueryOf } from './types';
import type { paths } from './generated/schema';

/**
 * 权限域的**实体管理**接线（用户 / 租户 / 资源 / 角色 / 用户组 / 组织）。
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

type ResourcePageOp = paths['/api/auth/resources']['get'];
type ResourceCreateOp = paths['/api/auth/resources']['post'];
type ResourceTreeOp = paths['/api/auth/resources/tree']['get'];
type ResourceDetailOp = paths['/api/auth/resources/{id}']['get'];

type RolePageOp = paths['/api/auth/roles']['get'];
type RoleCreateOp = paths['/api/auth/roles']['post'];
type RoleDetailOp = paths['/api/auth/roles/{id}']['get'];
type RoleGrantListOp = paths['/api/auth/roles/{id}/resources']['get'];
type RoleGrantOp = paths['/api/auth/roles/{id}/resources']['post'];
type RoleGrantRevokeOp = paths['/api/auth/roles/{id}/resources/{resourceId}']['delete'];
type RoleMemberPageOp = paths['/api/auth/roles/{id}/members']['get'];
type RoleMemberBindOp = paths['/api/auth/roles/{id}/members']['post'];
type RoleMemberRevokeOp = paths['/api/auth/roles/{id}/members/{memberType}/{memberId}']['delete'];

type UserGroupPageOp = paths['/api/auth/user-groups']['get'];
type UserGroupCreateOp = paths['/api/auth/user-groups']['post'];
type UserGroupDetailOp = paths['/api/auth/user-groups/{id}']['get'];
type UserGroupMemberPageOp = paths['/api/auth/user-groups/{id}/members']['get'];
type UserGroupMemberBindOp = paths['/api/auth/user-groups/{id}/members']['post'];
type UserGroupMemberRevokeOp =
  paths['/api/auth/user-groups/{id}/members/{memberType}/{memberId}']['delete'];

type UserRolesOp = paths['/api/auth/users/{id}/roles']['get'];
type UserOrganizationsOp = paths['/api/auth/users/{id}/organizations']['get'];
type UserDataScopeOp = paths['/api/auth/users/{id}/data-scope']['get'];

type OrgPageOp = paths['/api/auth/orgs']['get'];
type OrgCreateOp = paths['/api/auth/orgs']['post'];
type OrgTreeOp = paths['/api/auth/orgs/tree']['get'];
type OrgDetailOp = paths['/api/auth/orgs/{id}']['get'];
type OrgMoveOp = paths['/api/auth/orgs/{id}/parent']['put'];
type OrgMemberPageOp = paths['/api/auth/orgs/{id}/members']['get'];
type OrgMemberBindOp = paths['/api/auth/orgs/{id}/members']['post'];
/*
 * 撤销与设主岗的路径参数同形（组织主键 + 用户主键），因此只声明一次。
 *
 * 不在类型层强行区分这两条路径：它们是**同一个对象的两种操作**，
 * 而 `memberType`（角色那边必须显式带上的一段）在这里不存在 ——
 * 组织只有用户这一种成员，不存在"同号不同表"的歧义。
 */
type OrgMemberRevokeOp = paths['/api/auth/orgs/{id}/members/{userId}']['delete'];

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
  /**
   * 反查该用户的角色来源（直接授予 + 经用户组授予）。
   *
   * <p>权限归 `auth:user:read`（读的是"某个用户"的信息，与列表 / 详情同类），
   * 因此**审计员也能调** —— 与用户组那套权限是分开决定的。
   */
  roleSources(id: AccessUserId): Promise<AccessUserRoleSourceList>;
  /**
   * 反查该用户挂在哪几个组织下（含主岗标记与生效区间）。
   *
   * <p>与 {@link AccessUserApi.roleSources} 同类，权限也归 `auth:user:read`。
   * 但**两者的用途不同**：角色回答"他能做什么"，组织归属回答"他看得到哪些数据" ——
   * `DEPT` / `DEPT_AND_CHILD` 两档数据范围唯一的取值来源就是这里的**主岗**。
   *
   * <p>同时会带回 `orgStatus`：停用的组织不能作为范围目标，因此一行
   * "组织已停用"正是"为什么数据范围算不出来"的答案。
   */
  organizations(id: AccessUserId): Promise<AccessUserOrgList>;
  /**
   * 反查该用户的数据范围**求值结果**。
   *
   * <p>这是 ADR-0025 定义的"只做求值 + 下发"的观察窗口：它给的是**此刻算出来的结论**，
   * 不是配置。要回答"这个范围是谁给的"看 {@link AccessUserApi.roleSources}，
   * 要回答"最终算成了什么"看这里。
   *
   * <p><b>`precise` 为 false 不是错误</b>：它表示档位（CUSTOM 未实现）或
   * 主岗（DEPT 系列需要）缺失，此时已接入的查询**保守地按「仅本人」处理** ——
   * 比实际范围更窄，因此是安全的一侧。界面必须把这个区别显示出来，
   * 否则管理员会以为"配了 DEPT 却没有生效"是 bug。
   */
  dataScope(id: AccessUserId): Promise<AccessUserDataScope>;
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

/* ------------------------------------------------------------------ 资源目录 */

/**
 * 资源出参。
 *
 * <p>注意 `requestScoped`：它由后端从 `resourceType` 派生，表示"这条资源参不参与接口准入"。
 * `MENU` / `PAGE` / `DATA` 都是 `false` —— 它们影响的是**前端可见性与查询范围**，
 * 不拦接口。把它展示出来是有意的：否则会有人以为"给角色授了菜单就等于授权了接口"。
 */
export type AccessResource = ApiSchemas['ResourceVO'];

/** 资源分页条件 */
export type AccessResourceQuery = QueryOf<ResourcePageOp>;

/** 资源分页结果（平铺） */
export type AccessResourcePage = NonNullable<DataOf<ResourcePageOp>>;

/** 资源新增 / 修改入参。**修改时 `resourceCode` 不生效** */
export type AccessResourceSaveRequest = BodyOf<ResourceCreateOp>;

/** 资源主键 */
export type AccessResourceId = PathOf<ResourceDetailOp>['id'];

/** 资源树节点 */
export type AccessResourceTreeNode = ApiSchemas['ResourceTreeNode'];

/** 资源树（顶层数组，子节点在 `children`） */
export type AccessResourceTree = NonNullable<DataOf<ResourceTreeOp>>;

export interface AccessResourceApi {
  /** 平铺分页；要层级用 {@link tree} */
  page(query?: AccessResourceQuery): Promise<AccessResourcePage>;
  /** 全量资源树。孤儿与成环节点按根返回，不丢弃 */
  tree(): Promise<AccessResourceTree>;
  detail(id: AccessResourceId): Promise<AccessResource>;
  create(body: AccessResourceSaveRequest): Promise<AccessResource>;
  update(id: AccessResourceId, body: AccessResourceSaveRequest): Promise<AccessResource>;
  /** 有子资源（40913）或已被角色引用（40915）时服务端拒绝 */
  remove(id: AccessResourceId): Promise<void>;
}

/* ------------------------------------------------------------------ 角色 */

/** 角色出参 */
export type AccessRole = ApiSchemas['RoleVO'];

/** 角色分页条件 */
export type AccessRoleQuery = QueryOf<RolePageOp>;

/** 角色分页结果 */
export type AccessRolePage = NonNullable<DataOf<RolePageOp>>;

/** 角色新增 / 修改入参。**修改时 `roleCode` 不生效** */
export type AccessRoleSaveRequest = BodyOf<RoleCreateOp>;

/** 角色主键 */
export type AccessRoleId = PathOf<RoleDetailOp>['id'];

/**
 * 角色已授予的资源。
 *
 * <p>`grantId`（授权关系主键）与 `resourceId`（资源主键）是两个不同的东西：
 * 撤销走 `grantId`，但接口路径收的是 `resourceId` —— 契约如此，因此这里两个都暴露，
 * 调用点用 `resourceId` 拼路径、用 `grantId` 做列表 `key`。
 */
export type AccessRoleResource = ApiSchemas['RoleResourceVO'];

/** 角色已授予资源的列表。接口返回的是**裸数组**，不是分页结构 */
export type AccessRoleResourceList = NonNullable<DataOf<RoleGrantListOp>>;

/** 授予资源的入参 */
export type AccessRoleResourceGrantRequest = BodyOf<RoleGrantOp>;

/** 撤销授权的路径参数（不含角色主键 —— 它单独传） */
export type AccessRoleResourceRef = Omit<PathOf<RoleGrantRevokeOp>, 'id'>;

/** 角色成员出参 */
export type AccessRoleMember = ApiSchemas['RoleMemberVO'];

/** 角色成员分页条件 */
export type AccessRoleMemberQuery = QueryOf<RoleMemberPageOp>;

/** 角色成员分页结果 */
export type AccessRoleMemberPage = NonNullable<DataOf<RoleMemberPageOp>>;

/** 授予成员的入参。**`memberType` 必填** —— 默认为 `USER` 会把漏填变成"授给一个同号用户" */
export type AccessRoleMemberBindRequest = BodyOf<RoleMemberBindOp>;

/**
 * 撤销成员的路径参数（不含角色主键 —— 它单独传）。
 *
 * <p>`memberType` 是路径的一部分，不是可省的查询参数：`memberId` 只在所指的那张表里
 * 有意义，用户与用户组是两个独立的自增序列、会撞号。
 */
export type AccessRoleMemberRef = Omit<PathOf<RoleMemberRevokeOp>, 'id'>;

export interface AccessRoleApi {
  page(query?: AccessRoleQuery): Promise<AccessRolePage>;
  detail(id: AccessRoleId): Promise<AccessRole>;
  create(body: AccessRoleSaveRequest): Promise<AccessRole>;
  update(id: AccessRoleId, body: AccessRoleSaveRequest): Promise<AccessRole>;
  /** 系统内置角色（40917）／有子角色（40913）／有成员（40916）时服务端拒绝 */
  remove(id: AccessRoleId): Promise<void>;
  /** 角色已授予的资源（含效果、优先级与生效区间） */
  listGrants(id: AccessRoleId): Promise<AccessRoleResourceList>;
  /** 授予资源；同一角色与资源重复调用是改效果，不是堆行 */
  grant(id: AccessRoleId, body: AccessRoleResourceGrantRequest): Promise<AccessRoleResource>;
  /** 撤销资源授权；从未授予过时服务端返回 40407，不与撤销成功混淆 */
  revoke(id: AccessRoleId, ref: AccessRoleResourceRef): Promise<void>;
  pageMembers(id: AccessRoleId, query?: AccessRoleMemberQuery): Promise<AccessRoleMemberPage>;
  bindMember(id: AccessRoleId, body: AccessRoleMemberBindRequest): Promise<AccessRoleMember>;
  /** 解除成员关系；从未授过时服务端返回 40408 */
  revokeMember(id: AccessRoleId, ref: AccessRoleMemberRef): Promise<void>;
}

/* ------------------------------------------------------------------ 用户组 */

/** 用户组出参 */
export type AccessUserGroup = ApiSchemas['UserGroupVO'];

/** 用户组分页条件。`enabled` 是 `0 / 1` 而不是布尔 —— 契约如此，传 `true` 会被序列化成不认识的值 */
export type AccessUserGroupQuery = QueryOf<UserGroupPageOp>;

/** 用户组分页结果 */
export type AccessUserGroupPage = NonNullable<DataOf<UserGroupPageOp>>;

/** 用户组新增 / 修改入参。**只支持静态组**；`filterJson` / `orgScopeJson` 连入参都不接受 */
export type AccessUserGroupSaveRequest = BodyOf<UserGroupCreateOp>;

/** 用户组主键 */
export type AccessUserGroupId = PathOf<UserGroupDetailOp>['id'];

/** 用户组成员出参 */
export type AccessUserGroupMember = ApiSchemas['UserGroupMemberVO'];

/** 用户组成员分页条件 */
export type AccessUserGroupMemberQuery = QueryOf<UserGroupMemberPageOp>;

/** 用户组成员分页结果 */
export type AccessUserGroupMemberPage = NonNullable<DataOf<UserGroupMemberPageOp>>;

/**
 * 把成员加进用户组的入参。
 *
 * <p>`memberType` 当前**只接受 `USER`**：组的嵌套在判定链路里不生效，
 * 传 `GROUP` 服务端返回 40001，而不是静默接受一条"列表可见、判定不参与"的关系。
 */
export type AccessUserGroupMemberBindRequest = BodyOf<UserGroupMemberBindOp>;

/** 移出成员的路径参数（不含用户组主键 —— 它单独传） */
export type AccessUserGroupMemberRef = Omit<PathOf<UserGroupMemberRevokeOp>, 'id'>;

export interface AccessUserGroupApi {
  page(query?: AccessUserGroupQuery): Promise<AccessUserGroupPage>;
  detail(id: AccessUserGroupId): Promise<AccessUserGroup>;
  create(body: AccessUserGroupSaveRequest): Promise<AccessUserGroup>;
  update(id: AccessUserGroupId, body: AccessUserGroupSaveRequest): Promise<AccessUserGroup>;
  /** 有成员（40918）或有子组（40913）时服务端拒绝 */
  remove(id: AccessUserGroupId): Promise<void>;
  pageMembers(
    id: AccessUserGroupId,
    query?: AccessUserGroupMemberQuery,
  ): Promise<AccessUserGroupMemberPage>;
  bindMember(
    id: AccessUserGroupId,
    body: AccessUserGroupMemberBindRequest,
  ): Promise<AccessUserGroupMember>;
  /** 不在组里时服务端返回 40409，不与移除成功混淆 */
  revokeMember(id: AccessUserGroupId, ref: AccessUserGroupMemberRef): Promise<void>;
}

/* ------------------------------------------------------------------ 组织 */

/** 组织（部门）出参 */
export type AccessOrganization = ApiSchemas['OrganizationVO'];

/** 组织树节点 */
export type AccessOrganizationTreeNode = ApiSchemas['OrganizationTreeNode'];

/** 组织树（整个租户的森林；传 `rootId` 则只看一棵子树） */
export type AccessOrganizationTree = NonNullable<DataOf<OrgTreeOp>>;

/** 组织分页条件。`parentId` 取的是**直接下级**，要整棵子树请用树接口 */
export type AccessOrganizationQuery = QueryOf<OrgPageOp>;

/** 组织分页结果 */
export type AccessOrganizationPage = NonNullable<DataOf<OrgPageOp>>;

/**
 * 组织新增 / 修改入参。
 *
 * <p>`orgLevel` / `codePath` / `fullName` **连类型里都没有** —— 它们是 `parentId`
 * 与各节点名称的确定函数，由服务端派生。放开它们等于允许调用方写入一个
 * 与真实层级不一致的路径，而 `DEPT_AND_CHILD` 正好按那个路径求值。
 */
export type AccessOrganizationSaveRequest = BodyOf<OrgCreateOp>;

/** 组织主键 */
export type AccessOrganizationId = PathOf<OrgDetailOp>['id'];

/** 移动入参。**不传 `parentId` 或传 `null` 都表示提升为顶级** */
export type AccessOrganizationMoveRequest = BodyOf<OrgMoveOp>;

/** 组织成员出参 */
export type AccessOrgMember = ApiSchemas['OrgMemberVO'];

/** 组织成员分页条件。`primaryOnly` 为 true 时只看主岗 */
export type AccessOrgMemberQuery = QueryOf<OrgMemberPageOp>;

/** 组织成员分页结果 */
export type AccessOrgMemberPage = NonNullable<DataOf<OrgMemberPageOp>>;

/**
 * 把用户挂到组织下的入参。
 *
 * <p>**没有 `memberType`** —— 这个域的关系表只有用户（`bw_auth_rel_org_user`），
 * 组织之间靠 `parent_id` 成树，不像用户组那样需要在同一张关系表里混装两类成员。
 * 少了这个字段，也就少了一个"能选错"的开关。
 *
 * <p>`isPrimaryPosition` 不传时的语义是**刻意**的：该用户当前没有主岗则自动设为主岗，
 * 已有主岗则保持不变。它不会静默搬动一个人已有的数据范围。
 */
export type AccessOrgMemberBindRequest = BodyOf<OrgMemberBindOp>;

/** 移出成员的路径参数（组织主键单独传） */
export type AccessOrgMemberRef = Omit<PathOf<OrgMemberRevokeOp>, 'id'>;

export interface AccessOrganizationApi {
  page(query?: AccessOrganizationQuery): Promise<AccessOrganizationPage>;
  /** `rootId` 不传返回整个租户的森林 */
  tree(rootId?: number): Promise<AccessOrganizationTree>;
  detail(id: AccessOrganizationId): Promise<AccessOrganization>;
  create(body: AccessOrganizationSaveRequest): Promise<AccessOrganization>;
  /**
   * 修改组织资料。
   *
   * <p>**`parentId` 在这里是被拒的**：值非空且与原值不同时服务端返回 40001
   * （不传或传 `null` 都表示"这次不改层级"）。改层级必须走
   * {@link AccessOrganizationApi.move} —— 原因是它要重写整棵子树的派生列、
   * 并顺带维护两个父级的 `hasChild`，与"改资料"不是一件事。
   */
  update(
    id: AccessOrganizationId,
    body: AccessOrganizationSaveRequest,
  ): Promise<AccessOrganization>;
  /**
   * 移动节点，并**改写整棵子树**的 `orgLevel` / `codePath` / `fullName`。
   *
   * <p>独立接口、独立审计动作（`auth.org.move`），理由见 ADR-0030 第 3、4 条：
   * 一次移动会同时改变子树上**每个人**的 `DEPT_AND_CHILD` 可见范围。
   * 因此界面上它必须是独立入口，不能藏在"编辑资料"里。
   */
  move(id: AccessOrganizationId, body: AccessOrganizationMoveRequest): Promise<AccessOrganization>;
  /** 有下级（40913）或有成员（40919）时服务端拒绝，**没有级联删除** */
  remove(id: AccessOrganizationId): Promise<void>;
  pageMembers(id: AccessOrganizationId, query?: AccessOrgMemberQuery): Promise<AccessOrgMemberPage>;
  /** 重复调用是改岗位信息与时间窗，不是堆行 */
  bindMember(id: AccessOrganizationId, body: AccessOrgMemberBindRequest): Promise<AccessOrgMember>;
  /** 设为主岗；服务端会先把该用户级别处的主岗降下来，因此每人每租户至多一个 */
  setPrimaryPosition(id: AccessOrganizationId, ref: AccessOrgMemberRef): Promise<AccessOrgMember>;
  /** 解除归属关系；不在组织里时服务端返回 40410 */
  revokeMember(id: AccessOrganizationId, ref: AccessOrgMemberRef): Promise<void>;
}

/* ------------------------------------------------------------------ 用户子资源反查 */

/**
 * 用户的角色来源。
 *
 * <p>**与"权限集合"是两件事，不要混用**：判定侧给的是"此刻有效的权限码集合"（去重、过滤），
 * 这里给的是"配置里有哪些路径"（逐条、带来源）。因此**停用的角色 / 停用的组 / 已过期的授权
 * 照原样返回** —— 一行"角色已停用"正是"为什么没有权限"的答案。
 */
export type AccessUserRoleSource = ApiSchemas['UserRoleVO'];

/** 反查结果的数组类型 */
export type AccessUserRoleSourceList = NonNullable<DataOf<UserRolesOp>>;

/**
 * 用户的组织归属（一个用户可能在多个组织下有归属，其中至多一个是主岗）。
 *
 * <p>与 {@link AccessUserRoleSource} 同属"配置侧"的事实：**逐条、带状态、不过滤** ——
 * 停用的组织、已过期的归属照原样返回。要的是"原因"，不是"结论"。
 */
export type AccessUserOrg = ApiSchemas['UserOrgVO'];

/** 用户组织归属的数组类型 */
export type AccessUserOrgList = NonNullable<DataOf<UserOrganizationsOp>>;

/**
 * 用户的数据范围**求值结果**。
 *
 * <p>与上面两个是**不同性质**的东西：`roleSources` / `organizations` 给的是配置，
 * 这里给的是"按当前配置算出来是什么"。因此它只有一份，且必然自洽 ——
 * 而配置侧可以自相矛盾（这正是需要反查的原因）。
 */
export type AccessUserDataScope = NonNullable<DataOf<UserDataScopeOp>>;

/* ------------------------------------------------------------------ 接口 */

/** 权限域实体管理接口 */
export interface AccessApi {
  readonly users: AccessUserApi;
  readonly tenants: AccessTenantApi;
  readonly resources: AccessResourceApi;
  readonly roles: AccessRoleApi;
  readonly userGroups: AccessUserGroupApi;
  readonly organizations: AccessOrganizationApi;
}

/** 路径常量。`satisfies keyof paths` 让拼错的路径在编译期就暴露，而不是运行时 404 */
const PATH_USERS = '/api/auth/users' satisfies keyof paths;
const PATH_TENANTS = '/api/auth/tenants' satisfies keyof paths;
const PATH_RESOURCES = '/api/auth/resources' satisfies keyof paths;
const PATH_ROLES = '/api/auth/roles' satisfies keyof paths;
const PATH_USER_GROUPS = '/api/auth/user-groups' satisfies keyof paths;
const PATH_ORGS = '/api/auth/orgs' satisfies keyof paths;

export function createAccessApi(http: HttpClient): AccessApi {
  return {
    users: {
      page: (query) => http.get<AccessUserPage>(PATH_USERS, { query }),
      detail: (id) => http.get<AccessUser>(`${PATH_USERS}/${id}`),
      create: (body) => http.post<AccessUser>(PATH_USERS, body),
      update: (id, body) => http.put<AccessUser>(`${PATH_USERS}/${id}`, body),
      resetPassword: (id, body) => http.post<void>(`${PATH_USERS}/${id}/password`, body),
      remove: (id) => http.delete<void>(`${PATH_USERS}/${id}`),
      roleSources: (id) => http.get<AccessUserRoleSourceList>(`${PATH_USERS}/${id}/roles`),
      organizations: (id) => http.get<AccessUserOrgList>(`${PATH_USERS}/${id}/organizations`),
      dataScope: (id) => http.get<AccessUserDataScope>(`${PATH_USERS}/${id}/data-scope`),
    },
    tenants: {
      page: (query) => http.get<AccessTenantPage>(PATH_TENANTS, { query }),
      detail: (id) => http.get<AccessTenant>(`${PATH_TENANTS}/${id}`),
      create: (body) => http.post<AccessTenant>(PATH_TENANTS, body),
      update: (id, body) => http.put<AccessTenant>(`${PATH_TENANTS}/${id}`, body),
      remove: (id) => http.delete<void>(`${PATH_TENANTS}/${id}`),
    },
    resources: {
      page: (query) => http.get<AccessResourcePage>(PATH_RESOURCES, { query }),
      tree: () => http.get<AccessResourceTree>(`${PATH_RESOURCES}/tree`),
      detail: (id) => http.get<AccessResource>(`${PATH_RESOURCES}/${id}`),
      create: (body) => http.post<AccessResource>(PATH_RESOURCES, body),
      update: (id, body) => http.put<AccessResource>(`${PATH_RESOURCES}/${id}`, body),
      remove: (id) => http.delete<void>(`${PATH_RESOURCES}/${id}`),
    },
    roles: {
      page: (query) => http.get<AccessRolePage>(PATH_ROLES, { query }),
      detail: (id) => http.get<AccessRole>(`${PATH_ROLES}/${id}`),
      create: (body) => http.post<AccessRole>(PATH_ROLES, body),
      update: (id, body) => http.put<AccessRole>(`${PATH_ROLES}/${id}`, body),
      remove: (id) => http.delete<void>(`${PATH_ROLES}/${id}`),
      listGrants: (id) => http.get<AccessRoleResourceList>(`${PATH_ROLES}/${id}/resources`),
      grant: (id, body) => http.post<AccessRoleResource>(`${PATH_ROLES}/${id}/resources`, body),
      revoke: (id, ref) => http.delete<void>(`${PATH_ROLES}/${id}/resources/${ref.resourceId}`),
      pageMembers: (id, query) =>
        http.get<AccessRoleMemberPage>(`${PATH_ROLES}/${id}/members`, { query }),
      bindMember: (id, body) => http.post<AccessRoleMember>(`${PATH_ROLES}/${id}/members`, body),
      /*
       * 撤销路径里 `memberType` 是**必须**的一段。
       *
       * 不把它藏进 body 或省掉：`memberId` 只在所指的那张表里有意义，用户与用户组是两个
       * 独立的自增序列、会撞号。省掉类型段时 `memberId=3` 可能撤掉另一个对象，
       * 而症状是"某人的权限凭空消失"—— 离真正的原因非常远。
       */
      revokeMember: (id, ref) =>
        http.delete<void>(`${PATH_ROLES}/${id}/members/${ref.memberType}/${ref.memberId}`),
    },
    userGroups: {
      page: (query) => http.get<AccessUserGroupPage>(PATH_USER_GROUPS, { query }),
      detail: (id) => http.get<AccessUserGroup>(`${PATH_USER_GROUPS}/${id}`),
      create: (body) => http.post<AccessUserGroup>(PATH_USER_GROUPS, body),
      update: (id, body) => http.put<AccessUserGroup>(`${PATH_USER_GROUPS}/${id}`, body),
      remove: (id) => http.delete<void>(`${PATH_USER_GROUPS}/${id}`),
      pageMembers: (id, query) =>
        http.get<AccessUserGroupMemberPage>(`${PATH_USER_GROUPS}/${id}/members`, { query }),
      bindMember: (id, body) =>
        http.post<AccessUserGroupMember>(`${PATH_USER_GROUPS}/${id}/members`, body),
      revokeMember: (id, ref) =>
        http.delete<void>(`${PATH_USER_GROUPS}/${id}/members/${ref.memberType}/${ref.memberId}`),
    },
    organizations: {
      page: (query) => http.get<AccessOrganizationPage>(PATH_ORGS, { query }),
      // `rootId` 为 undefined 时不拼进查询串（内核会丢掉空值），因此"不传即整片森林"
      tree: (rootId) =>
        http.get<AccessOrganizationTree>(`${PATH_ORGS}/tree`, { query: { rootId } }),
      detail: (id) => http.get<AccessOrganization>(`${PATH_ORGS}/${id}`),
      create: (body) => http.post<AccessOrganization>(PATH_ORGS, body),
      update: (id, body) => http.put<AccessOrganization>(`${PATH_ORGS}/${id}`, body),
      /*
       * 移动走独立路径，**不拼进 update**。
       *
       * 它在服务端要做的事与"改资料"不同：重写整棵子树的三个派生列、
       * 维护原父级与新父级的 `hasChild`。混在一个接口里，"这次到底改了什么"
       * 就只能靠对比前后值来猜，而审计也就只剩一条 `auth.org.update`。
       */
      move: (id, body) => http.put<AccessOrganization>(`${PATH_ORGS}/${id}/parent`, body),
      remove: (id) => http.delete<void>(`${PATH_ORGS}/${id}`),
      pageMembers: (id, query) =>
        http.get<AccessOrgMemberPage>(`${PATH_ORGS}/${id}/members`, { query }),
      bindMember: (id, body) => http.post<AccessOrgMember>(`${PATH_ORGS}/${id}/members`, body),
      // 无请求体：这个动作没有参数可省 —— 目标就是路径上的这两个 id
      setPrimaryPosition: (id, ref) =>
        http.put<AccessOrgMember>(`${PATH_ORGS}/${id}/members/${ref.userId}/primary`),
      revokeMember: (id, ref) => http.delete<void>(`${PATH_ORGS}/${id}/members/${ref.userId}`),
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
 *
 * <p>具体的例子：`log:client:read` / `event:client:read`（ADR-0026）**刻意不在此列** ——
 * 日志台与事件台只是发请求，权限由后端 `@RequiresPermission` 判、前端读 401 / 403 的结果，
 * 没有任何一处会去比较这两个字符串。
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
  /*
   * 资源目录与角色：`auth:resource:*` / `auth:role:*`。
   *
   * 角色成员关系（授予 / 撤销成员）**刻意没有单独的权限码** —— 它与"改角色"复用
   * `auth:role:update`。理由是授角色给人本质上就是改这个角色的持有者，
   * 而它的强度等同于 `auth:user:update`（后者含重置任意口令）。
   * 但**审计动作是分开的**（`auth.role.grant_member` / `revoke_member`），
   * 因此"谁把人提上来了"在审计里查得到，不因为共用一个权限码而糊掉。
   */
  RESOURCE_READ: 'auth:resource:read',
  RESOURCE_CREATE: 'auth:resource:create',
  RESOURCE_UPDATE: 'auth:resource:update',
  RESOURCE_DELETE: 'auth:resource:delete',
  ROLE_READ: 'auth:role:read',
  ROLE_CREATE: 'auth:role:create',
  ROLE_UPDATE: 'auth:role:update',
  ROLE_DELETE: 'auth:role:delete',
  /*
   * 用户组：注意这里是**下划线**（`user_group`）而不是连字符。
   *
   * 接口路径写的是 `/api/auth/user-groups`（连字符，给人看），标识写的是
   * `auth:user_group:*`（下划线，给程序与配置用）—— 这是全仓唯一一处"两处不同形"。
   * 标识的字符集被后端 `AuthTaxonomyTest` 钉成 `[a-z][a-z0-9_]*`，
   * 放宽它会让所有"拿标识当键"的地方（缓存 key、前缀匹配、日志字段）都要开始转义。
   */
  USER_GROUP_READ: 'auth:user_group:read',
  USER_GROUP_CREATE: 'auth:user_group:create',
  USER_GROUP_UPDATE: 'auth:user_group:update',
  USER_GROUP_DELETE: 'auth:user_group:delete',
  /*
   * 组织（部门）。资源段是 `org` 而不是 `organization` ——
   * 路径写的是 `/api/auth/orgs`（缩写，给人看），标识写的是 `auth:org`（给程序用）。
   * 与用户组那处同理：标识的字符集被后端钉成 `[a-z][a-z0-9_]*`，两边不必同形，
   * 但**各自必须唯一且稳定** —— 标识一旦放开，拿它当键的地方都要开始转义。
   *
   * 下面四个覆盖了组织的全部写操作，包括**移动**与**设主岗**。
   * 它们没有单设权限码，理由与角色成员关系一致：能移动组织的人本来就能改组织，
   * 而"这次是移动还是改名"由**审计动作**回答（`auth.org.move` /
   * `auth.org.set_primary_position`），不必再拆权限码 ——
   * 拆出来的话，已经配好的角色会在升级后静默失去这两项能力。
   * 注意：**权限码不拆，但界面上必须是两个独立入口**，因为它们的后果不同。
   */
  ORG_READ: 'auth:org:read',
  ORG_CREATE: 'auth:org:create',
  ORG_UPDATE: 'auth:org:update',
  ORG_DELETE: 'auth:org:delete',
} as const;

/** 权限标识的字面量联合类型 */
export type AccessPermission = (typeof AccessPermissions)[keyof typeof AccessPermissions];
