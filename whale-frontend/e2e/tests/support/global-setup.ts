// 前置依赖自检：网关/auth/form/workflow 任一未就绪时快速失败，并给出可操作的启动提示。

import { request } from '@playwright/test';
import { API_BASE_URL } from '../../playwright.config';
import {
  ADMIN_PASSWORD,
  changeOwnPassword,
  fetchMe,
  login,
  loginFull,
  pageTableDefinitionsByCode,
  pageWorkflowDefinitions,
} from './api';

const BACKEND_HINT = [
  '请先启动：MySQL(3306)、Redis(6379)、Nacos(8848)、whale-gateway(8080)、whale-service-auth(8081)、whale-form(8084)、whale-workflow(8083)',
  `当前网关地址：${API_BASE_URL}`,
].join('\n');

export default async function globalSetup(): Promise<void> {
  const ctx = await request.newContext({ timeout: 10_000 });
  try {
    let token: string;
    try {
      // CI 全新库每轮由 AdminPasswordInitializer 轮换 admin 口令并置 must_change_password=1，
      // form/workflow designer 登录页遇该标记不跳转（弹强制改密框），导致相关用例集体超时。
      // 引导流程必须用「自助改密」端点（/auth/v1/password）：强制改密状态下 auth 侧只放行
      // 该端点与 logout/refresh/me，管理员重置端点 /user/v1/{id}/password 会被 403 拦掉。
      // 又因策略禁止「新口令与当前相同」，这里走两步：CI 口令 → 临时口令（清标记）→ 改回 CI 口令，
      // 使后续用例仍可用 E2E_ADMIN_PASSWORD 登录。
      const session = await loginFull(ctx);
      if (session.mustChangePassword) {
        const temporaryPassword = `${ADMIN_PASSWORD}Tmp1`;
        await changeOwnPassword(ctx, session.accessToken, ADMIN_PASSWORD, temporaryPassword);
        const tempToken = await login(ctx, undefined, temporaryPassword);
        await changeOwnPassword(ctx, tempToken, temporaryPassword, ADMIN_PASSWORD);
        token = await login(ctx);
      } else {
        token = session.accessToken;
      }
    } catch (error) {
      throw new Error(
        `E2E 前置失败：网关或 whale-service-auth 不可用。\n${BACKEND_HINT}\n原因：${(error as Error).message}`,
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

    try {
      await pageWorkflowDefinitions(ctx, token, 'e2e_bootstrap_probe');
    } catch (error) {
      const message = (error as Error).message;
      if (
        message.includes('HTTP 503') ||
        message.includes('HTTP 502') ||
        message.includes('HTTP 500')
      ) {
        throw new Error(
          `E2E 前置失败：whale-workflow 服务不可用。\n${BACKEND_HINT}\n原因：${message}`,
        );
      }
    }
  } finally {
    await ctx.dispose();
  }
}
