// 用例：数据列表动态字段筛选的 in / between UI 暴露。
// 表定义/表单/数据全部走 API 预置，UI 只验证筛选构建区（字段 → 操作符 → 值 → 查询）。
// 后端 filters 契约与 SQL 参数化已由 whale-form 单测覆盖，本用例锁定前端交互与序列化。

import { expect, test, type Page } from '@playwright/test';
import { API_BASE_URL, FORM_BASE_URL } from '../playwright.config';
import {
  ADMIN_PASSWORD,
  ADMIN_USERNAME,
  createFormDefinition,
  createTableDefinition,
  deleteResource,
  login,
  publishTableDefinition,
  submitFormData,
  tableDefinitionDetail,
} from './support/api';

const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
const tableCode = `e2e_flt_${suffix}`;
const formCode = `${tableCode}_form`;

let token = '';
let tableId = '';
let formId = '';
const dataIds: string[] = [];

test.afterAll(async ({ request }) => {
  if (!token) {
    return;
  }
  const cleanups = [
    ...dataIds.map((id) => `/form/data/${id}`),
    formId ? `/form/definition/${formId}` : '',
    tableId ? `/form/table-definition/${tableId}` : '',
  ].filter((path) => path.length > 0);
  for (const path of cleanups) {
    await deleteResource(request, token, path);
  }
});

/**
 * 选择 antd 下拉项：role=option 位于隐藏的无障碍列表（height:0），
 * 真正可点击的是下拉面板内的 .ant-select-item-option。
 */
async function pickOption(page: Page, label: string): Promise<void> {
  await page
    .locator(`.ant-select-dropdown:visible .ant-select-item-option[title="${label}"]`)
    .click();
}

/** 通过内部 search input 的 id 定位 antd Select 外层容器（有选中值时 input 会被遮挡） */
function selectByInputId(page: Page, id: string) {
  return page.locator('.ant-select').filter({ has: page.locator(`#${id}`) });
}

/** 打开筛选字段下拉并选择字段 */
async function chooseField(page: Page, label: string): Promise<void> {
  await selectByInputId(page, 'filter-field').click();
  await pickOption(page, label);
}

/** 打开操作符下拉并选择操作符 */
async function chooseOp(page: Page, label: string): Promise<void> {
  await selectByInputId(page, 'filter-op').click();
  await pickOption(page, label);
}

/** 点击指定条件 Tag 的关闭按钮移除条件 */
async function removeTag(page: Page, text: string): Promise<void> {
  await page
    .locator('.ant-tag')
    .filter({ hasText: text })
    .locator('.ant-tag-close-icon')
    .click();
}

/** 执行查询（antd 两字按钮会插入空格，用正则匹配） */
async function search(page: Page): Promise<void> {
  await page.getByRole('button', { name: /查\s*询/ }).click();
}

