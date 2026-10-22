// 用例 C（P0-3 第二批）：发起流程 → 首节点审批 → 会签（ALL，两人）→ 通过。
// 流程定义走 API 创建（含 USER_TASK + COUNTER_SIGN 节点），其余步骤（发起/待办审批/实例列表）全部走真实 UI。

import { expect, test } from '@playwright/test';
import { WORKFLOW_BASE_URL } from '../playwright.config';
import {
  ADMIN_PASSWORD,
  ADMIN_USERNAME,
  cancelWorkflowInstance,
  createWorkflowDefinition,
  deleteResource,
  findWorkflowInstanceByTitle,
  login,
  workflowInstanceDetail,
} from './support/api';

const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
const definitionCode = `e2e_wf_${suffix}`;
const definitionName = `E2E会签流程${suffix}`;
const instanceTitle = `E2E会签实例${suffix}`;

/** 规范化流程定义：开始 → 部门审批（单人）→ 会签审批（ALL，两人）→ 结束 */
const definitionJson = JSON.stringify({
  type: 'stateMachine',
  nodes: [
    { key: 'start', type: 'START_EVENT', name: '开始' },
    { key: 'dept_approve', type: 'USER_TASK', name: '部门审批', assignee: 'admin' },
    {
      key: 'counter_sign',
      type: 'COUNTER_SIGN',
      name: '会签审批',
      assignees: ['admin', 'e2e_signer'],
      counterSignPolicy: 'ALL',
    },
    { key: 'end', type: 'END_EVENT', name: '结束' },
  ],
  flows: [
    { key: 'f1', from: 'start', to: 'dept_approve' },
    { key: 'f2', from: 'dept_approve', to: 'counter_sign' },
    { key: 'f3', from: 'counter_sign', to: 'end' },
  ],
});

let token = '';
let definitionId = '';
let instanceId = '';

test.afterAll(async ({ request }) => {
  if (!token) {
    return;
  }
  // 尽力清理：运行中则撤销，避免残留待办干扰后续执行；失败不影响用例结论
  if (instanceId) {
    const detail = await workflowInstanceDetail(request, token, instanceId).catch(() => null);
    if (detail && detail.status === 0) {
      await cancelWorkflowInstance(request, token, instanceId);
    }
  }
  if (definitionId) {
    await deleteResource(request, token, `/workflow/definition/${definitionId}`);
  }
});

