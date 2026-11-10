// 消息中心（站内信收件箱）组件测试：未读过滤 / 标记已读广播与刷新 / 全部切换 / 异常空态
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Inbox, { INAPP_READ_EVENT } from './Inbox';
import { markInAppRead, pageInAppMessages, type InAppMessage } from '../api/inapp';

vi.mock('../api/inapp', () => ({
  pageInAppMessages: vi.fn(),
  markInAppRead: vi.fn(),
}));

const unread: InAppMessage = {
  deliveryId: 'd1',
  messageId: 'm1',
  title: '订单支付成功',
  content: '您的订单 PAY20261028001 已支付',
  bizType: 'PAY',
  readFlag: 0,
  sentAt: '2026-11-01 10:00:00',
};

const read: InAppMessage = {
  deliveryId: 'd2',
  messageId: 'm2',
  title: '账号安全提醒',
  content: '您修改了登录密码',
  readFlag: 1,
  sentAt: '2026-10-31 09:00:00',
};

function mockPage(messages: InAppMessage[]) {
  vi.mocked(pageInAppMessages).mockResolvedValue({
    records: messages,
    total: messages.length,
    size: 10,
    current: 1,
  });
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('Inbox 消息中心', () => {
  it('默认未读过滤：携带 readFlag=0 拉取，未读标记与已读操作互现', async () => {
    mockPage([unread, read]);
    render(<Inbox />);

    expect(await screen.findByText('订单支付成功')).toBeInTheDocument();
    expect(pageInAppMessages).toHaveBeenCalledWith(
      expect.objectContaining({ current: 1, size: 10, readFlag: 0 }),
    );
    // 过滤器按钮与未读角标各出现一次
    expect(screen.getAllByText('未读')).toHaveLength(2);
    expect(screen.getByText('PAY')).toBeInTheDocument();
    expect(screen.getByText('共 2 条')).toBeInTheDocument();

    // 未读行有「标记已读」，已读行没有
    expect(screen.getAllByRole('button', { name: '标记已读' })).toHaveLength(1);
    expect(screen.getByText('账号安全提醒')).toBeInTheDocument();
  });

  it('标记已读：调用接口、广播事件并刷新列表', async () => {
    mockPage([unread]);
    vi.mocked(markInAppRead).mockResolvedValue(undefined);
    const listener = vi.fn();
    window.addEventListener(INAPP_READ_EVENT, listener);

    render(<Inbox />);
    fireEvent.click(await screen.findByRole('button', { name: '标记已读' }));

    await waitFor(() => {
      expect(markInAppRead).toHaveBeenCalledWith('m1');
      expect(listener).toHaveBeenCalled();
    });
    // 标记成功后重新拉取当前页
    await waitFor(() => {
      expect(pageInAppMessages).toHaveBeenCalledTimes(2);
    });
    window.removeEventListener(INAPP_READ_EVENT, listener);
  });

  it('切换「全部」过滤后携带 readFlag=undefined 重新拉取', async () => {
    mockPage([unread, read]);
    render(<Inbox />);

    await screen.findByText('订单支付成功');
    fireEvent.click(screen.getByText('全部'));

    await waitFor(() => {
      expect(pageInAppMessages).toHaveBeenLastCalledWith(
        expect.objectContaining({ current: 1, size: 10, readFlag: undefined }),
      );
    });
  });

  it('接口异常降级为空态提示', async () => {
    vi.mocked(pageInAppMessages).mockRejectedValue(new Error('network down'));
    render(<Inbox />);

    expect(await screen.findByText('暂无未读消息')).toBeInTheDocument();
    expect(screen.getByText('共 0 条')).toBeInTheDocument();
  });
});
