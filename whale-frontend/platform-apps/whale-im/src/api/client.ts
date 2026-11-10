// 统一 HTTP 客户端：基于 @bluewhale/http，注入基座登录态
// 生产同源反代；开发由子应用 vite proxy 转发到网关，默认使用相对路径

import { createHttpClient } from '@bluewhale/http';
import { createLogger } from '@bluewhale/whale-common-tools';
import { getToken } from './token';

const logger = createLogger('whale-im › http');

const client = createHttpClient({
  getToken,
  onUnauthorized: () => logger.warn('登录态失效，请从平台基座重新进入'),
});

export { ApiError } from '@bluewhale/http';
export type { HttpOptions, Result } from '@bluewhale/http';

export const { get, post, del, downloadBlob } = client;