test('动态字段筛选：between 数值/日期区间 / in 选项与文本', async ({ page, request }) => {
  token = await login(request);

  // 1) 建表定义（number/select/text/date）并发布
  const table = await createTableDefinition(request, token, {
    tableCode,
    tableName: `E2E筛选表${suffix}`,
    version: 1,
    status: 1,
    columns: [
      {
        columnCode: 'amount',
        columnName: '金额',
        dataType: 'number',
        precision: 18,
        scale: 2,
        nullable: 1,
        required: 0,
        sortNo: 0,
        status: 1,
      },
      {
        columnCode: 'stage',
        columnName: '阶段',
        dataType: 'select',
        nullable: 1,
        required: 0,
        sortNo: 1,
        status: 1,
      },
      {
        columnCode: 'memo',
        columnName: '备注',
        dataType: 'text',
        length: 100,
        nullable: 1,
        required: 0,
        sortNo: 2,
        status: 1,
      },
      {
        columnCode: 'due_date',
        columnName: '到期日',
        dataType: 'date',
        nullable: 1,
        required: 0,
        sortNo: 3,
        status: 1,
      },
    ],
  });
  tableId = table.id;
  await publishTableDefinition(request, token, tableId);

  const detail = await tableDefinitionDetail(request, token, tableId);
  const columnIds = new Map((detail.columns ?? []).map((column) => [column.columnCode, column.id]));
  const columnId = (code: string): string => {
    const id = columnIds.get(code);
    if (!id) {
      throw new Error(`表定义缺少列：${code}`);
    }
    return id;
  };

  // 2) 建表单视图（select 字段带选项，用于 in 多选控件）
  const form = await createFormDefinition(request, token, {
    code: formCode,
    name: `E2E筛选表单${suffix}`,
    tableDefinitionId: tableId,
    version: 1,
    status: 1,
    fields: [
      {
        columnMetaId: columnId('amount'),
        fieldLabel: '金额',
        widgetType: 'number',
        showInList: 1,
        showInForm: 1,
        editable: 1,
        sortNo: 0,
        colSpan: 24,
        status: 1,
      },
      {
        columnMetaId: columnId('stage'),
        fieldLabel: '阶段',
        widgetType: 'select',
        showInList: 1,
        showInForm: 1,
        editable: 1,
        sortNo: 1,
        colSpan: 24,
        status: 1,
        options: [
          { optionLabel: '新建', optionValue: 'new', sortNo: 0 },
          { optionLabel: '完成', optionValue: 'done', sortNo: 1 },
        ],
      },
      {
        columnMetaId: columnId('memo'),
        fieldLabel: '备注',
        widgetType: 'text',
        showInList: 1,
        showInForm: 1,
        editable: 1,
        sortNo: 2,
        colSpan: 24,
        status: 1,
      },
      {
        columnMetaId: columnId('due_date'),
        fieldLabel: '到期日',
        widgetType: 'date',
        showInList: 1,
        showInForm: 1,
        editable: 1,
        sortNo: 3,
        colSpan: 24,
        status: 1,
      },
    ],
  });
  formId = form.id;

  // 3) 预置 5 条数据：stage=hold 的一条用于证明 in 条件确实生效
  const rows = [
    { amount: 50, stage: 'new', due_date: '2026-01-10' },
    { amount: 100, stage: 'new', memo: 'beta', due_date: '2026-03-01' },
    { amount: 150, stage: 'done', memo: 'alpha', due_date: '2026-06-01' },
    { amount: 200, stage: 'done', memo: 'beta gamma', due_date: '2026-12-01' },
    { amount: 300, stage: 'hold', memo: 'omega', due_date: '2027-01-01' },
  ];
  for (const row of rows) {
    const record = await submitFormData(request, token, formId, JSON.stringify(row));
    dataIds.push(record.id);
  }

  // 4) UI 登录并进入数据列表
  await page.goto(`${FORM_BASE_URL}/login`);
  await page.getByPlaceholder(/用户名/).fill(ADMIN_USERNAME);
  await page.getByPlaceholder(/密码/).fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await page.waitForURL((url) => url.pathname === '/tables');

  await page.goto(`${FORM_BASE_URL}/data?definitionCode=${formCode}`);
  await expect(page.getByText('共 5 条')).toBeVisible();

  // 5) between：金额区间 60..210 → 3 条（100/150/200）
  await chooseField(page, '金额');
  await chooseOp(page, '介于');
  await page.locator('#filter-between-from').fill('60');
  await page.locator('#filter-between-to').fill('210');
  await page.getByRole('button', { name: '添加条件' }).click();
  await expect(page.locator('.ant-tag').filter({ hasText: '金额 介于' })).toBeVisible();
  await search(page);
  await expect(page.getByText('共 3 条')).toBeVisible();

  // 6) in（select 选项多选）：阶段属于 新建/完成 → 4 条（排除 hold）
  await removeTag(page, '金额 介于');
  await chooseField(page, '阶段');
  await chooseOp(page, '属于');
  await selectByInputId(page, 'filter-value-multi').click();
  await pickOption(page, '新建');
  await pickOption(page, '完成');
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: '添加条件' }).click();
  await expect(page.locator('.ant-tag').filter({ hasText: '阶段 属于' })).toBeVisible();
  await search(page);
  await expect(page.getByText('共 4 条')).toBeVisible();

  // 7) in（文本标签输入）：备注等于 beta/omega → 2 条（in 为等值匹配，不含 "beta gamma"）
  await removeTag(page, '阶段 属于');
  await chooseField(page, '备注');
  await chooseOp(page, '属于');
  const tags = page.locator('#filter-value-multi');
  await tags.click();
  await tags.fill('beta');
  await tags.press('Enter');
  await tags.fill('omega');
  await tags.press('Enter');
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: '添加条件' }).click();
  await expect(page.locator('.ant-tag').filter({ hasText: '备注 属于' })).toBeVisible();
  await search(page);
  await expect(page.getByText('共 2 条')).toBeVisible();

  // 8) between（日期选择器）：到期日 2026-03-01..2026-12-31 → 3 条
  await removeTag(page, '备注 属于');
  await chooseField(page, '到期日');
  await chooseOp(page, '介于');
  // 日期区间必须渲染 DatePicker（.ant-picker）而非文本输入
  await expect(page.locator('.ant-picker')).toHaveCount(2);
  await page.locator('#filter-between-from').fill('2026-03-01');
  await page.locator('#filter-between-from').press('Enter');
  await page.locator('#filter-between-to').fill('2026-12-31');
  await page.locator('#filter-between-to').press('Enter');
  await page.getByRole('button', { name: '添加条件' }).click();
  await expect(page.locator('.ant-tag').filter({ hasText: '到期日 介于 2026-03-01 ~ 2026-12-31' })).toBeVisible();
  await search(page);
  await expect(page.getByText('共 3 条')).toBeVisible();

  // 9) 标量日期比较：到期日 > 2026-06-01，单值也走 DatePicker → 2 条
  await removeTag(page, '到期日 介于');
  await chooseField(page, '到期日');
  await chooseOp(page, '大于');
  await expect(page.locator('.ant-picker')).toHaveCount(1);
  await page.locator('#filter-value-date').fill('2026-06-01');
  await page.locator('#filter-value-date').press('Enter');
  await page.getByRole('button', { name: '添加条件' }).click();
  await expect(page.locator('.ant-tag').filter({ hasText: '到期日 大于 2026-06-01' })).toBeVisible();
  await search(page);
  await expect(page.getByText('共 2 条')).toBeVisible();

  // 10) API 对账：同一 filters 经网关命中数与 UI 一致
  const apiResponse = await request.get(
    `${API_BASE_URL}/form/data/page?current=1&size=10&definitionCode=${encodeURIComponent(
      formCode,
    )}&filters=${encodeURIComponent(JSON.stringify([{ field: 'memo', op: 'in', value: ['beta', 'omega'] }]))}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  expect(apiResponse.ok()).toBeTruthy();
  const envelope = (await apiResponse.json()) as { code: number; data: { total: number | string } };
  expect(envelope.code).toBe(0);
  expect(Number(envelope.data.total)).toBe(2);

  const dateResponse = await request.get(
    `${API_BASE_URL}/form/data/page?current=1&size=10&definitionCode=${encodeURIComponent(
      formCode,
    )}&filters=${encodeURIComponent(
      JSON.stringify([{ field: 'due_date', op: 'between', value: ['2026-03-01', '2026-12-31'] }]),
    )}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  expect(dateResponse.ok()).toBeTruthy();
  const dateEnvelope = (await dateResponse.json()) as {
    code: number;
    data: { total: number | string };
  };
  expect(dateEnvelope.code).toBe(0);
  expect(Number(dateEnvelope.data.total)).toBe(3);
});
