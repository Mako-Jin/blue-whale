// 前置依赖自检：网关/auth/form 任一未就绪时快速失败，并给出可操作的启动提示。

import { request } from '@playwright/test';
import { API_BASE_URL } from '../../playwright.config';
import { fetchMe, login, pageTableDefinitionsByCode } from './api';

const BACKEND_HINT = [
  '请先启动：MySQL(3306)、Redis(6379)、Nacos(8848)、whale-gateway(8080)、whale-auth(8081)、whale-form(8084)',
  `当前网关地址：${API_BASE_URL}`,
].join('\n');

export default async function globalSetup(): Promise<void> {
  const ctx = await request.newContext({ timeout: 10_000 });
  try {
    let token: string;
    try {
      token = await login(ctx);
    } catch (error) {
      throw new Error(
        `E2E 前置失败：网关或 whale-auth 不可用。\n${BACKEND_HINT}\n原因：${(error as Error).message}`,
      );
    }

    const me = await fetchMe(ctx, token);
    if (!me.menus?.length) {
      throw new Error('E2E 前置失败：GET /auth/me 未返回权限菜单，请检查 admin 种子与菜单数据');
    }

    try {
      await pageTableDefinitionsByCode(ctx, token, 'e2e_bootstrap_probe');
    } catch (error) {
      const message = (error as Error).message;
      // 编码不存在属正常（列表为空），只有服务不可达/网关 503 才视为前置失败
      if (
        message.includes('HTTP 503') ||
        message.includes('HTTP 502') ||
        message.includes('HTTP 500')
      ) {
        throw new Error(`E2E 前置失败：whale-form 服务不可用。\n${BACKEND_HINT}\n原因：${message}`);
      }
    }
  } finally {
    await ctx.dispose();
  }
}
