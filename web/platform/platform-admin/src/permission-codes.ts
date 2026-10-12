/**
 * 权限常量 —— 与后端 `AuthPermissions` 逐字一致。
 *
 * <p>**不在本应用里重新写一份**：它们来自 `common-api` 的 `AccessPermissions`
 * （契约包，且带一条"逐字钉死"的测试）。这里只是转出，让页面代码写
 * `PERM.USER_CREATE` 而不是裸字符串 —— 裸字符串拼错不会报错，
 * 只会让按钮永远不出现，而排查方向会先指向"权限没下发"
 * （`docs/decisions/0018` 的后续约束）。
 */
export { AccessPermissions as PERM } from 'common-api';

/**
 * 权限标识。
 *
 * <p>与 `common-tools/micro` 的 `HostGlobalState.permissions` 同一个含义：
 * `域:资源:动作`，由**后端**定义，前端只引用。
 */
export type { AccessPermission as PermissionCode } from 'common-api';
