/**
 * 基座（whale-platform）登录态 storage key 常量与用户缓存只读器。
 *
 * 子应用（contract/doc/erp/mes/oa/pdm/pm/portal 等）此前各自硬编码这些字符串
 * （审计 P1），key 改名时需逐文件排查；现统一从这里导入。
 *
 * V-03：accessToken 改为基座内存态，子应用经 qiankun props 的 getToken 读取
 * （见 whale-im/src/platform/host.ts 模式），不再有 token 的 localStorage key。
 */

/** 基座用户缓存的 localStorage key */
export const PLATFORM_USER_KEY = 'whale_platform_user';

/**
 * 读取基座缓存的当前用户（只读；无缓存或解析失败返回 null）。
 *
 * @typeParam U 期望的用户结构，各子应用可用本地窄化类型（如 ContractUser）
 */
export function getPlatformUser<U = unknown>(): U | null {
  const raw = localStorage.getItem(PLATFORM_USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as U;
  } catch {
    // 损坏的缓存直接忽略
    return null;
  }
}
