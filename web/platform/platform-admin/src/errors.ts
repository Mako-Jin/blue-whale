import { BusinessError, HttpStatusError, isHttpError } from 'common-tools/http';

/**
 * 把请求异常翻成给用户看的一句话。
 *
 * <p>只翻译**传输层**的错误（网络、超时、状态码）；业务错误直接用服务端给的文案 ——
 * 服务端比前端更清楚为什么拒绝，前端再包一层只会把"登录名已存在"改写成"操作失败"。
 *
 * <p>403 的文案是这里最要紧的一条：它**不是登录态问题**。
 * ADR-0016 把 401 与 403 的语义分开，前端据此决定"跳登录"还是"提示无权限"——
 * 管理后台恰恰是 403 的高发地（只读账号点到写操作），
 * 把它显示成"请重新登录"会让人白折腾一轮。
 */
export function describeError(error: unknown): string {
  if (error instanceof HttpStatusError) {
    if (error.status === 403) return '当前账号没有该操作的权限（403）';
    if (error.status === 401) return '登录态已失效（401），请重新登录';
    if (error.status === 404) return '目标不存在或已被删除（404）';
    return `请求失败（HTTP ${error.status}）${error.message ? `：${error.message}` : ''}`;
  }

  if (error instanceof BusinessError) {
    return error.message || `服务端拒绝了这次操作（code=${error.code}）`;
  }

  if (isHttpError(error)) return error.message;

  return error instanceof Error ? error.message : String(error);
}
