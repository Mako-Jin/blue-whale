// 执行统计页签测试：概览合计与成功率、按天明细、异常类型 TopN、样本截断与指定任务提示
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { JobStatsPanel } from './JobStatsPanel';
import { getJobLogStats, type JobLogStatsVO } from '../../api/job';
import { renderPage } from '../../test/test-utils';

vi.mock('../../api/job', () => ({
  getJobLogStats: vi.fn(),
}));

const stats: JobLogStatsVO = {
  from: '2026-11-12T00:00:00',
  to: '2026-11-18T23:59:59',
  sampled: false,
  totalCount: '20',
  daily: [
    {
      date: '2026-11-18',
      successCount: '18',
      failureCount: '2',
      avgDurationMillis: '320',
      p95DurationMillis: '1200',
    },
  ],
  failureTop: [{ jobId: '21', jobName: '订单下发', failureCount: '9' }],
  durationTop: [
    { jobId: '31', jobName: '分享清理', avgDurationMillis: '5300', executionCount: '4' },
  ],
  errorTypeTop: [{ errorType: 'BizException', count: '7', sampleMessage: '参数错误' }],
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getJobLogStats).mockResolvedValue(stats);
});

describe('JobStatsPanel 执行统计页签', () => {
  it('渲染概览、按天明细与三类 TopN', async () => {
    renderPage(<JobStatsPanel />);

    expect(await screen.findByText('订单下发（#21）')).toBeInTheDocument();
    expect(screen.getByText('20')).toBeInTheDocument();
    expect(screen.getByText('90.0%')).toBeInTheDocument();
    // 加权平均耗时与按天平均耗时同为 320 ms，两处都应渲染
    expect(screen.getAllByText('320 ms')).toHaveLength(2);
    expect(screen.getByText('5.30 s')).toBeInTheDocument();
    expect(screen.getByText('2026-11-18')).toBeInTheDocument();
    expect(screen.getByText('BizException')).toBeInTheDocument();
    expect(screen.getByText('参数错误')).toBeInTheDocument();
  });

  it('样本截断时给出提示', async () => {
    vi.mocked(getJobLogStats).mockResolvedValue({ ...stats, sampled: true });

    renderPage(<JobStatsPanel />);

    expect(await screen.findByText('样本已截断')).toBeInTheDocument();
  });

  it('指定任务查询时按 jobId 传参并提示 TopN 不适用', async () => {
    vi.mocked(getJobLogStats).mockResolvedValue({
      ...stats,
      totalCount: '3',
      daily: [
        {
          date: '2026-11-18',
          successCount: '3',
          failureCount: '0',
          avgDurationMillis: '210',
          p95DurationMillis: '400',
        },
      ],
      failureTop: [],
      durationTop: [],
      errorTypeTop: [],
    });

    renderPage(<JobStatsPanel />);
    // 该用例的返回值没有 TopN，用按天明细确认首次加载完成
    await screen.findByText('2026-11-18');

    fireEvent.change(screen.getByPlaceholderText('任务ID'), { target: { value: '21' } });
    fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));

    await waitFor(() =>
      expect(getJobLogStats).toHaveBeenLastCalledWith(expect.objectContaining({ jobId: '21' })),
    );
    expect(
      await screen.findByText('指定任务时 TopN 不适用，可查看该任务的按天趋势。'),
    ).toBeInTheDocument();
  });
});
