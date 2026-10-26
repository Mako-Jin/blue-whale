// 用例：一人多租户浏览器闭环（Wave 5.54 Phase 5.4）
// 覆盖：切换器渲染（>1 租户）→ 切换到 acme → 全量重载后租户上下文生效 → 切回默认租户。
// 数据自包含：运行前经 API 确保 acme 租户存在且 admin 已绑定，重复执行幂等。

import { expect, test, type APIRequestContext } from '@playwright/test';
import { ADMIN_BASE_URL, API_BASE_URL } from '../playwright.config';
import { ADMIN_PASSWORD, ADMIN_USERNAME, login } from './support/api';

const ACME_CODE = 'acme';
const ACME_NAME = 'Acme 公司';
const ADMIN_USER_ID = 1;

interface TenantPageItem {
  id: number | string;
  tenantCode: string;
}

interface MyTenantItem {
  tenantId: number | string;
  tenantCode: string;
  tenantName: string;
  current: boolean;
}

interface Envelope<T> {
  code: number;
  message: string;
  data: T;
}

async function unwrap<T>(response: { ok(): boolean; json(): Promise<unknown> }, label: string): Promise<T> {
  const body = (await response.json()) as Envelope<T>;
  if (!response.ok() || body.code !== 0) {
    throw new Error(`${label}失败：code=${body.code} message=${body.message}`);
  }
  return body.data;
}

/** 确保 acme 租户存在且 admin 已加入；返回 acme 租户 ID */
async function ensureAcmeMembership(request: APIRequestContext): Promise<string> {
  const token = await login(request);
  const headers = { Authorization: `Bearer ${token}` };

  const page = await unwrap<{ records: TenantPageItem[] }>(
    await request.get(`${API_BASE_URL}/tenant/page?current=1&size=50`, { headers }),
    '查询租户分页',
  );
  let acme = page.records.find((item) => item.tenantCode === ACME_CODE);
  if (!acme) {
    acme = await unwrap<TenantPageItem>(
      await request.post(`${API_BASE_URL}/tenant`, {
        headers,
        data: {
          tenantCode: ACME_CODE,
          tenantName: ACME_NAME,
          adminUsername: 'acmeadmin',
          adminPassword: 'acme123456',
          adminNickname: 'Acme 管理员',
        },
      }),
      '开通租户',
    );
  }
  const acmeId = String(acme.id);

  const myTenants = await unwrap<MyTenantItem[]>(
    await request.get(`${API_BASE_URL}/auth/tenants`, { headers }),
    '查询我的租户',
  );
  if (!myTenants.some((item) => String(item.tenantId) === acmeId)) {
    // 雪花 ID 超 Number.MAX_SAFE_INTEGER，必须以字符串原样传递（Jackson 可反序列化为 Long）
    await unwrap<null>(
      await request.post(`${API_BASE_URL}/user/${ADMIN_USER_ID}/tenants`, {
        headers,
        data: { tenantId: acmeId },
      }),
      '绑定用户租户',
    );
  }
  return acmeId;
}

/** 经切换器选择目标租户并等待切换接口与整页重载完成 */
async function switchTenantViaUi(
  page: import('@playwright/test').Page,
  optionText: string,
): Promise<void> {
  const switched = page.waitForResponse(
    (response) => response.url().includes('/auth/tenant/switch') && response.ok(),
  );
  await page.locator('.admin-tenant-switcher').click();
  await page.locator('.ant-select-dropdown .ant-select-item-option', { hasText: optionText }).click();
  await switched;
  // 切换成功后前端 window.location.replace('/') 整页重载，重建权限与租户上下文
  await page.waitForEvent('load');
  await expect(page.locator('.admin-tenant-switcher')).toBeVisible();
}

test('一人多租户：切换器切换到 acme 并切回默认租户', async ({ page, request }) => {
  const acmeId = await ensureAcmeMembership(request);

  // 1) 真实登录（默认租户）
  await page.goto(`${ADMIN_BASE_URL}/login`);
  await page.getByPlaceholder(/用户名/).fill(ADMIN_USERNAME);
  await page.getByPlaceholder(/密码/).fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await page.waitForURL((url) => url.pathname === '/');

  // 2) 顶栏出现租户切换器（仅当可进入租户 > 1），初始值为默认租户
  const switcher = page.locator('.admin-tenant-switcher');
  await expect(switcher).toBeVisible();
  await expect(switcher.locator('.ant-select-selection-item')).toHaveText(/默认租户/);

  // 3) 切换到 acme
  await switchTenantViaUi(page, ACME_NAME);
  await expect(switcher.locator('.ant-select-selection-item')).toHaveText(new RegExp(ACME_NAME));

  // 4) 新令牌租户上下文 = acme（/auth/me 以重载后 localStorage 中的新令牌查询）
  const acmeToken = await page.evaluate(() => localStorage.getItem('whale_admin_access_token'));
  expect(acmeToken).toBeTruthy();
  const meInAcme = await unwrap<{ tenantId: number | string; menus: unknown[] }>(
    await request.get(`${API_BASE_URL}/auth/me`, {
      headers: { Authorization: `Bearer ${acmeToken}` },
    }),
    '查询切换后的当前用户',
  );
  expect(String(meInAcme.tenantId)).toBe(acmeId);

  // 5) 切回默认租户，保证用例可重复执行
  await switchTenantViaUi(page, '默认租户');
  await expect(switcher.locator('.ant-select-selection-item')).toHaveText(/默认租户/);
  const defaultToken = await page.evaluate(() => localStorage.getItem('whale_admin_access_token'));
  const meInDefault = await unwrap<{ tenantId: number | string }>(
    await request.get(`${API_BASE_URL}/auth/me`, {
      headers: { Authorization: `Bearer ${defaultToken}` },
    }),
    '查询切回后的当前用户',
  );
  expect(String(meInDefault.tenantId)).toBe('1');
});
