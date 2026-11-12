// 评论审核页组件测试：列表渲染 / 操作按钮权限门控 / 通过审核交互
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import CommentAudit from './CommentAudit';
import { approveComment, pageCommentAudit, type CommentView } from '../../api/music';
import { MUSIC_PERMS, pageResult, renderPage } from '../../test/test-utils';

vi.mock('../../api/music', () => ({
  pageCommentAudit: vi.fn(),
  approveComment: vi.fn(),
  hideComment: vi.fn(),
}));

const pendingComment: CommentView = {
  id: '5',
  songId: '9',
  userId: '1001',
  parentId: '0',
  content: '这首歌太好听了',
  status: 'PENDING',
  createTime: '2026-10-31 12:00:00',
};

beforeEach(() => {
  vi.clearAllMocks();
});

function mockPage(comments: CommentView[]) {
  vi.mocked(pageCommentAudit).mockResolvedValue(pageResult(comments));
}

describe('CommentAudit 评论审核页', () => {
  it('渲染列表：评论正文、状态标签与无歌名时的歌曲 ID 回退', async () => {
    mockPage([pendingComment]);
    renderPage(<CommentAudit />, MUSIC_PERMS);

    expect(await screen.findByText('这首歌太好听了')).toBeInTheDocument();
    expect(screen.getByText('待审核')).toBeInTheDocument();
    expect(screen.getByText('歌曲 9')).toBeInTheDocument();
    expect(screen.getByText('1001')).toBeInTheDocument();
    expect(screen.getByText('2026-10-31 12:00:00')).toBeInTheDocument();
  });

  it('有审核权限时显示通过/隐藏按钮', async () => {
    mockPage([pendingComment]);
    renderPage(<CommentAudit />, MUSIC_PERMS);

    await screen.findByText('这首歌太好听了');
    expect(screen.getByRole('button', { name: '通过' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '隐藏' })).toBeInTheDocument();
  });

  it('无审核权限时不渲染操作按钮', async () => {
    mockPage([pendingComment]);
    renderPage(<CommentAudit />, []);

    await screen.findByText('这首歌太好听了');
    expect(screen.queryByRole('button', { name: '通过' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '隐藏' })).not.toBeInTheDocument();
  });

  it('点击通过后调用审核接口并刷新列表', async () => {
    mockPage([pendingComment]);
    vi.mocked(approveComment).mockResolvedValue({ ...pendingComment, status: 'VISIBLE' });
    renderPage(<CommentAudit />, MUSIC_PERMS);

    fireEvent.click(await screen.findByRole('button', { name: '通过' }));

    await waitFor(() => {
      expect(approveComment).toHaveBeenCalledWith('5');
    });
    // 通过成功后触发 reload（初始加载 + 刷新 = 2 次）
    await waitFor(() => {
      expect(pageCommentAudit).toHaveBeenCalledTimes(2);
    });
  });

  it('已可见评论不显示通过按钮，已隐藏评论不显示隐藏按钮', async () => {
    mockPage([
      { ...pendingComment, id: '6', status: 'VISIBLE' },
      { ...pendingComment, id: '7', status: 'HIDDEN' },
    ]);
    renderPage(<CommentAudit />, MUSIC_PERMS);

    await screen.findByText('可见');
    expect(screen.getByText('已隐藏')).toBeInTheDocument();
    // VISIBLE 行只显示「隐藏」，HIDDEN 行只显示「通过」
    expect(screen.queryAllByRole('button', { name: '通过' })).toHaveLength(1);
    expect(screen.queryAllByRole('button', { name: '隐藏' })).toHaveLength(1);
  });
});
