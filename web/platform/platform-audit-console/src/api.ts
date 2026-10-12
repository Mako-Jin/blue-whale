import { createApiClient } from 'common-api';
import type { ApiClient } from 'common-api';

import { getAuthPort } from './auth';

/**
 * 本应用的接口客户端。
 *
 * <p>只做一件事：把**认证端口**接到传输内核的鉴权钩子上 —— 令牌由端口按需提供
 * （基座在后台刷新之后，这里下一次请求拿到的就是新令牌），401 时请端口恢复一次。
 *
 * <p>延迟创建是必要的：被基座加载时，认证端口要等 `mount` 之后才能确定。
 * 页面里的请求都发生在 effect 之后，因此拿得到。
 */
let client: ApiClient | null = null;

export function api(): ApiClient {
  if (!client) {
    const port = getAuthPort();
    client = createApiClient({
      accessTokenProvider: () => port.getAccessToken(),
      onUnauthorized: () => port.reauthenticate(),
    });
  }
  return client;
}
