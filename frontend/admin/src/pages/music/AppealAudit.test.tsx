// 版权申诉复核页组件测试：列表渲染 / 复核弹窗（通过 / 驳回）意见透传
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AppealAudit from './AppealAudit';
import {
  approveAppeal,
  pageAppeals,
  rejectAppeal,
  type AppealView,
} from '../../api/music';
import { MUSIC_PERMS, pageResult, renderPage } from '../../test/test-utils';

vi.mock('../../api/music', () => ({
  pageAppeals: vi.fn(),
  approveAppeal: vi.fn(),
  rejectAppeal: vi.fn(),
}));

const pendingAppeal: AppealView = {
  id: 7,
  songId: 2,
  songTitle: '稻香',
  applicantId: 1001,
  reason: '本人原创',
  status: 'PENDING',
  createTime: '2026-10-31 09:00:00',
};

const approvedAppeal: AppealView = {
  ...pendingAppeal,
  id: 8,
  songId: 3,
  status: 'APPROVED',
  reviewOpinion: '证据充分',
  reviewedAt: '2026-10-31 10:00:00',
};

beforeEach(() => {
  vi.clearAllMocks();
});

function mockPage(appeals: AppealView[]) {
  vi.mocked(pageAppeals).mockResolvedValue(pageResult(appeals));
}

describe('AppealAudit 版权申诉复核页', () => {
  it('渲染列表：申诉理由、状态标签、待复核显示操作、已复核显示文案', async () => {
    mockPage([pendingAppeal, approvedAppeal]);
    renderPage(<AppealAudit />, MUSIC_PERMS);

    // Typography ellipsis 会渲染隐藏测量副本，用 findAllByText 容忍重复
    expect((await screen.findAllByText('本人原创')).length).toBeGreaterThan(0);
    expect(screen.getByText('待复核')).toBeInTheDocument();
    expect(screen.getByText('已通过')).toBeInTheDocument();
    expect(screen.getByText('证据充分')).toBeInTheDocument();

    // 待复核行有通过/驳回，已复核行显示「已复核」（antd 两字按钮会插空格，用正则匹配）
    expect(screen.getAllByRole('button', { name: /通\s*过/ })).toHaveLength(1);
    expect(screen.getAllByRole('button', { name: /驳\s*回/ })).toHaveLength(1);
    expect(screen.getByText('已复核')).toBeInTheDocument();
  });

  it('通过申诉：弹窗展示说明，复核意见随提交透传', async () => {
    mockPage([pendingAppeal]);
    vi.mocked(approveAppeal).mockResolvedValue({ ...pendingAppeal, status: 'APPROVED' });
    renderPage(<AppealAudit />, MUSIC_PERMS);

    fireEvent.click(await screen.findByRole('button', { name: /通\s*过/ }));
    expect(
      await screen.findByText(/版权恢复为「有效」，但发布状态保持「已下架」/),
    ).toBeInTheDocument();

    const modal = screen.getByText('通过申诉').closest('.ant-modal') as HTMLElement;
    fireEvent.change(screen.getByPlaceholderText('复核意见（可选，最多 500 字）'), {
      target: { value: '情况属实' },
    });
    fireEvent.click(within(modal).getByRole('button', { name: /通\s*过/ }));

    await waitFor(() => {
      expect(approveAppeal).toHaveBeenCalledWith(7, '情况属实');
    });
  });

  it('驳回申诉：意见为空时提交 undefined', async () => {
    mockPage([pendingAppeal]);
    vi.mocked(rejectAppeal).mockResolvedValue({ ...pendingAppeal, status: 'REJECTED' });
    renderPage(<AppealAudit />, MUSIC_PERMS);

    fireEvent.click(await screen.findByRole('button', { name: /驳\s*回/ }));
    expect(await screen.findByText('驳回申诉')).toBeInTheDocument();

    const modal = screen.getByText('驳回申诉').closest('.ant-modal') as HTMLElement;
    fireEvent.click(within(modal).getByRole('button', { name: /驳\s*回/ }));

    await waitFor(() => {
      expect(rejectAppeal).toHaveBeenCalledWith(7, undefined);
    });
    expect(approveAppeal).not.toHaveBeenCalled();
  });

  it('复核成功后关闭弹窗并刷新列表', async () => {
    mockPage([pendingAppeal]);
    vi.mocked(approveAppeal).mockResolvedValue({ ...pendingAppeal, status: 'APPROVED' });
    renderPage(<AppealAudit />, MUSIC_PERMS);

    fireEvent.click(await screen.findByRole('button', { name: /通\s*过/ }));
    const modal = (await screen.findByText('通过申诉')).closest('.ant-modal') as HTMLElement;
    fireEvent.click(within(modal).getByRole('button', { name: /通\s*过/ }));

    // 弹窗关闭 + 列表刷新（初始 + reload = 2 次）
    await waitFor(() => {
      expect(screen.queryByText('通过申诉')).not.toBeInTheDocument();
    });
    await waitFor(() => {
      expect(pageAppeals).toHaveBeenCalledTimes(2);
    });
  });
});
