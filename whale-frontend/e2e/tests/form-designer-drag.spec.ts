// V-02（P1 验证）：form-designer @dnd-kit 真实鼠标拖拽复核。
// 既有结论「合成 PointerEvent 不可自动化」只针对 dispatchEvent；这里用 Playwright 真实鼠标
// 从左侧「数据字段」拖到画布，保存后由 API 反查字段配置，并保留截图供人工复核。

import { expect, test } from '@playwright/test';
import { FORM_BASE_URL } from '../playwright.config';
import {
  ADMIN_PASSWORD,
  ADMIN_USERNAME,
  createFormDefinition,
  createTableDefinition,
  deleteResource,
  formDefinitionDetail,
  login,
  publishTableDefinition,
  tableDefinitionDetail,
} from './support/api';

const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
const tableCode = `e2e_drag_${suffix}`;
const formCode = `${tableCode}_form`;
const formName = `E2E拖拽表单${suffix}`;

let token = '';
let tableId = '';
let formId = '';

test.afterAll(async ({ request }) => {
  if (!token) {
    return;
  }
  for (const path of [
    formId ? `/form/definition/${formId}` : '',
    tableId ? `/form/table-definition/${tableId}` : '',
  ].filter((item) => item.length > 0)) {
    await deleteResource(request, token, path);
  }
});

test('数据字段拖入画布并保存', async ({ page, request }, testInfo) => {
  token = await login(request);

  // 1) API 准备表定义（列名即调色板条目）与空表单定义
  const table = await createTableDefinition(request, token, {
    tableCode,
    tableName: `E2E拖拽表${suffix}`,
    version: 1,
    status: 1,
    columns: [
      {
        columnCode: 'e2e_drag_name',
        columnName: '姓名',
        dataType: 'text',
        length: 128,
        // 必填列必须非空：后端建表校验会拒绝 required=1 且 nullable=1 的组合
        nullable: 0,
        required: 1,
        sortNo: 1,
        status: 1,
      },
    ],
  });
  tableId = table.id;
  await publishTableDefinition(request, token, tableId);
  const tableDetail = await tableDefinitionDetail(request, token, tableId);
  const column = tableDetail.columns?.[0];
  if (!column) {
    throw new Error(`表定义 ${tableCode} 未返回列元数据`);
  }

  const form = await createFormDefinition(request, token, {
    code: formCode,
    name: formName,
    version: 1,
    status: 1,
  });
  formId = form.id;

  // 2) 登录 form-designer，直接进入设计器并带上所属表定义
  await page.goto(`${FORM_BASE_URL}/login`);
  await expect(page.getByRole('heading', { name: '蓝鲸表单设计器' })).toBeVisible();
  await page.getByPlaceholder(/用户名/).fill(ADMIN_USERNAME);
  await page.getByPlaceholder(/密码/).fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await page.waitForURL((url) => url.pathname === '/tables');

  await page.goto(`${FORM_BASE_URL}/design/${formId}?tableDefinitionId=${tableId}`);
  const canvas = page.getByTestId('form-designer-canvas');
  await expect(canvas.getByText('从左侧拖拽组件到此处')).toBeVisible();

  // 3) 真实鼠标拖拽：调色板条目 → 画布（dnd-kit PointerSensor，distance=5）
  const paletteItem = page.getByRole('button', { name: /姓名\s*e2e_drag_name/ }).first();
  await expect(paletteItem).toBeVisible();
  await paletteItem.dragTo(canvas, { targetPosition: { x: 80, y: 80 } });

  await expect(canvas.getByText('姓名', { exact: true })).toBeVisible();
  await expect(page.getByText('已使用')).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('form-designer-drag.png'), fullPage: true });

  // 4) 保存并由 API 核验字段落库
  await page.getByRole('button', { name: /保\s*存/ }).click();
  await expect(page.locator('.ant-message').getByText('保存成功').first()).toBeVisible();

  const saved = await formDefinitionDetail(request, token, formId);
  expect(saved.tableDefinitionId).toBe(tableId);
  expect(saved.fields).toHaveLength(1);
  expect(saved.fields?.[0]?.columnMetaId).toBe(column.id);
  expect(saved.fields?.[0]?.fieldLabel).toBe('姓名');
  expect(saved.fields?.[0]?.widgetType).toBe('text');
});