test('发起流程→首节点审批→会签→通过', async ({ page, request }) => {
  // 1) API 建流程定义（含 USER_TASK + COUNTER_SIGN ALL）
  token = await login(request);
  const definition = await createWorkflowDefinition(request, token, {
    code: definitionCode,
    name: definitionName,
    definitionJson,
    version: 1,
    status: 1,
  });
  definitionId = definition.id;

  // 2) 未登录访问发起页 → 跳登录 → 登录成功进入流程定义页
  await page.goto(`${WORKFLOW_BASE_URL}/start`);
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: '蓝鲸工作流设计器' })).toBeVisible();
  await page.getByPlaceholder(/用户名/).fill(ADMIN_USERNAME);
  await page.getByPlaceholder(/密码/).fill(ADMIN_PASSWORD);
  // antd 会在两个汉字间插入空格（登 录），故用正则匹配
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await page.waitForURL((url) => url.pathname === '/definitions');

  // 3) 流程发起：选定义 → 填业务标题 → 发起
  await page.getByRole('menuitem', { name: '流程发起' }).click();
  await expect(page).toHaveURL(/\/start$/);
  const definitionSelect = page.getByRole('combobox');
  await definitionSelect.click();
  await definitionSelect.fill(definitionCode);
  await page.locator('.ant-select-item-option').filter({ hasText: definitionCode }).first().click();
  await page.getByPlaceholder('请输入业务标题').fill(instanceTitle);
  await page.getByRole('button', { name: '发起流程' }).click();
  await expect(page.getByText('流程发起成功').first()).toBeVisible();

  // 4) API 复核：实例运行中，且首个待办落在部门审批
  const started = await findWorkflowInstanceByTitle(request, token, instanceTitle);
  instanceId = started.id;
  expect(started.status).toBe(0);
  const initialDetail = await workflowInstanceDetail(request, token, instanceId);
  expect(initialDetail.tasks).toHaveLength(1);
  expect(initialDetail.tasks?.[0]?.nodeKey).toBe('dept_approve');

  // 5) 我的待办：审批首节点
  await page.getByRole('menuitem', { name: '我的待办' }).click();
  await expect(page).toHaveURL(/\/tasks$/);
  const taskRow = page.locator('tbody tr').filter({ hasText: instanceTitle });
  await expect(taskRow).toHaveCount(1);
  await taskRow.getByRole('button', { name: /审\s*批/ }).click();

  const drawer = page.locator('.ant-drawer-content');
  await expect(drawer.getByText('部门审批')).toBeVisible();
  await drawer.getByPlaceholder('请输入审批意见（可选）').fill('首节点同意');
  await drawer.getByRole('button', { name: /同\s*意/ }).click();
  await expect(page.locator('.ant-message').getByText('已同意').first()).toBeVisible();

  // 6) 会签节点：ALL 策略展开两条待办，实例仍运行中
  const counterRows = page
    .locator('tbody tr')
    .filter({ hasText: instanceTitle })
    .filter({ hasText: '会签审批' });
  await expect(counterRows).toHaveCount(2);
  await expect
    .poll(async () => (await workflowInstanceDetail(request, token, instanceId)).status)
    .toBe(0);
  await expect
    .poll(async () => (await workflowInstanceDetail(request, token, instanceId)).currentNodeKey)
    .toBe('counter_sign');

  // 7) 会签第一人同意：策略 ALL 未达成，实例保持运行中，仍剩一条待办
  await counterRows
    .first()
    .getByRole('button', { name: /审\s*批/ })
    .click();
  await expect(drawer.getByText('会签策略')).toBeVisible();
  await expect(drawer.getByText('ALL', { exact: true })).toBeVisible();
  await drawer.getByPlaceholder('请输入审批意见（可选）').fill('会签 A 同意');
  await drawer.getByRole('button', { name: /同\s*意/ }).click();
  await expect(page.locator('.ant-message').getByText('已同意').first()).toBeVisible();
  await expect(counterRows).toHaveCount(1);
  await expect
    .poll(async () => (await workflowInstanceDetail(request, token, instanceId)).status)
    .toBe(0);

  // 8) 会签最后一人同意 → 实例已通过，待办清空
  await counterRows
    .first()
    .getByRole('button', { name: /审\s*批/ })
    .click();
  await drawer.getByPlaceholder('请输入审批意见（可选）').fill('会签 B 同意');
  await drawer.getByRole('button', { name: /同\s*意/ }).click();
  await expect(page.locator('.ant-message').getByText('已同意').first()).toBeVisible();
  await expect(taskRow).toHaveCount(0);
  await expect
    .poll(async () => (await workflowInstanceDetail(request, token, instanceId)).status)
    .toBe(1);

  // 9) API 复核任务轨迹：1 条普通审批 + 2 条会签，全部已同意
  const finished = await workflowInstanceDetail(request, token, instanceId);
  const tasks = finished.tasks ?? [];
  expect(tasks).toHaveLength(3);
  const counters = tasks.filter((task) => task.signType === 'COUNTER_SIGN');
  expect(counters).toHaveLength(2);
  expect(counters.every((task) => task.signPolicy === 'ALL')).toBe(true);
  expect(tasks.every((task) => task.status === 1)).toBe(true);

  // 10) 流程实例页：按编码查询，状态展示已通过
  await page.getByRole('menuitem', { name: '流程实例' }).click();
  await expect(page).toHaveURL(/\/instances$/);
  await page.getByPlaceholder('精确匹配').fill(definitionCode);
  await page.getByRole('button', { name: /查\s*询/ }).click();
  const instanceRow = page.locator('tbody tr').filter({ hasText: instanceTitle });
  await expect(instanceRow).toHaveCount(1);
  await expect(instanceRow.getByText('已通过')).toBeVisible();
});
