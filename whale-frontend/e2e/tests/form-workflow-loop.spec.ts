// V-01（P1 验证）：表单填报 → 工作流发起 → 待办审批通过 的跨模块闭环。
// 表/表单/流程定义走 API 准备（避开 @dnd-kit 合成 PointerEvent 限制），
// 填报、发起、审批全部走真实 UI；最后用 API 核验实例 businessKey 指向表单数据且字段值一致。

import { expect, test } from '@playwright/test';
import { WORKFLOW_BASE_URL } from '../playwright.config';
import {
  ADMIN_PASSWORD,
  ADMIN_USERNAME,
  cancelWorkflowInstance,
  createFormDefinition,
  createTableDefinition,
  createWorkflowDefinition,
  deleteResource,
  findWorkflowInstanceByTitle,
  formDataDetail,
  login,
  publishTableDefinition,
  tableDefinitionDetail,
  workflowInstanceDetail,
} from './support/api';

const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
const tableCode = `e2e_link_${suffix}`;
const tableName = `E2E跨模块表${suffix}`;
const formCode = `${tableCode}_form`;
const definitionCode = `e2e_link_wf_${suffix}`;
const definitionName = `E2E跨模块流程${suffix}`;
const instanceTitle = `E2E跨模块实例${suffix}`;
const submittedValue = `跨模块填报${suffix}`;

let token = '';
let tableId = '';
let formId = '';
let definitionId = '';
let instanceId = '';
let formDataId = '';

test.afterAll(async ({ request }) => {
  if (!token) {
    return;
  }
  // 尽力清理：运行中实例先撤销，再按 数据 → 表单 → 流程定义 → 表 的顺序删除。
  if (instanceId) {
    const detail = await workflowInstanceDetail(request, token, instanceId).catch(() => null);
    if (detail && detail.status === 0) {
      await cancelWorkflowInstance(request, token, instanceId);
    }
  }
  const cleanups = [
    definitionId ? `/workflow/v1/definition/${definitionId}` : '',
    formDataId ? `/form/v1/data/${formDataId}` : '',
    formId ? `/form/v1/definition/${formId}` : '',
    tableId ? `/form/v1/table-definition/${tableId}` : '',
  ].filter((path) => path.length > 0);
  for (const path of cleanups) {
    await deleteResource(request, token, path);
  }
});

test('表单填报→发起流程→审批通过', async ({ page, request }) => {
  // 1) API 准备：建表 → 发布 → 建表单 → 建入口节点绑定表单的流程定义
  token = await login(request);
  const table = await createTableDefinition(request, token, {
    tableCode,
    tableName,
    version: 1,
    status: 1,
    columns: [
      {
        columnCode: 'e2e_name',
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
    name: `E2E跨模块表单${suffix}`,
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

  const definitionJson = JSON.stringify({
    type: 'stateMachine',
    nodes: [
      { key: 'start', type: 'START_EVENT', name: '开始' },
      {
        key: 'form_approve',
        type: 'USER_TASK',
        name: '表单审批',
        assignee: 'admin',
        formId,
        formCode,
      },
      { key: 'end', type: 'END_EVENT', name: '结束' },
    ],
    flows: [
      { key: 'f1', from: 'start', to: 'form_approve' },
      { key: 'f2', from: 'form_approve', to: 'end' },
    ],
  });
  const definition = await createWorkflowDefinition(request, token, {
    code: definitionCode,
    name: definitionName,
    definitionJson,
    version: 1,
    status: 1,
  });
  definitionId = definition.id;

  // 2) UI 登录工作流设计器
  await page.goto(`${WORKFLOW_BASE_URL}/start`);
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: '蓝鲸工作流设计器' })).toBeVisible();
  await page.getByPlaceholder(/用户名/).fill(ADMIN_USERNAME);
  await page.getByPlaceholder(/密码/).fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await page.waitForURL((url) => url.pathname === '/definitions');

  // 3) 流程发起：选定义 → 填业务标题 → 填绑定表单 → 发起
  await page.getByRole('menuitem', { name: '流程发起' }).click();
  await expect(page).toHaveURL(/\/start$/);
  const definitionSelect = page.getByRole('combobox');
  await definitionSelect.click();
  await definitionSelect.fill(definitionCode);
  await page.locator('.ant-select-item-option').filter({ hasText: definitionCode }).first().click();
  await page.getByPlaceholder('请输入业务标题').fill(instanceTitle);
  // 入口节点绑定表单后，发起页应渲染出表单字段（若未绑定则不会出现）
  await expect(page.getByText(`表单信息：E2E跨模块表单${suffix}`)).toBeVisible();
  await page.getByLabel('姓名').fill(submittedValue);
  await page.getByRole('button', { name: '发起流程' }).click();
  await expect(page.getByText('流程发起成功').first()).toBeVisible();

  // 4) API 核验实例：businessKey 指向表单数据，字段值与 UI 填报一致
  const started = await findWorkflowInstanceByTitle(request, token, instanceTitle);
  instanceId = started.id;
  expect(started.status).toBe(0);
  expect(started.currentNodeKey).toBe('form_approve');
  expect(started.businessKey).toBeTruthy();
  formDataId = started.businessKey as string;

  const formData = await formDataDetail(request, token, formDataId);
  expect(formData.definitionCode).toBe(formCode);
  expect((formData.fields ?? {}).e2e_name).toBe(submittedValue);

  const initialDetail = await workflowInstanceDetail(request, token, instanceId);
  expect(initialDetail.tasks).toHaveLength(1);
  expect(initialDetail.tasks?.[0]?.nodeKey).toBe('form_approve');

  // 5) UI 我的待办：审批抽屉应能看到同一表单数据 → 同意
  await page.getByRole('menuitem', { name: '我的待办' }).click();
  await expect(page).toHaveURL(/\/tasks$/);
  const taskRow = page.locator('tbody tr').filter({ hasText: instanceTitle });
  await expect(taskRow).toHaveCount(1);
  await taskRow.getByRole('button', { name: /审\s*批/ }).click();

  const drawer = page.locator('.ant-drawer-content');
  await expect(drawer.getByText('表单审批')).toBeVisible();
  // 审批抽屉按入口节点绑定的表单渲染只读数据，字段值应与发起时一致
  await expect(drawer.getByRole('textbox', { name: '* 姓名' })).toHaveValue(submittedValue);
  await drawer.getByPlaceholder('请输入审批意见（可选）').fill('跨模块审批通过');
  await drawer.getByRole('button', { name: /同\s*意/ }).click();
  await expect(page.locator('.ant-message').getByText('已同意').first()).toBeVisible();

  // 6) API 复核：实例通过，任务轨迹完成，表单数据仍可读
  await expect
    .poll(async () => (await workflowInstanceDetail(request, token, instanceId)).status)
    .toBe(1);
  const finished = await workflowInstanceDetail(request, token, instanceId);
  expect(finished.tasks).toHaveLength(1);
  expect(finished.tasks?.[0]?.status).toBe(1);
  const persisted = await formDataDetail(request, token, formDataId);
  expect((persisted.fields ?? {}).e2e_name).toBe(submittedValue);
});
