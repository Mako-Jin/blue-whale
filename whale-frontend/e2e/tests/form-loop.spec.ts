// 用例 B（P0-3 第一批）：建表 → 加列 → 发布 → 填报 → 数据列表可见。
// 字段配置走 API：@dnd-kit PointerSensor 不接受合成 PointerEvent（Wave 5.29/5.30/5.32 既有结论），
// 其余步骤（建表/加列/发布/填报/列表/详情）全部走真实 UI。

import { expect, test } from '@playwright/test';
import { FORM_BASE_URL } from '../playwright.config';
import {
  ADMIN_PASSWORD,
  ADMIN_USERNAME,
  createFormDefinition,
  deleteResource,
  login,
  pageFormData,
  pageTableDefinitionsByCode,
  tableDefinitionDetail,
} from './support/api';

const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
const tableCode = `e2e_${suffix}`;
const tableName = `E2E冒烟表${suffix}`;
const formCode = `${tableCode}_form`;
const submittedValue = `测试用户${suffix}`;

let token = '';
let tableId = '';
let formId = '';
let dataId = '';

test.afterAll(async ({ request }) => {
  if (!token) {
    return;
  }
  // 尽力清理，避免反复执行污染数据库；失败不影响用例结论
  const cleanups = [
    dataId ? `/form/v1/data/${dataId}` : '',
    formId ? `/form/v1/definition/${formId}` : '',
    tableId ? `/form/v1/table-definition/${tableId}` : '',
  ].filter((path) => path.length > 0);
  for (const path of cleanups) {
    await deleteResource(request, token, path);
  }
});

test('建表→加列→发布→填报→列表可见', async ({ page, request }) => {
  // 1) UI 登录表单设计器
  await page.goto(`${FORM_BASE_URL}/login`);
  await expect(page.getByRole('heading', { name: '蓝鲸表单设计器' })).toBeVisible();
  await page.getByPlaceholder(/用户名/).fill(ADMIN_USERNAME);
  await page.getByPlaceholder(/密码/).fill(ADMIN_PASSWORD);
  // antd 会在两个汉字间插入空格（登 录），故用正则匹配
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await page.waitForURL((url) => url.pathname === '/tables');

  // 2) 建表 + 加一列
  await page.getByRole('button', { name: '新建表定义' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByPlaceholder('如 user_info').fill(tableCode);
  await dialog.getByPlaceholder('如 用户信息表').fill(tableName);
  await dialog.getByRole('button', { name: '新增列' }).click();
  await dialog.getByPlaceholder('如 user_name').fill('e2e_name');
  await dialog.getByPlaceholder('如 用户名').fill('姓名');

  // 3) 保存 → 列表出现该表
  await dialog.getByRole('button', { name: /保\s*存/ }).click();
  await expect(page.getByText('创建成功')).toBeVisible();
  const tableRow = page.locator('tr', { hasText: tableCode });
  await expect(tableRow).toBeVisible();

  // 4) 发布（物理表结构同步）
  await tableRow.getByRole('button', { name: /发\s*布/ }).click();
  await expect(page.getByText('发布成功，物理表结构已同步')).toBeVisible();

  // 5) API 建表单视图：取表 id + 列 id，按元数据模型提交字段
  token = await login(request);
  const table = await pageTableDefinitionsByCode(request, token, tableCode);
  tableId = table.id;
  const detail = await tableDefinitionDetail(request, token, tableId);
  const column = detail.columns?.[0];
  if (!column) {
    throw new Error(`表定义 ${tableCode} 未返回列元数据`);
  }
  const form = await createFormDefinition(request, token, {
    code: formCode,
    name: 'E2E冒烟表单',
    tableDefinitionId: tableId,
    version: 1,
    status: 1,
    fields: [
      {
        columnMetaId: column.id,
        fieldLabel: '姓名',
        widgetType: 'text',
        showInList: 1,
        showInForm: 1,
        editable: 1,
        sortNo: 0,
        colSpan: 24,
        status: 1,
      },
    ],
  });
  formId = form.id;

  // 6) 填报页提交
  await page.goto(`${FORM_BASE_URL}/render/${formId}`);
  await expect(page.getByText('E2E冒烟表单')).toBeVisible();
  await page.getByLabel('姓名').fill(submittedValue);
  await page.getByRole('button', { name: /提\s*交/ }).click();
  await expect(page.getByText('提交成功')).toBeVisible();

  // 7) 数据列表可见，详情展示字段值
  await page.goto(`${FORM_BASE_URL}/data?definitionCode=${formCode}`);
  const dataRow = page.locator('tr', { hasText: formCode });
  await expect(dataRow).toBeVisible();
  await dataRow.getByRole('button', { name: /详\s*情/ }).click();
  const drawer = page.locator('.ant-drawer-content');
  await expect(drawer).toBeVisible();
  await expect(drawer.getByText('姓名', { exact: true })).toBeVisible();
  await expect(drawer.getByText(submittedValue, { exact: true })).toBeVisible();

  // 8) API 复核：该表单至少一条数据
  const dataPage = await pageFormData(request, token, formCode);
  expect(Number(dataPage.total)).toBeGreaterThanOrEqual(1);
  dataId = dataPage.records.find((record) => record.definitionCode === formCode)?.id ?? '';
});
