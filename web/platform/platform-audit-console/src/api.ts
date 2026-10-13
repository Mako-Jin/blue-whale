import { createApiClient } from 'common-api';

import { consoleAuth } from './auth';

/**
 * 本应用的接口客户端。
 *
 * <p>只做一件事：把**认证端口**接到传输内核的鉴权钩子上 ——
 * 令牌由端口按需提供（基座在后台刷新之后，这里下一次请求拿到的就是新令牌），
 * 401 时请端口恢复一次（刷新因此由持有令牌的那一方合并，见 `auth/index.ts`）。
 *
 * <p>在模块加载时就装配好是安全的：两个 provider 都是**请求时求值**，
 * 而 `consoleAuth.port` 每次调用时才去问"基座在不在"。
 * 此前这里是一个延迟创建的函数，因为端口要等 `mount` 之后才能确定；
 * 两段式初始化取消后（见 `auth/index.ts`），那层延迟也没有理由了。
 */
export const api = createApiClient({
  accessTokenProvider: () => consoleAuth.port.getAccessToken(),
  onUnauthorized: () => consoleAuth.port.reauthenticate(),
});
