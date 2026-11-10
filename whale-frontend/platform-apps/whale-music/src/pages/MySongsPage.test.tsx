// 我的作品页组件测试：列表渲染 / 编辑弹窗 / 版权申诉弹窗必填理由 / 删除确认
import { App } from 'antd';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import MySongsPage from './MySongsPage';
import {
  deleteMySong,
  listTags,
  pageAlbums,
  pageArtists,
  pageMySongs,
  submitAppeal,
  type SongView,
} from '../api/music';

vi.mock('../api/music', () => ({
  pageMySongs: vi.fn(),
  pageArtists: vi.fn(),
  pageAlbums: vi.fn(),
  listTags: vi.fn(),
  updateMySong: vi.fn(),
  deleteMySong: vi.fn(),
  submitAppeal: vi.fn(),
}));

const publishedSong: SongView = {
  id: 1,
  title: '晴天',
  artistId: 1,
  audioFileId: 11,
  durationMs: 269000,
  visibility: 'PUBLIC',
  publishState: 'PUBLISHED',
  copyrightState: 'VALID',
  playCount: 42,
};

const takedownSong: SongView = {
  ...publishedSong,
  id: 2,
  title: '七里香',
  publishState: 'OFFLINE',
  copyrightState: 'TAKEDOWN',
  takedownReason: '授权到期',
};

function renderPage(songs: SongView[]) {
  vi.mocked(pageMySongs).mockResolvedValue({
    records: songs,
    total: songs.length,
    size: 10,
    current: 1,
  });
  vi.mocked(pageArtists).mockResolvedValue({
    records: [{ id: 1, name: '周杰伦' }],
    total: 1,
    size: 200,
    current: 1,
  });
  vi.mocked(pageAlbums).mockResolvedValue({
    records: [],
    total: 0,
    size: 200,
    current: 1,
  });
  vi.mocked(listTags).mockResolvedValue([]);
  return render(
    <App>
      <MemoryRouter>
        <MySongsPage />
      </MemoryRouter>
    </App>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('MySongsPage 我的作品页', () => {
  it('渲染列表：标题、歌手、状态与版权标签', async () => {
    renderPage([publishedSong]);

    expect(await screen.findByText('晴天')).toBeInTheDocument();
    expect(screen.getByText('周杰伦')).toBeInTheDocument();
    expect(screen.getByText('已上架')).toBeInTheDocument();
    expect(screen.getByText('有效')).toBeInTheDocument();
    expect(screen.getByText('公开')).toBeInTheDocument();
    expect(screen.getByText('4:29')).toBeInTheDocument();
    expect(screen.getByText('我的作品')).toBeInTheDocument();
  });

  it('已上架公开作品显示查看详情，版权下架作品显示申诉按钮', async () => {
    renderPage([publishedSong, takedownSong]);

    await screen.findByText('晴天');
    expect(screen.getAllByText('查看详情')).toHaveLength(1);
    expect(screen.getByText('申诉')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '编辑' })).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: '删除' })).toHaveLength(2);
  });

  it('点击编辑打开弹窗并回填标题', async () => {
    renderPage([publishedSong]);

    fireEvent.click((await screen.findAllByRole('button', { name: '编辑' }))[0]);
    expect(await screen.findByText('编辑作品：晴天')).toBeInTheDocument();
    expect((screen.getByLabelText('歌曲标题') as HTMLInputElement).value).toBe('晴天');
  });

  it('版权申诉：理由为空不允许提交，填写后携带理由调用接口', async () => {
    renderPage([takedownSong]);
    vi.mocked(submitAppeal).mockResolvedValue({
      id: 9,
      songId: 2,
      applicantId: 1,
      reason: '我持有授权',
      status: 'PENDING',
    });

    fireEvent.click(await screen.findByRole('button', { name: '申诉' }));
    expect(await screen.findByText('版权申诉：七里香')).toBeInTheDocument();
    expect(screen.getByText('下架原因：授权到期')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '提交申诉' }));
    await waitFor(() => {
      expect(submitAppeal).not.toHaveBeenCalled();
    });

    fireEvent.change(
      screen.getByPlaceholderText('请说明版权归属或授权情况，便于运营复核（1~500 字）'),
      { target: { value: '我持有授权' } },
    );
    fireEvent.click(screen.getByRole('button', { name: '提交申诉' }));
    await waitFor(() => {
      expect(submitAppeal).toHaveBeenCalledWith(2, '我持有授权');
    });
  });

  it('删除作品经确认后调用删除接口', async () => {
    renderPage([publishedSong]);
    vi.mocked(deleteMySong).mockResolvedValue(undefined);

    fireEvent.click(await screen.findByRole('button', { name: '删除' }));
    fireEvent.click(await screen.findByRole('button', { name: 'OK' }));

    await waitFor(() => {
      expect(deleteMySong).toHaveBeenCalledWith(1);
    });
  });
});
