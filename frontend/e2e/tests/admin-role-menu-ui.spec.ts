// V-03（P1 验证）：admin 角色权限分配、用户角色分配、角色删除、菜单删除的 UI 闭环。
// API 只负责造数/最终核验；所有被复验动作均通过真实后台界面完成。

import { expect, test } from '@playwright/test';
import { ADMIN_BASE_URL } from '../playwright.config';
import {
  ADMIN_PASSWORD,
  ADMIN_USERNAME,
  createMenu,
  createRole,
  createUser,
  deleteResource,
  login,
  pagePermissionRecords,
  pageRoleRecords,
  rolePermissionIds,
  treeMenuRecords,
  userRoleIds,
  type MenuRecord,
} from './support/api';

const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
const roleCode = `e2e_role_${suffix}`;
const roleName = `E2E角色${suffix}`;
const menuName = `E2E菜单${suffix}`;
const username = `e2e_user_${suffix}`;

let token = '';
let roleId = 0;
let menuId = 0;
let userId = 0;

function flattenMenus(nodes: MenuRecord[]): MenuRecord[] {
  return nodes.flatMap((node) => [node, ...flattenMenus(node.children ?? [])]);
}

test.afterAll(async ({ request }) => {
  if (!token) {
    return;
  }
  await deleteResource(request, token, `/user/${userId}`);
  await deleteResource(request, token, `/role/${roleId}`);
  await deleteResource(request, token, `/menu/${menuId}`);
});

test('角色权限分配→用户角色分配→角色/菜单删除', async ({ page, request }) => {
  token = await login(request);

  // 1) API 造数：角色、菜单、用户；权限取现有第一条
  const role = await createRole(request, token, {
    roleCode,
    roleName,
    description: 'V-03 UI 复验角色',
    sortNo: 99,
    status: 1,
  });
  roleId = role.id;

  await createMenu(request, token, {
    parentId: 0,
    name: menuName,
    path: `/e2e-${suffix}`,
    component: 'Home',
    sort: 99,
    menuType: 'MENU',
    visible: 1,
    status: 1,
  });
  const menu = flattenMenus(await treeMenuRecords(request, token)).find(
    (item) => item.name === menuName,
  );
  if (!menu) {
    throw new Error(`菜单 ${menuName} 创建后未出现在菜单树`);
  }
  menuId = menu.id;

  const user = await createUser(request, token, {
    username,
    password: 'E2ePass123!',
    nickname: `E2E用户${suffix}`,
    status: 1,
  });
  userId = user.id;

  const permissions = await pagePermissionRecords(request, token);
  const permission = permissions.records[0];
  if (!permission) {
    throw new Error('权限列表为空，无法复验角色权限分配');
  }

  // 2) 登录 admin
  await page.goto(`${ADMIN_BASE_URL}/roles`);
  await expect(page).toHaveURL(/\/login$/);
  await page.getByPlaceholder(/用户名/).fill(ADMIN_USERNAME);
  await page.getByPlaceholder(/密码/).fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await page.waitForURL((url) => url.pathname === '/');

  // 3) 角色管理 → 分配权限
  await page.goto(`${ADMIN_BASE_URL}/roles`);
  const roleSearch = page.getByPlaceholder('角色编码 / 名称');
  await roleSearch.fill(roleCode);
  await page.getByRole('button', { name: /查\s*询/ }).click();
  const roleRow = page.locator('tbody tr').filter({ hasText: roleCode });
  await expect(roleRow).toHaveCount(1);
  await roleRow.getByRole('button', { name: '分配权限' }).click();

  const permModal = page.locator('.ant-modal-content').filter({ hasText: `分配权限：${roleName}` });
  await expect(permModal).toBeVisible();
  const firstPermRow = permModal.locator('tbody tr.ant-table-row').first();
  await expect(firstPermRow).toBeVisible();
  await firstPermRow.locator('.ant-checkbox').click();
  await expect(firstPermRow.locator('.ant-checkbox-checked')).toBeVisible();
  await permModal.getByRole('button', { name: /OK|确\s*定/ }).click();
  await expect(page.locator('.ant-message').getByText('权限已分配').first()).toBeVisible();

  const assignedPermissionIds = await rolePermissionIds(request, token, roleId);
  expect(assignedPermissionIds).toContain(permission.id);

  // 4) 用户管理 → 分配角色
  await page.goto(`${ADMIN_BASE_URL}/users`);
  const userSearch = page.getByPlaceholder('用户名 / 昵称 / 手机号');
  await userSearch.fill(username);
  await page.getByRole('button', { name: /查\s*询/ }).click();
  const userRow = page.locator('tbody tr').filter({ hasText: username });
  await expect(userRow).toHaveCount(1);
  await userRow.getByRole('button', { name: '分配角色' }).click();

  const userRoleModal = page
    .locator('.ant-modal-content')
    .filter({ hasText: `分配角色：${username}` });
  await expect(userRoleModal).toBeVisible();
  await userRoleModal.locator('label').filter({ hasText: roleName }).click();
  await userRoleModal.getByRole('button', { name: /OK|确\s*定/ }).click();
  await expect(page.locator('.ant-message').getByText('角色已分配').first()).toBeVisible();

  const assignedRoleIds = await userRoleIds(request, token, userId);
  expect(assignedRoleIds).toContain(roleId);

  // 5) 先解除用户占用，再复验角色删除
  await deleteResource(request, token, `/user/${userId}`);
  userId = 0;

  await page.goto(`${ADMIN_BASE_URL}/roles`);
  await page.getByPlaceholder('角色编码 / 名称').fill(roleCode);
  await page.getByRole('button', { name: /查\s*询/ }).click();
  const deleteRoleRow = page.locator('tbody tr').filter({ hasText: roleCode });
  await expect(deleteRoleRow).toHaveCount(1);
  await deleteRoleRow.getByRole('button', { name: /删\s*除/ }).click();
  await page.locator('.ant-popconfirm').getByRole('button', { name: /OK|确\s*定/ }).click();
  await expect(page.locator('.ant-message').getByText('角色已删除').first()).toBeVisible();
  const rolesAfterDelete = await pageRoleRecords(request, token, roleCode);
  expect(rolesAfterDelete.records.some((item) => item.id === roleId)).toBe(false);
  roleId = 0;

  // 6) 菜单管理 → 删除
  await page.goto(`${ADMIN_BASE_URL}/menus`);
  const menuRow = page.locator('tbody tr').filter({ hasText: menuName });
  await expect(menuRow).toHaveCount(1);
  await menuRow.getByRole('button', { name: /删\s*除/ }).click();
  await page.locator('.ant-popconfirm').getByRole('button', { name: /OK|确\s*定/ }).click();
  await expect(page.locator('.ant-message').getByText('菜单已删除').first()).toBeVisible();
  const menusAfterDelete = flattenMenus(await treeMenuRecords(request, token));
  expect(menusAfterDelete.some((item) => item.id === menuId)).toBe(false);
  menuId = 0;
});
