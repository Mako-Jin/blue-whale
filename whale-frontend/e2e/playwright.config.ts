// P0-3 E2E 冒烟配置：webServer 自动拉起 admin（8001）、表单设计器（8002）与工作流设计器（8003），
// 后端网关/auth/form/workflow 必须先就绪（global-setup 会快速失败并给出提示）。

import { defineConfig } from '@playwright/test';

/** 目标地址均可由环境变量覆盖，默认本机开发端口 */
export const ADMIN_BASE_URL = process.env.E2E_ADMIN_BASE_URL ?? 'http://localhost:8001';
export const FORM_BASE_URL = process.env.E2E_FORM_BASE_URL ?? 'http://localhost:8002';
export const WORKFLOW_BASE_URL = process.env.E2E_WORKFLOW_BASE_URL ?? 'http://localhost:8003';
export const API_BASE_URL = process.env.E2E_API_BASE_URL ?? 'http://localhost:8080';

export default defineConfig({
  testDir: './tests',
  globalSetup: './tests/support/global-setup.ts',
  // 表单单测会写库，串行执行避免唯一编码/清理互相干扰
  workers: 1,
  timeout: 90_000,
  expect: { timeout: 10_000 },
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  webServer: [
    {
      command: 'pnpm --filter whale-admin dev -- --port 8001 --strictPort',
      cwd: '..',
      url: `${ADMIN_BASE_URL}/login`,
      reuseExistingServer: true,
      timeout: 120_000,
    },
    {
      command: 'pnpm --filter whale-form-designer dev -- --port 8002 --strictPort',
      cwd: '..',
      url: `${FORM_BASE_URL}/login`,
      reuseExistingServer: true,
      timeout: 120_000,
    },
    {
      command: 'pnpm --filter whale-workflow-designer dev -- --port 8003 --strictPort',
      cwd: '..',
      url: `${WORKFLOW_BASE_URL}/login`,
      reuseExistingServer: true,
      timeout: 120_000,
    },
  ],
});
