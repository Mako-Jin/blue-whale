import { BusinessError, HttpStatusError, isHttpError } from 'common-tools/http';

/**
 * 把请求异常翻成给用户看的一句话。
 *
 * <h3>为什么这里比权限管理台多一层"按码分流"</h3>
 *
 * 本应用要读的审计接口只有三个，它们的失败含义**极不相同**，
 * 而默认兜底是一句"操作失败"：
 *
 * - 没有 `auth:audit:read` → 需要换账号，不是"重试一下"；
 * - 令牌过期 → 需要重新登录；
 * - 对只读接口发了写方法 → 这不是故障，是**设计**（40500，见 `docs/decisions/0019`），
 *   把它显示成"系统内部错误"会让人去查服务端日志，而正确答案是"审计没有写接口"。
 *
 * 其余情况一律用**服务端给的文案** —— 后端比前端更清楚为什么拒绝，
 * 前端再包一层只会把"缺少权限：auth:audit:read"改写成"无权访问"。
 */

/**
 * 需要单独处置的两个业务码。
 *
 * <p>它们与后端 `ResultCode` / `AuthResultCode` 逐字对应（编码规则：5 位 = HTTP 3 位 + 细分 2 位）。
 * 在这里各留一份而不是从 `common-api` 引，是因为**前端没有消费它们的通用需求**：
 * 只有本应用的文案层需要区分"重新登录"与"没权限"，
 * 为此在契约包里加一份错误码表，等于让所有应用都拿到一张自己用不到的映射。
 */
const CODE_UNAUTHORIZED = 40100;
const CODE_NO_PERMISSION = 40301;

export function describeError(error: unknown): string {
  if (error instanceof BusinessError) {
    if (error.code === CODE_UNAUTHORIZED) return '登录态已失效（40100），请重新登录';
    if (error.code === CODE_NO_PERMISSION) return '当前账号没有该操作的权限（40301）';
    return error.message || `服务端拒绝了这次请求（code=${error.code}）`;
  }

  if (error instanceof HttpStatusError) {
    if (error.status === 403) return '当前账号没有该操作的权限（403）';
    if (error.status === 401) return '登录态已失效（401），请重新登录';
    if (error.status === 404) return '这条审计记录不存在或已被清理（404）';
    if (error.status === 405) return '该接口不支持这个请求方法（405）';
    return `请求失败（HTTP ${error.status}）${error.message ? `：${error.message}` : ''}`;
  }

  if (isHttpError(error)) return error.message;

  return error instanceof Error ? error.message : String(error);
}
