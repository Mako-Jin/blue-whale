// 用例 A（P0-3 第一批）：未登录访问受保护页 → 跳登录 → 登录成功 → 菜单按权限渲染。

import { expect, test } from '@playwright/test';
import { ADMIN_BASE_URL } from '../playwright.config';
import { ADMIN_PASSWORD, ADMIN_USERNAME, fetchMe, login } from './support/api';

test('未登录跳登录，登录后菜单按权限渲染', async ({ page, request }) => {
  // 1) 未登录访问受保护页面 → /login
  await page.goto(`${ADMIN_BASE_URL}/users`);
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: 'BlueWhale Admin' })).toBeVisible();

  // 2) 真实登录
  await page.getByPlaceholder(/用户名/).fill(ADMIN_USERNAME);
  await page.getByPlaceholder(/密码/).fill(ADMIN_PASSWORD);
  // antd 会在两个汉字间插入空格（登 录），故用正则匹配
  await page.getByRole('button', { name: /登\s*录/ }).click();

  // 3) 登录后进入首页；PermissionGate 拉取 /auth/me 后才渲染动态菜单
  await page.waitForURL((url) => url.pathname === '/');
  await expect(page.getByText('知识助手', { exact: true })).toBeVisible();

  // 4) 期望菜单 = /auth/me 返回的权限菜单（顶层，按 sort 排序）
  const token = await login(request);
  const me = await fetchMe(request, token);
  const expectedLabels = [...me.menus]
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
    .map((menu) => menu.name.trim());

  // 5) 侧边栏顶层菜单文本集合必须与权限菜单一致（权限过滤被改坏时此处失败）
  const topLevelLabels = page.locator(
    '.ant-layout-sider .ant-menu-root > li > .ant-menu-submenu-title .ant-menu-title-content, ' +
      '.ant-layout-sider .ant-menu-root > li > .ant-menu-item .ant-menu-title-content',
  );
  await expect(topLevelLabels.first()).toBeVisible();
  const renderedLabels = (await topLevelLabels.allInnerTexts()).map((text) => text.trim());
  expect(renderedLabels).toEqual(expectedLabels);

  // 6) admin 具备知识问答权限 → 菜单必须出现「知识助手」
  expect(me.permissions).toContain('sys:agent:chat');
  expect(renderedLabels).toContain('知识助手');

  // 7) 动态菜单可进入：系统管理 → 用户管理
  await page.locator('.ant-layout-sider .ant-menu-submenu-title', { hasText: '系统管理' }).click();
  await page.getByRole('menuitem', { name: '用户管理' }).click();
  await expect(page).toHaveURL(/\/system\/user$/);
  await expect(page.locator('.ant-table').first()).toBeVisible();
});
