// 系统公告管理页组件测试：列表渲染 / 权限门控 / 发布弹窗 / 撤回 / 删除
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AnnouncementList from './AnnouncementList';
import {
  deleteAnnouncement,
  pageAnnouncements,
  publishAnnouncement,
  revokeAnnouncement,
  type AnnouncementVO,
} from '../../api/message';
import { pageResult, renderPage } from '../../test/test-utils';

vi.mock('../../api/message', () => ({
  pageAnnouncements: vi.fn(),
  publishAnnouncement: vi.fn(),
  revokeAnnouncement: vi.fn(),
  deleteAnnouncement: vi.fn(),
}));

const ANNOUNCE_PERMS = [
  'sys:message:announce:publish',
  'sys:message:announce:revoke',
  'sys:message:announce:delete',
];

const published: AnnouncementVO = {
  id: '1',
  recordId: '1001',
  title: '平台维护通知',
  content: '今晚 22:00 平台维护',
  bizType: 'SYSTEM_ANNOUNCE',
  status: 'PUBLISHED',
  publisherId: '9',
  publishTime: '2026-11-02 20:00:00',
};

const revoked: AnnouncementVO = {
  id: '2',
  title: '已撤回公告',
  content: '此公告已撤回',
  status: 'REVOKED',
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(pageAnnouncements).mockResolvedValue(pageResult([published, revoked]));
});

describe('AnnouncementList 系统公告页', () => {
  it('渲染列表：标题、正文、业务类型、状态标签与发布时间', async () => {
    renderPage(<AnnouncementList />, ANNOUNCE_PERMS);

    expect(await screen.findByText('平台维护通知')).toBeInTheDocument();
    expect(screen.getByText('今晚 22:00 平台维护')).toBeInTheDocument();
    expect(screen.getByText('SYSTEM_ANNOUNCE')).toBeInTheDocument();
    expect(screen.getByText('已发布')).toBeInTheDocument();
    expect(screen.getByText('已撤回')).toBeInTheDocument();
    expect(screen.getByText('2026-11-02 20:00:00')).toBeInTheDocument();
    expect(screen.getByText('系统公告')).toBeInTheDocument();
  });

  it('无权限时隐藏发布/撤回/删除按钮', async () => {
    renderPage(<AnnouncementList />, []);

    await screen.findByText('平台维护通知');
    expect(screen.queryByRole('button', { name: '发布公告' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /撤\s*回/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /删\s*除/ })).not.toBeInTheDocument();
  });

  it('已撤回公告不显示撤回按钮，仅保留删除', async () => {
    renderPage(<AnnouncementList />, ANNOUNCE_PERMS);

    await screen.findByText('已撤回公告');
    // 只有已发布行有「撤回」，共 1 个；两条行各有一个「删除」
    expect(screen.getAllByRole('button', { name: /撤\s*回/ })).toHaveLength(1);
    expect(screen.getAllByRole('button', { name: /删\s*除/ })).toHaveLength(2);
  });

  it('发布公告：弹窗必填校验拦截空标题，填写后提交并刷新', async () => {
    vi.mocked(publishAnnouncement).mockResolvedValue(published);
    renderPage(<AnnouncementList />, ANNOUNCE_PERMS);

    fireEvent.click(await screen.findByRole('button', { name: '发布公告' }));
    const modal = document.querySelector('.ant-modal') as HTMLElement;

    // 空表单直接确认 → 校验拦截，不调用发布接口
    fireEvent.click(within(modal).getByRole('button', { name: /确\s*定/ }));
    expect(publishAnnouncement).not.toHaveBeenCalled();

    fireEvent.change(within(modal).getByPlaceholderText('如 平台维护通知'), {
      target: { value: '平台维护通知' },
    });
    fireEvent.change(within(modal).getByPlaceholderText('面向本租户全体用户的公告内容'), {
      target: { value: '今晚 22:00 平台维护' },
    });
    fireEvent.click(within(modal).getByRole('button', { name: /确\s*定/ }));

    await waitFor(() => {
      expect(publishAnnouncement).toHaveBeenCalledWith({
        title: '平台维护通知',
        content: '今晚 22:00 平台维护',
        bizType: undefined,
      });
    });
    await waitFor(() => {
      expect(pageAnnouncements).toHaveBeenCalledTimes(2);
    });
  });

  it('撤回公告调用撤回接口并刷新', async () => {
    vi.mocked(revokeAnnouncement).mockResolvedValue(undefined);
    renderPage(<AnnouncementList />, ANNOUNCE_PERMS);

    fireEvent.click(await screen.findByRole('button', { name: /撤\s*回/ }));

    await waitFor(() => {
      expect(revokeAnnouncement).toHaveBeenCalledWith('1');
    });
    await waitFor(() => {
      expect(pageAnnouncements).toHaveBeenCalledTimes(2);
    });
  });

  it('删除经确认后调用删除接口', async () => {
    vi.mocked(deleteAnnouncement).mockResolvedValue(undefined);
    renderPage(<AnnouncementList />, ANNOUNCE_PERMS);

    fireEvent.click((await screen.findAllByRole('button', { name: /删\s*除/ }))[0]);
    fireEvent.click(await screen.findByRole('button', { name: 'OK' }));

    await waitFor(() => {
      expect(deleteAnnouncement).toHaveBeenCalledWith('1');
    });
  });
});
