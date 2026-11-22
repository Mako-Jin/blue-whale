// 定时任务页组件测试：工具栏权限裁剪 / 批量选择后批量启用 / 告警通道与恢复标识 / 拓扑页签渲染
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import JobList from './JobList';
import {
  batchChangeJobStatus,
  getJobGraph,
  pageJobAlert,
  pageJobInfo,
  pageJobLog,
  type JobAlertVO,
  type JobGraphVO,
  type JobInfoVO,
} from '../../api/job';
import { pageResult, renderPage } from '../../test/test-utils';

vi.mock('../../api/job', () => ({
  pageJobInfo: vi.fn(),
  pageJobLog: vi.fn(),
  pageJobAlert: vi.fn(),
  getJobDependencies: vi.fn(),
  createJob: vi.fn(),
  updateJob: vi.fn(),
  deleteJob: vi.fn(),
  changeJobStatus: vi.fn(),
  runJob: vi.fn(),
  setJobDependencies: vi.fn(),
  batchChangeJobStatus: vi.fn(),
  batchDeleteJobs: vi.fn(),
  downloadJobExport: vi.fn(),
  getJobGraph: vi.fn(),
  validateJobGraph: vi.fn(),
  importJobs: vi.fn(),
  getJobLogStats: vi.fn(),
}));

const ALL_PERMS = [
  'sys:job:list',
  'sys:job:add',
  'sys:job:edit',
  'sys:job:delete',
  'sys:job:run',
  'sys:job:log:list',
];

const jobRow: JobInfoVO = {
  id: '1',
  jobName: '订单同步',
  jobGroup: 'CRM',
  cronExpression: '0 0/5 * * * ?',
  handlerBean: 'httpJobHandler',
  status: 1,
};

const recoveredAlert: JobAlertVO = {
  id: '9',
  jobId: '1',
  jobName: '订单同步',
  level: 'INFO',
  channel: 'DINGTALK,FEISHU',
  recovered: true,
  status: 1,
  message: '任务执行已恢复',
  createTime: '2026-11-19 10:00:00',
};

const graph: JobGraphVO = {
  nodes: [
    { id: '1', jobName: '上游任务', status: 1 },
    { id: '2', jobName: '下游任务', status: 0 },
  ],
  edges: [{ from: '1', to: '2' }],
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(pageJobInfo).mockResolvedValue(pageResult([jobRow]));
  vi.mocked(pageJobLog).mockResolvedValue(pageResult([]));
  vi.mocked(pageJobAlert).mockResolvedValue(pageResult([recoveredAlert]));
  vi.mocked(getJobGraph).mockResolvedValue(graph);
  vi.mocked(batchChangeJobStatus).mockResolvedValue({ successCount: 1, failures: [] });
});

describe('JobList 定时任务页', () => {
  it('仅有列表权限时隐藏新增、导入与批量操作，保留导出', async () => {
    renderPage(<JobList />, ['sys:job:list']);

    await screen.findByText('订单同步');
    expect(screen.getByRole('button', { name: '导出全部' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新增任务' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '导入' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '批量启用' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '批量删除' })).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: '执行统计' })).not.toBeInTheDocument();
  });

  it('选中任务后批量启用可用，并按选中 ID 提交', async () => {
    renderPage(<JobList />, ALL_PERMS);
    await screen.findByText('订单同步');

    const batchEnable = screen.getByRole('button', { name: '批量启用' });
    expect(batchEnable).toBeDisabled();

    const rowCheckbox = document.querySelector(
      'tbody input[type="checkbox"]',
    ) as HTMLInputElement | null;
    expect(rowCheckbox).not.toBeNull();
    fireEvent.click(rowCheckbox as HTMLInputElement);

    await waitFor(() => expect(batchEnable).toBeEnabled());
    fireEvent.click(batchEnable);

    await waitFor(() => expect(batchChangeJobStatus).toHaveBeenCalledWith(['1'], 1));
  });

  it('告警记录拆分多通道并显示恢复标识', async () => {
    renderPage(<JobList />, ALL_PERMS);
    await screen.findByText('订单同步');

    fireEvent.click(screen.getByRole('tab', { name: '告警记录' }));

    expect(await screen.findByText('DINGTALK')).toBeInTheDocument();
    expect(screen.getByText('FEISHU')).toBeInTheDocument();
    expect(screen.getByText('已恢复')).toBeInTheDocument();
    await waitFor(() => expect(pageJobAlert).toHaveBeenCalled());
  });

  it('依赖拓扑页签加载并渲染节点', async () => {
    renderPage(<JobList />, ALL_PERMS);
    await screen.findByText('订单同步');

    fireEvent.click(screen.getByRole('tab', { name: '依赖拓扑' }));

    expect(await screen.findByText('上游任务')).toBeInTheDocument();
    expect(screen.getByText('下游任务')).toBeInTheDocument();
    expect(getJobGraph).toHaveBeenCalled();
  });

  it('有日志权限时渲染执行统计页签', async () => {
    renderPage(<JobList />, ALL_PERMS);
    await screen.findByText('订单同步');

    expect(screen.getByRole('tab', { name: '执行统计' })).toBeInTheDocument();
  });
});
