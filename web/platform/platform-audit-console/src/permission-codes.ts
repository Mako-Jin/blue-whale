/**
 * 权限常量 —— 与后端 `AuthPermissions` 逐字一致。
 *
 * <p>**不在本应用里重新写一份**：它来自 `common-api` 的 `AuditPermissions`
 * （契约包，带一条"逐字钉死"的测试）。这里只是转出，让页面代码写
 * `PERM.AUDIT_READ` 而不是裸字符串 —— 裸字符串拼错不会报错，
 * 只会让界面永远显示"无权查看"，而排查方向会先指向"权限没下发"。
 */
export { AuditPermissions as PERM } from 'common-api';

/** 权限标识：`域:资源:动作`，由**后端**定义，前端只引用 */
export type { AuditPermission as PermissionCode } from 'common-api';
