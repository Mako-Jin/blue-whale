import { isHttpError } from 'common-tools/http';
import type { HttpErrorKind } from 'common-tools/http';

/**
 * 把"网络层的失败"翻成"给人看的一句话"。
 *
 * <p>刻意只翻**传输层**那几类：`business` / `http` 两类直接用服务端给的消息 ——
 * 「用户名或密码错误」这类文案由后端统一维护（而且后端刻意不区分"用户不存在"与"口令错误"，
 * 以避免账号枚举），前端另写一套只会变成两份不一致的说法。
 */
const TRANSPORT_MESSAGE: Partial<Record<HttpErrorKind, string>> = {
  network: '连不上服务端，请确认后端已启动',
  timeout: '请求超时，请稍后重试',
  abort: '请求已取消',
  parse: '服务端响应无法解析，可能是前后端版本不一致',
};

export function describeAuthError(cause: unknown): string {
  if (isHttpError(cause)) {
    return TRANSPORT_MESSAGE[cause.kind] ?? cause.message;
  }
  return cause instanceof Error ? cause.message : '登录失败，请重试';
}
