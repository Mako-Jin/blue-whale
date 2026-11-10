// ERP 子应用按钮级权限：F-10
//
// 权限码来自基座登录后写入的 whale_platform_user.permissions；
// 前端仅做「按钮不渲染」的体验收敛，接口仍由网关/服务端 @RequirePermission 兜底 403。

/** 基座用户缓存 key（对齐 whale-platform/src/api/token.ts） */
import { PLATFORM_USER_KEY } from '@bluewhale/whale-auth';

const USER_STORAGE_KEY = PLATFORM_USER_KEY;

/** 基座缓存用户的最小结构 */
interface CachedUser {
  permissions?: string[];
}

/** 读取当前用户权限码（无缓存或解析失败时返回空数组） */
export function getPermissions(): string[] {
  const raw = localStorage.getItem(USER_STORAGE_KEY);
  if (!raw) {
    return [];
  }
  try {
    const user = JSON.parse(raw) as CachedUser;
    return Array.isArray(user.permissions) ? user.permissions : [];
  } catch {
    return [];
  }
}

/** 判断是否持有指定权限码 */
export function hasPermission(code: string): boolean {
  return getPermissions().includes(code);
}
