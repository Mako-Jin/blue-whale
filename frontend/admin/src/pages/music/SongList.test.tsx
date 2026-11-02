// 曲库页组件测试：表格渲染 / 操作按钮权限门控 / 版权下架弹窗必填原因
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SongList from './SongList';
import {
  pageAlbums,
  pageArtists,
  listTags,
  pageSongs,
  takedownSong,
  type SongView,
} from '../../api/music';
import { MUSIC_PERMS, pageResult, renderPage } from '../../test/test-utils';

vi.mock('../../api/music', () => ({
  pageSongs: vi.fn(),
  pageArtists: vi.fn(),
  pageAlbums: vi.fn(),
  listTags: vi.fn(),
  createSong: vi.fn(),
  updateSong: vi.fn(),
  deleteSong: vi.fn(),
  publishSong: vi.fn(),
  offlineSong: vi.fn(),
  takedownSong: vi.fn(),
}));
vi.mock('../../api/file', () => ({ uploadFile: vi.fn() }));

const draftSong: SongView = {
  id: 2,
  title: '稻香',
  subtitle: '回忆版',
  artistId: 1,
  visibility: 'PUBLIC',
  publishState: 'DRAFT',
  copyrightState: 'VALID',
  playCount: 0,
};

function mockApis(songs: SongView[]) {
  vi.mocked(pageSongs).mockResolvedValue(pageResult(songs));
  vi.mocked(pageArtists).mockResolvedValue(pageResult([{ id: 1, name: '周杰伦' }]));
  vi.mocked(pageAlbums).mockResolvedValue(pageResult([]));
  vi.mocked(listTags).mockResolvedValue([]);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('SongList 曲库页', () => {
  it('渲染列表：歌曲标题、歌手名映射、状态与版权标签', async () => {
    mockApis([
      {
        ...draftSong,
        id: 1,
        title: '晴天',
        publishState: 'PUBLISHED',
        copyrightState: 'VALID',
        playCount: 42,
        publishedAt: '2026-10-01 10:00:00',
      },
    ]);
    renderPage(<SongList />, MUSIC_PERMS);

    expect(await screen.findByText('晴天')).toBeInTheDocument();
    expect(screen.getByText('周杰伦')).toBeInTheDocument();
    expect(screen.getByText('已上架')).toBeInTheDocument();
    expect(screen.getByText('有效')).toBeInTheDocument();
    expect(screen.getByText('公开')).toBeInTheDocument();
    expect(screen.getByText('42')).toBeInTheDocument();
    expect(screen.getByText('曲库')).toBeInTheDocument();
  });

  it('副标题随标题渲染', async () => {
    mockApis([draftSong]);
    renderPage(<SongList />, MUSIC_PERMS);

    expect(await screen.findByText('稻香')).toBeInTheDocument();
    expect(screen.getByText('回忆版')).toBeInTheDocument();
  });

  it('草稿行在上架权限下显示上架/编辑/删除/版权下架按钮', async () => {
    mockApis([draftSong]);
    renderPage(<SongList />, MUSIC_PERMS);

    await screen.findByText('稻香');
    expect(screen.getByRole('button', { name: '上架' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '编辑' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '删除' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '版权下架' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新增歌曲' })).toBeInTheDocument();
  });

  it('无权限时不渲染操作按钮', async () => {
    mockApis([draftSong]);
    renderPage(<SongList />, []);

    await screen.findByText('稻香');
    expect(screen.queryByRole('button', { name: '上架' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '编辑' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '删除' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '版权下架' })).not.toBeInTheDocument();
  });

  it('版权下架必须填写原因，填写后携带原因提交', async () => {
    mockApis([draftSong]);
    vi.mocked(takedownSong).mockResolvedValue(draftSong);
    renderPage(<SongList />, MUSIC_PERMS);

    fireEvent.click(await screen.findByRole('button', { name: '版权下架' }));
    expect(await screen.findByText(/版权下架将强制下线并拒绝播放/)).toBeInTheDocument();

    // 原因为空：不允许提交
    fireEvent.click(screen.getByRole('button', { name: '确认下架' }));
    await waitFor(() => {
      expect(takedownSong).not.toHaveBeenCalled();
    });

    fireEvent.change(screen.getByPlaceholderText('如：权利人投诉 / 授权到期'), {
      target: { value: '权利人投诉' },
    });
    fireEvent.click(screen.getByRole('button', { name: '确认下架' }));
    await waitFor(() => {
      expect(takedownSong).toHaveBeenCalledWith(2, '权利人投诉');
    });
  });
});
